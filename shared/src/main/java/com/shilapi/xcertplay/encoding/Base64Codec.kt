package com.shilapi.xcertplay.encoding

/**
 * RFC 4648 Base64 for every supported API level: java.util.Base64 needs API 26 and
 * android.util.Base64 is unavailable in JVM unit tests.
 */
object Base64Codec {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

    private val INVERSE = IntArray(128) { -1 }.also { inverse ->
        ALPHABET.forEachIndexed { index, character -> inverse[character.code] = index }
    }

    /** Standard encoding with padding and no line breaks, like java.util.Base64.getEncoder. */
    fun encode(bytes: ByteArray): String {
        val builder = StringBuilder((bytes.size + 2) / 3 * 4)
        var index = 0
        while (index + 3 <= bytes.size) {
            appendGroup(builder, bytes, index, 3)
            index += 3
        }
        when (bytes.size - index) {
            1 -> appendGroup(builder, bytes, index, 1)
            2 -> appendGroup(builder, bytes, index, 2)
        }
        return builder.toString()
    }

    /**
     * RFC 2045 MIME encoding: [lineLength]-character lines joined by [separator], no separator
     * after the last line, like java.util.Base64.getMimeEncoder(lineLength, separator).
     */
    fun encodeLines(bytes: ByteArray, lineLength: Int, separator: String): String {
        require(lineLength in 4..76 && lineLength % 4 == 0) {
            "lineLength must be a multiple of 4 between 4 and 76"
        }
        val encoded = encode(bytes)
        if (encoded.length <= lineLength) return encoded
        return encoded.chunked(lineLength).joinToString(separator)
    }

    /**
     * Decodes standard input, also accepting whitespace and missing padding like the MIME decoder.
     * Rejected input throws IllegalArgumentException, as the JDK decoders do.
     */
    fun decode(text: String): ByteArray {
        val output = ByteArray(text.length / 4 * 3 + 3)
        var out = 0
        var buffer = 0
        var bits = 0
        var padded = false
        for (character in text) {
            when {
                padded -> Unit
                character == '=' -> padded = true
                character == ' ' || character == '\t' || character == '\r' || character == '\n' -> Unit
                else -> {
                    val value = if (character.code < 128) INVERSE[character.code] else -1
                    require(value >= 0) { "Illegal Base64 character U+${character.code.toString(16)}" }
                    buffer = (buffer shl 6) or value
                    bits += 6
                    if (bits >= 8) {
                        bits -= 8
                        output[out++] = ((buffer shr bits) and 0xff).toByte()
                    }
                }
            }
        }
        return output.copyOf(out)
    }

    /** Same as [decode] for ASCII input bytes, like the JDK MIME decoder's byte[] overload. */
    fun decode(bytes: ByteArray): ByteArray = decode(String(bytes, Charsets.US_ASCII))

    private fun appendGroup(builder: StringBuilder, bytes: ByteArray, offset: Int, size: Int) {
        val byte0 = bytes[offset].toInt() and 0xff
        val byte1 = if (size > 1) bytes[offset + 1].toInt() and 0xff else 0
        val byte2 = if (size > 2) bytes[offset + 2].toInt() and 0xff else 0
        val group = (byte0 shl 16) or (byte1 shl 8) or byte2
        builder.append(ALPHABET[(group shr 18) and 0x3f])
        builder.append(ALPHABET[(group shr 12) and 0x3f])
        builder.append(if (size > 1) ALPHABET[(group shr 6) and 0x3f] else '=')
        builder.append(if (size > 2) ALPHABET[group and 0x3f] else '=')
    }
}
