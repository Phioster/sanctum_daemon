package org.phioster.sanctumd.ui.common

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.phioster.sanctumd.demo.DemoMode
import org.phioster.sanctumd.ui.theme.AppIcons
import org.phioster.sanctumd.ui.theme.Black
import org.phioster.sanctumd.ui.theme.MatrixGreen
import org.phioster.sanctumd.ui.theme.Mono
import org.phioster.sanctumd.ui.theme.WarnAmber

/** Hidden by a long press for this session, e.g. to take screenshots without it. */
private val badgeHidden = mutableStateOf(false)

/**
 * While demo mode is on: the always-visible way out. A tap leaves the demo and brings back the
 * user's own services (or the first-run setup when there are none).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun DemoExitChip() {
    val on by DemoMode.enabled.collectAsState()
    if (!on || badgeHidden.value) return
    val context = LocalContext.current
    Row(
        Modifier
            .padding(end = 4.dp)
            .clip(RoundedCornerShape(50))
            .background(WarnAmber)
            .combinedClickable(onClick = { DemoMode.set(context, false) }, onLongClick = { badgeHidden.value = true })
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("DEMO", fontFamily = Mono, color = Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(6.dp))
        Text("exit", fontFamily = Mono, color = Black, fontSize = 11.sp)
        Spacer(Modifier.width(4.dp))
        Icon(AppIcons.Cancel, contentDescription = "Leave demo mode", tint = Black, modifier = Modifier.size(13.dp))
    }
}

/** The way in: shown where a first service would be added. */
@Composable
internal fun TryDemoButton(modifier: Modifier = Modifier, onStarted: () -> Unit = {}) {
    val context = LocalContext.current
    OutlinedButton(
        onClick = { DemoMode.set(context, true); onStarted() },
        modifier = modifier.fillMaxWidth(),
    ) {
        Icon(AppIcons.Play, contentDescription = null, tint = MatrixGreen, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text("no server yet? try the demo", fontFamily = Mono, color = MatrixGreen, fontSize = 13.sp)
    }
}
