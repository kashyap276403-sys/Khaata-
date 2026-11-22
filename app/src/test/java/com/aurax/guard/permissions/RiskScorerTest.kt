package com.aurax.guard.permissions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RiskScorerTest {
    private fun app(block: AppFacts.() -> AppFacts = { this }) =
        AppFacts(packageName = "x.y", label = "X").block()

    @Test
    fun plainAppIsNormal() {
        assertEquals(RiskLevel.NORMAL, RiskScorer.assess(app()).level)
    }

    @Test
    fun cameraAndMicAloneStayNormal() {
        val r = RiskScorer.assess(app { copy(camera = true, microphone = true, contacts = true) })
        assertEquals(RiskLevel.NORMAL, r.level)
    }

    @Test
    fun accessibilityAloneIsMedium() {
        assertEquals(RiskLevel.MEDIUM, RiskScorer.assess(app { copy(accessibility = true) }).level)
    }

    @Test
    fun accessibilityPlusOverlayIsHighAndNamesTheCombo() {
        val r = RiskScorer.assess(app { copy(accessibility = true, overlay = true) })
        assertEquals(RiskLevel.HIGH, r.level)
        assertTrue(r.reasons.any { it.title.contains("Accessibility + Overlay") })
    }

    @Test
    fun otpStealerPatternIsHigh() {
        val r = RiskScorer.assess(app { copy(notificationListener = true, sms = true) })
        assertEquals(RiskLevel.HIGH, r.level)
    }

    @Test
    fun sideloadedAdminIsAtLeastMedium() {
        val r = RiskScorer.assess(app { copy(deviceAdmin = true, installer = Installer.SIDELOADED) })
        assertTrue(r.level != RiskLevel.NORMAL)
    }

    @Test
    fun systemAppIsNotCalledSideloaded() {
        val f = app { copy(isSystem = true, installer = Installer.SIDELOADED) }
        assertTrue(!f.sideloaded)
    }

    @Test
    fun ownAppIsAlwaysNormalEvenAsDeviceAdmin() {
        val r = RiskScorer.assess(app { copy(isSelf = true, deviceAdmin = true, camera = true, backgroundLocation = true) })
        assertEquals(RiskLevel.NORMAL, r.level)
    }

    @Test
    fun resultsAreSortedByScore() {
        val list = RiskScorer.assessAll(listOf(
            app { copy(packageName = "a") },
            app { copy(packageName = "b", accessibility = true, overlay = true) },
        ))
        assertEquals("b", list.first().facts.packageName)
    }

    @Test
    fun everyReasonHasAPlainExplanation() {
        val r = RiskScorer.assess(app {
            copy(accessibility = true, overlay = true, sms = true, microphone = true, camera = true,
                backgroundLocation = true, deviceAdmin = true, notificationListener = true,
                callLog = true, allFiles = true, installPackages = true, installer = Installer.SIDELOADED)
        })
        assertTrue(r.reasons.isNotEmpty())
        assertTrue(r.reasons.all { it.why.length > 10 && it.title.isNotBlank() })
    }

    @Test
    fun groupFilterMatches() {
        val f = app { copy(overlay = true, call = true) }
        assertTrue(f.has(PermGroup.OVERLAY))
        assertTrue(f.has(PermGroup.SMS_CALL))
        assertTrue(!f.has(PermGroup.CAMERA))
    }

    @Test
    fun installerClassification() {
        assertEquals(Installer.STORE, InstallerKind.classify("com.android.vending", false))
        assertEquals(Installer.STORE, InstallerKind.classify("com.vivo.appstore", false))
        assertEquals(Installer.SIDELOADED, InstallerKind.classify(null, false))
        assertEquals(Installer.SIDELOADED, InstallerKind.classify("com.google.android.packageinstaller", false))
        assertEquals(Installer.SYSTEM, InstallerKind.classify(null, true))
    }
}
