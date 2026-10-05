package com.shilapi.xcertplay.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualHotspotCredentialTest {
    @Test
    fun accessPointKeyWinsWhenThePlatformExposesIt() {
        val credential = resolveManualHotspotCredential(
            configured = "888888888",
            fromAccessPoint = "1357924680",
        )

        assertTrue(credential.fromAccessPoint)
        assertEquals(
            "the access point is the source of truth; advertising the app setting here would tell " +
                "the iPhone a key the accessory does not use",
            "1357924680",
            credential.passphrase,
        )
        assertEquals(false, credential.matchesSetting)
    }

    @Test
    fun matchingKeyIsReportedAsMatching() {
        val credential = resolveManualHotspotCredential(
            configured = "888888888",
            fromAccessPoint = "888888888",
        )

        assertTrue(credential.fromAccessPoint)
        assertEquals(true, credential.matchesSetting)
        assertEquals("888888888", credential.passphrase)
    }

    @Test
    fun maskedOrUnusablePlatformValuesFallBackToTheSetting() {
        for (masked in listOf(null, "", "   ", "********", "............", "short", "x".repeat(64))) {
            val credential = resolveManualHotspotCredential(
                configured = "888888888",
                fromAccessPoint = masked,
            )
            assertFalse("a masked value must never be advertised: '$masked'", credential.fromAccessPoint)
            assertEquals("888888888", credential.passphrase)
            assertNull("nothing can be compared when the platform hides the key", credential.matchesSetting)
        }
    }

    @Test
    fun openAccessPointKeepsTheEmptyConfiguredKey() {
        val credential = resolveManualHotspotCredential(configured = "", fromAccessPoint = "")

        assertFalse(credential.fromAccessPoint)
        assertEquals("", credential.passphrase)
    }
}
