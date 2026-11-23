package com.aurax.guard.oem

import org.junit.Assert.*
import org.junit.Test

class OemRulesTest {
    @Test fun detectsFamilies() {
        assertEquals(OemFamily.VIVO, OemRules.family("vivo", "vivo"))
        assertEquals(OemFamily.VIVO, OemRules.family("BBK", "iQOO"))
        assertEquals(OemFamily.MOTOROLA, OemRules.family("motorola", "motorola"))
        assertEquals(OemFamily.OTHER, OemRules.family("samsung", "samsung"))
    }
    @Test fun stepsNotEmpty() = OemFamily.values().forEach { assertTrue(OemRules.steps(it).isNotEmpty()) }
}
