package org.phioster.sanctumd.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context

/**
 * Whether [appWidgetId] really belongs to one of this app's [receiver] widgets.
 *
 * The two configuration activities have to be exported. The launcher starts them when a widget is
 * placed. So any installed app can start them with an id of its own choosing. Without this check
 * that renders the user's configured service labels and shortcut names onto a screen the caller
 * asked for. An id the system never handed out for our provider is not ours to configure.
 */
internal fun ownsAppWidget(context: Context, receiver: Class<*>, appWidgetId: Int): Boolean =
    runCatching {
        AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, receiver))
            .contains(appWidgetId)
    }.getOrDefault(false)
