package com.aurax.guard

/**
 * Decides whether acceleration samples look like a snatch.
 * [magnitude] is the strength of movement WITHOUT gravity, in m/s^2.
 *
 * One lone spike (phone dropped on a table, a bump) is ignored: the movement
 * must stay above [threshold] for [minSamples] samples in a row, like a real
 * hard pull. Pure logic (no Android classes) so it can be unit tested.
 */
class SnatchDetector(
    private val threshold: Float,
    private val armDelayMs: Long,
    private val cooldownMs: Long,
    private val startMs: Long,
    private val minSamples: Int = 3,
) {
    private var lastTriggerMs = Long.MIN_VALUE
    private var run = 0

    fun onSample(magnitude: Float, nowMs: Long): Boolean {
        if (nowMs - startMs < armDelayMs) return false
        if (lastTriggerMs != Long.MIN_VALUE && nowMs - lastTriggerMs < cooldownMs) return false
        if (magnitude < threshold) {
            run = 0
            return false
        }
        run++
        if (run < minSamples) return false
        run = 0
        lastTriggerMs = nowMs
        return true
    }
}
