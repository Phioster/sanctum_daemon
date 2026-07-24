package org.phioster.sanctumd.service

import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Background music playback. A [MediaSessionService] hosting one ExoPlayer + MediaSession — media3
 * supplies the media-style notification and lock-screen controls automatically, and playback keeps
 * going when the app is backgrounded. Per-service custom headers (e.g. Cloudflare Access) are added
 * to every request via a resolving data source reading [authHeaders]; the Jellyfin token rides in the
 * stream URL (api_key), like the rest of the audio endpoints.
 */
@UnstableApi
class MusicService : MediaSessionService() {

    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val http = DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true)
        // DefaultDataSource handles local files (offline downloads) + content, delegating http to `http`.
        val base = DefaultDataSource.Factory(this, http)
        val resolving = ResolvingDataSource.Factory(base) { spec ->
            if (authHeaders.isEmpty()) spec else spec.withRequestHeaders(authHeaders)
        }
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setMediaSourceFactory(DefaultMediaSourceFactory(resolving))
            .build()
        session = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        // If nothing is playing when the task is swiped away, don't leave a dangling service.
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        session?.run { player.release(); release() }
        session = null
        super.onDestroy()
    }

    companion object {
        /** Extra request headers (per-service custom headers) applied to every playback request. */
        @Volatile var authHeaders: Map<String, String> = emptyMap()
    }
}
