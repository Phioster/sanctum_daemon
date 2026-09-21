package org.phioster.sanctumd.net

import android.content.Context
import org.phioster.sanctumd.BuildConfig
import java.util.UUID

/**
 * How the app introduces itself to Jellyfin.
 *
 * Jellyfin wants four things in the `Authorization` header, and it takes them seriously: the
 * DeviceId is how it tells one device from another. It keys sessions, the device list in the
 * dashboard and resume positions off that id.
 *
 * Both of those used to be hardcoded. Every install of this app, phone and TV alike, reported
 * `DeviceId="sanctumd"`, so a server saw them all as one device, and the version was frozen at
 * 0.3.0 while the app moved on to 2.x.
 *
 * SharedPreferences rather than DataStore, for the same reason as
 * [org.phioster.sanctumd.ui.theme.ThemeStore]: this has to be readable synchronously, and one
 * string does not justify a second asynchronous store.
 */
internal object ClientIdentity {

    private const val PREFS = "sanctumd_client"
    private const val KEY_DEVICE_ID = "device_id"

    /** Filled once from [org.phioster.sanctumd.SanctumdApp] before anything can make a request. */
    @Volatile
    private var deviceId: String = ""

    fun init(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        deviceId = prefs.getString(KEY_DEVICE_ID, null) ?: UUID.randomUUID().toString()
            .replace("-", "")
            .also { prefs.edit().putString(KEY_DEVICE_ID, it).apply() }
    }

    /**
     * The four fields, as Jellyfin wants them.
     *
     * Phone and TV name themselves differently so the dashboard shows which is which. Their ids
     * differ by themselves: the TV flavour has its own application id and therefore its own
     * preferences file, so it draws its own random one.
     *
     * The device id is empty only if a request somehow beats Application.onCreate, which cannot
     * happen in practice. The server rejecting it is a better failure than quietly going back to
     * one shared id.
     */
    val mediaBrowserClient: String
        get() {
            val tv = BuildConfig.FLAVOR == "tv"
            return "Client=\"${if (tv) "Sanctumd TV" else "Sanctumd"}\", " +
                "Device=\"${if (tv) "Android TV" else "Android"}\", " +
                "DeviceId=\"$deviceId\", Version=\"${BuildConfig.VERSION_NAME}\""
        }
}
