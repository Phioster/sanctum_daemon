package org.phioster.sanctumd.ui.settings

import android.os.Build
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.phioster.sanctumd.ui.DashboardViewModel
import org.phioster.sanctumd.ui.common.SecondaryButton
import org.phioster.sanctumd.ui.common.openInBrowser
import org.phioster.sanctumd.ui.theme.MatrixGreen
import org.phioster.sanctumd.ui.theme.Mono

private const val REPO = "https://github.com/Phioster/sanctum_daemon"

/**
 * The about screen: what this is, what to paste into an issue, and whose work is in here.
 *
 * The screen used to be three lines in a corner. It is the page someone opens right after
 * installing and the page they open again when something is wrong, so it answers both: the stack
 * it speaks to with links that can actually be tapped, and a diagnostics block built for a bug
 * report. See [diagnosticsReport] for why that block names kinds of services and nothing else.
 */
@Composable
internal fun AboutSection(vm: DashboardViewModel) {
    val services by vm.services.collectAsState()
    AboutBody(serviceTypes = serviceKinds(services))
}

/** The screen without a view model, so its layout can be tested. */
@Composable
internal fun AboutBody(serviceTypes: List<String>) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    val info = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
    }
    val version = info?.versionName ?: "?"
    val versionCode = remember(info) {
        when {
            info == null -> 0L
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> info.longVersionCode
            else -> @Suppress("DEPRECATION") info.versionCode.toLong()
        }
    }

    Text(
        "> sanctumd_",
        fontFamily = Mono, color = MatrixGreen, fontSize = 18.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 16.dp),
    )
    Text("v$version", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.7f), fontSize = 13.sp)
    Spacer(Modifier.height(12.dp))
    Text(
        "Unified dashboard for Jellyfin and the *arr stack.",
        fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.55f), fontSize = 11.sp,
    )

    AboutHeading("what it talks to")
    Text(
        "Jellyfin · Radarr · Sonarr · Lidarr · Seerr · Prowlarr · NZBGet · ntfy",
        fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.55f), fontSize = 11.sp,
    )

    AboutHeading("project")
    SettingsCategoryRow("source", "github.com/Phioster/sanctum_daemon") { openInBrowser(context, REPO) }
    SettingsCategoryRow("changelog", "what changed, by milestone") {
        openInBrowser(context, "$REPO/blob/master/CHANGELOG.md")
    }
    SettingsCategoryRow("report a problem", "open an issue") { openInBrowser(context, "$REPO/issues") }
    SettingsCategoryRow("licence", "GPL-3.0, free software") {
        openInBrowser(context, "$REPO/blob/master/LICENSE")
    }

    AboutHeading("diagnostics")
    val report = diagnosticsReport(
        version = version,
        versionCode = versionCode,
        androidRelease = Build.VERSION.RELEASE ?: "?",
        sdk = Build.VERSION.SDK_INT,
        device = "${Build.MANUFACTURER} ${Build.MODEL}",
        serviceTypes = serviceTypes,
    )
    Text(
        report,
        fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.75f), fontSize = 11.sp,
        modifier = Modifier.padding(top = 2.dp),
    )
    Text(
        "Kinds of services only. No names, addresses or keys, so this is safe to paste into a public issue.",
        fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.5f), fontSize = 10.sp,
        modifier = Modifier.padding(top = 8.dp, bottom = 10.dp),
    )
    SecondaryButton("copy for an issue", Modifier.fillMaxWidth(), icon = Icons.Filled.ContentCopy) {
        clipboard.setText(AnnotatedString(report))
        android.widget.Toast.makeText(context, "copied", android.widget.Toast.LENGTH_SHORT).show()
    }

    AboutHeading("built with")
    for (line in THIRD_PARTY) {
        Text(line, fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.55f), fontSize = 11.sp)
    }
}

/**
 * The bundled work of others. Everything here is Apache-2.0 except libmpv, and that one is the
 * reason this app is GPL-3.0 rather than a matter of taste.
 *
 * Read out of the published AAR rather than guessed: `dev.jdtech.mpv:libmpv:1.0.0` declares MIT in
 * its POM, but that covers the Android packaging. The FFmpeg libraries it ships are built
 * `--enable-gpl --enable-version3` (the configuration string is in libavcodec.so), so they are
 * GPL-3.0, and mpv links against them. Anything distributing this AAR is therefore bound to
 * GPL-3.0. Which sanctumd is. Re-check when the libmpv version changes.
 */
private val THIRD_PARTY = listOf(
    "Jetpack Compose · AndroidX · Glance (Apache-2.0)",
    "media3 / ExoPlayer (Apache-2.0)",
    "Retrofit · OkHttp (Apache-2.0)",
    "kotlinx.serialization (Apache-2.0)",
    "Coil (Apache-2.0)",
    "libmpv-android. MIT; its bundled mpv & FFmpeg are GPL-3.0",
)

/**
 * A section heading, carrying the same weight as the name at the top of the screen and a step
 * below it in size. At the body's 11sp it was smaller than the rows underneath it, which read as
 * if the rows outranked their own heading.
 */
@Composable
private fun AboutHeading(text: String) {
    Text(
        "> $text",
        fontFamily = Mono, color = MatrixGreen, fontSize = 16.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 28.dp, bottom = 10.dp),
    )
}
