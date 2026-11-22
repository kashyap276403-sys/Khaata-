package com.aurax.guard

import com.aurax.guard.cleaner.SizeFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportBuilderTest {
    private val alert = EventRecord(
        id = "1", timeMs = 1_700_000_000_000, reason = "Galat password #1",
        lat = 28.6139, lon = 77.2090, locationNote = "Live location (GPS)",
        batteryPct = 50, network = "Wi-Fi",
    )

    @Test
    fun reportWithoutScanSaysSo() {
        val t = ReportBuilder.build(0, emptyList(), null, null, null)
        assertTrue(t.contains("abhi nahi hua"))
        assertTrue(t.contains("Abhi koi alert nahi"))
    }

    @Test
    fun reportHasCountsAndAlerts() {
        val t = ReportBuilder.build(0, listOf(alert), 2, 5, 80)
        assertTrue(t.contains("High risk indicators: 2"))
        assertTrue(t.contains("Medium risk indicators: 5"))
        assertTrue(t.contains("Galat password #1"))
        assertTrue(t.contains("malware ka daawa nahi"))
    }

    @Test
    fun reportHasNoTokensOrSecrets() {
        val t = ReportBuilder.build(0, listOf(alert), 0, 0, 10).lowercase()
        assertFalse(t.contains("token"))
        assertFalse(t.contains("password:"))
    }

    @Test
    fun reportKeepsOnlyTenAlerts() {
        val many = (1..25).map { alert.copy(id = "$it", reason = "R$it") }
        val t = ReportBuilder.build(0, many, 0, 0, 1)
        assertTrue(t.contains("R10"))
        assertFalse(t.contains("R11"))
    }

    @Test
    fun sizeFormat() {
        assertEquals("512 B", SizeFormat.bytes(512))
        assertEquals("1.0 KB", SizeFormat.bytes(1024))
        assertEquals("1.5 MB", SizeFormat.bytes(1_572_864))
        assertEquals("2.0 GB", SizeFormat.bytes(2L * 1024 * 1024 * 1024))
    }
}
