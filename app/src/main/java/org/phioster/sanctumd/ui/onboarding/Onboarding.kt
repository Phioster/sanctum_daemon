package org.phioster.sanctumd.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.phioster.sanctumd.ui.theme.Black
import org.phioster.sanctumd.ui.theme.MatrixGreen
import org.phioster.sanctumd.ui.theme.Mono
import org.phioster.sanctumd.ui.common.*
import org.phioster.sanctumd.ui.arr.*
import org.phioster.sanctumd.ui.dashboard.*
import org.phioster.sanctumd.ui.home.*
import org.phioster.sanctumd.ui.jellyfin.*
import org.phioster.sanctumd.ui.ntfy.*
import org.phioster.sanctumd.ui.nzbget.*
import org.phioster.sanctumd.ui.prowlarr.*
import org.phioster.sanctumd.ui.search.*
import org.phioster.sanctumd.ui.seerr.*
import org.phioster.sanctumd.ui.services.*
import org.phioster.sanctumd.ui.settings.*
import org.phioster.sanctumd.ui.shortcuts.*
import org.phioster.sanctumd.ui.theme.*

@Composable
internal fun OnboardingScreen(onDismiss: (openAdd: Boolean) -> Unit) {
    val pages = listOf(
        OnboardPage(AppIcons.Dashboard, "WELCOME TO SANCTUMD", "One dark, matrix-green cockpit for your whole homelab. Run Jellyfin and steer your *arr and download stack without app-hopping."),
        OnboardPage(AppIcons.Server, "CONNECT YOUR STACK", "Point it at Jellyfin, Radarr / Sonarr / Lidarr, Prowlarr, NZBGet, Jellyseerr and ntfy, just a URL and API key each. Your keys stay encrypted on this phone and go nowhere else."),
        OnboardPage(AppIcons.Edit, "MAKE IT YOUR OWN", "Spin up tabs and drop in cards. Download queues, release calendars, live stats, even a watch leaderboard. Tap the pencil to rearrange, and pin widgets to your home screen."),
        OnboardPage(AppIcons.Notify, "NEVER MISS A BEAT", "Instant push comes straight from your own ntfy topic, backed by quiet background checks for fresh media, finished downloads and new requests."),
        OnboardPage(AppIcons.Locked, "PRIVATE BY DESIGN", "Secrets are encrypted, the app can lock behind your fingerprint or face, and an encrypted export carries your whole setup to a new phone."),
    )
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == pages.lastIndex
    Column(Modifier.fillMaxSize().background(Black).systemBarsPadding().padding(24.dp)) {
        Spacer(Modifier.height(24.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { onDismiss(false) }) { Text("skip", fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.6f)) }
        }
        HorizontalPager(state = pager, modifier = Modifier.weight(1f).fillMaxWidth()) { i ->
            val p = pages[i]
            Column(
                Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(p.icon, contentDescription = null, tint = MatrixGreen, modifier = Modifier.size(56.dp))
                Spacer(Modifier.height(24.dp))
                Text(p.title, fontFamily = Mono, color = MatrixGreen, fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Text(p.body, fontFamily = Mono, color = MatrixGreen.copy(alpha = 0.75f), fontSize = 14.sp, textAlign = TextAlign.Center, lineHeight = 21.sp)
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.Center) {
            repeat(pages.size) { i ->
                Box(
                    Modifier.padding(horizontal = 4.dp)
                        .size(if (i == pager.currentPage) 10.dp else 7.dp)
                        .clip(CircleShape)
                        .background(if (i == pager.currentPage) MatrixGreen else MatrixGreen.copy(alpha = 0.3f)),
                )
            }
        }
        Button(
            onClick = { if (last) onDismiss(true) else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MatrixGreen, contentColor = Black),
        ) {
            Text(if (last) "get started" else "next", fontFamily = Mono, fontWeight = FontWeight.Bold)
        }
    }
}
