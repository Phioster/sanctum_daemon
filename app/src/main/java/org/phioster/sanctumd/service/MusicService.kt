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
 * Background music playback. A [MediaSessionService] hosting one ExoPlayer + MediaSession — media3
 * supplies the media-style notification and lock-screen controls automatically, and playback keeps
 * going when the app is backgrounded. Per-service custom headers (e.g. Cloudflare Access) are added
 * to every request via a resolving data source reading [authHeaders] — and so is the Jellyfin token,
 * which used to ride in the stream URL instead. It cannot: this service is exported (media3 requires
 * it), media3's default callback accepts every controller, and `MediaMetadata.artworkUri` is bundled
 * to each one — so a token in that URL was readable by any app on the device. The artwork is fetched
 * through the same header-injecting factory, so it still loads without the URL carrying a secret.
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
        session = MediaSession.Builder(this, player)
            // Artwork over the same factory, so its request carries the auth header too.
            .setBitmapLoader(
                DataSourceBitmapLoader(
                    MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor()),
                    resolving,
                ),
            )
            .setCallback(OwnPackageOnly())
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

    /**
     * Only this app may drive or read the session. Defence in depth behind the real fix (no secret
     * in the metadata): media3's own callback accepts every caller, so without this any installed
     * app could connect and read what is playing.
     */
    private inner class OwnPackageOnly : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult =
            if (controller.packageName == packageName) {
                MediaSession.ConnectionResult.AcceptedResultBuilder(session).build()
            } else {
                MediaSession.ConnectionResult.reject()
            }
    }

    companion object {
        /** Extra request headers (custom headers + Jellyfin auth) applied to every playback request. */
        @Volatile var authHeaders: Map<String, String> = emptyMap()
    }
}
