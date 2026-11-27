package com.aurax.guard.ui

import org.junit.Assert.*
import org.junit.Test

class ThemeTest {
    @Test fun unknownKeyFallsBackToDark() = assertEquals("dark", ThemePalette.of("zzz").key)
    @Test fun nullKeyFallsBackToDark() = assertEquals("dark", ThemePalette.of(null).key)
    @Test fun threeDistinctThemes() = assertEquals(3, ThemePalette.ALL.map { it.key }.toSet().size)
    @Test fun amoledIsPureBlackAndDimmest() {
        val a = ThemePalette.AMOLED
        assertEquals(0xFF000000.toInt(), a.baseTop); assertEquals(0xFF000000.toInt(), a.baseMid); assertEquals(0xFF000000.toInt(), a.navBg)
        assertTrue(a.glassTopAlpha < ThemePalette.DARK.glassTopAlpha)
        assertTrue(a.glows.size <= 2)
    }
    @Test fun glowsStayOnScreenAndSoft() = ThemePalette.ALL.forEach { p ->
        p.glows.forEach { assertTrue(it.xFrac in 0f..1f && it.yFrac in 0f..1f && (it.argb ushr 24) <= 110) }
    }
}

class CyberThemeTest {
    @Test fun cyberIsSeparateFromGlassThemes() {
        assertEquals(3, ThemePalette.ALL.size)
        assertFalse(ThemePalette.ALL.any { it.key == "cyber" })
        assertEquals("cyber", ThemePalette.CYBER.key)
        assertEquals(6, Accent.ALL.size)
        assertFalse(Accent.ALL.any { it.key == "cyber" })
    }

    @Test fun cyberLooksTactical() {
        val p = ThemePalette.CYBER
        assertTrue(p.glows.isEmpty())
        assertEquals(0xFF000000.toInt(), p.baseBottom)
        val g = (Accent.CYBER.main shr 8) and 255
        assertTrue(g > 200 && ((Accent.CYBER.main shr 16) and 255) < 40) // neon green
        assertTrue(((Accent.CYBER.rim) and 255) > 200) // cyan rim
        // falls back safely and keeps accent logic intact
        assertEquals("dark", ThemePalette.of("cyber").key)
        assertEquals("emerald", Accent.of("cyber").key)
    }

    @Test fun telemetryTags() {
        assertEquals(com.aurax.guard.smart.Tag.CMD, com.aurax.guard.smart.TelemetryRules.tag("Remote /loc chali"))
        assertEquals(com.aurax.guard.smart.Tag.COPY, com.aurax.guard.smart.TelemetryRules.tag("Copy progress 10/20"))
        assertEquals(com.aurax.guard.smart.Tag.TAMPER, com.aurax.guard.smart.TelemetryRules.tag("SIM badla"))
        assertEquals(com.aurax.guard.smart.Tag.GUARD, com.aurax.guard.smart.TelemetryRules.tag("Watchdog: guard ruka tha"))
        assertEquals(com.aurax.guard.smart.Tag.SYS, com.aurax.guard.smart.TelemetryRules.tag("kuch aur"))
        val l = com.aurax.guard.smart.TelemetryRules.lines(listOf("c", "b", "a"), 2)
        assertEquals(listOf("b", "c"), l.map { it.second }) // oldest first, capped
        assertFalse(com.aurax.guard.smart.TelemetryRules.changed("x".hashCode(), "x"))
        assertTrue(com.aurax.guard.smart.TelemetryRules.changed("x".hashCode(), "y"))
    }
}
