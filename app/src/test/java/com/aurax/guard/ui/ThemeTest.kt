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
