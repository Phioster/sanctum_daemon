package org.phioster.sanctumd.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.Inet4Address
import java.net.InetAddress

/**
 * The unicast fallback for server discovery. A Jellyfin server on an Android phone with its screen
 * off never sees the broadcast, so discovery also asks every neighbour in the subnet directly.
 */
class JellyfinDiscoveryTest {

    private fun v4(s: String) = InetAddress.getByName(s) as Inet4Address

    @Test
    fun `a slash 24 yields every neighbour but itself`() {
        val hosts = subnetHosts(v4("192.168.2.240"), 24).map { it.hostAddress }
        assertEquals(253, hosts.size)
        assertEquals("192.168.2.1", hosts.first())
        assertEquals("192.168.2.254", hosts.last())
        assertTrue("192.168.2.183" in hosts)
        assertFalse("own address", "192.168.2.240" in hosts)
        assertFalse("network address", "192.168.2.0" in hosts)
        assertFalse("broadcast address", "192.168.2.255" in hosts)
    }

    @Test
    fun `a narrower subnet stays inside its bounds`() {
        val hosts = subnetHosts(v4("10.0.0.9"), 29).map { it.hostAddress }
        assertEquals(listOf("10.0.0.10", "10.0.0.11", "10.0.0.12", "10.0.0.13", "10.0.0.14"), hosts)
    }

    @Test
    fun `high octets do not go negative`() {
        val hosts = subnetHosts(v4("172.31.255.200"), 24).map { it.hostAddress }
        assertTrue("172.31.255.254" in hosts)
        assertTrue(hosts.all { it.startsWith("172.31.255.") })
    }

    @Test
    fun `wide or degenerate subnets are not swept`() {
        assertTrue(subnetHosts(v4("10.0.0.9"), 16).isEmpty())
        assertTrue(subnetHosts(v4("10.0.0.9"), 23).isEmpty())
        assertTrue(subnetHosts(v4("10.0.0.9"), 31).isEmpty())
        assertTrue(subnetHosts(v4("10.0.0.9"), 32).isEmpty())
    }
}
