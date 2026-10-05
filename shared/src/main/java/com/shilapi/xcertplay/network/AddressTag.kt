package com.shilapi.xcertplay.network

import java.net.Inet4Address
import java.net.InetAddress
import java.security.MessageDigest

/**
 * Privacy-safe but comparable tag for a network address.
 *
 * Diagnostic reports redact every address to the same `[ip]` placeholder, so a report cannot show
 * whether the accessory and the phone agreed on one subnet, or whether a run advertised the same
 * address as the run before it — exactly the questions that matter when a phone joins the accessory
 * network, browses the accessory's services and then never opens an AirPlay connection. A short
 * digest keeps the address out of the report while still letting two runs be compared, and
 * [sameSubnetV4] answers the subnet question directly without printing anything.
 */
internal object AddressTag {
    /** `ip#a1b2c3` for IPv4, `ip6#a1b2c3` for IPv6, `none` when the address is unknown. */
    fun of(address: InetAddress?): String {
        if (address == null) return NONE
        val family = if (address is Inet4Address) "ip#" else "ip6#"
        val digest = MessageDigest.getInstance("SHA-256").digest(address.address)
        return family + digest.take(DIGEST_BYTES).joinToString("") { "%02x".format(it) }
    }

    /** [of] for an address that arrives as text; a name is never resolved, it has no tag. */
    fun of(host: String?): String = of(parse(host))

    /** The whole verdict a report needs about a peer: how it tags and whether it shares our /24. */
    fun scope(self: InetAddress?, host: String?): String {
        val peer = parse(host)
        return "tag=${of(peer)} sameSubnet=${sameSubnetV4(self, peer)}"
    }

    /** True when both addresses are IPv4 and share their first three octets; null when undecidable. */
    fun sameSubnetV4(first: InetAddress?, second: InetAddress?): Boolean? {
        val a = first as? Inet4Address ?: return null
        val b = second as? Inet4Address ?: return null
        return a.address.take(SUBNET_OCTETS) == b.address.take(SUBNET_OCTETS)
    }

    private fun parse(host: String?): InetAddress? {
        val literal = host?.substringBefore('%')?.takeIf { LITERAL.matches(it) } ?: return null
        return runCatching { InetAddress.getByName(literal) }.getOrNull()
    }

    private const val NONE = "none"
    private const val DIGEST_BYTES = 3
    private const val SUBNET_OCTETS = 3
    private val LITERAL = Regex("^[0-9a-fA-F:.]{3,45}$")
}
