package com.shilapi.xcertplay.transport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WirelessPeerPresenceTest {
    @Test
    fun marksOnlyTheFirstSighting() {
        val presence = WirelessPeerPresence()
        assertFalse(presence.isPresent)
        assertNull(presence.address)

        assertEquals("192.168.43.112", presence.markPresent("192.168.43.112"))
        assertTrue(presence.isPresent)
        assertEquals("192.168.43.112", presence.address)

        assertNull("later sightings do not re-fire", presence.markPresent("fe80::1c7d"))
        assertEquals("192.168.43.112", presence.address)
    }
}
