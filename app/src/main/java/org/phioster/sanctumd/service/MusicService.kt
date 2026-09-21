package org.phioster.sanctumd.service

import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DataSourceBitmapLoader
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.MoreExecutors
import java.util.concurrent.Executors

/**
 * Background music playback: one ExoPlayer and MediaSession, with media3's notification and
 * lock-screen controls.
 *
 * Credentials travel as headers ([authHeaders], via a resolving data source), never in the URL:
 * this service is exported and `MediaMetadata.artworkUri` is handed to every controller.
 *
 * The session accepts every controller, as media3 does by default. A callback admitting only
 * this package locked out the lock screen, Bluetooth and car head units, which all arrive under
 * media3's sentinel package name.
 */
@UnstableApi
class MusicService : MediaSessionService() {

    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        // No cross-protocol redirects: authHeaders (Jellyfin auth, Cloudflare Access) are applied
        // to every request and would follow an https -> http hop straight into the clear.
        val http = DefaultHttpDataSource.Factory()
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
        session = MediaSession.Builder(this, player)
            // Artwork over the same factory, so its request carries the auth header too.
            .setBitmapLoader(
                DataSourceBitmapLoader(
                    MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor()),
                    resolving,
                ),
            )
            .build()
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
        /** Extra request headers (custom headers + Jellyfin auth) applied to every playback request. */
        @Volatile var authHeaders: Map<String, String> = emptyMap()
    }
}
