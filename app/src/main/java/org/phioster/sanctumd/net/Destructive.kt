package org.phioster.sanctumd.net

import android.util.Log

/**
 * One gate in front of every call that changes something on a user's server.
 *
 * Sanctumd can delete Jellyfin users, wipe libraries, grab releases and decide requests.
 * "Be careful while testing" is not a safety mechanism, so safe mode is: while it is on,
 * a guarded call never reaches the network and reports back what it would have done.
 * The op label is what the UI shows and what lands in logcat, so a blocked action is
 * visible rather than silent.
 */
object SafeMode {
    /** Mirrors the persisted setting; read on every guarded call, so flipping it is instant. */
    @Volatile
    var enabled: Boolean = false

    /** Ops blocked since the process started — the settings screen shows the count. */
    @Volatile
    var blockedCount: Int = 0
        internal set

    const val TAG = "SanctumdSafeMode"
}

/**
 * Runs [block] unless safe mode is on, in which case it returns [blocked] instead.
 *
 * @param op human-readable label, e.g. "delete Jellyfin user 'Nadine'"
 * @param blocked what the caller gets back when the call was suppressed
 */
internal suspend fun <T> destructive(op: String, blocked: T, block: suspend () -> T): T {
    if (SafeMode.enabled) {
        SafeMode.blockedCount++
        Log.w(SafeMode.TAG, "blocked: $op")
        return blocked
    }
    Log.i(SafeMode.TAG, "running: $op")
    return block()
}

/** Same gate for the common case of an action that reports back a status string. */
internal suspend fun destructive(op: String, block: suspend () -> String): String =
    destructive(op, "blocked by safe mode", block)
