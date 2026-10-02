package com.shilapi.xcertplay.network

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Bind counts are platform-dependent (exclusive-address stacks such as Windows refuse the
 * wildcard secondary; Linux/Android allow it, as the shipped IapTunnel logs show), so the
 * assertions cover the portable guarantee: the primary always serves its own family, and any
 * added wildcard socket shares the primary port and switches family.
 */
class AirPlayServerBindTest {
    @Test fun ipv6BindAddsV4WildcardSecondaryWhenTheStackAllowsIt() {
        val servers = bindAirPlayServerSockets(ip("::1"), 0)
        try {
            val port = servers[0].localPort
            Socket("::1", port).use { assertTrue(it.isConnected) }
            assertWildcardSecondaries(servers, port, Inet4Address::class.java)
        } finally {
            servers.forEach { runCatching { it.close() } }
        }
    }

    @Test fun ipv4BindAddsV6WildcardSecondaryWhenTheStackAllowsIt() {
        val servers = bindAirPlayServerSockets(ip("127.0.0.1"), 0)
        try {
            val port = servers[0].localPort
            Socket("127.0.0.1", port).use { assertTrue(it.isConnected) }
            assertWildcardSecondaries(servers, port, Inet6Address::class.java)
        } finally {
            servers.forEach { runCatching { it.close() } }
        }
    }

    private fun assertWildcardSecondaries(
        servers: List<ServerSocket>,
        port: Int,
        family: Class<*>,
    ) {
        servers.drop(1).forEach { secondary ->
            assertEquals(port, secondary.localPort)
            val address = (secondary.localSocketAddress as InetSocketAddress).address
            assertTrue(family.isInstance(address))
            assertTrue(address.isAnyLocalAddress)
        }
    }

    private fun ip(value: String) = InetAddress.getByName(value)
}
