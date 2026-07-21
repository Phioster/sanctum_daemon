package org.phioster.sanctumd.widget

import org.phioster.sanctumd.model.ServiceType
import org.phioster.sanctumd.service.ServiceAction
import org.phioster.sanctumd.service.ServiceRegistry

/**
 * Quick actions for the widget callbacks. The table itself lives in [ServiceRegistry] —
 * it used to be duplicated here and in the dashboard, which meant a new action had to be
 * added twice or the widget quietly lagged behind.
 */
object WidgetActions {
    fun forType(type: ServiceType): List<ServiceAction> = ServiceRegistry.actions(type)
}
