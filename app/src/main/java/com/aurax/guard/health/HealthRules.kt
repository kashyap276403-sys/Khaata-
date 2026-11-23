package com.aurax.guard.health

enum class Level { OK, WARN, BAD }
data class Check(val name: String, val level: Level, val detail: String)

/** Everything the health card needs, as plain values so the rules can be unit tested. */
data class HealthInputs(
    val tokenSaved: Boolean,
    val tokenFormatOk: Boolean,
    val chatSaved: Boolean,
    val chatFormatOk: Boolean,
    val telegramEnabled: Boolean,
    val deviceLabel: String,
    val labelIsDefault: Boolean,
    val pending: Int,
    val failed: Int,
    val lastTelegramStatus: String,
    val online: Boolean,
    val camera: Boolean,
    val location: Boolean,
    val notifications: Boolean,
    val deviceAdmin: Boolean,
    val batteryUnrestricted: Boolean,
    val audioWanted: Boolean,
    val micPermission: Boolean,
    val guardOn: Boolean,
)

object HealthRules {
    fun build(i: HealthInputs): List<Check> {
        val out = mutableListOf<Check>()
        out.add(
            when {
                !i.tokenSaved -> Check("Bot Token", Level.BAD, "Saved nahi. Telegram alerts > BOT_TOKEN daalein")
                !i.tokenFormatOk -> Check("Bot Token", Level.WARN, "Saved hai par format ajeeb lag raha hai")
                else -> Check("Bot Token", Level.OK, "Saved")
            },
        )
        out.add(
            when {
                !i.chatSaved -> Check("Chat ID", Level.BAD, "Saved nahi")
                !i.chatFormatOk -> Check("Chat ID", Level.BAD, "Sirf number hona chahiye")
                else -> Check("Chat ID", Level.OK, "Saved")
            },
        )
        out.add(
            if (!i.telegramEnabled) Check("Telegram alerts", Level.WARN, "Band hain (alerts sirf phone me rahenge)")
            else Check("Telegram alerts", Level.OK, "Chalu")
        )
        out.add(
            if (i.labelIsDefault) Check("Device label", Level.WARN, "'${i.deviceLabel}' (phone ka model). Adi-Main jaisa naam rakhein")
            else Check("Device label", Level.OK, i.deviceLabel)
        )
        out.add(
            when {
                i.failed > 0 -> Check("Queue", Level.BAD, "${i.failed} alert fail hue. ${i.lastTelegramStatus}")
                i.pending > 0 && !i.online -> Check("Queue", Level.WARN, "${i.pending} alert ruke hain: offline, internet aate hi jayenge")
                i.pending > 0 -> Check("Queue", Level.WARN, "${i.pending} alert baaki hain, abhi bheje ja rahe hain")
                else -> Check("Queue", Level.OK, "Khaali, sab bhej diya")
            },
        )
        out.add(if (i.guardOn) Check("Guard", Level.OK, "Chalu") else Check("Guard", Level.BAD, "Band hai: Home par chalu karein"))
        out.add(if (i.deviceAdmin) Check("Device Admin", Level.OK, "ON (lock aur galat password pakad)") else Check("Device Admin", Level.BAD, "OFF: screen lock aur galat-password alert nahi chalenge"))
        out.add(if (i.camera) Check("Camera", Level.OK, "Permission hai") else Check("Camera", Level.BAD, "Permission nahi: intruder photo nahi banegi"))
        out.add(if (i.location) Check("Location", Level.OK, "Permission hai") else Check("Location", Level.WARN, "Permission nahi: alert me map link nahi hoga"))
        out.add(if (i.notifications) Check("Notifications", Level.OK, "Allowed") else Check("Notifications", Level.WARN, "Band: siren stop button/status nahi dikhega"))
        if (i.audioWanted) out.add(if (i.micPermission) Check("Microphone", Level.OK, "Permission hai") else Check("Microphone", Level.BAD, "Audio chalu hai par permission nahi"))
        out.add(if (i.batteryUnrestricted) Check("Background (OEM)", Level.OK, "Battery restriction nahi") else Check("Background (OEM)", Level.WARN, "Battery optimization ON: guard band ho sakta hai. Phone readiness kholein"))
        return out
    }

    fun headline(checks: List<Check>): String {
        val bad = checks.count { it.level == Level.BAD }
        val warn = checks.count { it.level == Level.WARN }
        return when {
            bad > 0 -> "$bad zaroori cheez theek karni hai" + (if (warn > 0) ", $warn dhyan wali" else "")
            warn > 0 -> "$warn cheez dhyan maangti hai"
            else -> "Sab theek hai"
        }
    }
}
