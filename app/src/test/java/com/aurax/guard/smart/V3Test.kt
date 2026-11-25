package com.aurax.guard.smart

import org.junit.Assert.*
import org.junit.Test

class V3Test {
    @Test fun saverRules() {
        assertTrue(BatterySaver.active(true, false, 20, 90, false))
        assertTrue(BatterySaver.active(false, true, 20, 15, false))
        assertFalse(BatterySaver.active(false, true, 20, 15, true))
        assertFalse(BatterySaver.active(false, true, 20, 50, false))
        assertFalse(BatterySaver.active(false, false, 20, 5, false))
        assertFalse(BatterySaver.active(false, true, 20, -1, false))
        assertTrue(BatterySaver.describe(false, true, 20, true).contains("20%"))
    }

    @Test fun apkVerdicts() {
        val clean = ApkRules.assess("notes.apk", listOf("android.permission.INTERNET"), false, false, true)
        assertEquals(ApkLevel.LOW, clean.level)
        val bad = ApkRules.assess("free_premium_game.apk", listOf("android.permission.READ_SMS", "android.permission.SYSTEM_ALERT_WINDOW", "android.permission.BIND_ACCESSIBILITY_SERVICE"), false, false, true)
        assertEquals(ApkLevel.HIGH, bad.level)
        assertEquals(ApkLevel.HIGH, ApkRules.assess("whatsapp.apk", emptyList(), true, false, true).level)
        assertEquals(ApkLevel.MEDIUM, ApkRules.assess("x.apk", emptyList(), false, false, false).level)
        assertTrue(ApkRules.assess("photo.jpg.apk", emptyList(), false, false, true).reasons.any { it.contains("Double") })
    }

    @Test fun statusWords() {
        val s = StatusRules.items(true, true, true, 3, true, false, false, true)
        assertEquals("Active", s[0].label); assertEquals("Ready", s[1].label)
        assertEquals("3 queued", s[2].label); assertEquals(StatusKind.QUEUED, s[2].kind)
        assertEquals("Ready", s[3].label); assertEquals("Off", s[4].label)
        val t = StatusRules.items(false, false, false, 0, false, false, true, false)
        assertEquals("Off", t[0].label); assertEquals("Setup baaki", t[1].label); assertEquals("Khaali", t[2].label); assertEquals("Active", t[4].label)
    }
}

class GuardRulesTest {
    @Test fun watchdogOnlyRestartsWhenOn() {
        assertTrue(GuardRules.shouldRestart(true, false))
        assertFalse(GuardRules.shouldRestart(true, true))
        assertFalse(GuardRules.shouldRestart(false, false)) // user turned it off: stays off
    }

    @Test fun modesNeverSwitchParkOff() {
        assertTrue(GuardRules.parkAfterMode(true, false))
        assertTrue(GuardRules.parkAfterMode(false, true))
        assertFalse(GuardRules.parkAfterMode(false, false))
    }

    @Test fun protectionWords() {
        assertEquals("Off", GuardRules.status(false, false, true))
        assertEquals("Ruka hua", GuardRules.status(true, false, true))
        assertEquals("Adhoora", GuardRules.status(true, true, false))
        assertEquals("Active", GuardRules.status(true, true, true))
        assertEquals("Ruka hua", StatusRules.items(true, true, true, 0, false, false, false, true, false)[0].label)
    }
}
