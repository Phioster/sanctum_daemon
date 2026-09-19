package org.phioster.sanctumd.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import kotlin.math.roundToInt
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import org.phioster.sanctumd.ui.theme.withBackground
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.phioster.sanctumd.ui.DashboardViewModel
import org.phioster.sanctumd.ui.theme.Black
import org.phioster.sanctumd.ui.theme.MatrixGreen
import org.phioster.sanctumd.ui.theme.Mono
import org.phioster.sanctumd.ui.theme.Surface
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
import org.phioster.sanctumd.ui.shortcuts.*
import org.phioster.sanctumd.ui.theme.*
import org.phioster.sanctumd.showUnlockPrompt

/** Dedicated settings hub: categories on the first level, one section per screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(vm: DashboardViewModel, onBack: () -> Unit, onShowIntro: () -> Unit = {}) {
    var section by remember { mutableStateOf<String?>(null) }
    BackHandler(enabled = section != null) { section = null }

    Scaffold(
        containerColor = Black,
        topBar = {
            TopAppBar(
                title = { Text(section ?: "settings", fontFamily = Mono, color = MatrixGreen) },
                navigationIcon = {
                    IconButton(onClick = { if (section != null) section = null else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MatrixGreen)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Black, titleContentColor = MatrixGreen),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
            when (section) {
                null -> {
                    SettingsCategoryRow("notifications", "Background polling: what to check and how often") { section = "notifications" }
                    SettingsCategoryRow("live push (ntfy)", "Instant notifications from your ntfy server") { section = "live push (ntfy)" }
                    SettingsCategoryRow("security", "Biometric app lock") { section = "security" }
                    SettingsCategoryRow("content", "Hide adult / 18+ content") { section = "content" }
                    SettingsCategoryRow("general", "Which screen the app opens on") { section = "general" }
                    SettingsCategoryRow("theme", "Accent presets and background") { section = "theme" }
                    SettingsCategoryRow("playback", "Languages, subtitles, autoplay & skipping") { section = "playback" }
                    SettingsCategoryRow("gestures", "Swipe between dashboard tabs + swipe zone") { section = "gestures" }
                    SettingsCategoryRow("backup / data", "Export or import your config (encrypted)") { section = "backup / data" }
                    SettingsCategoryRow("about", "Version & project info") { section = "about" }
                    SettingsCategoryRow("welcome intro", "Replay the first-run walkthrough") { onShowIntro() }
                }
                "notifications" -> NotifyPollingSection(vm)
                "live push (ntfy)" -> LivePushSection(vm)
                "security" -> SecuritySection(vm)
                "content" -> ContentSection(vm)
                "general" -> GeneralSection(vm)
                "theme" -> ThemeSection()
                "playback" -> PlaybackSection(vm)
                "gestures" -> GesturesSection(vm)
                "backup / data" -> BackupSection(vm)
                "about" -> AboutSection(vm)
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
internal fun SettingsCategoryRow(title: String, sub: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontFamily = Mono, color = MatrixGreen, fontSize = 15.sp)
            Text(sub, fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.55f), fontSize = 11.sp)
        }
        Text("›", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 18.sp)
    }
    HorizontalDivider(color = MatrixGreen.copy(alpha = 0.12f))
}

/** Asks for POST_NOTIFICATIONS on API 33+ when a notification feature is switched on. */
@Composable
internal fun rememberNotifPermissionRequester(): () -> Unit {
    val context = LocalContext.current
    val permLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { }
    return {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            permLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@Composable
internal fun NotifyPollingSection(vm: DashboardViewModel) {
    val s by vm.notifySettings.collectAsState()
    val requestPermIfNeeded = rememberNotifPermissionRequester()
    NotifyToggleRow("Enable notifications", "Background check every ${s.intervalMin} min", s.enabled) { on ->
        if (on) requestPermIfNeeded()
        vm.saveNotifySettings(s.copy(enabled = on))
    }
    HorizontalDivider(color = MatrixGreen.copy(alpha = 0.15f))
    Text("NOTIFY ME ABOUT", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 11.sp, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
    NotifyToggleRow("New media", "Newly added movies/episodes in Jellyfin", s.newMedia, s.enabled) { vm.saveNotifySettings(s.copy(newMedia = it)) }
    NotifyToggleRow("Downloads imported", "Radarr / Sonarr / Lidarr finished importing", s.imports, s.enabled) { vm.saveNotifySettings(s.copy(imports = it)) }
    NotifyToggleRow("New requests", "New pending requests in Seerr", s.requests, s.enabled) { vm.saveNotifySettings(s.copy(requests = it)) }
    NotifyToggleRow("Health issues", "New Radarr / Sonarr / Lidarr warnings & errors", s.health, s.enabled) { vm.saveNotifySettings(s.copy(health = it)) }
    HorizontalDivider(color = MatrixGreen.copy(alpha = 0.15f))
    Text("CHECK INTERVAL", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 11.sp, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
    Row {
        listOf(15, 30, 60).forEach { m ->
            val sel = s.intervalMin == m
            Box(
                Modifier.padding(end = 8.dp).size(width = 72.dp, height = 40.dp).clip(RoundedCornerShape(8.dp))
                    .background(if (sel) MatrixGreen else Surface)
                    .border(1.dp, if (sel) MatrixGreen else MatrixGreen.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                    .clickable(enabled = s.enabled) { vm.saveNotifySettings(s.copy(intervalMin = m)) },
                contentAlignment = Alignment.Center,
            ) { Text("${m}m", fontFamily = Mono, color = if (sel) Black else MatrixGreen, fontSize = 14.sp) }
        }
    }
    Spacer(Modifier.height(16.dp))
    Text(
        "Android runs background checks at most every 15 minutes and may delay them to save battery. The first check just records the current state, so you only get notified about things that happen afterwards.",
        fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.5f), fontSize = 11.sp,
    )
}

@Composable
internal fun LivePushSection(vm: DashboardViewModel) {
    val s by vm.notifySettings.collectAsState()
    val requestPermIfNeeded = rememberNotifPermissionRequester()
    Text(
        "Instant — no 15-minute wait. Sanctumd subscribes directly to a topic on your ntfy server and shows every message posted to it (your existing service webhooks already do this). Keeps a small background connection open. Topics of configured ntfy services are subscribed too.",
        fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.5f), fontSize = 11.sp, modifier = Modifier.padding(top = 12.dp),
    )
    Spacer(Modifier.height(10.dp))
    var srv by remember { mutableStateOf(s.ntfyServer) }
    var top by remember { mutableStateOf(s.ntfyTopic) }
    var tok by remember { mutableStateOf(s.ntfyToken) }
    Field("Server URL (https://ntfy…)", srv) { srv = it }
    Spacer(Modifier.height(8.dp))
    Field("Topic (e.g. Homelab)", top) { top = it }
    Spacer(Modifier.height(8.dp))
    Field("Access token (optional)", tok) { tok = it }
    Spacer(Modifier.height(4.dp))
    NotifyToggleRow("Live push", if (s.live) "Connected to ${s.ntfyServer.ifBlank { "?" }}/${s.ntfyTopic.ifBlank { "?" }}" else "Off", s.live, srv.isNotBlank() && top.isNotBlank()) { on ->
        if (on) requestPermIfNeeded()
        vm.saveNotifySettings(s.copy(live = on, ntfyServer = srv.trim().trimEnd('/'), ntfyTopic = top.trim(), ntfyToken = tok.trim()))
    }
}

@Composable
internal fun ContentSection(vm: DashboardViewModel) {
    val hide by vm.hideAdult.collectAsState()
    NotifyToggleRow("Hide adult content (XXX)", "Hides pornographic titles from Jellyfin browsing and Seerr discovery", hide) { vm.setHideAdult(it) }
    Text(
        "Only real porn is hidden — XXX / X / X18+ / Adult ratings on Jellyfin and the TMDB adult flag on Seerr. Mainstream 18-rated films (horror, NC-17, R, FSK 18, R18+) stay visible. Doesn't touch Radarr/Sonarr or global search.",
        fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.5f), fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp),
    )

    val region by vm.watchRegion.collectAsState()
    SettingsPickerRow(
        "Streaming region",
        listOf("" to "device (${org.phioster.sanctumd.net.watchRegionOf("")})") +
            org.phioster.sanctumd.ui.common.WATCH_REGION_OPTIONS.map { it to it },
        region,
    ) { vm.setWatchRegion(it) }
    Text(
        "Which country the \"streaming\" row on Seerr, Radarr and Sonarr detail screens is read for — " +
            "a title is on different services one border over. The data is TMDB's, fetched through your " +
            "configured Seerr, so the row only appears when Seerr is set up, and it stays hidden for titles " +
            "that stream nowhere in that country.",
        fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.5f), fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp),
    )
}

