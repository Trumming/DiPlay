package com.shilapi.xcertplay.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

class AddressTagTest {
    @Test
    fun tagIsStableAndDoesNotCarryTheAddress() {
        val address = InetAddress.getByName("192.168.43.1")
        val tag = AddressTag.of(address)

        assertEquals("the same address always tags the same way", tag, AddressTag.of(address))
        assertEquals("text and parsed addresses tag alike", tag, AddressTag.of("192.168.43.1"))
        assertTrue("IPv4 tags are marked as such", tag.startsWith("ip#"))
        assertFalse("the tag must not leak the address", tag.contains("192") || tag.contains("43"))
        assertNotEquals(
            "different addresses tag differently",
            tag,
            AddressTag.of(InetAddress.getByName("192.168.44.1")),
        )
    }

    @Test
    fun unknownAddressesAreTaggedAsNone() {
        assertEquals("none", AddressTag.of(null as InetAddress?))
        assertEquals("none", AddressTag.of(null as String?))
        assertEquals("none", AddressTag.of(""))
        assertEquals("none", AddressTag.of("example.local"))
    }

    @Test
    fun scopedAndUnscopedTextAddressesTagAlike() {
        val plain = AddressTag.of(InetAddress.getByName("fe80::1"))
        assertEquals("the zone suffix is not part of the address", plain, AddressTag.of("fe80::1%wlan0"))
        assertTrue("IPv6 tags are marked as such", plain.startsWith("ip6#"))
    }

    @Test
    fun scopeReportsTheTagAndTheSubnetVerdict() {
        val host = InetAddress.getByName("192.168.43.1")

        assertEquals(
            "peer on our /24",
            "tag=${AddressTag.of("192.168.43.112")} sameSubnet=true",
            AddressTag.scope(host, "192.168.43.112"),
        )
        assertEquals(
            "peer on another /24",
            "tag=${AddressTag.of("10.0.0.7")} sameSubnet=false",
            AddressTag.scope(host, "10.0.0.7"),
        )
        assertEquals(
            "an unknown peer has no verdict",
            "tag=none sameSubnet=null",
            AddressTag.scope(host, null),
        )
    }

    @Test
    fun sameSubnetComparesIpv4Only() {
        val host = InetAddress.getByName("192.168.43.1")
        assertEquals(true, AddressTag.sameSubnetV4(host, InetAddress.getByName("192.168.43.112")))
        assertEquals(false, AddressTag.sameSubnetV4(host, InetAddress.getByName("192.168.44.112")))
        assertNull(
            "an IPv6 peer has no /24 verdict",
            AddressTag.sameSubnetV4(host, InetAddress.getByName("fe80::1")),
        )
        assertNull("an unknown peer has no verdict", AddressTag.sameSubnetV4(host, null))
    }
}
