package com.shilapi.xcertplay.transport

/**
 * Decides when the wireless 0x4301 CarPlay invite should be re-sent.
 *
 * On a cold start the iPhone can process the invite while it is still off the accessory
 * network. It then joins Wi-Fi but never opens the AirPlay connection: observed on an
 * Android 8 hotspot as ~34s of routine mDNS browsing, no _airplay._tcp query, no TCP to the
 * AirPlay port, then the phone dropping the procedure — while the same 0x4301 bytes deliver
 * a TCP connection within 216ms whenever the phone is already on the network. Production
 * dongle firmware (catplay-labs/catplay, CarPlayInviter) also re-invites when an invite
 * "gets accepted but never results in any connections".
 *
 * This policy keeps two independent triggers:
 *
 *  - the peer trigger, when the iPhone appears on the accessory network after a cold send.
 *    It has its own single-shot budget, so a phone that takes tens of seconds to associate
 *    still gets its invite re-sent. Observed on an Android 8 hotspot: the timed ladder spent
 *    its whole budget at +4s/+8s and the phone only joined at +24s, which left the trigger
 *    that actually matches the cold-start failure with nothing to send;
 *  - the timed ladder, which re-sends every [spacingMillis] while AirPlay has not connected.
 *    It covers networks where the peer cannot be observed at all (multicast filtered), and
 *    is bounded by [maxResends].
 *
 * An active AirPlay session disarms both. Every method is called from the single control-loop
 * thread only.
 */
class Iap2CarPlayStartResendPolicy(
    private val maxResends: Int = DEFAULT_MAX_RESENDS,
    private val spacingMillis: Long = DEFAULT_SPACING_MILLIS,
    private val nowNanos: () -> Long = System::nanoTime,
) {
    private var inviteSentWithPeerOnNetwork = false
    private var timedResends = 0
    private var nextResendAtNanos = 0L

    /** A fresh 0x4301 went out in response to the phone's 0x4300; resets the ladder. */
    fun onInviteSent(peerOnNetwork: Boolean) {
        inviteSentWithPeerOnNetwork = peerOnNetwork
        timedResends = 0
        armNextResend()
    }

    /** True while the control loop must keep waking up to evaluate a re-send. */
    fun needsWakeup(): Boolean {
        if (nextResendAtNanos == 0L) return false
        if (timedResends < maxResends) return true
        // The timed ladder is spent, but a cold invite still waits for the phone to appear.
        return !inviteSentWithPeerOnNetwork
    }

    /**
     * Human-readable reason a re-send is due right now, or null when it is not. Calling this
     * performs the resend bookkeeping, so a non-null result must be acted upon exactly once.
     */
    fun resendDue(peerOnNetwork: Boolean, airPlayActive: Boolean): String? {
        if (airPlayActive) {
            nextResendAtNanos = 0L
            return null
        }
        if (nextResendAtNanos == 0L) return null
        if (!inviteSentWithPeerOnNetwork && peerOnNetwork) {
            inviteSentWithPeerOnNetwork = true
            armNextResend()
            return "Wi-Fi peer joined the network after the cold invite"
        }
        if (timedResends >= maxResends) return null
        if (nowNanos() >= nextResendAtNanos) {
            timedResends++
            armNextResend()
            return "AirPlay still not connected after the previous invite"
        }
        return null
    }

    private fun armNextResend() {
        nextResendAtNanos = nowNanos() + spacingMillis * NANOS_PER_MILLISECOND
    }

    private companion object {
        const val DEFAULT_MAX_RESENDS = 2

        /**
         * Long enough that the timed re-send lands after a phone that is still associating, and
         * short enough to fit two of them plus the peer trigger inside the 45s invite window.
         */
        const val DEFAULT_SPACING_MILLIS = 10_000L
        const val NANOS_PER_MILLISECOND = 1_000_000L
    }
}
