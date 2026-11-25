package com.aurax.guard.smart

enum class ApkLevel(val label: String) { LOW("Kam khatra"), MEDIUM("Dhyan dein"), HIGH("Zyada khatra") }
data class ApkVerdict(val level: ApkLevel, val score: Int, val reasons: List<String>)

/**
 * Read-only APK safety check from what the installer file itself declares. It cannot prove an app is safe or
 * a virus; it points out risky combinations so YOU can decide. It never deletes or installs anything.
 */
object ApkRules {
    private val W = mapOf(
        "android.permission.SEND_SMS" to 3, "android.permission.READ_SMS" to 3, "android.permission.RECEIVE_SMS" to 2,
        "android.permission.BIND_ACCESSIBILITY_SERVICE" to 4, "android.permission.BIND_DEVICE_ADMIN" to 3,
        "android.permission.SYSTEM_ALERT_WINDOW" to 2, "android.permission.REQUEST_INSTALL_PACKAGES" to 2,
        "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE" to 3, "android.permission.READ_CALL_LOG" to 2,
        "android.permission.READ_CONTACTS" to 1, "android.permission.RECORD_AUDIO" to 1, "android.permission.CAMERA" to 1,
        "android.permission.ACCESS_BACKGROUND_LOCATION" to 2, "android.permission.MANAGE_EXTERNAL_STORAGE" to 2,
        "android.permission.REQUEST_DELETE_PACKAGES" to 2, "android.permission.READ_PHONE_NUMBERS" to 1,
    )
    private val TXT = mapOf(
        "android.permission.SEND_SMS" to "SMS bhej sakta hai (paise kat sakte hain)",
        "android.permission.READ_SMS" to "aapke SMS/OTP padh sakta hai",
        "android.permission.RECEIVE_SMS" to "aane wale SMS dekh sakta hai",
        "android.permission.BIND_ACCESSIBILITY_SERVICE" to "Accessibility maangta hai (screen padh/control kar sakta hai)",
        "android.permission.BIND_DEVICE_ADMIN" to "Device Admin banna chahta hai",
        "android.permission.SYSTEM_ALERT_WINDOW" to "dusre apps ke upar screen dikha sakta hai",
        "android.permission.REQUEST_INSTALL_PACKAGES" to "aur apps install kar sakta hai",
        "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE" to "aapke notifications (OTP) padh sakta hai",
        "android.permission.READ_CALL_LOG" to "call history padh sakta hai",
        "android.permission.READ_CONTACTS" to "contacts padh sakta hai",
        "android.permission.RECORD_AUDIO" to "mic use kar sakta hai",
        "android.permission.CAMERA" to "camera use kar sakta hai",
        "android.permission.ACCESS_BACKGROUND_LOCATION" to "background me location le sakta hai",
        "android.permission.MANAGE_EXTERNAL_STORAGE" to "saari files tak pahunch maangta hai",
        "android.permission.REQUEST_DELETE_PACKAGES" to "apps uninstall kar sakta hai",
        "android.permission.READ_PHONE_NUMBERS" to "phone number padh sakta hai",
    )
    private val BAD_NAME = listOf("crack", "keygen", "hack", "cheat", "patched", "modded", "mod_apk", "premium_free", "free_premium", "spy", "unlocked")

    fun assess(name: String, perms: List<String>, signatureChangedForInstalled: Boolean, hasDebugFlag: Boolean, parsed: Boolean): ApkVerdict {
        val reasons = mutableListOf<String>()
        var score = 0
        if (!parsed) {
            return ApkVerdict(ApkLevel.MEDIUM, 2, listOf("File padhi nahi ja saki (kharab ya adhoora APK ho sakta hai). Install na karein jab tak source pakka na ho."))
        }
        val lower = name.lowercase()
        val hits = perms.distinct().filter { W.containsKey(it) }
        hits.forEach { score += W.getValue(it); TXT[it]?.let(reasons::add) }
        if (BAD_NAME.any { lower.contains(it) }) { score += 3; reasons.add("Naam me crack/mod/hack jaisa shabd hai") }
        val parts = lower.split('.')
        if (parts.size >= 3 && parts.dropLast(1).any { it in setOf("pdf", "jpg", "png", "doc", "mp4", "mp3", "txt") }) { score += 3; reasons.add("Double extension (jaise photo.jpg.apk): bhramak naam") }
        if (signatureChangedForInstalled) { score += 5; reasons.add("Aapke phone ke isi naam ke app se alag signature: nakli/badla hua ho sakta hai") }
        if (hasDebugFlag) { score += 1; reasons.add("Debug build hai (normal store app nahi)") }
        val smsCombo = perms.contains("android.permission.READ_SMS") || perms.contains("android.permission.RECEIVE_SMS")
        val overlay = perms.contains("android.permission.SYSTEM_ALERT_WINDOW") || perms.contains("android.permission.BIND_ACCESSIBILITY_SERVICE")
        if (smsCombo && overlay) { score += 3; reasons.add("SMS + overlay/accessibility ek saath: banking-trojan jaisa mel") }
        val level = when { score >= 8 -> ApkLevel.HIGH; score >= 3 -> ApkLevel.MEDIUM; else -> ApkLevel.LOW }
        if (reasons.isEmpty()) reasons.add("Koi bada risky sanket nahi mila. Phir bhi sirf bharosemand source se install karein.")
        return ApkVerdict(level, score, reasons)
    }
}
