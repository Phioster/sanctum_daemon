package org.phioster.sanctumd

import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import androidx.activity.viewModels
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.background
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.animation.core.tween
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.model.ServiceType
import org.phioster.sanctumd.service.ServiceRegistry
import org.phioster.sanctumd.ui.DashboardViewModel
import org.phioster.sanctumd.ui.theme.Black
import org.phioster.sanctumd.ui.theme.MatrixGreen
import org.phioster.sanctumd.ui.theme.Mono
import org.phioster.sanctumd.ui.theme.Surface
import org.phioster.sanctumd.ui.theme.SanctumdColors
import org.phioster.sanctumd.ui.common.*
import org.phioster.sanctumd.ui.arr.*
import org.phioster.sanctumd.ui.dashboard.*
import org.phioster.sanctumd.ui.home.*
import org.phioster.sanctumd.ui.jellyfin.*
import org.phioster.sanctumd.ui.ntfy.*
import org.phioster.sanctumd.ui.nzbget.*
import org.phioster.sanctumd.ui.onboarding.*
import org.phioster.sanctumd.ui.prowlarr.*
import org.phioster.sanctumd.ui.search.*
import org.phioster.sanctumd.ui.seerr.*
import org.phioster.sanctumd.ui.services.*
import org.phioster.sanctumd.ui.settings.*
import org.phioster.sanctumd.ui.shortcuts.*
import org.phioster.sanctumd.ui.theme.*
import org.phioster.sanctumd.ui.search.SearchDeepLink

// FragmentActivity (not ComponentActivity) because BiometricPrompt requires it.
class MainActivity : androidx.fragment.app.FragmentActivity() {
    private val vm: DashboardViewModel by viewModels()
    private var backgroundedAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        org.phioster.sanctumd.notify.Notifications.ensureChannels(this)
        org.phioster.sanctumd.widget.StatsHistoryWorker.schedule(this) // daily stat snapshot for trend charts
        lifecycleScope.launch {
            val s = org.phioster.sanctumd.data.NotifyStore(this@MainActivity).currentSettings()
            if (s.enabled) org.phioster.sanctumd.notify.Notifications.schedule(this@MainActivity, s.intervalMin)
            val services = runCatching {
                org.phioster.sanctumd.data.ServiceStore(this@MainActivity).services.first()
            }.getOrDefault(emptyList())
            // Publish launcher shortcuts on cold start too, so they exist before the app
            // is ever unlocked (SanctumdApp keeps them in sync afterwards on any change).
            updateShortcuts(this@MainActivity, services)
            val hasNtfyService = services.any { it.type == ServiceType.NTFY && it.topics.isNotEmpty() }
            if ((s.live && s.ntfyServer.isNotBlank() && s.ntfyTopic.isNotBlank()) || hasNtfyService) {
                org.phioster.sanctumd.notify.NtfyStreamService.start(this@MainActivity)
            }
        }
        vm.setRoute(routeFromIntent(intent)) // launcher shortcut, if any
        // Before the first frame, or the app paints matrix green and then repaints.
        org.phioster.sanctumd.ui.theme.ThemeState.palette = org.phioster.sanctumd.ui.theme.ThemeStore.read(this)
        setContent {
            MaterialTheme(colorScheme = SanctumdColors) {
                AppLockGate(vm = vm, activity = this) { SanctumdApp(vm = vm) }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        routeFromIntent(intent)?.let { vm.setRoute(it) }
    }

    /** The only routes the app navigates to from an intent. It is exported, so any app can send one. */
    private val knownRoutes = setOf("search", "settings", "service")

    private fun routeFromIntent(intent: android.content.Intent?): org.phioster.sanctumd.ui.PendingRoute? {
        val route = intent?.getStringExtra("route")?.takeIf { it in knownRoutes } ?: return null
        return org.phioster.sanctumd.ui.PendingRoute(route, intent.getStringExtra("serviceId"), intent.getStringExtra("itemId"))
    }

    override fun onStop() {
        super.onStop()
        backgroundedAt = android.os.SystemClock.elapsedRealtime()
    }

    /** The player overlay hides its chrome while floating; it reads this. */
    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        org.phioster.sanctumd.ui.player.PipState.inPip.value = isInPictureInPictureMode
    }

