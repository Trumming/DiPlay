package com.shilapi.xcertplay.network

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

/**
 * Picks the address iPhones should dial on a wireless hotspot interface.
 *
 * Routable addresses come first: on legacy hotspot networks (API 26-28) the iPhone joins over
 * IPv4, and legacy Bonjour stacks neither answer queries scoped to a link-local source nor
 * advertise one, so a scoped link-local endpoint there would never be reached. Wi-Fi Direct group
 * interfaces have no IPv4 at all, so the scoped link-local path stays as the fallback.
 */
internal fun wirelessHostAddress(addresses: List<InetAddress>, interfaceIndex: Int): InetAddress? {
    addresses.filterIsInstance<Inet6Address>()
        .firstOrNull { isRoutable(it) && !it.isLinkLocalAddress }
        ?.let { return it }
    addresses.firstOrNull { it is Inet4Address && isRoutable(it) }?.let { return it }
    if (interfaceIndex > 0) {
        addresses.filterIsInstance<Inet6Address>().firstOrNull { it.isLinkLocalAddress }?.let {
            return Inet6Address.getByAddress(null, it.address, interfaceIndex)
        }
    }
    return null
}

private fun isRoutable(address: InetAddress): Boolean =
    !address.isLoopbackAddress && !address.isLinkLocalAddress &&
        !address.isAnyLocalAddress && !address.isMulticastAddress