/** Accent presets plus the independent background choice. Applies instantly — the whole UI reads
 *  its colours from [org.phioster.sanctumd.ui.theme.ThemeState]. */
@Composable
internal fun ThemeSection() {
    val context = LocalContext.current
    val active = org.phioster.sanctumd.ui.theme.ThemeState.palette
    var presetId by remember { mutableStateOf(org.phioster.sanctumd.ui.theme.ThemeStore.paletteId(context)) }
    var background by remember { mutableStateOf(org.phioster.sanctumd.ui.theme.ThemeStore.backgroundMode(context)) }

    Text(
        "PRESET",
        fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 11.sp,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
    )
    org.phioster.sanctumd.ui.theme.PALETTES.forEach { p ->
        // Show each preset in its own colours, with the current background choice applied, so the
        // row is a real preview rather than a name.
        val shown = p.withBackground(background)
        val selected = p.id == presetId
        Row(
            Modifier.fillMaxWidth()
                .clickable {
                    presetId = p.id
                    org.phioster.sanctumd.ui.theme.ThemeStore.writePreset(context, p.id)
                }
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(shown.accent, shown.background, shown.surface).forEach { c ->
                    Box(
                        Modifier.size(width = 22.dp, height = 22.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(c)
                            .border(1.dp, MatrixGreen.copy(alpha = 0.25f), RoundedCornerShape(4.dp)),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Text(p.label, fontFamily = Mono, color = MatrixGreen, fontSize = 14.sp, modifier = Modifier.weight(1f))
            if (selected) Text("✓", fontFamily = Mono, color = MatrixGreen, fontSize = 14.sp)
        }
        HorizontalDivider(color = MatrixGreen.copy(alpha = 0.1f))
    }

    SettingsPickerRow(
        "Background",
        org.phioster.sanctumd.ui.theme.BackgroundMode.entries.map { it.id to it.label },
        background.id,
    ) { picked ->
        background = org.phioster.sanctumd.ui.theme.BackgroundMode.from(picked)
        org.phioster.sanctumd.ui.theme.ThemeStore.writeBackground(context, background)
    }
    Text(
        "\"Preset\" keeps each theme's own background. \"OLED black\" makes the large areas true black " +
            "while cards stay a shade above it, so they don't disappear. \"Anthracite\" is the neutral " +
            "dark grey the app shipped with.",
        fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.5f), fontSize = 11.sp,
        modifier = Modifier.padding(top = 4.dp),
    )
    Text(
        "The accent colours everything you see — text, numbers, buttons. The preset's own colour " +
            "tints the background and the cards behind it.",
        fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.5f), fontSize = 11.sp,
        modifier = Modifier.padding(top = 8.dp),
    )
    Text(
        "Service colours (Jellyfin blue, Prowlarr orange…) and the red used for errors stay as they are.",
        fontFamily = Mono, color = active.accent.copy(alpha = 0.6f), fontSize = 11.sp,
        modifier = Modifier.padding(top = 8.dp),
    )
}

/** App-wide behaviour that doesn't belong to any one service. */
@Composable
internal fun GeneralSection(vm: DashboardViewModel) {
    val start by vm.startScreen.collectAsState()
    SettingsPickerRow(
        "Open on",
        listOf("dashboard" to "dashboard", "services" to "services list"),
        start,
    ) { vm.setStartScreen(it) }
    Text(
        "Which surface greets you on a cold start. The dashboard remembers its last tab either way.",
        fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.5f), fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp),
    )
}

