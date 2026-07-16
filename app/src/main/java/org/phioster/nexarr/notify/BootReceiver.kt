package org.phioster.nexarr.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.phioster.nexarr.data.NotifyStore

/** Restarts the live-push stream after a reboot, if the user has it enabled. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val s = NotifyStore(context.applicationContext).currentSettings()
                if (s.live && s.ntfyServer.isNotBlank() && s.ntfyTopic.isNotBlank()) {
                    NtfyStreamService.start(context.applicationContext)
                }
            } finally {
                result.finish()
            }
        }
    }
}
