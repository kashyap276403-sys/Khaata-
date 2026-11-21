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
        minSamples = 3,
    )

    private fun SnatchDetector.burst(mag: Float, startMs: Long, n: Int = 3): Boolean {
        var hit = false
        for (i in 0 until n) hit = onSample(mag, startMs + i * 20L) || hit
        return hit
    }

    @Test
    fun ignoresGentleMovement() {
        assertFalse(detector().burst(5f, 10_000, 10))
    }

    @Test
    fun triggersOnSustainedJerkAfterArming() {
        assertTrue(detector().burst(25f, 10_000))
    }

    @Test
    fun singleSpikeIsIgnored() {
        val d = detector()
        assertFalse(d.onSample(40f, 10_000))
        assertFalse(d.onSample(2f, 10_020))
        assertFalse(d.onSample(40f, 10_040))
    }

    @Test
    fun ignoresJerkDuringArmDelay() {
        assertFalse(detector().burst(40f, 2_000))
    }

    @Test
    fun cooldownBlocksImmediateRepeat() {
        val d = detector()
        assertTrue(d.burst(30f, 10_000))
        assertFalse(d.burst(30f, 12_000))
        assertTrue(d.burst(30f, 15_100))
    }

    @Test
    fun exactThresholdTriggers() {
        assertTrue(detector().burst(20f, 6_000))
    }
}
