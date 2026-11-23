package com.aurax.guard.cleaner

import org.junit.Assert.*
import org.junit.Test

class FileRulesTest {
    @Test fun doubleExtensionIsHigh() = assertEquals(FileLevel.HIGH, FileRules.assess("invoice.pdf.apk", 1000).level)
    @Test fun plainApkIsMedium() { val v = FileRules.assess("app.apk", 1000); assertEquals(FileKind.APK, v.kind); assertEquals(FileLevel.MEDIUM, v.level) }
    @Test fun crackedApkIsHigh() = assertEquals(FileLevel.HIGH, FileRules.assess("game_mod_crack.apk", 1000).level)
    @Test fun photoIsInfo() = assertEquals(FileLevel.INFO, FileRules.assess("holiday.jpg", 2_000_000).level)
    @Test fun largeAndTemp() {
        assertEquals(FileKind.LARGE, FileRules.assess("movie.mp4", 80L * 1024 * 1024).kind)
        assertEquals(FileKind.TEMP, FileRules.assess("x.tmp", 10).kind)
    }
    @Test fun selectionStartsEmptyAndToggles() {
        val s = Selection()
        assertEquals(0, s.size)
        s.toggle("a"); s.toggle("b"); s.toggle("a")
        assertEquals(setOf("b"), s.ids())
        assertEquals(30L, s.totalBytes(mapOf("a" to 5L, "b" to 30L)))
    }
}
