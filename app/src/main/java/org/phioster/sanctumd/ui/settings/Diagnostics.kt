package org.phioster.sanctumd.ui.settings

import org.phioster.sanctumd.model.ServiceConfig

/**
 * The block of text the about screen hands to the clipboard for a bug report.
 *
 * It is written to be pasted into a PUBLIC issue, which decides what may be in it. The services
 * appear as kinds and a count — never a label, an address or a key. Those are exactly what a
 * helpful user would publish without thinking, so the function is not given them in the first
 * place: [serviceKinds] is the only way configured services reach this string.
 */
fun diagnosticsReport(
    version: String,
    versionCode: Long,
    androidRelease: String,
    sdk: Int,
    device: String,
    serviceTypes: List<String>,
): String {
    val present = serviceTypes.map { it.lowercase().trim() }.filter { it.isNotBlank() }
    val services =
        if (present.isEmpty()) "none configured"
        else "${present.distinct().sorted().joinToString(", ")} · ${present.size} configured"
    return listOf(
        "sanctumd $version ($versionCode)",
        "Android $androidRelease (SDK $sdk)",
        device.trim().ifBlank { "unknown device" },
        "services: $services",
    ).joinToString("\n")
}

/** The kinds of the configured services, and nothing else about them. */
fun serviceKinds(services: List<ServiceConfig>): List<String> = services.map { it.type.name }
