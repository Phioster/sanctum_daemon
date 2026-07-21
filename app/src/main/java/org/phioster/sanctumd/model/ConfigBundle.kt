package org.phioster.sanctumd.model

import kotlinx.serialization.Serializable

/**
 * Everything a portable config export carries: the configured services (with their
 * secrets), the notification settings (ntfy token/topics) and the dashboard layout.
 * The device-local biometric app-lock preference is intentionally NOT included.
 */
@Serializable
data class ConfigBundle(
    val version: Int = 1,
    val services: List<ServiceConfig> = emptyList(),
    val notify: NotifySettings = NotifySettings(),
    val tabs: List<DashTab> = emptyList(),
)
