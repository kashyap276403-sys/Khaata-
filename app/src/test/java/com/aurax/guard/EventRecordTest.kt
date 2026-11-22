package com.aurax.guard

import javax.crypto.KeyGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EventRecordTest {
    private val full = EventRecord(
        id = "abc",
        timeMs = 1_700_000_000_000,
        reason = "Galat password #2",
        photoName = "intruder_1.jpg",
        lat = 28.6139,
        lon = 77.2090,
        accuracyM = 12.5f,
        locationNote = "Live location (GPS)",
        batteryPct = 63,
        charging = true,
        network = "Wi-Fi",
    )

    @Test
    fun jsonRoundTripKeepsEverything() {
        assertEquals(full, EventRecord.fromJson(full.toJson()))
    }

    @Test
    fun jsonRoundTripWithMissingParts() {
        val r = EventRecord(id = "x", timeMs = 5, reason = "Test", locationNote = "Location band hai")
        val back = EventRecord.fromJson(r.toJson())
        assertEquals(r, back)
        assertNull(back.lat)
        assertNull(back.photoName)
    }

    @Test
    fun mapsLinkFormat() {
        assertEquals("https://www.google.com/maps?q=28.613900,77.209000", full.mapsLink)
        assertNull(EventRecord(id = "x", timeMs = 0, reason = "t").mapsLink)
    }

    @Test
    fun reportHasKeyFacts() {
        val t = full.report()
        assertTrue(t.contains("Galat password #2"))
        assertTrue(t.contains("63%"))
        assertTrue(t.contains("Wi-Fi"))
        assertTrue(t.contains("https://www.google.com/maps?q="))
        assertTrue(t.contains("intruder_1.jpg"))
    }

    @Test
    fun reportWithoutLocationSaysWhy() {
        val t = EventRecord(id = "x", timeMs = 0, reason = "Test", locationNote = "Location band hai").report()
        assertTrue(t.contains("Location band hai"))
        assertTrue(t.contains("nahi mili"))
    }

    @Test
    fun storeLineRoundTripAndGarbage() {
        val key = KeyGenerator.getInstance("AES").apply { init(128) }.generateKey()
        val line = EventStore.encodeLine(key, full)
        assertEquals(full, EventStore.decodeLine(key, line))
        assertNull(EventStore.decodeLine(key, "not-a-valid-line"))
        assertTrue(!line.contains("Galat"))
    }
}
