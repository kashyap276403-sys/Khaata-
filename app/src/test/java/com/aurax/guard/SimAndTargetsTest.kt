package com.aurax.guard

import com.aurax.guard.telegram.TelegramRules
import org.junit.Assert.*
import org.junit.Test

class SimAndTargetsTest {
    @Test fun startupIsNotAChange() = assertNull(SimRules.fingerprint(listOf(0), "40410"))
    @Test fun removalDetected() {
        val a = SimRules.fingerprint(listOf(5, 5), "40410")!!
        val b = SimRules.fingerprint(listOf(5, 1), "40410")!!
        assertTrue(SimRules.change(a, b)!!.contains("nikali"))
    }
    @Test fun carrierChangeDetected() = assertNotNull(SimRules.change(SimRules.fingerprint(listOf(5), "40410")!!, SimRules.fingerprint(listOf(5), "40445")!!))
    @Test fun sameIsNoChange() { val a = SimRules.fingerprint(listOf(5), "40410")!!; assertNull(SimRules.change(a, a)) }
    @Test fun firstRunNoAlert() = assertNull(SimRules.change("", SimRules.fingerprint(listOf(5), "x")))
    @Test fun cooldown() { assertFalse(SimRules.cooledDown(1000, 2000)); assertTrue(SimRules.cooledDown(0, 70_000)) }

    @Test fun deliveryNeverDuplicatesAndGivesUp() {
        val ids = listOf("main", "x1")
        var done = setOf("main")
        assertFalse(TelegramRules.complete(done, ids, false))
        assertTrue(TelegramRules.msgDone(done, "main"))
        done = done + "x1!"
        assertTrue(TelegramRules.complete(done, ids, true))
        assertFalse(TelegramRules.complete(setOf("main", "x1"), ids, true))
        assertEquals(setOf("a", "b"), TelegramRules.parseDone(TelegramRules.joinDone(setOf("b", "a"))))
    }
}
