package org.phioster.sanctumd

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import org.phioster.sanctumd.demo.DemoMode
import org.phioster.sanctumd.net.ClientIdentity
import org.phioster.sanctumd.net.baseOkClient

/**
 * Hands [ClientIdentity] a context before the first request goes out, so the per-install device
 * id is read off disk, or created, exactly once; and sets up demo mode the same way.
 */
class SanctumdApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        ClientIdentity.init(this)
        DemoMode.init(this)
    }

    /** Pictures go through the shared client too, so demo posters are answered like demo data. */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this).okHttpClient { baseOkClient }.build()
}
