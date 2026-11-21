package com.aurax.guard

import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** App PIN hashing. The PIN itself is never stored. Pure Kotlin, unit tested. */
object PinHasher {
    private const val ITERATIONS = 60_000

    fun newSalt(): String = ByteArray(16).also { SecureRandom().nextBytes(it) }.toHex()

    fun hash(pin: String, saltHex: String): String {
        val spec = PBEKeySpec(pin.toCharArray(), saltHex.hexToBytes(), ITERATIONS, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(spec).encoded.toHex()
    }

    fun verify(pin: String, saltHex: String, expectedHex: String): Boolean {
        val got = hash(pin, saltHex)
        if (got.length != expectedHex.length) return false
        var diff = 0
        for (i in got.indices) diff = diff or (got[i].code xor expectedHex[i].code)
        return diff == 0
    }

    /** 4 to 8 digits. */
    fun isValidPin(pin: String) = pin.length in 4..8 && pin.all { it in '0'..'9' }

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
    private fun String.hexToBytes() =
        chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
