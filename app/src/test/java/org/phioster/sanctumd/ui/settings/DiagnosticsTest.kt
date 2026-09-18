package org.phioster.sanctumd.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.phioster.sanctumd.model.ServiceConfig
import org.phioster.sanctumd.model.ServiceType

/** What may and may not end up in a block of text that gets pasted into a public issue. */
class DiagnosticsTest {

    private fun report(services: List<ServiceConfig>) = diagnosticsReport(
        version = "1.57.0",
        versionCode = 260,
        androidRelease = "15",
        sdk = 35,
        device = "Xiaomi A024",
        serviceTypes = serviceKinds(services),
    )

    @Test
    fun `the report carries version, android and device`() {
        val text = report(emptyList())
        assertTrue(text, text.contains("sanctumd 1.57.0 (260)"))
        assertTrue(text, text.contains("Android 15 (SDK 35)"))
        assertTrue(text, text.contains("Xiaomi A024"))
        assertTrue(text, text.contains("services: none configured"))
    }

    @Test
    fun `no address, label or key of a configured service reaches the report`() {
        val secrets = listOf(
            ServiceConfig(
                type = ServiceType.JELLYFIN,
                label = "Wohnzimmer-Server",
                baseUrl = "https://jellyfin.inside.example.net:8096",
                apiKey = "d41d8cd98f00b204e9800998ecf8427e",
            ),
            ServiceConfig(
                type = ServiceType.RADARR,
                label = "Filme",
                baseUrl = "http://10.0.0.7:7878",
                username = "marc",
                password = "hunter2",
            ),
        )
        val text = report(secrets)
        for (secret in listOf(
            "Wohnzimmer-Server", "jellyfin.inside.example.net", "8096",
            "d41d8cd98f00b204e9800998ecf8427e", "Filme", "10.0.0.7", "7878", "marc", "hunter2",
        )) {
            assertFalse("leaked: $secret\n$text", text.contains(secret))
        }
    }

    @Test
    fun `services show as kinds and a count`() {
        val text = report(
            listOf(
                ServiceConfig(type = ServiceType.RADARR, label = "a", baseUrl = "http://a"),
                ServiceConfig(type = ServiceType.JELLYFIN, label = "b", baseUrl = "http://b"),
                ServiceConfig(type = ServiceType.RADARR, label = "c", baseUrl = "http://c"),
            ),
        )
        // Sorted and de-duplicated for the kinds, but the count is of actual services.
        assertTrue(text, text.contains("services: jellyfin, radarr · 3 configured"))
    }

    @Test
    fun `an empty or blank device does not leave a dangling line`() {
        val text = diagnosticsReport("1.0", 1, "15", 35, "   ", emptyList())
        assertTrue(text, text.contains("unknown device"))
        assertEquals(4, text.lines().size)
    }

    @Test
    fun `serviceKinds hands over nothing but the type`() {
        val kinds = serviceKinds(
            listOf(ServiceConfig(type = ServiceType.SEERR, label = "secret", baseUrl = "http://secret", apiKey = "k")),
        )
        assertEquals(listOf("SEERR"), kinds)
    }
}
