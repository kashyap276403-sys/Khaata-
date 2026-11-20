package com.aurax.guard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SnatchDetectorTest {
    private fun detector() = SnatchDetector(
        threshold = 20f,
        armDelayMs = 5_000,
        cooldownMs = 5_000,
        startMs = 0,
    )

    @Test
    fun ignoresGentleMovement() {
        assertFalse(detector().onSample(5f, 10_000))
    }

    @Test
    fun triggersOnStrongJerkAfterArming() {
        assertTrue(detector().onSample(25f, 10_000))
    }

    @Test
    fun ignoresJerkDuringArmDelay() {
        assertFalse(detector().onSample(40f, 2_000))
    }

    @Test
    fun cooldownBlocksImmediateRepeat() {
        val d = detector()
        assertTrue(d.onSample(30f, 10_000))
        assertFalse(d.onSample(30f, 12_000))
        assertTrue(d.onSample(30f, 15_001))
    }

    @Test
    fun exactThresholdTriggers() {
        assertTrue(detector().onSample(20f, 6_000))
    }
}
