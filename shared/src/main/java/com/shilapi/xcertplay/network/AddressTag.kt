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
        if (address == null) return "none"
        val family = if (address is Inet4Address) "ip#" else "ip6#"
        val digest = MessageDigest.getInstance("SHA-256").digest(address.address)
        return family + digest.take(DIGEST_BYTES).joinToString("") { "%02x".format(it) }
    }

    /** [of] for an address that arrives as text; never resolves names. */
    fun ofText(host: String?): String {
        val trimmed = host?.substringBefore('%')?.takeIf { it.isNotBlank() } ?: return "none"
        if (!LITERAL.matches(trimmed)) return "none"
        return runCatching { of(InetAddress.getByName(trimmed)) }.getOrDefault("none")
    }

    /** True when both addresses are IPv4 and share their first three octets; null when undecidable. */
    fun sameSubnetV4(first: InetAddress?, second: InetAddress?): Boolean? {
        val a = first as? Inet4Address ?: return null
        val b = second as? Inet4Address ?: return null
        return a.address.take(SUBNET_OCTETS) == b.address.take(SUBNET_OCTETS)
    }

    /** [sameSubnetV4] where the second address arrives as text. */
    fun sameSubnetV4Text(first: InetAddress?, second: String?): Boolean? {
        val trimmed = second?.substringBefore('%')?.takeIf { it.isNotBlank() } ?: return null
        if (!LITERAL.matches(trimmed)) return null
        return runCatching { sameSubnetV4(first, InetAddress.getByName(trimmed)) }.getOrNull()
    }

    private const val DIGEST_BYTES = 3
    private const val SUBNET_OCTETS = 3
    private val LITERAL = Regex("^[0-9a-fA-F:.]{3,45}$")
}
