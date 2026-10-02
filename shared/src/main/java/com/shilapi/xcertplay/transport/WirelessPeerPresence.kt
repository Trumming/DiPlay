package com.shilapi.xcertplay.transport

/**
 * Marks the moment a Wi-Fi peer (the iPhone) first shows up on the accessory network.
 *
 * The wireless control loop needs exactly one bit of live state from outside its socket:
 * whether the phone is on the network before it decides to re-send the 0x4301 CarPlay invite
 * (see [Iap2CarPlayStartResendPolicy]). The passive link probe feeds this holder from ARP
 * entries and mDNS traffic; both run on their own threads, so the transition is synchronized.
 */
class WirelessPeerPresence {
    @Volatile
    private var firstAddress: String? = null

    val isPresent: Boolean
        get() = firstAddress != null

    val address: String?
        get() = firstAddress

    /**
     * Records a peer sighting. Returns the address on the absent→present transition (so the
     * caller can log it once) and null when a peer was already marked present.
     */
    fun markPresent(address: String): String? {
        synchronized(this) {
            if (firstAddress != null) return null
            firstAddress = address
        }
        return address
    }
}
