package org.phioster.sanctumd.ui.common

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import kotlinx.coroutines.delay

/** Pull-to-refresh around a screen's list; the same [onRefresh] as its refresh button. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RefreshBox(refreshing: Boolean, onRefresh: () -> Unit, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = modifier, content = content)
}

/**
 * Calls [tick] every [periodMs] while the screen is in the foreground; in the background it
 * stops, so a forgotten screen costs no battery or traffic. [periodMs] is read anew before
 * each wait, so it can follow the data (fast while downloading); null means "not now".
 * [keys] restart the timer, e.g. on a tab change.
 */
@Composable
internal fun AutoRefresh(vararg keys: Any?, periodMs: () -> Long?, tick: suspend () -> Unit) {
    val resumed by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val currentTick by rememberUpdatedState(tick)
    val currentPeriod by rememberUpdatedState(periodMs)
    val active = resumed.isAtLeast(Lifecycle.State.RESUMED)
    LaunchedEffect(active, *keys) {
        if (!active) return@LaunchedEffect
        while (true) {
            val p = currentPeriod()
            if (p == null) {
                delay(IDLE_CHECK_MS)
                continue
            }
            delay(p)
            runCatching { currentTick() }
        }
    }
}

/** How often a paused auto-refresh checks whether it should run again. */
private const val IDLE_CHECK_MS = 5_000L