/** Playback preferences: track languages, subtitle look, autoplay, segment skipping, resume. */
@Composable
internal fun PlaybackSection(vm: DashboardViewModel) {
    val audioLang by vm.audioLanguage.collectAsState()
    val subLang by vm.subtitleLanguage.collectAsState()
    val subMode by vm.subtitleMode.collectAsState()
    val subScale by vm.subtitleScale.collectAsState()
    val autoplay by vm.autoplayNext.collectAsState()
    val autoSkip by vm.autoSkipSegments.collectAsState()
    val askResume by vm.askResume.collectAsState()
    val ambientGlow by vm.ambientGlow.collectAsState()

    SettingsPickerRow(
        "Audio language",
        org.phioster.sanctumd.ui.player.LANGUAGE_OPTIONS,
        audioLang,
    ) { vm.setAudioLanguage(it) }
    Text(
        "The player picks the first audio track in this language; \"file default\" leaves the choice to the file.",
        fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.5f), fontSize = 11.sp, modifier = Modifier.padding(bottom = 12.dp),
    )

    SettingsPickerRow(
        "Subtitle language",
        org.phioster.sanctumd.ui.player.LANGUAGE_OPTIONS.filter { it.first.isNotBlank() },
        subLang,
    ) { vm.setSubtitleLanguage(it) }

    SettingsPickerRow(
        "Subtitles",
        listOf(
            "forced" to "forced only, else off",
            "any" to "forced, else a normal track",
            "off" to "always off",
        ),
        subMode,
    ) { vm.setSubtitleMode(it) }
    Text(
        "\"Forced only\" shows the signs-and-songs track when the file has one and otherwise leaves subtitles off — a file whose default track is a full translation stays quiet.",
        fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.5f), fontSize = 11.sp, modifier = Modifier.padding(bottom = 12.dp),
    )

    var scaleLocal by remember(subScale) { mutableStateOf(subScale) }
    Text(
        "SUBTITLE SIZE  (${(scaleLocal * 100).roundToInt()}%)",
        fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
    )
    androidx.compose.material3.Slider(
        value = scaleLocal,
        onValueChange = { scaleLocal = it },
        onValueChangeFinished = { vm.setSubtitleScale(scaleLocal) },
        valueRange = 0.5f..2.5f,
        colors = androidx.compose.material3.SliderDefaults.colors(
            thumbColor = MatrixGreen, activeTrackColor = MatrixGreen, inactiveTrackColor = MatrixGreen.copy(alpha = 0.25f),
        ),
    )
    Text(
        "Applies to the mpv engine (the default). Size and timing can also be nudged per playback from the player's settings panel.",
        fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.5f), fontSize = 11.sp, modifier = Modifier.padding(bottom = 12.dp),
    )

    NotifyToggleRow("Autoplay next episode", "Shows a countdown card near the end and rolls on", autoplay) { vm.setAutoplayNext(it) }
    NotifyToggleRow("Auto-skip intro & outro", "Skips without asking — the skip button appears either way", autoSkip) { vm.setAutoSkipSegments(it) }
    val nextLead by vm.nextEpisodeLead.collectAsState()
    SettingsPickerRow(
        "Next-episode card",
        listOf("30" to "30 s", "45" to "45 s", "60" to "60 s", "90" to "90 s", "120" to "120 s"),
        nextLead.toString(),
    ) { vm.setNextEpisodeLead(it.toIntOrNull() ?: 45) }
    Text(
        "How long before the end the card appears when the server reports no outro segment. With a real outro segment the card follows that instead — the info panel in the player shows which one you got.",
        fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.5f), fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
    )

    NotifyToggleRow("Ask where to resume", "Offers \"resume\" vs \"start over\" instead of jumping straight in", askResume) { vm.setAskResume(it) }
    Text(
        "Intro/outro ranges come from the server: Jellyfin 10.10+ media segments, or the Intro Skipper plugin. Without either, no skip button appears.",
        fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.5f), fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp),
    )

    NotifyToggleRow(
        "Ambient glow",
        "Lets the picture bleed into the black bars, following the scene",
        ambientGlow,
    ) { vm.setAmbientGlow(it) }
    Text(
        "The colours come from the server's trickplay previews — the same images you see when scrubbing. Items the server has no trickplay for keep plain black bars, as do downloads played offline.",
        fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.5f), fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp),
    )
}