    override fun onStart() {
        super.onStart()
        // Re-lock after more than 2 minutes in the background (cold start locks anyway).
        if (backgroundedAt > 0 && android.os.SystemClock.elapsedRealtime() - backgroundedAt > 2 * 60_000L) {
            vm.unlocked.value = false
        }
    }
}

/** (Re)publishes the launcher long-press shortcuts: Search + Settings always, plus
 *  Seerr / Jellyfin when such a service is configured (opens that service directly). */
internal fun updateShortcuts(context: android.content.Context, services: List<ServiceConfig>) {
    fun make(id: String, label: String, longLabel: String, iconRes: Int, extras: Map<String, String>): androidx.core.content.pm.ShortcutInfoCompat {
        val intent = android.content.Intent(context, MainActivity::class.java).setAction(android.content.Intent.ACTION_VIEW)
        extras.forEach { (k, v) -> intent.putExtra(k, v) }
        return androidx.core.content.pm.ShortcutInfoCompat.Builder(context, id)
            .setShortLabel(label)
            .setLongLabel(longLabel)
            .setIcon(androidx.core.graphics.drawable.IconCompat.createWithResource(context, iconRes))
            .setIntent(intent)
            .build()
    }
    val list = mutableListOf<androidx.core.content.pm.ShortcutInfoCompat>()
    list += make("search", "Search", "Search", R.drawable.ic_shortcut_search, mapOf("route" to "search"))
    list += make("settings", "Settings", "Settings", R.drawable.ic_shortcut_settings, mapOf("route" to "settings"))
    services.firstOrNull { it.type == ServiceType.SEERR }?.let {
        list += make("svc_seerr", "Seerr", "Open Seerr", R.drawable.svc_seerr, mapOf("route" to "service", "serviceId" to it.id))
    }
    services.firstOrNull { it.type == ServiceType.JELLYFIN }?.let {
        list += make("svc_jellyfin", "Jellyfin", "Open Jellyfin", R.drawable.svc_jellyfin, mapOf("route" to "service", "serviceId" to it.id))
    }
    runCatching { androidx.core.content.pm.ShortcutManagerCompat.setDynamicShortcuts(context, list.take(4)) }
}

/** Fires the system biometric/credential prompt; [onSuccess] runs on the main executor. */
internal fun showUnlockPrompt(activity: androidx.fragment.app.FragmentActivity, onSuccess: () -> Unit) {
    val prompt = androidx.biometric.BiometricPrompt(
        activity,
        androidx.core.content.ContextCompat.getMainExecutor(activity),
        object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: androidx.biometric.BiometricPrompt.AuthenticationResult) = onSuccess()
        },
    )
    val info = androidx.biometric.BiometricPrompt.PromptInfo.Builder()
        .setTitle("Sanctumd")
        .setSubtitle("unlock")
        .setAllowedAuthenticators(
            androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK or
                androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL,
        )
        .build()
    prompt.authenticate(info)
}

/** Shows a lock screen (and the system prompt) until unlocked, when the app lock is enabled. */
@Composable
internal fun AppLockGate(vm: DashboardViewModel, activity: androidx.fragment.app.FragmentActivity, content: @Composable () -> Unit) {
    val appLock by vm.appLock.collectAsState()
    var unlocked by vm.unlocked
    // Only an explicit false opens the gate; null means the answer is still being read.
    if (appLock == false || unlocked) {
        content()
        return
    }
    LaunchedEffect(Unit) { showUnlockPrompt(activity) { unlocked = true } }
    Column(
        Modifier.fillMaxSize().background(Black),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.Lock, contentDescription = "Locked", tint = MatrixGreen, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(16.dp))
        Text("sanctumd locked", fontFamily = Mono, color = MatrixGreen, fontSize = 16.sp)
        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = { showUnlockPrompt(activity) { unlocked = true } }) {
            Text("unlock", fontFamily = Mono, color = MatrixGreen)
        }
    }
}

