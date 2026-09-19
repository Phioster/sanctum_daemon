package org.phioster.sanctumd.ui.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.phioster.sanctumd.net.MusicTrack
import org.phioster.sanctumd.service.MusicService

/** One entry in the playback queue (for the now-playing track list). */
data class QueueTrack(val title: String, val artist: String)

/** Snapshot of the music player for the now-playing UI. */
data class MusicState(
    val hasMedia: Boolean = false,
    val title: String = "",
    val artist: String = "",
    val artworkUri: String = "",
    val isPlaying: Boolean = false,
    val hasNext: Boolean = false,
    val hasPrev: Boolean = false,
    val queue: List<QueueTrack> = emptyList(),
    val currentIndex: Int = 0,
)

/**
 * App-wide handle to the background [MusicService]. Connects a [MediaController] lazily, plays a queue
 * of [MusicTrack]s, and publishes a [state] flow the now-playing bar/screen observe. The service keeps
 * playing when the app is gone; this controller is just the remote.
 */
object MusicController {
    private var controller: MediaController? = null
    private var connecting = false
    private var pending: (() -> Unit)? = null

    private val _state = MutableStateFlow(MusicState())
    val state: StateFlow<MusicState> = _state.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) { pushState() }
    }

    private fun ensure(context: Context) {
        if (controller != null || connecting) return
        connecting = true
        val app = context.applicationContext
        val token = SessionToken(app, ComponentName(app, MusicService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        future.addListener({
            controller = runCatching { future.get() }.getOrNull()
            connecting = false
            controller?.addListener(listener)
            pending?.invoke(); pending = null
            pushState()
        }, ContextCompat.getMainExecutor(app))
    }

    /** Play [tracks] from [startIndex]; [headers] are the service's per-request custom headers. */
    fun play(context: Context, tracks: List<MusicTrack>, startIndex: Int, headers: Map<String, String>) {
        if (tracks.isEmpty()) return
        // The track carries what its own server needs (auth included, since the token no longer
        // rides in the URL); [headers] stays for callers that pass service headers directly.
        MusicService.authHeaders = headers + tracks.first().headers
        val action = {
            val c = controller
            if (c != null) {
                val items = tracks.map { t ->
                    MediaItem.Builder()
                        .setUri(t.streamUrl)
                        .setMediaId(t.id)
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(t.title)
                                .setArtist(t.artist.ifBlank { t.album })
                                .apply { if (t.artUrl.isNotBlank()) setArtworkUri(Uri.parse(t.artUrl)) }
                                .build(),
                        )
                        .build()
                }
                c.setMediaItems(items, startIndex.coerceIn(0, items.size - 1), 0L)
                c.prepare()
                c.play()
            }
        }
        if (controller != null) action() else { pending = action; ensure(context) }
    }

    fun playPause() { controller?.let { if (it.isPlaying) it.pause() else it.play() } }
    fun next() { controller?.seekToNextMediaItem() }
    fun prev() { controller?.seekToPreviousMediaItem() }
    fun seekToIndex(index: Int) { controller?.seekTo(index, 0L) }
    fun seekTo(ms: Long) { controller?.seekTo(ms.coerceAtLeast(0)) }
    fun stop() { controller?.run { stop(); clearMediaItems() } }
    fun positionMs(): Long = controller?.currentPosition?.coerceAtLeast(0) ?: 0
    fun durationMs(): Long = controller?.let { if (it.duration > 0) it.duration else 0 } ?: 0

    /** Attach to an existing session on app start so a running playback shows up in the now-playing bar. */
    fun bind(context: Context) = ensure(context)

    private fun pushState() {
        val c = controller
        if (c == null) { _state.value = MusicState(); return }
        val md = c.currentMediaItem?.mediaMetadata
        val queue = (0 until c.mediaItemCount).map { i ->
            val m = c.getMediaItemAt(i).mediaMetadata
            QueueTrack(m.title?.toString().orEmpty(), m.artist?.toString().orEmpty())
        }
        _state.value = MusicState(
            hasMedia = c.currentMediaItem != null,
            title = md?.title?.toString().orEmpty(),
            artist = md?.artist?.toString().orEmpty(),
            artworkUri = md?.artworkUri?.toString().orEmpty(),
            isPlaying = c.isPlaying,
            hasNext = c.hasNextMediaItem(),
            hasPrev = c.hasPreviousMediaItem(),
            queue = queue,
            currentIndex = c.currentMediaItemIndex,
        )
    }
}
