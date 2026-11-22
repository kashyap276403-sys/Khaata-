package com.aurax.guard

import javax.crypto.AEADBadTagException
import javax.crypto.KeyGenerator
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhotoCryptoTest {
    private fun key() = KeyGenerator.getInstance("AES").apply { init(128) }.generateKey()

    @Test
    fun roundTrip() {
        val k = key()
        val plain = ByteArray(5000) { (it % 251).toByte() }
        assertArrayEquals(plain, PhotoCrypto.decrypt(k, PhotoCrypto.encrypt(k, plain)))
    }

    @Test
    fun cipherTextDiffersFromPlain() {
        val k = key()
        val plain = "intruder photo bytes".toByteArray()
        val enc = PhotoCrypto.encrypt(k, plain)
        assertFalse(enc.copyOfRange(12, 12 + plain.size).contentEquals(plain))
    }

    @Test
    fun sameInputGivesDifferentCipherEachTime() {
        val k = key()
        val plain = "abc".toByteArray()
        assertFalse(PhotoCrypto.encrypt(k, plain).contentEquals(PhotoCrypto.encrypt(k, plain)))
    }

    @Test
    fun tamperedDataIsRejected() {
        val k = key()
        val enc = PhotoCrypto.encrypt(k, "hello world".toByteArray())
        enc[enc.size - 1] = (enc[enc.size - 1].toInt() xor 1).toByte()
        var failed = false
        try {
            PhotoCrypto.decrypt(k, enc)
        } catch (e: AEADBadTagException) {
            failed = true
        }
        assertTrue(failed)
    }

    @Test
    fun wrongKeyIsRejected() {
        val enc = PhotoCrypto.encrypt(key(), "secret".toByteArray())
        var failed = false
        try {
            PhotoCrypto.decrypt(key(), enc)
        } catch (e: java.security.GeneralSecurityException) {
            failed = true
        }
        assertTrue(failed)
    }
}