@Composable
internal fun SanctumdApp(vm: DashboardViewModel = viewModel()) {
    var addOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ServiceConfig?>(null) }
    var detail by remember { mutableStateOf<ServiceConfig?>(null) }
    var searchOpen by remember { mutableStateOf(false) }
    var searchTerm by remember { mutableStateOf("") }
    var notifOpen by remember { mutableStateOf(false) }
    var statsOpen by remember { mutableStateOf(false) }
    var detailFromSearch by remember { mutableStateOf(false) } // service opened from search results
    var searchDeepLink by remember { mutableStateOf<SearchDeepLink?>(null) }

    // Back from a service returns to where it was opened from (search stays search).
    val closeDetail = {
        detail = null
        searchDeepLink = null
        if (detailFromSearch) { detailFromSearch = false; searchOpen = true }
    }
    val editorOpen = addOpen || editing != null
    BackHandler(enabled = editorOpen || detail != null || searchOpen || notifOpen || statsOpen) {
        when {
            editorOpen -> { addOpen = false; editing = null }
            detail != null -> closeDetail()
            notifOpen -> notifOpen = false
            statsOpen -> statsOpen = false
            else -> searchOpen = false
        }
    }

    // Keep the launcher long-press shortcuts in sync with the configured services.
    val shortcutCtx = LocalContext.current
    val allServicesForShortcuts by vm.services.collectAsState()
    LaunchedEffect(allServicesForShortcuts) { updateShortcuts(shortcutCtx, allServicesForShortcuts) }

    // Route a tap on a launcher shortcut to the matching screen, once.
    val pendingRoute by vm.pendingRoute.collectAsState()
    LaunchedEffect(pendingRoute) {
        val p = pendingRoute ?: return@LaunchedEffect
        when (p.kind) {
            "search" -> { addOpen = false; editing = null; detail = null; searchTerm = ""; searchOpen = true }
            "settings" -> { addOpen = false; editing = null; detail = null; searchOpen = false; notifOpen = true }
            "service" -> allServicesForShortcuts.firstOrNull { it.id == p.serviceId }?.let {
                detailFromSearch = p.itemId != null
                // Reuse the search deep-link carrier so the Jellyfin screen opens that item's detail.
                searchDeepLink = p.itemId?.let { id -> org.phioster.sanctumd.ui.search.SearchDeepLink(jellyItemId = id) }
                searchOpen = false; notifOpen = false; detail = it
            }
        }
        vm.consumeRoute()
    }

    // First-run onboarding: shown only once, when there are no services yet. Existing
    // users (already have services) are silently marked done so they never see it.
    val onboardingDone by vm.onboardingDone.collectAsState()
    val servicesLoaded by vm.servicesLoaded.collectAsState()
    var forceIntro by remember { mutableStateOf(false) }
    LaunchedEffect(onboardingDone, servicesLoaded, allServicesForShortcuts) {
        if (onboardingDone == false && servicesLoaded && allServicesForShortcuts.isNotEmpty()) vm.setOnboardingDone(true)
    }
    val showOnboarding = forceIntro || (onboardingDone == false && servicesLoaded && allServicesForShortcuts.isEmpty())
    BackHandler(enabled = showOnboarding) { forceIntro = false; vm.setOnboardingDone(true) }

    // Fade between top-level screens. `route` decides which screen shows; the detail config +
    // deep-link are remembered so the outgoing detail frame still has data while it fades out.
    val route = when {
        showOnboarding -> "onboarding"
        editorOpen -> "editor"
        detail != null -> "detail:${detail!!.id}"
        searchOpen -> "search"
        notifOpen -> "settings"
        statsOpen -> "stats"
        else -> "home"
    }
    val shownDetail = remember { mutableStateOf<ServiceConfig?>(null) }
    val shownLink = remember { mutableStateOf<SearchDeepLink?>(null) }
    if (detail != null && shownDetail.value !== detail) { shownDetail.value = detail; shownLink.value = searchDeepLink }
    androidx.compose.animation.Crossfade(targetState = route, animationSpec = androidx.compose.animation.core.tween(200), label = "screen") { r ->
        when {
            r == "onboarding" -> OnboardingScreen(onDismiss = { openAdd ->
                forceIntro = false
                vm.setOnboardingDone(true)
                if (openAdd) addOpen = true
            })
            r == "editor" -> AddServiceScreen(
                existing = editing,
                onCancel = { addOpen = false; editing = null },
                onSave = {
                    vm.upsertService(it)
                    if (detail?.id == it.id) detail = it // stay on the (now updated) service
                    addOpen = false; editing = null
                },
                onTest = { vm.test(it) },
            )
            r.startsWith("detail") -> {
                val cfg = shownDetail.value
                if (cfg != null) {
                    val link = shownLink.value
                    val back = closeDetail
                    val edit = { editing = cfg } // keep detail so back returns to the service
                    val del = { vm.removeService(cfg.id); detail = null; detailFromSearch = false; searchDeepLink = null }
                    when (cfg.type) {
                        ServiceType.NZBGET -> NzbgetScreen(vm = vm, config = cfg, onBack = back, onEdit = edit, onDelete = del)
                        ServiceType.RADARR, ServiceType.SONARR, ServiceType.LIDARR ->
                            ArrScreen(vm = vm, config = cfg, onBack = back, onEdit = edit, onDelete = del, initialDetailId = link?.arrDetailId, initialAddTerm = link?.arrAddTerm)
                        ServiceType.SEERR -> SeerrScreen(vm = vm, config = cfg, onBack = back, onEdit = edit, onDelete = del, initialDetail = link?.seerrTmdb?.let { it to link.seerrMediaType })
                        ServiceType.PROWLARR -> ProwlarrScreen(vm = vm, config = cfg, onBack = back, onEdit = edit, onDelete = del)
                        ServiceType.JELLYFIN -> JellyfinScreen(vm = vm, config = cfg, onBack = back, onEdit = edit, onDelete = del, initialItemId = link?.jellyItemId)
                        ServiceType.NTFY -> NtfyScreen(vm = vm, config = cfg, onBack = back, onEdit = edit, onDelete = del)
                        ServiceType.SHORTCUTS -> ShortcutsScreen(vm = vm, config = cfg, onBack = back, onEdit = edit, onDelete = del)
                        else -> ServiceDetailScreen(vm = vm, config = cfg, onBack = back, onEdit = edit, onDelete = del)
                    }
                }
            }
            r == "search" -> GlobalSearchScreen(
                vm = vm,
                onBack = { searchOpen = false },
                onOpenService = { cfg, link -> searchOpen = false; detailFromSearch = true; searchDeepLink = link; detail = cfg },
                initialTerm = searchTerm,
                onTermChange = { searchTerm = it },
            )
            r == "settings" -> SettingsScreen(vm = vm, onBack = { notifOpen = false }, onShowIntro = { notifOpen = false; forceIntro = true })
            r == "stats" -> org.phioster.sanctumd.ui.stats.StatsScreen(vm = vm, onBack = { statsOpen = false })
            else -> HomeShell(
                vm = vm,
                onAdd = { addOpen = true },
                onOpen = { detail = it },
                onEdit = { editing = it },
                onSearch = { term -> searchTerm = term; searchOpen = true },
                onNotifications = { notifOpen = true },
                onStats = { statsOpen = true },
            )
        }
    }

    // After a restore onto a new device the encrypted services blob can't be
    // decrypted (its Keystore key stayed on the old device) — explain instead
    // of silently showing an empty services list.
    val servicesUnreadable by vm.servicesUnreadable.collectAsState()
    if (servicesUnreadable) {
        AlertDialog(
            onDismissRequest = {},
            containerColor = Surface,
            title = { Text("stored data unreadable", fontFamily = Mono, color = MatrixGreen) },
            text = {
                Text(
                    "Your saved service list can't be read. This happens if it was restored from another device's backup (its encryption key stays on that device) or the stored file is corrupt.\n\nReset the store and add your services again.",
                    fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.8f), fontSize = 12.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.clearUnreadableServices() }) { Text("Reset & start over", fontFamily = Mono, color = MatrixGreen) }
            },
        )
    }
}

/** Real brand logo for a service type (colored PNG in drawable-nodpi). */
internal fun serviceLogoRes(type: ServiceType): Int = ServiceRegistry.logoRes(type)

@Composable
internal fun ServiceLogo(type: ServiceType, size: androidx.compose.ui.unit.Dp = 24.dp, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Image(
        painter = androidx.compose.ui.res.painterResource(serviceLogoRes(type)),
        contentDescription = "${type.label} logo",
        modifier = modifier.size(size),
    )
}
