package com.shilapi.xcertplay.network

import java.net.Inet6Address
import java.net.InetAddress
import org.junit.Assert.*
import org.junit.Test

class WirelessHostAddressTest {
    @Test fun manualApPrefersRoutableIpv4OverScopedLinkLocal() {
        // Legacy hotspot networks (API 26-28) advertise and join over IPv4; a scoped link-local
        // endpoint there never receives the iPhone's mDNS queries or RTSP connection.
        val ipv4 = ip("192.168.43.1")
        assertEquals(ipv4, wirelessHostAddress(listOf(ip("fe80::1234"), ipv4), 7))
        assertEquals(ipv4, wirelessHostAddress(listOf(ipv4, ip("fe80::1234")), 7))
    }

    @Test fun globalIpv6StillWins() {
        val global = ip("2001:db8::1")
        assertEquals(global, wirelessHostAddress(listOf(ip("192.168.43.1"), global), 7))
    }

    @Test fun replacesScopeFromAnotherInterface() {
        val wrongScope = Inet6Address.getByAddress(null, ip("fe80::1234").address, 3)
        assertEquals(8, (wirelessHostAddress(listOf(wrongScope), 8) as Inet6Address).scopeId)
    }

    @Test fun fallsBackToScopedLinkLocalWithoutIpv4() {
        // Wi-Fi Direct group interfaces have no IPv4; the scoped link-local path stays.
        val scoped = wirelessHostAddress(listOf(ip("fe80::1234")), 7) as Inet6Address
        assertTrue(scoped.isLinkLocalAddress)
        assertEquals(7, scoped.scopeId)
        assertNull(wirelessHostAddress(listOf(ip("0.0.0.0"), ip("127.0.0.1"), ip("224.0.0.251")), 7))
    }

    private fun ip(value: String) = InetAddress.getByName(value)
}
