package com.aurax.guard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinHasherTest {
    @Test
    fun correctPinVerifies() {
        val salt = PinHasher.newSalt()
        val h = PinHasher.hash("1234", salt)
        assertTrue(PinHasher.verify("1234", salt, h))
    }

    @Test
    fun wrongPinFails() {
        val salt = PinHasher.newSalt()
        val h = PinHasher.hash("1234", salt)
        assertFalse(PinHasher.verify("1235", salt, h))
    }

    @Test
    fun saltChangesHash() {
        assertNotEquals(
            PinHasher.hash("1234", PinHasher.newSalt()),
            PinHasher.hash("1234", PinHasher.newSalt()),
        )
    }

    @Test
    fun hashDoesNotContainPin() {
        val h = PinHasher.hash("987654", PinHasher.newSalt())
        assertEquals(64, h.length)
    }

    @Test
    fun pinRules() {
        assertTrue(PinHasher.isValidPin("1234"))
        assertTrue(PinHasher.isValidPin("12345678"))
        assertFalse(PinHasher.isValidPin("123"))
        assertFalse(PinHasher.isValidPin("123456789"))
        assertFalse(PinHasher.isValidPin("12a4"))
    }
}
