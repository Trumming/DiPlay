package com.shilapi.xcertplay.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ManualHotspotCredentialTest {
    @Test
    fun accessPointKeyWinsWhenThePlatformExposesIt() {
        val credential = resolveManualHotspotCredential(
            configured = "888888888",
            fromAccessPoint = "1357924680",
        )

        assertEquals(SOURCE_ACCESS_POINT, credential.source)
        assertEquals(
            "the access point is the source of truth; advertising the app setting here would tell " +
                "the iPhone a key the accessory does not use",
            "1357924680",
            credential.passphrase,
        )
        assertEquals(false, credential.matchesConfigured)
    }

    @Test
    fun matchingKeyIsReportedAsMatching() {
        val credential = resolveManualHotspotCredential(
            configured = "888888888",
            fromAccessPoint = "888888888",
        )

        assertEquals(SOURCE_ACCESS_POINT, credential.source)
        assertEquals(true, credential.matchesConfigured)
        assertEquals("888888888", credential.passphrase)
    }

    @Test
    fun maskedOrUnusablePlatformValuesFallBackToTheSetting() {
        for (masked in listOf(null, "", "   ", "********", "............", "short", "x".repeat(64))) {
            val credential = resolveManualHotspotCredential(
                configured = "888888888",
                fromAccessPoint = masked,
            )
            assertEquals(
                "a masked or impossible value must never be advertised: '$masked'",
                SOURCE_SETTING,
                credential.source,
            )
            assertEquals("888888888", credential.passphrase)
            assertNull("nothing can be compared when the platform hides the key", credential.matchesConfigured)
        }
    }

    @Test
    fun openAccessPointKeepsTheEmptyConfiguredKey() {
        val credential = resolveManualHotspotCredential(configured = "", fromAccessPoint = "")

        assertEquals(SOURCE_SETTING, credential.source)
        assertEquals("", credential.passphrase)
    }
}
