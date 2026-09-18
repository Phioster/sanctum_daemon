package org.phioster.sanctumd.ui.common

import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

internal fun fmtWatch(s: Long): String {
    val h = s / 3600; val m = (s % 3600) / 60
    return when {
        h >= 1 -> "${h}h ${m}m"
        m >= 1 -> "${m}m"
        else -> "${s}s"
    }
}

/** Top-3 watch-time leaderboard as a little podium (2nd · 1st · 3rd, center tallest). */

/** Known Android app packages that can display a Jellyfin server. */
internal val jellyfinAppPackages = listOf("org.jellyfin.mobile", "dev.jdtech.jellyfin")

/** Known Android app packages for Overseerr/Jellyseerr. */
internal val seerrAppPackages = listOf("dev.seerr.mobileapp")

/**
 * Open [webUrl] in the first installed app from [packages]; otherwise hand the URL to the
 * system, which routes it to an installed PWA (e.g. Seerr added to the home screen) or the
 * browser.
 */
internal fun openExternal(context: android.content.Context, packages: List<String>, webUrl: String) {
    for (pkg in packages) {
        val launch = context.packageManager.getLaunchIntentForPackage(pkg)
        if (launch != null) { runCatching { context.startActivity(launch) }; return }
    }
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(webUrl))) }
}

/**
 * Hand [url] straight to the system (browser or whatever claims the link).
 *
 * Unlike [openExternal] this never redirects to an installed app: a watch-provider link points
 * at one specific page, and opening some app's home screen instead would lose it.
 */
internal fun openInBrowser(context: android.content.Context, url: String) {
    if (url.isBlank()) return
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}
