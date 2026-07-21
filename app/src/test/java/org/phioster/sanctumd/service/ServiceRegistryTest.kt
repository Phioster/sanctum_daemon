package org.phioster.sanctumd.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.phioster.sanctumd.model.ServiceType

/**
 * The point of the registry: adding a ServiceType and forgetting to describe it should
 * fail here in seconds, not as a non-exhaustive `when` several CI runs later.
 */
class ServiceRegistryTest {

    @Test
    fun `every service type is registered`() {
        val missing = ServiceType.entries.filterNot { it in ServiceRegistry.registered() }
        assertTrue("not registered in ServiceRegistry: $missing", missing.isEmpty())
    }

    @Test
    fun `every service type has a logo`() {
        ServiceType.entries.forEach { type ->
            assertTrue("$type has no logo", ServiceRegistry.logoRes(type) != 0)
        }
    }

    @Test
    fun `action labels are unique per service`() {
        ServiceType.entries.forEach { type ->
            val labels = ServiceRegistry.actions(type).map { it.label }
            assertEquals("duplicate action labels on $type", labels.distinct(), labels)
        }
    }

    @Test
    fun `the arr services share the same quick actions`() {
        val radarr = ServiceRegistry.actions(ServiceType.RADARR).map { it.label }
        assertEquals(radarr, ServiceRegistry.actions(ServiceType.SONARR).map { it.label })
        assertEquals(radarr, ServiceRegistry.actions(ServiceType.LIDARR).map { it.label })
        assertEquals(listOf("Search all missing", "RSS sync"), radarr)
    }

    @Test
    fun `services without server-side actions expose none`() {
        listOf(ServiceType.SEERR, ServiceType.NTFY, ServiceType.SHORTCUTS).forEach {
            assertTrue("$it should have no quick actions", ServiceRegistry.actions(it).isEmpty())
        }
    }
}
