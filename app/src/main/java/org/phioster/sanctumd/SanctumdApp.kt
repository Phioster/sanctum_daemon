package org.phioster.sanctumd

import android.app.Application
import org.phioster.sanctumd.net.ClientIdentity

/**
 * Exists for one job: hand [ClientIdentity] a context before the first request goes out, so the
 * per-install device id is read off disk, or created, exactly once.
 */
class SanctumdApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ClientIdentity.init(this)
    }
}
