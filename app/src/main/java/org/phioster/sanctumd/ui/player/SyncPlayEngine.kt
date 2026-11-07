package org.phioster.sanctumd.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.delay
import org.phioster.sanctumd.net.SyncPlay

/**
 * The engine as the player screen sees it. In a SyncPlay group, play/pause/seek from the screen
 * (buttons, double tap, chapter jump, skip intro, sleep timer) become requests to the server; the
 * actual player only moves when the server's command arrives, through [SyncPlayBinding]. Outside a
 * group it passes straight through.
 */
internal class SyncPlayEngine(private val inner: MediaPlayerEngine) : MediaPlayerEngine by inner {
    @Volatile var grouped: Boolean = false

    override fun prepare(
        url: String,
        isHls: Boolean,
        startPositionMs: Long,
        headers: Map<String, String>,
        externalSubs: List<org.phioster.sanctumd.net.ExternalSubtitle>,
    ) {
        inner.prepare(url, isHls, startPositionMs, headers, externalSubs)
        // Loaded but held: the group starts everyone together.
        if (grouped) inner.pause()
    }

    override fun play() = if (grouped) SyncPlay.requestUnpause() else inner.play()
    override fun pause() = if (grouped) SyncPlay.requestPause() else inner.pause()
    override fun togglePlay() {
        if (!grouped) return inner.togglePlay()
        if (inner.snapshot().isPlaying) SyncPlay.requestPause() else SyncPlay.requestUnpause()
    }
    override fun seekTo(positionMs: Long) = if (grouped) SyncPlay.requestSeek(positionMs) else inner.seekTo(positionMs)
    override fun seekBy(deltaMs: Long) =
        if (grouped) SyncPlay.requestSeek(inner.snapshot().positionMs + deltaMs) else inner.seekBy(deltaMs)

    fun localPlay() = inner.play()
    fun localPause() = inner.pause()
    /** Seeks only when it matters; a needless seek costs a rebuffer on both players. */
    fun localSeek(positionMs: Long) {
        if (kotlin.math.abs(inner.snapshot().positionMs - positionMs) > 300) inner.seekTo(positionMs.coerceAtLeast(0))
    }
}

/** Waits until the file is loaded and not buffering (near [target] when given), up to 30 s. */
private suspend fun awaitLoaded(engine: MediaPlayerEngine, target: Long? = null) {
    val until = System.currentTimeMillis() + 30_000
    while (System.currentTimeMillis() < until) {
        val s = engine.snapshot()
        if (s.durationMs > 0 && !s.isBuffering && (target == null || kotlin.math.abs(s.positionMs - target) < 2_000)) return
        delay(200)
    }
}

/**
 * Carries out the server's commands at the moment the server named, and tells it when this
 * player is ready: once after loading, and again after every seek.
 */
@Composable
internal fun SyncPlayBinding(engine: SyncPlayEngine, active: Boolean, itemKey: String, onStop: () -> Unit) {
    if (!active) return
    val stop by rememberUpdatedState(onStop)
    val context = androidx.compose.ui.platform.LocalContext.current
    // Where the group says playback is: this position at this moment. Null while paused/seeking.
    var anchor by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    LaunchedEffect(Unit) { SyncPlay.loadPrefs(context) }
    LaunchedEffect(itemKey) {
        awaitLoaded(engine)
        SyncPlay.ready(engine.snapshot().positionMs, false)
    }
    LaunchedEffect(Unit) {
        SyncPlay.commands.collect { c ->
            // A command for another queue entry belongs to an item this player isn't showing.
            if (c.playlistItemId.isNotBlank() && SyncPlay.playlistItemId.isNotBlank() && c.playlistItemId != SyncPlay.playlistItemId) return@collect
            val wait = c.atLocalMs - System.currentTimeMillis()
            when (c.type) {
                "Unpause" -> {
                    if (wait > 0) {
                        engine.localSeek(c.positionMs); delay(wait); engine.localPlay()
                    } else {
                        // Late: join in where the others already are.
                        engine.localSeek(c.positionMs - wait); engine.localPlay()
                    }
                    anchor = c.positionMs to c.atLocalMs
                }
                "Pause" -> {
                    anchor = null
                    if (wait > 0) delay(wait)
                    engine.localPause(); engine.localSeek(c.positionMs)
                }
                "Seek" -> {
                    anchor = null
                    engine.localPause(); engine.localSeek(c.positionMs)
                    awaitLoaded(engine, c.positionMs)
                    SyncPlay.ready(engine.snapshot().positionMs, false)
                }
                "Stop" -> { engine.localPause(); stop() }
            }
        }
    }
    // Sync correction: every few seconds compare with where the group is. A small drift is
    // caught up by playing a little faster or slower for two seconds (Jellyfin's SpeedToSync),
    // a big one by jumping there (SkipToSync).
    LaunchedEffect(Unit) {
        while (true) {
            delay(3_000)
            val a = anchor ?: continue
            if (!SyncPlay.correction) continue
            val s = engine.snapshot()
            if (!s.isPlaying || s.isBuffering) continue
            val drift = syncDrift(s.positionMs, a.first, a.second, System.currentTimeMillis())
            when {
                kotlin.math.abs(drift) > 3_000 -> engine.localSeek(a.first + (System.currentTimeMillis() - a.second))
                kotlin.math.abs(drift) > 120 -> {
                    engine.setSpeed(catchUpSpeed(drift))
                    delay(2_000)
                    engine.setSpeed(1f)
                }
            }
        }
    }
}

/** How far this player is ahead (+) or behind (-) of the group, in ms. */
internal fun syncDrift(positionMs: Long, anchorPos: Long, anchorAt: Long, now: Long): Long =
    positionMs - (anchorPos + (now - anchorAt))

/** Speed that removes [driftMs] over two seconds, kept within what still sounds like speech. */
internal fun catchUpSpeed(driftMs: Long): Float = (1f - driftMs / 2_000f).coerceIn(0.5f, 1.5f)
