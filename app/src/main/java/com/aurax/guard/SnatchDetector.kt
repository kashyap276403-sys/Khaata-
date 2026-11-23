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
    /** The strong movement must last at least this long (0 = off). Sensor speed differs per phone, so time is safer than a sample count. */
    private val minDurationMs: Long = 0,
    /** A short dip below the threshold (up to this long) does not restart the count, because real jerks are bumpy. */
    private val dipToleranceMs: Long = 0,
) {
    private var lastTriggerMs = Long.MIN_VALUE
    private var run = 0
    private var runStartMs = Long.MIN_VALUE
    private var lastStrongMs = Long.MIN_VALUE

    /** Forget any half-finished movement (used while the guard is deliberately ignoring motion). */
    fun reset() {
        run = 0
        runStartMs = Long.MIN_VALUE
        lastStrongMs = Long.MIN_VALUE
    }

    fun onSample(magnitude: Float, nowMs: Long): Boolean {
        if (nowMs - startMs < armDelayMs) return false
        if (lastTriggerMs != Long.MIN_VALUE && nowMs - lastTriggerMs < cooldownMs) return false
        if (magnitude < threshold) {
            if (lastStrongMs == Long.MIN_VALUE || nowMs - lastStrongMs > dipToleranceMs) {
                run = 0
                runStartMs = Long.MIN_VALUE
            }
            return false
        }
        if (run == 0) runStartMs = nowMs
        lastStrongMs = nowMs
        run++
        if (run < minSamples) return false
        if (nowMs - runStartMs < minDurationMs) return false
        run = 0
        runStartMs = Long.MIN_VALUE
        lastStrongMs = Long.MIN_VALUE
        lastTriggerMs = nowMs
        return true
    }
}
