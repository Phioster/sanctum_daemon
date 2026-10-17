package org.phioster.sanctumd.ui.nzbget

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.model.ServiceType
import org.phioster.sanctumd.net.NzbServerVolume
import org.phioster.sanctumd.ui.DashboardViewModel
import org.phioster.sanctumd.ui.common.AppChip
import org.phioster.sanctumd.ui.common.PrimaryButton
import org.phioster.sanctumd.ui.common.SectionHeader
import org.phioster.sanctumd.ui.theme.AppIcons
import org.phioster.sanctumd.ui.theme.Black
import org.phioster.sanctumd.ui.theme.ErrRed
import org.phioster.sanctumd.ui.theme.MatrixGreen
import org.phioster.sanctumd.ui.theme.Mono
import org.phioster.sanctumd.ui.theme.Surface
import org.phioster.sanctumd.ui.theme.SurfaceHi
import org.phioster.sanctumd.ui.theme.WarnAmber

/** "1.4 TB", "23.5 GB", "812 MB": what the volume page and the upload result speak in. */
internal fun volumeLabel(bytes: Long): String {
    val units = listOf("B", "KB", "MB", "GB", "TB", "PB")
    var v = bytes.toDouble()
    var i = 0
    while (v >= 1024 && i < units.lastIndex) { v /= 1024; i++ }
    return if (i <= 2 || v >= 100) "${v.toLong()} ${units[i]}" else "%.1f %s".format(java.util.Locale.ROOT, v, units[i])
}