/** A labelled row of choice chips — used where a toggle isn't enough but a dialog is too much. */
@Composable
internal fun SettingsPickerRow(
    label: String,
    options: List<Pair<String, String>>,
    selected: String,
    onPick: (String) -> Unit,
) {
    Text(label.uppercase(), fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 11.sp, modifier = Modifier.padding(top = 14.dp, bottom = 6.dp))
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (value, text) ->
            val on = value == selected
            Text(
                text,
                fontFamily = Mono, fontSize = 12.sp,
                color = if (on) Black else MatrixGreen,
                modifier = Modifier
                    .background(if (on) MatrixGreen else Color.Transparent, RoundedCornerShape(6.dp))
                    .border(1.dp, MatrixGreen.copy(alpha = if (on) 0f else 0.35f), RoundedCornerShape(6.dp))
                    .clickable { onPick(value) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
internal fun GesturesSection(vm: DashboardViewModel) {
    val swipeTabs by vm.swipeTabs.collectAsState()
    val swipeDrawer by vm.swipeDrawer.collectAsState()
    val band by vm.drawerBand.collectAsState()
    NotifyToggleRow("Swipe between tabs", "Left/right swipe above the drawer band switches dashboard tabs", swipeTabs) { vm.setSwipeTabs(it) }
    NotifyToggleRow("Swipe to open drawer", "Right-swipe in the bottom band opens the Services drawer", swipeDrawer) { vm.setSwipeDrawer(it) }
    var local by remember(band) { mutableStateOf(band) }
    Text(
        "DRAWER BAND  (bottom ${(local * 100).roundToInt()}% of the screen)",
        fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 11.sp, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
    androidx.compose.material3.Slider(
        value = local,
        onValueChange = { local = it },
        onValueChangeFinished = { vm.setDrawerBand(local) },
        valueRange = 0f..0.5f,
        enabled = swipeDrawer,
        colors = androidx.compose.material3.SliderDefaults.colors(
            thumbColor = MatrixGreen,
            activeTrackColor = MatrixGreen,
            inactiveTrackColor = MatrixGreen.copy(alpha = 0.25f),
        ),
    )
    Text(
        "How far up from the bottom edge a right-swipe opens the Services drawer (max 50%). Above this band, swipes switch tabs. Poster rows keep scrolling.",
        fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.5f), fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp),
    )

    // ── Player brightness/volume swipe ──
    val mag by vm.playerSwipeMagnitude.collectAsState()
    var magLocal by remember(mag) { mutableStateOf(mag) }
    Text(
        "PLAYER SWIPE SENSITIVITY  (${"%.1f".format(magLocal)}×)",
        fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 11.sp, modifier = Modifier.padding(top = 20.dp, bottom = 4.dp),
    )
    androidx.compose.material3.Slider(
        value = magLocal,
        onValueChange = { magLocal = it },
        onValueChangeFinished = { vm.setPlayerSwipeMagnitude(magLocal) },
        valueRange = 0.3f..3f,
        colors = androidx.compose.material3.SliderDefaults.colors(
            thumbColor = MatrixGreen, activeTrackColor = MatrixGreen, inactiveTrackColor = MatrixGreen.copy(alpha = 0.25f),
        ),
    )
    Text(
        "How much a vertical swipe in the player changes brightness (left) / volume (right).",
        fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.5f), fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp),
    )

    val margin by vm.playerSwipeMargin.collectAsState()
    var marginLocal by remember(margin) { mutableStateOf(margin) }
    Text(
        "PLAYER SWIPE EDGE MARGIN  (top & bottom ${(marginLocal * 100).roundToInt()}%)",
        fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f), fontSize = 11.sp, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
    androidx.compose.material3.Slider(
        value = marginLocal,
        onValueChange = { marginLocal = it },
        onValueChangeFinished = { vm.setPlayerSwipeMargin(marginLocal) },
        valueRange = 0f..0.3f,
        colors = androidx.compose.material3.SliderDefaults.colors(
            thumbColor = MatrixGreen, activeTrackColor = MatrixGreen, inactiveTrackColor = MatrixGreen.copy(alpha = 0.25f),
        ),
    )
    Text(
        "A dead zone at the top & bottom of the player where a vertical swipe won't start (avoids the system edge gestures).",
        fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.5f), fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
internal fun SecuritySection(vm: DashboardViewModel) {
    val context = LocalContext.current
    val appLock by vm.appLock.collectAsState()
    NotifyToggleRow("App lock", "Require fingerprint/face or device PIN on open", appLock == true) { on ->
        if (!on) { vm.setAppLock(false); return@NotifyToggleRow }
        val bm = androidx.biometric.BiometricManager.from(context)
        val authenticators = androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK or
            androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
        if (bm.canAuthenticate(authenticators) != androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS) {
            android.widget.Toast.makeText(context, "no biometrics or device PIN set up", android.widget.Toast.LENGTH_LONG).show()
            return@NotifyToggleRow
        }
        // Require one successful unlock before enabling, so nobody locks themselves out.
        (context as? androidx.fragment.app.FragmentActivity)?.let { act ->
            showUnlockPrompt(act) { vm.setAppLock(true); vm.unlocked.value = true }
        }
    }
    Text(
        "Locks on cold start and after more than 2 minutes in the background. Live push keeps running while locked.",
        fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.5f), fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp),
    )

    Spacer(Modifier.height(20.dp))
    val safeMode by vm.safeMode.collectAsState()
    NotifyToggleRow(
        "Safe mode",
        "Block anything that changes your servers",
        safeMode,
    ) { vm.setSafeMode(it) }
    Text(
        "While on, deletes, grabs, imports, request decisions, restarts and shortcuts are refused " +
            "before they reach a server — the action reports \"blocked by safe mode\" instead. " +
            "Browsing, search and notifications are unaffected.",
        fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.5f), fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp),
    )
    if (safeMode && org.phioster.sanctumd.net.SafeMode.blockedCount > 0) {
        Text(
            "blocked so far: ${org.phioster.sanctumd.net.SafeMode.blockedCount}",
            fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.7f), fontSize = 11.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
internal fun BackupSection(vm: DashboardViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showExport by remember { mutableStateOf(false) }
    var importBytes by remember { mutableStateOf<ByteArray?>(null) } // set once a file is picked -> triggers pw dialog
    var pendingSaveBytes by remember { mutableStateOf<ByteArray?>(null) } // bytes awaiting a save location

    val saveLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri: android.net.Uri? ->
        val bytes = pendingSaveBytes
        pendingSaveBytes = null
        if (uri != null && bytes != null) {
            val ok = runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } }.isSuccess
            android.widget.Toast.makeText(context, if (ok) "config saved" else "save failed", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    val openLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            val bytes = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            if (bytes != null) importBytes = bytes
            else android.widget.Toast.makeText(context, "couldn't read file", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    Text(
        "Export bundles your services, ntfy settings and dashboard layout into one encrypted file, locked with a password you choose. Import replaces the current config on this device.",
        fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.55f), fontSize = 11.sp, modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
    )
    SettingsCategoryRow("export config", "save or share an encrypted backup") { showExport = true }
    SettingsCategoryRow("import config", "restore from an encrypted backup file") {
        openLauncher.launch(arrayOf("application/octet-stream", "*/*"))
    }
    Text(
        "⚠ the file holds your API keys and tokens — only the password protects them. keep it somewhere safe.",
        fontFamily = Mono, color = Color(0xFFE0A030), fontSize = 10.sp, modifier = Modifier.padding(top = 12.dp),
    )

    if (showExport) {
        var pw by remember { mutableStateOf("") }
        var pw2 by remember { mutableStateOf("") }
        // 12, nicht 6: die Datei ist zum Verschicken gedacht, ihr Kopf nennt das Verfahren, und
    // hinter dem Passwort liegt JEDER Zugang auf einmal. 210k Runden kaufen keine sechs Zeichen frei.
    val valid = pw.length >= 12 && pw == pw2
        val doExport: (Boolean) -> Unit = { share ->
            scope.launch {
                val bytes = runCatching { vm.exportConfig(pw) }.getOrNull()
                showExport = false
                if (bytes == null) {
                    android.widget.Toast.makeText(context, "export failed", android.widget.Toast.LENGTH_SHORT).show()
                    return@launch
                }
                if (share) shareConfig(context, bytes)
                else { pendingSaveBytes = bytes; saveLauncher.launch("sanctumd-config.sanctum") }
            }
        }
        AlertDialog(
            onDismissRequest = { showExport = false },
            containerColor = Surface,
            title = { Text("export config", fontFamily = Mono, color = MatrixGreen) },
            text = {
                Column {
                    Text("Choose a password (min 12). You'll need it to import.", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.7f), fontSize = 12.sp)
                    Field("password", pw, isPassword = true) { pw = it }
                    Field("repeat password", pw2, isPassword = true) { pw2 = it }
                }
            },
            confirmButton = {
                Row {
                    TextButton(onClick = { doExport(false) }, enabled = valid) { Text("save file", fontFamily = Mono, color = if (valid) MatrixGreen else MatrixGreen.copy(alpha = 0.4f)) }
                    TextButton(onClick = { doExport(true) }, enabled = valid) { Text("share", fontFamily = Mono, color = if (valid) MatrixGreen else MatrixGreen.copy(alpha = 0.4f)) }
                }
            },
            dismissButton = { TextButton(onClick = { showExport = false }) { Text("cancel", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f)) } },
        )
    }

    importBytes?.let { bytes ->
        var pw by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { importBytes = null },
            containerColor = Surface,
            title = { Text("import config", fontFamily = Mono, color = MatrixGreen) },
            text = {
                Column {
                    Text("This replaces your current services, ntfy settings and dashboard. Enter the file's password.", fontFamily = Mono, color = Color(0xFFE0A030), fontSize = 12.sp)
                    Field("password", pw, isPassword = true) { pw = it }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            val res = vm.importConfig(bytes, pw)
                            importBytes = null
                            res.onSuccess { n -> android.widget.Toast.makeText(context, "config imported · $n services", android.widget.Toast.LENGTH_LONG).show() }
                                .onFailure { android.widget.Toast.makeText(context, "import failed — wrong password or bad file", android.widget.Toast.LENGTH_LONG).show() }
                        }
                    },
                    enabled = pw.isNotEmpty(),
                ) { Text("import", fontFamily = Mono, color = if (pw.isNotEmpty()) MatrixGreen else MatrixGreen.copy(alpha = 0.4f)) }
            },
            dismissButton = { TextButton(onClick = { importBytes = null }) { Text("cancel", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f)) } },
        )
    }
}

/** Writes the encrypted bytes to a cache file and opens a share sheet via FileProvider. */
internal fun shareConfig(context: android.content.Context, bytes: ByteArray) {
    runCatching {
        val dir = java.io.File(context.cacheDir, "exports").apply { mkdirs() }
        // Clear what an earlier share left behind: the blob is encrypted, but there is no reason
        // for a bundle of every credential to sit in the cache until the OS feels like reaping it.
        dir.listFiles()?.forEach { it.delete() }
        val file = java.io.File(dir, "sanctumd-config.sanctum")
        file.writeBytes(bytes)
        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share config"))
    }.onFailure {
        android.widget.Toast.makeText(context, "share failed", android.widget.Toast.LENGTH_SHORT).show()
    }
}
