package org.phioster.sanctumd.widget

import android.content.Context
import android.widget.Toast
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.phioster.sanctumd.data.ServiceStore
import org.phioster.sanctumd.net.runHttpShortcut

/** Fires the tapped HTTP shortcut and reports the result as a Toast (same as in-app). */
class RunShortcutAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val serviceId = parameters[serviceIdKey] ?: return
        val name = parameters[shortcutNameKey] ?: return
        val services = runCatching { ServiceStore(context).services.first() }.getOrDefault(emptyList())
        val config = services.firstOrNull { it.id == serviceId } ?: return
        val sc = config.shortcuts.firstOrNull { it.name == name } ?: return
        val result = runCatching { runHttpShortcut(config, sc) }.getOrElse { "error: ${it.message ?: "failed"}" }
        withContext(Dispatchers.Main) {
            Toast.makeText(context, result, Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        val serviceIdKey = ActionParameters.Key<String>("serviceId")
        val shortcutNameKey = ActionParameters.Key<String>("shortcutName")
    }
}
