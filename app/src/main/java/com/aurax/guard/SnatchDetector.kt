package com.aurax.guard

/**
 * Decides whether one acceleration sample looks like a snatch.
 * [magnitude] is the strength of movement WITHOUT gravity, in m/s^2.
 * Pure logic (no Android classes) so it can be unit tested.
 */
class SnatchDetector(
    private val threshold: Float,
    private val armDelayMs: Long,
    private val cooldownMs: Long,
    private val startMs: Long,
) {
    private var lastTriggerMs = Long.MIN_VALUE

    fun onSample(magnitude: Float, nowMs: Long): Boolean {
        if (nowMs - startMs < armDelayMs) return false
        if (lastTriggerMs != Long.MIN_VALUE && nowMs - lastTriggerMs < cooldownMs) return false
        if (magnitude < threshold) return false
        lastTriggerMs = nowMs
        return true
    }
}
