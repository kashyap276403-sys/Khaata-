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
    /**
     * Fast pull: ONE very hard spike (>= [hardThreshold]) that is followed within [followWindowMs] by at least
     * [followSamples] samples that keep moving (>= [followThreshold]) = the phone was yanked away and is being carried off.
     * A drop or a knock gives a spike and then stillness, so it does not count. Float.MAX_VALUE = off (default).
     */
    private val hardThreshold: Float = Float.MAX_VALUE,
    private val followThreshold: Float = 0f,
    private val followSamples: Int = 3,
    private val followWindowMs: Long = 500,
    private val followMinGapMs: Long = 40,
) {
    private var hardAtMs = Long.MIN_VALUE
    private var follow = 0
    private var lastTriggerMs = Long.MIN_VALUE
    private var run = 0
    private var runStartMs = Long.MIN_VALUE
    private var lastStrongMs = Long.MIN_VALUE

    /** Forget any half-finished movement (used while the guard is deliberately ignoring motion). */
    fun reset() {
        hardAtMs = Long.MIN_VALUE
        follow = 0
        run = 0
        runStartMs = Long.MIN_VALUE
        lastStrongMs = Long.MIN_VALUE
    }

    fun onSample(magnitude: Float, nowMs: Long): Boolean {
        if (nowMs - startMs < armDelayMs) return false
        if (lastTriggerMs != Long.MIN_VALUE && nowMs - lastTriggerMs < cooldownMs) return false
        if (hardThreshold < Float.MAX_VALUE) {
            if (magnitude >= hardThreshold) {
                hardAtMs = nowMs
                follow = 0
            } else if (hardAtMs != Long.MIN_VALUE) {
                if (nowMs - hardAtMs > followWindowMs) {
                    hardAtMs = Long.MIN_VALUE
                    follow = 0
                } else if (magnitude >= followThreshold && nowMs - hardAtMs >= followMinGapMs) {
                    follow++
                    if (follow >= followSamples) {
                        reset()
                        lastTriggerMs = nowMs
                        return true
                    }
                }
            }
        }
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
