package com.shilapi.xcertplay.encoding

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class Base64CodecTest {
    /** RFC 4648 section 10 test vectors. */
    @Test
    fun encodesRfc4648Vectors() {
        assertEquals("", Base64Codec.encode(ByteArray(0)))
        assertEquals("Zg==", Base64Codec.encode("f".toByteArray()))
        assertEquals("Zm8=", Base64Codec.encode("fo".toByteArray()))
        assertEquals("Zm9v", Base64Codec.encode("foo".toByteArray()))
        assertEquals("Zm9vYg==", Base64Codec.encode("foob".toByteArray()))
        assertEquals("Zm9vYmE=", Base64Codec.encode("fooba".toByteArray()))
        assertEquals("Zm9vYmFy", Base64Codec.encode("foobar".toByteArray()))
    }

    @Test
    fun decodesRfc4648Vectors() {
        assertArrayEquals(ByteArray(0), Base64Codec.decode(""))
        assertArrayEquals("f".toByteArray(), Base64Codec.decode("Zg=="))
        assertArrayEquals("fo".toByteArray(), Base64Codec.decode("Zm8="))
        assertArrayEquals("foo".toByteArray(), Base64Codec.decode("Zm9v"))
        assertArrayEquals("foob".toByteArray(), Base64Codec.decode("Zm9vYg=="))
        assertArrayEquals("fooba".toByteArray(), Base64Codec.decode("Zm9vYmE="))
        assertArrayEquals("foobar".toByteArray(), Base64Codec.decode("Zm9vYmFy"))
    }

    @Test
    fun roundTripsEveryLength() {
        val random = java.util.Random(0x5eed)
        for (size in 0..300) {
            val bytes = ByteArray(size).also(random::nextBytes)
            assertArrayEquals(bytes, Base64Codec.decode(Base64Codec.encode(bytes)))
        }
    }

    @Test
    fun decodeAcceptsWhitespaceAndMissingPaddingLikeMimeDecoder() {
        assertArrayEquals("foob".toByteArray(), Base64Codec.decode("Zm9v\nYg=="))
        assertArrayEquals("foob".toByteArray(), Base64Codec.decode("Zm9vYg"))
        assertArrayEquals("foobar".toByteArray(), Base64Codec.decode("Zm9v\r\nYmFy"))
    }

    @Test
    fun decodeRejectsInvalidCharacters() {
        assertThrows(IllegalArgumentException::class.java) { Base64Codec.decode("Zm9*") }
    }

    @Test
    fun encodeLinesMatchesMimeEncoderLayout() {
        val random = java.util.Random(0x5eed)
        val bytes = ByteArray(200).also(random::nextBytes)
        val encoded = Base64Codec.encode(bytes)
        assertEquals(encoded.chunked(64).joinToString("\n"), Base64Codec.encodeLines(bytes, 64, "\n"))
        // No trailing separator, matching java.util.Base64.getMimeEncoder.
        org.junit.Assert.assertNotEquals('\n', Base64Codec.encodeLines(bytes, 64, "\n").last())
    }

    @Test
    fun encodeLinesKeepsShortInputOnOneLine() {
        assertEquals("Zm9v", Base64Codec.encodeLines("foo".toByteArray(), 64, "\n"))
    }
}
