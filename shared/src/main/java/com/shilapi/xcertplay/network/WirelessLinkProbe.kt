package com.shilapi.xcertplay.network

import com.shilapi.xcertplay.transport.WirelessPeerPresence
import java.io.File
import java.net.DatagramPacket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Passive observer for the wireless bring-up window, when the only evidence of the iPhone's
 * progress is whatever reaches this device. The car head units export no logcat, so every
 * observation is written straight into the session log through [sink]:
 *
 *  - interface table: which interfaces carry which addresses (is the AP really on [interfaceName]?)
 *  - ARP entries: any client that obtained an address and spoke shows up here
 *  - mDNS queries: proof the iPhone joined and what it is resolving
 *
 * The first ARP entry on the AP interface, or the first mDNS packet from another address,
 * also marks [presence] so the wireless control loop learns that the phone joined the network.
 * Everything is best-effort; a missing piece logs one line and stops.
 */
class WirelessLinkProbe(
    private val interfaceName: String?,
    private val interfaceAddress: InetAddress?,
    private val sink: (String) -> Unit,
    private val presence: WirelessPeerPresence? = null,
) {
    private val closed = AtomicBoolean(false)
    private val threads = mutableListOf<Thread>()
    private val seenArp = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())
    private val seenMdns = ConcurrentHashMap<String, Long>()
    private val selfHostAddress = interfaceAddress?.hostAddress

    /** Starts the observers. Calling more than once is harmless. */
    fun start() {
        if (threads.isNotEmpty()) return
        dumpInterfaces()
        startArpWatcher()
        startMdnsSniffer()
    }

    fun close() {
        closed.set(true)
        val joining = synchronized(threads) { threads.toList() }
        joining.forEach { thread ->
            runCatching { thread.join(TimeUnit.SECONDS.toMillis(1)) }
            runCatching { thread.interrupt() }
        }
        synchronized(threads) { threads.clear() }
    }

    private fun spawn(name: String, block: () -> Unit) {
        val thread = Thread(block, name).apply { isDaemon = true }
        synchronized(threads) { threads += thread }
        thread.start()
    }

    private fun dumpInterfaces() {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return
            val lines = Collections.list(interfaces)
                .asSequence()
                .filterNot { it.isLoopback }
                .map { networkInterface ->
                    val addresses = try {
                        Collections.list(networkInterface.inetAddresses)
                            .joinToString(",") { it.hostAddress ?: "?" }
                    } catch (_: Exception) {
                        "?"
                    }
                    val up = try { networkInterface.isUp } catch (_: Exception) { false }
                    "${networkInterface.name} up=$up [$addresses]"
                }
                .joinToString("; ")
            sink("probe ifaces $lines")
        } catch (error: Exception) {
            sink("probe ifaces unavailable: ${error.message}")
        }
    }

    private fun startArpWatcher() {
        spawn("wireless-link-probe-arp") {
            var ticks = 0
            while (!closed.get()) {
                try {
                    watchArpOnce()
                    ticks++
                    if (ticks % INTERFACE_DUMP_INTERVAL_TICKS == 0) dumpInterfaces()
                } catch (_: Exception) {
                    // A transient read failure must not end the watcher.
                }
                if (!sleepWhileOpen(ARP_POLL_MILLIS)) return@spawn
            }
        }
    }

    private fun watchArpOnce() {
        val text = try {
            File(ARP_PATH).readText()
        } catch (error: Exception) {
            sink("probe arp unavailable: ${error.message}")
            closed.set(true)
            return
        }
        text.lineSequence()
            .drop(1)
            .map { it.trim().split(WHITESPACE) }
            .filter { it.size >= 6 }
            .filter { fields -> fields[2] != "0x0" }
            .forEach { fields ->
                val ip = fields[0]
                val mac = fields[3]
                val device = fields[5]
                val key = "$ip|$mac|$device"
                if (seenArp.add(key)) {
                    sink("probe arp $ip at $mac dev=$device ${AddressTag.scope(interfaceAddress, ip)}")
                }
                if (presence != null && (interfaceName == null || device == interfaceName)) {
                    presence.markPresent(ip)?.let {
                        sink(
                            "probe peer present $ip dev=$device (arp) " +
                                AddressTag.scope(interfaceAddress, ip),
                        )
                    }
                }
            }
        if (seenArp.size > MAX_SEEN_ARP) seenArp.clear()
    }

    private fun startMdnsSniffer() {
        spawn("wireless-link-probe-mdns") { mdnsLoop() }
    }

    private fun mdnsLoop() {
        val socket = MulticastSocket(null)
        try {
            socket.reuseAddress = true
            socket.bind(InetSocketAddress(MDNS_PORT))
            val group = InetAddress.getByName(MDNS_GROUP) as Inet4Address
            val network = interfaceName?.let {
                try { NetworkInterface.getByName(it) } catch (_: Exception) { null }
            }
            val joined = when {
                network != null -> runCatching {
                    socket.joinGroup(InetSocketAddress(group, MDNS_PORT), network)
                }.isSuccess
                else -> false
            } || runCatching { socket.joinGroup(group) }.isSuccess
            socket.soTimeout = SOCKET_TIMEOUT_MILLIS
            sink("probe mdns listening iface=$interfaceName joined=$joined")
            val buffer = ByteArray(DATAGRAM_BYTES)
            while (!closed.get()) {
                val packet = DatagramPacket(buffer, buffer.size)
                try {
                    socket.receive(packet)
                } catch (_: SocketTimeoutException) {
                    continue
                }
                describeMdnsPacket(packet)
            }
        } catch (error: Exception) {
            if (!closed.get()) sink("probe mdns unavailable: ${error.message}")
        } finally {
            runCatching { socket.close() }
        }
    }

    private fun describeMdnsPacket(packet: DatagramPacket) {
        val payload = packet.data
        val length = packet.length
        if (length < DNS_HEADER_BYTES) return
        val flags = ((payload[2].toInt() and 0xff) shl 8) or (payload[3].toInt() and 0xff)
        val source = packet.address?.hostAddress ?: "?"
        if (presence != null && source != "?" && source != selfHostAddress) {
            presence.markPresent(source)?.let {
                sink("probe peer present $source (mdns) ${AddressTag.scope(interfaceAddress, source)}")
            }
        }
        if (flags and DNS_FLAG_RESPONSE != 0) {
            // Responses show whether the responder side (JmDNS announcements and answers) is alive.
            val answerCount = ((payload[6].toInt() and 0xff) shl 8) or (payload[7].toInt() and 0xff)
            val firstName = readQueryName(payload, length, DNS_HEADER_BYTES)?.first
            val signature = "r|$source|$answerCount|$firstName"
            if (throttle(signature)) return
            sink("probe mdns -> $source answers=$answerCount${firstName?.let { " first=$it" } ?: ""}")
            return
        }
        val questionCount = ((payload[4].toInt() and 0xff) shl 8) or (payload[5].toInt() and 0xff)
        if (questionCount == 0) return
        val names = mutableListOf<String>()
        var offset = DNS_HEADER_BYTES
        repeat(minOf(questionCount, MAX_LOGGED_QUESTIONS)) {
            val parsed = readQueryName(payload, length, offset) ?: return
            names += parsed.first
            offset = parsed.second + QUESTION_TRAILER_BYTES
            if (offset > length) return
        }
        if (names.isEmpty()) return
        val signature = "q|$source|${names.first()}"
        if (throttle(signature)) return
        sink("probe mdns <- $source q=${names.joinToString(",")}")
    }

    /** True when this signature was logged within [MDNS_REPEAT_NANOS]. */
    private fun throttle(signature: String): Boolean {
        val now = System.nanoTime()
        val previous = seenMdns[signature]
        if (previous != null && now - previous < MDNS_REPEAT_NANOS) return true
        if (seenMdns.size > MAX_SEEN_MDNS) seenMdns.clear()
        seenMdns[signature] = now
        return false
    }

    /** Returns the name and the offset just past it; never follows compression pointers. */
    private fun readQueryName(payload: ByteArray, length: Int, start: Int): Pair<String, Int>? {
        if (start >= length) return null
        val labels = mutableListOf<String>()
        var offset = start
        while (offset < length) {
            val labelLength = payload[offset].toInt() and 0xff
            if (labelLength == 0 || labelLength >= 0xc0) {
                val name = labels.joinToString(".").takeIf { it.isNotEmpty() } ?: return null
                val end = if (labelLength == 0) offset + 1 else offset + 2
                return name to end
            }
            if (offset + 1 + labelLength > length || labels.size > MAX_LABELS) return null
            labels += String(payload, offset + 1, labelLength, Charsets.US_ASCII)
            offset += 1 + labelLength
        }
        return null
    }

    private fun sleepWhileOpen(millis: Long): Boolean {
        var remaining = millis
        while (remaining > 0) {
            if (closed.get()) return false
            val step = minOf(remaining, 500L)
            try {
                Thread.sleep(step)
            } catch (_: InterruptedException) {
                return false
            }
            remaining -= step
        }
        return !closed.get()
    }

    private companion object {
        const val ARP_PATH = "/proc/net/arp"
        const val ARP_POLL_MILLIS = 3_000L
        const val INTERFACE_DUMP_INTERVAL_TICKS = 5
        const val MAX_SEEN_ARP = 64
        const val MDNS_PORT = 5353
        const val MDNS_GROUP = "224.0.0.251"
        const val MDNS_REPEAT_NANOS = 2L * 1_000_000_000L
        const val MAX_SEEN_MDNS = 64
        const val DATAGRAM_BYTES = 1_500
        const val SOCKET_TIMEOUT_MILLIS = 1_000
        const val DNS_HEADER_BYTES = 12
        const val DNS_FLAG_RESPONSE = 0x8000
        const val QUESTION_TRAILER_BYTES = 4
        const val MAX_LOGGED_QUESTIONS = 3
        const val MAX_LABELS = 8
        val WHITESPACE = Regex("\\s+")
    }
}
