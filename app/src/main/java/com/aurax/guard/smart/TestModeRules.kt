package com.aurax.guard.smart

import com.aurax.guard.SensitivityProfile
import com.aurax.guard.SnatchDetector
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

data class SimSample(val tMs: Long, val mag: Float)
data class SimScenario(val key: String, val title: String, val expectTrigger: Boolean, val samples: List<SimSample>)
data class SimOutcome(val scenario: SimScenario, val triggered: Boolean) { val ok get() = triggered == scenario.expectTrigger }

/**
 * Test Mode brain: feeds made-up movement (walking, lifting, a knock, a hard snatch, a fast yank) into a FRESH
 * detector built from your current settings. The real guard is never touched, so nothing can lock, sound or be saved.
 * Pure and unit tested.
 */
object TestModeRules {
    private const val STEP = 20L // 50 samples per second

    private fun series(durMs: Long, f: (Long) -> Float) = (0..durMs / STEP).map { SimSample(it * STEP, f(it * STEP)) }
    private fun seq(vararg v: Float) = v.mapIndexed { i, m -> SimSample(i * STEP, m) }

    fun scenarios(park: Boolean): List<SimScenario> {
        val snatch = SimScenario("snatch", "Zor ka jhatka (chheenna)", true, series(400) { 40f })
        if (park) return listOf(
            SimScenario("touch", "Phone ko haath lagana", true, series(240) { 6f }),
            snatch,
        )
        return listOf(
            SimScenario("walk", "Jeb me chalna", false, series(3000) { t -> 3f + 4f * abs(sin(2 * PI * 2 * t / 1000.0)).toFloat() }),
            SimScenario("lift", "Haath se aaram se uthana", false, series(600) { t -> 12f * sin(PI * t / 600.0).toFloat() }),
            SimScenario("knock", "Table par patakna / takrana", false, seq(0f, 0f, 45f, 9f, 5f, 3f, 1f, 0f, 0f)),
            snatch,
            SimScenario("yank", "Tez chheen (ek bahut zor ka jhatka + le jaana)", true, seq(0f, 0f, 60f, 18f, 18f, 18f, 18f, 18f, 2f, 0f)),
        )
    }

    /** Runs one made-up movement through a fresh detector. [fastPull] mirrors the Settings switch. */
    fun simulate(p: SensitivityProfile, fastPull: Boolean, samples: List<SimSample>): Boolean {
        val d = SnatchDetector(
            p.threshold, 0, p.cooldownMs, 0, p.minSamples, p.minDurationMs, p.dipToleranceMs,
            if (fastPull) p.hardThreshold else Float.MAX_VALUE, p.followThreshold,
        )
        val base = 10_000L
        return samples.any { d.onSample(it.mag, base + it.tMs) }
    }

    fun run(level: Int, park: Boolean, fastPull: Boolean): List<SimOutcome> {
        val p = SensitivityProfile.forLevel(level, park)
        return scenarios(park).map { SimOutcome(it, simulate(p, fastPull && !park, it.samples)) }
    }

    fun hint(outcomes: List<SimOutcome>, park: Boolean): String {
        val bad = outcomes.filter { !it.ok }
        if (bad.isEmpty()) return "Sab theek: normal movement par alarm nahi, asli jhatke par alarm."
        val falseAlarm = bad.filter { !it.scenario.expectTrigger }
        val missed = bad.filter { it.scenario.expectTrigger }
        return buildString {
            if (falseAlarm.isNotEmpty() && !park) append("Galat alarm ho sakta hai (${falseAlarm.joinToString { it.scenario.title }}): sensitivity Kam ya Madhyam karein. ")
            if (missed.isNotEmpty()) append("Ye pakad me nahi aaya (${missed.joinToString { it.scenario.title }}): sensitivity badhayein" + (if (!park) " ya fast-pull ON karein" else "") + ".")
        }.trim()
    }
}
