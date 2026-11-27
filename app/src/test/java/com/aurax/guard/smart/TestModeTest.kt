package com.aurax.guard.smart

import com.aurax.guard.SensitivityProfile
import com.aurax.guard.SnatchDetector
import org.junit.Assert.*
import org.junit.Test

class TestModeTest {
    @Test fun everyLevelPassesItsScenarios() {
        for (lvl in 0..2) for (fast in listOf(true)) {
            val outs = TestModeRules.run(lvl, false, fast)
            outs.forEach { assertTrue("level $lvl: ${it.scenario.key} triggered=${it.triggered}", it.ok) }
            assertTrue(TestModeRules.hint(outs, false).startsWith("Sab theek"))
        }
    }

    @Test fun parkModeCatchesTouch() {
        val outs = TestModeRules.run(1, true, true)
        assertEquals(2, outs.size); assertTrue(outs.all { it.ok })
    }

    @Test fun fastPullOffMissesTheYankButNotTheHardSnatch() {
        val outs = TestModeRules.run(1, false, false)
        assertFalse(outs.first { it.scenario.key == "yank" }.triggered)
        assertTrue(outs.first { it.scenario.key == "snatch" }.triggered)
        assertTrue(TestModeRules.hint(outs, false).contains("fast-pull"))
    }

    @Test fun knockWithStillnessNeverTriggersFastPull() {
        val p = SensitivityProfile.forLevel(0, false) // most touchy level
        val d = SnatchDetector(p.threshold, 0, p.cooldownMs, 0, p.minSamples, p.minDurationMs, p.dipToleranceMs, p.hardThreshold, p.followThreshold)
        // big spike, then quiet
        val s = listOf(0f, 60f, 3f, 2f, 1f, 1f, 0f, 0f, 0f, 0f, 0f)
        assertFalse(s.mapIndexed { i, m -> d.onSample(m, 10_000L + i * 20L) }.any { it })
    }

    @Test fun oldDetectorBehaviourUnchanged() {
        // no fast-pull args: exactly the old rules
        val d = SnatchDetector(20f, 0, 10_000, 0, 3, 250, 100)
        val yank = listOf(0f, 60f, 18f, 18f, 18f, 18f, 2f)
        assertFalse(yank.mapIndexed { i, m -> d.onSample(m, 10_000L + i * 20L) }.any { it })
    }

    @Test fun sensorLiveness() {
        assertFalse(GuardRules.sensorStale(false, 1, 1_000_000))
        assertFalse(GuardRules.sensorStale(true, 0, 1_000_000)) // never started: not "stale"
        assertFalse(GuardRules.sensorStale(true, 900_000, 1_000_000))
        assertTrue(GuardRules.sensorStale(true, 100_000, 1_000_000))
    }
}
