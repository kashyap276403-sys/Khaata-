package com.aurax.guard.smart

import org.junit.Assert.*
import org.junit.Test

class SetupRulesTest {
    @Test fun order() {
        val s = SetupRules.steps(false, false, false, false)
        assertEquals(SetupAction.TELEGRAM, SetupRules.next(s)!!.action)
        assertEquals(SetupAction.PROTECTION, SetupRules.next(SetupRules.steps(true, false, true, false))!!.action)
        assertEquals(SetupAction.PROTECTION, SetupRules.next(SetupRules.steps(true, true, false, false))!!.action)
        assertEquals(SetupAction.TEST, SetupRules.next(SetupRules.steps(true, true, true, false))!!.action)
        assertTrue(SetupRules.allDone(SetupRules.steps(true, true, true, true)))
        assertNull(SetupRules.next(SetupRules.steps(true, true, true, true)))
    }
}
