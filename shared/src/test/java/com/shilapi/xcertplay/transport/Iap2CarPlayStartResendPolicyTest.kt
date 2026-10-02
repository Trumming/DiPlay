package com.shilapi.xcertplay.transport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Iap2CarPlayStartResendPolicyTest {
    private fun nanos(millis: Long) = millis * 1_000_000L

    @Test
    fun coldInviteResendsWhenThePeerJoinsAndThenAfterSpacing() {
        var now = 0L
        val policy = Iap2CarPlayStartResendPolicy(nowNanos = { now })

        policy.onInviteSent(peerOnNetwork = false)
        assertNull(
            "no resend while the phone is still off the network",
            policy.resendDue(peerOnNetwork = false, airPlayActive = false),
        )
        assertEquals(
            "Wi-Fi peer joined the network after the cold invite",
            policy.resendDue(peerOnNetwork = true, airPlayActive = false),
        )
        assertNull(
            "the peer-triggered resend fires once",
            policy.resendDue(peerOnNetwork = true, airPlayActive = false),
        )

        now += nanos(10_001)
        assertEquals(
            "AirPlay still not connected after the previous invite",
            policy.resendDue(peerOnNetwork = true, airPlayActive = false),
        )
        now += nanos(10_001)
        assertEquals(
            "AirPlay still not connected after the previous invite",
            policy.resendDue(peerOnNetwork = true, airPlayActive = false),
        )
        now += nanos(10_001)
        assertNull(
            "the timed ladder is bounded by the budget",
            policy.resendDue(peerOnNetwork = true, airPlayActive = false),
        )
    }

    @Test
    fun peerTriggerSurvivesASpentTimedLadder() {
        // Regression from an Android 8 hotspot: the timed ladder spent its whole budget at
        // +4s/+8s while the phone only associated at +24s, so the trigger that matches the
        // cold-start failure had nothing left to send.
        var now = 0L
        val policy = Iap2CarPlayStartResendPolicy(nowNanos = { now })

        policy.onInviteSent(peerOnNetwork = false)
        now += nanos(10_001)
        assertEquals(
            "AirPlay still not connected after the previous invite",
            policy.resendDue(peerOnNetwork = false, airPlayActive = false),
        )
        now += nanos(10_001)
        assertEquals(
            "AirPlay still not connected after the previous invite",
            policy.resendDue(peerOnNetwork = false, airPlayActive = false),
        )

        now += nanos(4_000)
        assertEquals(
            "the phone associates long after the timed budget is spent",
            "Wi-Fi peer joined the network after the cold invite",
            policy.resendDue(peerOnNetwork = true, airPlayActive = false),
        )
        assertNull(
            "and that is the last re-send of the window",
            policy.resendDue(peerOnNetwork = true, airPlayActive = false),
        )
    }

    @Test
    fun coldInviteKeepsWakingUntilThePeerAppears() {
        var now = 0L
        val policy = Iap2CarPlayStartResendPolicy(nowNanos = { now })

        policy.onInviteSent(peerOnNetwork = false)
        now += nanos(10_001)
        policy.resendDue(peerOnNetwork = false, airPlayActive = false)
        now += nanos(10_001)
        policy.resendDue(peerOnNetwork = false, airPlayActive = false)

        assertTrue(
            "a cold invite still waits for the phone after the timed ladder is spent",
            policy.needsWakeup(),
        )
        policy.resendDue(peerOnNetwork = true, airPlayActive = false)
        assertFalse("nothing left to wait for", policy.needsWakeup())
    }

    @Test
    fun warmInviteSkipsThePeerTriggerButKeepsTheTimedLadder() {
        var now = 0L
        val policy = Iap2CarPlayStartResendPolicy(nowNanos = { now })

        policy.onInviteSent(peerOnNetwork = true)
        assertNull(
            "the phone was on the network at send time; nothing to recover",
            policy.resendDue(peerOnNetwork = true, airPlayActive = false),
        )
        now += nanos(10_001)
        assertEquals(
            "AirPlay still not connected after the previous invite",
            policy.resendDue(peerOnNetwork = true, airPlayActive = false),
        )
        now += nanos(10_001)
        assertEquals(
            "AirPlay still not connected after the previous invite",
            policy.resendDue(peerOnNetwork = true, airPlayActive = false),
        )
        assertFalse("a warm invite stops waking once the ladder is spent", policy.needsWakeup())
    }

    @Test
    fun airPlayActivityDisarmsTheLadder() {
        var now = 0L
        val policy = Iap2CarPlayStartResendPolicy(nowNanos = { now })

        policy.onInviteSent(peerOnNetwork = false)
        assertNull(policy.resendDue(peerOnNetwork = false, airPlayActive = true))
        assertFalse(policy.needsWakeup())

        now += nanos(30_000)
        assertNull(
            "a disarmed ladder stays disarmed even if the peer joins later",
            policy.resendDue(peerOnNetwork = true, airPlayActive = false),
        )
    }

    @Test
    fun freshInviteResetsTheLadder() {
        var now = 0L
        val policy = Iap2CarPlayStartResendPolicy(nowNanos = { now })

        policy.onInviteSent(peerOnNetwork = false)
        now += nanos(10_001)
        assertEquals(
            "AirPlay still not connected after the previous invite",
            policy.resendDue(peerOnNetwork = false, airPlayActive = false),
        )
        now += nanos(10_001)
        assertEquals(
            "AirPlay still not connected after the previous invite",
            policy.resendDue(peerOnNetwork = false, airPlayActive = false),
        )
        assertNull(policy.resendDue(peerOnNetwork = false, airPlayActive = false))

        // The phone re-sent 0x4300; the fresh answer starts a new ladder.
        policy.onInviteSent(peerOnNetwork = true)
        now += nanos(10_001)
        assertEquals(
            "AirPlay still not connected after the previous invite",
            policy.resendDue(peerOnNetwork = true, airPlayActive = false),
        )
    }

    @Test
    fun wakeupTrackingFollowsTheLadder() {
        var now = 0L
        val policy = Iap2CarPlayStartResendPolicy(nowNanos = { now })

        assertFalse(policy.needsWakeup())
        policy.onInviteSent(peerOnNetwork = true)
        assertTrue(policy.needsWakeup())
        now += nanos(10_001)
        policy.resendDue(peerOnNetwork = true, airPlayActive = false)
        assertTrue(policy.needsWakeup())
        now += nanos(10_001)
        policy.resendDue(peerOnNetwork = true, airPlayActive = false)
        assertFalse(policy.needsWakeup())
    }

    @Test
    fun zeroTimedBudgetKeepsThePeerTrigger() {
        var now = 0L
        val policy = Iap2CarPlayStartResendPolicy(maxResends = 0, nowNanos = { now })

        policy.onInviteSent(peerOnNetwork = true)
        assertFalse("a warm invite with no timed budget never wakes", policy.needsWakeup())
        assertNull(policy.resendDue(peerOnNetwork = true, airPlayActive = false))

        policy.onInviteSent(peerOnNetwork = false)
        assertTrue("a cold invite still waits for the phone", policy.needsWakeup())
        assertEquals(
            "Wi-Fi peer joined the network after the cold invite",
            policy.resendDue(peerOnNetwork = true, airPlayActive = false),
        )
        assertFalse(policy.needsWakeup())
    }
}
