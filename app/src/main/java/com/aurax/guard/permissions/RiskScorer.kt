package com.aurax.guard.permissions

/**
 * Turns [AppFacts] into a risk badge. These are RISK INDICATORS (what an app is
 * able to do), not a claim that an app is malware. Many honest apps score
 * Medium or High (for example a banking app that uses overlay).
 */
object RiskScorer {
    const val HIGH_AT = 6
    const val MEDIUM_AT = 3

    fun assess(f: AppFacts): RiskResult {
        if (f.isSelf) {
            return RiskResult(
                RiskLevel.NORMAL, 0,
                listOf(Reason("Ye auraX Guard hai", "Ye aapki suraksha app hai; Device Admin iska kaam hai.", 0)),
            )
        }
        val r = mutableListOf<Reason>()
        fun add(on: Boolean, title: String, why: String, pts: Int) {
            if (on) r.add(Reason(title, why, pts))
        }

        add(f.accessibility, "Accessibility chalu",
            "Ye app aapki screen padh sakti hai aur aapki jagah tap kar sakti hai.", 3)
        add(f.deviceAdmin, "Device Admin",
            "Isko hataana mushkil ho sakta hai, aur ye phone lock ya wipe kar sakti hai.", 3)
        add(f.notificationListener, "Notification access",
            "Aapke saare notification padh sakti hai (OTP samet).", 2)
        add(f.overlay, "Dusri apps ke upar dikhna",
            "Kisi aur app ke upar nakli screen dikha sakti hai (password churane ka tareeqa).", 2)
        add(f.backgroundLocation, "Background location",
            "App band hone par bhi aapki location dekh sakti hai.", 2)
        add(f.sms, "SMS access", "SMS padh ya bhej sakti hai (OTP ka risk).", 2)
        add(f.microphone, "Microphone", "Awaaz record kar sakti hai.", 1)
        add(f.camera, "Camera", "Camera use kar sakti hai.", 1)
        add(f.callLog, "Call log", "Aapki call history padh sakti hai.", 1)
        add(f.allFiles, "Saari files ka access", "Phone ki lagbhag saari files padh/badal sakti hai.", 1)
        add(f.installPackages, "Apps install kar sakti hai", "Doosri apps phone me daal sakti hai.", 1)
        add(f.sideloaded, "Store se nahi aayi",
            "Play Store ya official store se install nahi hui; source pakka nahi.", 1)

        // Dangerous combinations score extra.
        add(f.accessibility && f.overlay, "Accessibility + Overlay",
            "Dono milkar screen par nakli window dikha kar tap bhi kar sakti hain. Sabse zyada dhyan dein.", 3)
        add(f.accessibility && f.sms, "Accessibility + SMS",
            "Screen aur SMS dono par control; OTP chori ka risk.", 2)
        add(f.notificationListener && f.sms, "Notification + SMS",
            "OTP dono jagah se padhe ja sakte hain.", 2)
        add(f.overlay && f.sms, "Overlay + SMS", "Nakli screen aur OTP dono ka risk.", 1)
        add(f.microphone && f.camera && f.backgroundLocation, "Mic + Camera + Background location",
            "Awaaz, photo aur location teeno; nigrani jaisa combination.", 2)
        add(f.deviceAdmin && f.sideloaded, "Device Admin + store se nahi aayi",
            "Anjaan source ki app jo hataane se bhi rok sakti hai.", 2)

        val sorted = r.sortedByDescending { it.points }
        val score = sorted.sumOf { it.points }
        val level = when {
            score >= HIGH_AT -> RiskLevel.HIGH
            score >= MEDIUM_AT -> RiskLevel.MEDIUM
            else -> RiskLevel.NORMAL
        }
        return RiskResult(level, score, sorted)
    }

    fun assessAll(apps: List<AppFacts>): List<AppAssessment> =
        apps.map { AppAssessment(it, assess(it)) }.sortedByDescending { it.result.score }
}

/** Last audit result, kept in memory only (nothing is written to disk). */
object AuditStore {
    @Volatile var apps: List<AppAssessment>? = null
    @Volatile var scannedAtMs: Long = 0

    fun set(list: List<AppAssessment>) {
        apps = list
        scannedAtMs = System.currentTimeMillis()
    }

    fun count(level: RiskLevel, includeSystem: Boolean = false): Int? =
        apps?.count { it.result.level == level && (includeSystem || !it.facts.isSystem) }
}
