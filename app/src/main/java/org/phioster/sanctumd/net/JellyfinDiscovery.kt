package org.phioster.sanctumd.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException

/**
 * Finding Jellyfin servers on the local network.
 *
 * Every Jellyfin server listens on UDP 7359 and answers a broadcast asking "who is JellyfinServer?"
 * with its own address and name. That is how the official clients discover servers, and it saves the
 * one thing a television is genuinely bad at: typing a URL with a remote control.
 */

/** A server that answered the discovery broadcast. */
@Serializable
data class DiscoveredServer(
    val Address: String = "",
    val Id: String = "",
    val Name: String = "",
) {
    val displayName: String get() = Name.ifBlank { Address.substringAfter("://").substringBefore('/') }
}

private const val DISCOVERY_PORT = 7359
private const val DISCOVERY_MESSAGE = "who is JellyfinServer?"

/**
 * Every broadcast address worth trying: the global one plus each interface's subnet broadcast.
 * Some Android TV boxes silently drop 255.255.255.255, so the directed addresses are what actually
 * reaches the server on those.
 */
private fun broadcastTargets(): List<InetAddress> {
    val out = mutableListOf<InetAddress>()
    runCatching { out += InetAddress.getByName("255.255.255.255") }
    runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.interfaceAddresses }
            .mapNotNull { it.broadcast }
            .filterIsInstance<Inet4Address>()
            .forEach { out += it }
    }
    return out.distinctBy { it.hostAddress }
}

/**
 * Every other host address in [address]'s subnet, for asking each one directly.
 *
 * A broadcast only helps if the server's network stack lets it in, and an Android phone running
 * Jellyfin does not: with its screen off and no multicast lock held, the Wi-Fi firmware drops every
 * broadcast frame to save power, while unicast still arrives. Asking each address on its own finds
 * such a server anyway. Limited to /24 and narrower, 254 small packets at most; a wider subnet
 * returns nothing rather than flooding it.
 */
internal fun subnetHosts(address: Inet4Address, prefixLength: Int): List<InetAddress> {
    if (prefixLength !in 24..30) return emptyList()
    val bytes = address.address
    val ip = bytes.fold(0L) { acc, b -> (acc shl 8) or (b.toLong() and 0xFF) }
    val mask = (0xFFFFFFFFL shl (32 - prefixLength)) and 0xFFFFFFFFL
    val network = ip and mask
    val broadcast = network or (mask.inv() and 0xFFFFFFFFL)
    return ((network + 1) until broadcast)
        .filter { it != ip }
        .map { h -> InetAddress.getByAddress(ByteArray(4) { i -> (h shr (24 - 8 * i)).toByte() }) }
}

/** The unicast fallback for [broadcastTargets]: every neighbour on each IPv4 interface. */
private fun unicastTargets(): List<InetAddress> = runCatching {
    NetworkInterface.getNetworkInterfaces().toList()
        .filter { it.isUp && !it.isLoopback }
        .flatMap { it.interfaceAddresses }
        .filter { it.address is Inet4Address }
        .flatMap { subnetHosts(it.address as Inet4Address, it.networkPrefixLength.toInt()) }
        .distinctBy { it.hostAddress }
}.getOrDefault(emptyList())

/**
 * Broadcasts a discovery request, asks every neighbour directly as well (see [subnetHosts]), and
 * collects answers for [timeoutMs].
 *
 * Deduplicated by server id (a server on several interfaces answers more than once). Never throws:
 * a network the broadcast can't leave simply yields an empty list, and the setup screen falls back
 * to manual entry.
 */
suspend fun jellyfinDiscoverServers(timeoutMs: Int = 2500): List<DiscoveredServer> = withContext(Dispatchers.IO) {
    val found = LinkedHashMap<String, DiscoveredServer>()
    runCatching {
        DatagramSocket().use { socket ->
            socket.broadcast = true
            socket.soTimeout = 400
            val payload = DISCOVERY_MESSAGE.toByteArray()
            (broadcastTargets() + unicastTargets()).forEach { target ->
                runCatching {
                    socket.send(DatagramPacket(payload, payload.size, InetSocketAddress(target, DISCOVERY_PORT)))
                }
            }

            val deadline = System.currentTimeMillis() + timeoutMs
            val buffer = ByteArray(2048)
            while (System.currentTimeMillis() < deadline) {
                val packet = DatagramPacket(buffer, buffer.size)
                try {
                    socket.receive(packet)
                } catch (_: SocketTimeoutException) {
                    continue // keep listening until the deadline; servers answer at their own pace
                }
                val body = String(packet.data, 0, packet.length).trim()
                val server = runCatching { json.decodeFromString<DiscoveredServer>(body) }.getOrNull() ?: continue
                if (server.Address.isBlank()) continue
                found.putIfAbsent(server.Id.ifBlank { server.Address }, server)
            }
        }
    }
    found.values.toList()
}
