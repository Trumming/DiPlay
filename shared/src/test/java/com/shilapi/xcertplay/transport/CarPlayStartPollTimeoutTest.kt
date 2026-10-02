package com.shilapi.xcertplay.transport

import org.junit.Assert.assertEquals
import org.junit.Test

class CarPlayStartPollTimeoutTest {
    @Test
    fun armedLadderWakesUpWithinASecondEvenWithAFullWatchdog() {
        // Regression: capping the poll by the watchdog alone let the loop sleep through the whole
        // invite window, so the peer-join resend never fired on a real cold start.
        assertEquals(
            1_000L,
            carPlayStartPollTimeout(
                pollTimeoutMillis = 45_000L,
                watchdogRemainingMillis = 45_000L,
                resendWakeupNeeded = true,
            ),
        )
    }

    @Test
    fun armedLadderShortensThePollWithoutAWatchdog() {
        assertEquals(
            1_000L,
            carPlayStartPollTimeout(
                pollTimeoutMillis = 60_000L,
                watchdogRemainingMillis = 0L,
                resendWakeupNeeded = true,
            ),
        )
    }

    @Test
    fun watchdogStillCapsThePollOnceTheLadderIsDone() {
        assertEquals(
            4_000L,
            carPlayStartPollTimeout(
                pollTimeoutMillis = 30_000L,
                watchdogRemainingMillis = 4_000L,
                resendWakeupNeeded = false,
            ),
        )
    }

    @Test
    fun idleLoopKeepsItsOwnPollTimeout() {
        assertEquals(
            30_000L,
            carPlayStartPollTimeout(
                pollTimeoutMillis = 30_000L,
                watchdogRemainingMillis = 0L,
                resendWakeupNeeded = false,
            ),
        )
    }

    @Test
    fun shorterProviderTimeoutIsNotExtended() {
        assertEquals(
            250L,
            carPlayStartPollTimeout(
                pollTimeoutMillis = 250L,
                watchdogRemainingMillis = 9_000L,
                resendWakeupNeeded = true,
            ),
        )
    }
}
