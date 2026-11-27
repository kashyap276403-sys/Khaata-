package com.aurax.guard

/**
 * One place that decides how touchy the motion guard is.
 * Levels: 0 = Zyada (touchy), 1 = Madhyam, 2 = Kam (needs a hard pull).
 * Same index as [Prefs.snatchNames], so the saved setting keeps working.
 */
data class SensitivityProfile(
    val threshold: Float,
    val minSamples: Int,
    val minDurationMs: Long,
    val dipToleranceMs: Long,
    val armDelayMs: Long,
    val cooldownMs: Long,
    /** Fast-pull settings (see [SnatchDetector]); MAX_VALUE = not available (Park mode). */
    val hardThreshold: Float = Float.MAX_VALUE,
    val followThreshold: Float = 0f,
) {
    companion object {
        fun forLevel(level: Int, park: Boolean): SensitivityProfile {
            if (park) {
                // "Haath mat lagao": phone lies still, so even a small touch counts, but not one tap.
                return SensitivityProfile(3f, 2, 120, 60, 10_000, 15_000)
            }
            return when (level.coerceIn(0, 2)) {
                0 -> SensitivityProfile(14f, 3, 120, 80, 5_000, 10_000, 14f * 1.8f, 14f * 0.5f)
                1 -> SensitivityProfile(20f, 3, 180, 80, 5_000, 15_000, 20f * 1.8f, 20f * 0.5f)
                else -> SensitivityProfile(28f, 4, 250, 100, 5_000, 20_000, 28f * 1.8f, 28f * 0.5f)
            }
        }

        fun describe(level: Int): String = when (level.coerceIn(0, 2)) {
            0 -> "Zyada: halke jhatke par bhi lock. Chalte waqt galti se ho sakta hai."
            1 -> "Madhyam: normal chheen-jhapat par lock, haath se uthane par nahi."
            else -> "Kam: sirf zor ka jhatka. Jeb me chalte ya table par rakhne par nahi."
        }
    }
}
