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

class SnatchDetectorFilterTest {
    private fun d(minDur: Long = 250, dip: Long = 100) = SnatchDetector(
        threshold = 20f, armDelayMs = 0, cooldownMs = 10_000, startMs = 0,
        minSamples = 3, minDurationMs = minDur, dipToleranceMs = dip,
    )

    @Test
    fun shortBumpBelowDurationIgnored() {
        val x = d()
        var hit = false
        for (i in 0 until 5) hit = x.onSample(30f, 1000L + i * 20) || hit // 80 ms only
        assertFalse(hit)
    }

    @Test
    fun sustainedPullTriggers() {
        val x = d()
        var hit = false
        for (i in 0 until 20) hit = x.onSample(30f, 1000L + i * 20) || hit
        assertTrue(hit)
    }

    @Test
    fun briefDipDoesNotResetButLongGapDoes() {
        val x = d()
        var hit = false
        for (i in 0 until 8) hit = x.onSample(30f, 1000L + i * 20) || hit
        x.onSample(5f, 1160L) // dip within tolerance
        for (i in 0 until 8) hit = x.onSample(30f, 1180L + i * 20) || hit
        assertTrue(hit)
        val y = d()
        var hit2 = false
        for (i in 0 until 5) hit2 = y.onSample(30f, 1000L + i * 20) || hit2
        y.onSample(5f, 1100L); y.onSample(5f, 1400L) // long calm: reset
        for (i in 0 until 5) hit2 = y.onSample(30f, 1500L + i * 20) || hit2
        assertFalse(hit2)
    }

    @Test
    fun cooldownBlocksSecondTrigger() {
        val x = d(0, 0)
        var n = 0
        for (i in 0 until 40) if (x.onSample(30f, 1000L + i * 20)) n++
        assertTrue(n == 1)
    }

    @Test
    fun profilesGetStricter() {
        val z = SensitivityProfile.forLevel(0, false)
        val k = SensitivityProfile.forLevel(2, false)
        assertTrue(k.threshold > z.threshold && k.minDurationMs > z.minDurationMs && k.cooldownMs > z.cooldownMs)
    }
}
