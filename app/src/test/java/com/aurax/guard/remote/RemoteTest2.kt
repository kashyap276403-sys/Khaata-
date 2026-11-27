package com.aurax.guard.remote

import com.aurax.guard.smart.EssentialsRules
import com.aurax.guard.smart.StatusKind
import org.junit.Assert.*
import org.junit.Test

class RemoteTest2 {
    @Test fun staleFixRetry() {
        assertTrue(RemoteRules.staleFix(null, ""))
        assertTrue(RemoteRules.staleFix(28.6, "Live fix nahi mila; aakhri location (12 min pehle)"))
        assertTrue(RemoteRules.staleFix(null, "Location nahi mili (andar ya signal kam)"))
        assertFalse(RemoteRules.staleFix(28.6, "Live location (GPS)"))
    }

    @Test fun essentials() {
        val ok = EssentialsRules.items(true, true, false, true, true, false, true, true, true, 55, false, false)
        assertEquals(4, ok.size)
        assertEquals("Active", ok[0].label); assertEquals("Ready", ok[1].label); assertEquals("Ready", ok[2].label)
        assertTrue(ok[3].label.startsWith("55%"))
        val bad = EssentialsRules.items(true, false, true, false, false, false, true, false, true, -1, true, true)
        assertEquals("Ruka hua", bad[0].label); assertEquals("Setup baaki", bad[1].label); assertEquals(StatusKind.ATTENTION, bad[2].kind)
        assertTrue(bad[3].label.contains("survival ON") && bad[3].label.contains("saver"))
        assertEquals("Lockdown ON", EssentialsRules.items(true, true, true, true, true, true, true, true, true, 50, false, false)[0].label)
        assertEquals("Ready + remote", EssentialsRules.items(true, true, false, true, true, true, true, true, true, 50, false, false)[1].label)
        assertEquals("Off", EssentialsRules.items(false, false, false, true, true, false, false, true, true, 50, false, false)[0].label)
    }
}
