package com.shilapi.xcertplay.network

import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket

/**
 * Binds the AirPlay listener on [address] plus a best-effort wildcard socket of the opposite
 * address family on the same port, so an advertisement in either family still reaches the service.
 *
 * Some stacks (dual-stack-by-default JVMs) refuse the second bind; the primary then serves alone,
 * which is why callers must tolerate a one-element result.
 */
internal fun bindAirPlayServerSockets(address: InetAddress, port: Int): List<ServerSocket> {
    val primary = ServerSocket().apply {
        reuseAddress = true
        bind(InetSocketAddress(address, port))
    }
    val secondaryAddress = if (address is Inet4Address) "::" else "0.0.0.0"
    val secondary = ServerSocket()
    val bound = runCatching {
        secondary.reuseAddress = true
        secondary.bind(InetSocketAddress(InetAddress.getByName(secondaryAddress), primary.localPort))
    }.onFailure { error ->
        System.err.println(
            "AirPlay secondary bind unavailable address=$secondaryAddress " +
                "port=${primary.localPort}: $error",
        )
    }.isSuccess
    if (!bound) {
        runCatching { secondary.close() }
        return listOf(primary)
    }
    return listOf(primary, secondary)
}
