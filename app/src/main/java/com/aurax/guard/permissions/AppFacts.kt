package com.aurax.guard.permissions

enum class RiskLevel { NORMAL, MEDIUM, HIGH }

/** Where an app came from. */
enum class Installer { STORE, SIDELOADED, SYSTEM }

/** Groups the Permission Control Hub filters by. */
enum class PermGroup(val label: String) {
    CAMERA("Camera"),
    MIC("Microphone"),
    BG_LOCATION("Background location"),
    OVERLAY("Overlay"),
    ACCESSIBILITY("Accessibility"),
    DEVICE_ADMIN("Device admin"),
    NOTIFICATIONS("Notification access"),
    SMS_CALL("SMS / Call / All files"),
}

/**
 * What one app can do right now (granted / enabled, not just declared).
 * Plain data so the scoring can be unit tested without a phone.
 */
data class AppFacts(
    val packageName: String,
    val label: String,
    val isSystem: Boolean = false,
    val isSelf: Boolean = false,
    val installer: Installer = Installer.STORE,
    val camera: Boolean = false,
    val microphone: Boolean = false,
    val location: Boolean = false,
    val backgroundLocation: Boolean = false,
    val overlay: Boolean = false,
    val accessibility: Boolean = false,
    val deviceAdmin: Boolean = false,
    val notificationListener: Boolean = false,
    val sms: Boolean = false,
    val call: Boolean = false,
    val callLog: Boolean = false,
    val contacts: Boolean = false,
    val allFiles: Boolean = false,
    val installPackages: Boolean = false,
) {
    fun has(g: PermGroup): Boolean = when (g) {
        PermGroup.CAMERA -> camera
        PermGroup.MIC -> microphone
        PermGroup.BG_LOCATION -> backgroundLocation
        PermGroup.OVERLAY -> overlay
        PermGroup.ACCESSIBILITY -> accessibility
        PermGroup.DEVICE_ADMIN -> deviceAdmin
        PermGroup.NOTIFICATIONS -> notificationListener
        PermGroup.SMS_CALL -> sms || call || callLog || allFiles
    }

    val sideloaded: Boolean get() = installer == Installer.SIDELOADED && !isSystem
}

/** One line shown to the user: what it is + why it matters, in plain words. */
data class Reason(val title: String, val why: String, val points: Int)

data class RiskResult(val level: RiskLevel, val score: Int, val reasons: List<Reason>)

data class AppAssessment(val facts: AppFacts, val result: RiskResult)

/** Classifies the package that installed an app. */
object InstallerKind {
    private val stores = setOf(
        "com.android.vending",
        "com.vivo.appstore",
        "com.bbk.appstore",
        "com.heytap.market",
        "com.oppo.market",
        "com.xiaomi.market",
        "com.sec.android.app.samsungapps",
        "com.amazon.venezia",
        "com.huawei.appmarket",
        "com.google.android.feedback",
    )

    /** [installer] null or a file installer means the app was installed from a file. */
    fun classify(installer: String?, isSystem: Boolean): Installer = when {
        isSystem -> Installer.SYSTEM
        installer != null && installer in stores -> Installer.STORE
        else -> Installer.SIDELOADED
    }
}
