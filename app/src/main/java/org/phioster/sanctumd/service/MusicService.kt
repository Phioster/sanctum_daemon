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
 *
 * The session accepts every controller, the way media3 does by default. 1.59.0 shipped a callback
 * that only admitted this package — but the lock screen, Bluetooth headsets and car head units all
 * reach a media app through the platform session, and media3 gives that a fixed sentinel package
 * name (ControllerInfo.LEGACY_CONTROLLER_PACKAGE_NAME), so the callback shut them all out. What is
 * worth guarding here is the credential, and that is no longer in the metadata for anyone to read.
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
