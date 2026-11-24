package com.aurax.guard.ui

import com.aurax.guard.AlertType
import com.aurax.guard.QuietRules
import org.junit.Assert.*
import org.junit.Test

class AccentTest {
    @Test fun unknownAccentIsEmerald() { assertEquals("emerald", Accent.of("x").key); assertEquals("emerald", Accent.of(null).key) }
    @Test fun sixAccents() = assertEquals(6, Accent.ALL.map { it.key }.toSet().size)

    @Test fun singleAccentColoursOnlyFirstGlowAndKeepsStrength() {
        val p = ThemePalette.MIDNIGHT.withAccent(Accent.ROSE)
        assertEquals(Accent.ROSE.main and 0xFFFFFF, p.glows[0].argb and 0xFFFFFF)
        assertEquals(ThemePalette.MIDNIGHT.glows[0].argb ushr 24, p.glows[0].argb ushr 24)
        assertEquals(ThemePalette.MIDNIGHT.glows[1], p.glows[1])
        assertEquals(ThemePalette.MIDNIGHT.baseTop, p.baseTop)
    }

    @Test fun auroraRecolorsEveryGlowWithDifferentColours() {
        val p = ThemePalette.DARK.withAccent(Accent.AURORA)
        assertTrue(p.glows.map { it.argb and 0xFFFFFF }.toSet().size >= 3)
        p.glows.forEachIndexed { i, g -> assertEquals(ThemePalette.DARK.glows[i].argb ushr 24, g.argb ushr 24) }
    }

    @Test fun amoledStaysPureBlackWhateverTheAccent() = Accent.ALL.forEach {
        val p = ThemePalette.AMOLED.withAccent(it)
        assertEquals(0xFF000000.toInt(), p.baseTop); assertEquals(0xFF000000.toInt(), p.baseBottom)
    }

    @Test fun darkTopFollowsAccentAndStaysDark() = Accent.ALL.forEach {
        val top = ThemePalette.DARK.withAccent(it).baseTop
        assertTrue(((top shr 16) and 255) <= 60 && ((top shr 8) and 255) <= 60 && (top and 255) <= 60)
    }

    @Test fun quietWindowSimpleAndAcrossMidnight() {
        assertTrue(QuietRules.inWindow(8 * 60, 7 * 60, 9 * 60))
        assertFalse(QuietRules.inWindow(9 * 60, 7 * 60, 9 * 60))
        assertTrue(QuietRules.inWindow(23 * 60, 22 * 60, 6 * 60))
        assertTrue(QuietRules.inWindow(2 * 60, 22 * 60, 6 * 60))
        assertFalse(QuietRules.inWindow(12 * 60, 22 * 60, 6 * 60))
        assertFalse(QuietRules.inWindow(100, 500, 500))
        assertEquals("07:05", QuietRules.fmt(7 * 60 + 5))
    }

    @Test fun batteryAlertType() {
        assertEquals(AlertType.BATTERY, AlertType.classify("Battery bahut kam (9%): aakhri location"))
        assertEquals(AlertType.CHARGER, AlertType.classify("Charger nikala gaya"))
    }
}