/** How much went through each news server: today, this week, this month, ever. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NzbServersScreen(vm: DashboardViewModel, config: ServiceConfig, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val scope = rememberCoroutineScope()
    var list by remember { mutableStateOf<List<NzbServerVolume>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var confirmReset by remember { mutableStateOf<NzbServerVolume?>(null) }
    suspend fun load() {
        runCatching { vm.nzbVolumes(config) }
            .onSuccess { list = it; error = null }
            .onFailure { error = it.message ?: it.javaClass.simpleName }
    }
    LaunchedEffect(config.id) { load() }

    Scaffold(
        containerColor = Black,
        topBar = {
            TopAppBar(
                title = { Text("News servers", fontFamily = Mono, color = MatrixGreen) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(AppIcons.Back, contentDescription = "Back", tint = MatrixGreen) } },
                actions = {
                    IconButton(onClick = { scope.launch { load() } }) { Icon(AppIcons.Refresh, contentDescription = "Refresh", tint = MatrixGreen) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Black, titleContentColor = MatrixGreen),
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            message?.let { m ->
                item { Text(m, fontFamily = Mono, fontSize = 12.sp, color = if (m.startsWith("error")) ErrRed else MatrixGreen) }
            }
            val l = list
            when {
                error != null && l == null -> item { Text("error: $error", fontFamily = Mono, color = ErrRed, fontSize = 13.sp) }
                l == null -> item { Text("loading…", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f)) }
                l.isEmpty() -> item { Text("NZBGet reports no servers", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f)) }
                else -> items(l, key = { it.id }) { v -> VolumeCard(v, onReset = { confirmReset = v }) }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    confirmReset?.let { v ->
        AlertDialog(
            onDismissRequest = { confirmReset = null },
            containerColor = Surface,
            title = { Text("Reset counter", fontFamily = Mono, color = MatrixGreen) },
            text = {
                Text(
                    "Set the hand-reset counter of ${v.name} back to zero? Totals and the daily history stay.",
                    fontFamily = Mono, color = MatrixGreen, fontSize = 13.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = null
                    scope.launch {
                        message = vm.nzbResetVolume(config, if (v.id == 0) -1 else v.id, v.name)
                        load()
                    }
                }) { Text("Reset", fontFamily = Mono, color = ErrRed) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = null }) { Text("Cancel", fontFamily = Mono, color = MatrixGreen) } },
        )
    }
}

@Composable
private fun VolumeCard(v: NzbServerVolume, onReset: () -> Unit) {
    val total = v.id == 0
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(if (total) MatrixGreen.copy(alpha = 0.08f) else SurfaceHi)
            .border(1.dp, MatrixGreen.copy(alpha = if (total) 0.5f else 0.2f), RoundedCornerShape(12.dp))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(v.name, fontFamily = Mono, color = MatrixGreen, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!total) {
                    val role = if (v.level == 0) "main" else "fill · level ${v.level}"
                    Text(listOf(v.host, role).filter { it.isNotBlank() }.joinToString(" · "),
                        fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.55f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (!v.active) Text("off", fontFamily = Mono, color = WarnAmber, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Figure("today", v.todayBytes)
            Figure("7 days", v.weekBytes)
            Figure("30 days", v.monthBytes)
            Figure("total", v.totalBytes)
        }
        if (v.days.any { it > 0 }) {
            Spacer(Modifier.height(12.dp))
            DayBars(v.days)
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                v.successRate?.let { r ->
                    val pct = "%.1f %%".format(java.util.Locale.ROOT, r * 100)
                    Text("articles found $pct", fontFamily = Mono, fontSize = 12.sp,
                        color = when { r >= 0.98 -> MatrixGreen; r >= 0.9 -> WarnAmber; else -> ErrRed })
                }
                val since = if (v.customSince > 0) java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(v.customSince * 1000)) else "always"
                Text("since $since: ${volumeLabel(v.customBytes)}", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.7f), fontSize = 12.sp)
            }
            AppChip("reset", icon = AppIcons.Refresh, onClick = onReset)
        }
    }
}

@Composable
private fun Figure(label: String, bytes: Long) {
    Column {
        Text(volumeLabel(bytes), fontFamily = Mono, color = MatrixGreen, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Text(label.uppercase(), fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.55f), fontSize = 10.sp)
    }
}

/** The last 30 days as bars, today on the right. */
@Composable
private fun DayBars(days: List<Long>) {
    val max = days.maxOrNull()?.takeIf { it > 0 } ?: return
    Row(Modifier.fillMaxWidth().height(44.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        days.forEachIndexed { i, b ->
            val f = (b.toFloat() / max).coerceIn(0.03f, 1f)
            Box(
                Modifier.weight(1f).fillMaxHeight(f).clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                    .background(MatrixGreen.copy(alpha = if (i == days.lastIndex) 1f else 0.55f)),
            )
        }
    }
}

// ---- Upload ----

/** An NZB file that arrived from the share menu or the file picker, read but not sent yet. */
class PendingNzb(val name: String?, val bytes: ByteArray?, val error: String? = null)

/**
 * Where an NZB file goes: which NZBGet (when there are several), which category, paused or not.
 * Opened from the NZBGet menu or by sharing an .nzb file to the app.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NzbUploadDialog(vm: DashboardViewModel, file: PendingNzb, preselected: ServiceConfig? = null, onDone: (String?) -> Unit) {
    val scope = rememberCoroutineScope()
    val services by vm.services.collectAsState()
    val targets = services.filter { it.type == ServiceType.NZBGET }
    var target by remember { mutableStateOf(preselected ?: targets.firstOrNull()) }
    var categories by remember { mutableStateOf<List<String>>(emptyList()) }
    var category by remember { mutableStateOf("") }
    var paused by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(file.error) }
    LaunchedEffect(target?.id) {
        categories = target?.let { t -> runCatching { vm.nzbCategoryList(t) }.getOrDefault(emptyList()) }.orEmpty()
        if (category !in categories) category = ""
    }
    val done = result != null && !result!!.startsWith("error")

    AlertDialog(
        onDismissRequest = { if (!busy) onDone(result?.takeIf { done }) },
        containerColor = Surface,
        title = { Text("Upload NZB", fontFamily = Mono, color = MatrixGreen) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(file.name ?: "upload.nzb", fontFamily = Mono, color = MatrixGreen, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                file.bytes?.let { Text(volumeLabel(it.size.toLong()), fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.55f), fontSize = 11.sp) }
                when {
                    targets.isEmpty() -> Text("No NZBGet set up yet. Add one under services first.", fontFamily = Mono, color = WarnAmber, fontSize = 13.sp)
                    file.bytes == null -> {}
                    else -> {
                        if (targets.size > 1) {
                            SectionHeader("to")
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                targets.forEach { t -> Pick(t.label, t.id == target?.id) { target = t } }
                            }
                        }
                        if (categories.isNotEmpty()) {
                            SectionHeader("category")
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Pick("none", category.isEmpty()) { category = "" }
                                categories.forEach { c -> Pick(c, c == category) { category = c } }
                            }
                        }
                        Row(Modifier.clickable { paused = !paused }, verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(paused, { paused = it }, colors = CheckboxDefaults.colors(checkedColor = MatrixGreen, checkmarkColor = Black))
                            Text("add paused", fontFamily = Mono, color = MatrixGreen, fontSize = 13.sp)
                        }
                    }
                }
                result?.let { Text(it, fontFamily = Mono, fontSize = 12.sp, color = if (done) MatrixGreen else ErrRed) }
            }
        },
        confirmButton = {
            val t = target
            if (done || t == null || file.bytes == null) {
                TextButton(onClick = { onDone(result?.takeIf { done }) }) { Text("Close", fontFamily = Mono, color = MatrixGreen) }
            } else {
                PrimaryButton(if (busy) "uploading…" else "upload", icon = AppIcons.Add, enabled = !busy) {
                    busy = true
                    scope.launch {
                        result = vm.nzbAddFile(t, file.name, file.bytes, category, paused)
                        busy = false
                        if (!result!!.startsWith("error")) vm.refreshAll()
                    }
                }
            }
        },
        dismissButton = {
            if (!done && target != null && file.bytes != null) {
                TextButton(enabled = !busy, onClick = { onDone(null) }) { Text("Cancel", fontFamily = Mono, color = MatrixGreen) }
            }
        },
    )
}

@Composable
private fun Pick(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label, fontFamily = Mono, fontSize = 13.sp,
        color = if (selected) Black else MatrixGreen,
        modifier = Modifier.clip(RoundedCornerShape(8.dp))
            .background(if (selected) MatrixGreen else SurfaceHi)
            .border(1.dp, MatrixGreen.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

/** Reads a shared or picked file; NZBs are small, so it is read whole. */
internal fun readNzb(context: android.content.Context, uri: android.net.Uri): PendingNzb {
    val name = runCatching {
        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment
    return try {
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
                if (out.size() > org.phioster.sanctumd.net.NZB_MAX_BYTES) return PendingNzb(name, null, "error: file too large for an NZB")
            }
            out.toByteArray()
        } ?: return PendingNzb(name, null, "error: could not open the file")
        if (org.phioster.sanctumd.net.looksLikeNzb(bytes)) PendingNzb(name, bytes) else PendingNzb(name, null, "error: that is not an NZB file")
    } catch (t: Throwable) {
        PendingNzb(name, null, "error: ${t.message ?: t.javaClass.simpleName}")
    }
}
