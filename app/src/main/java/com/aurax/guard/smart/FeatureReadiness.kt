package com.aurax.guard.smart

data class ReadyInputs(
    val telegramReady: Boolean, val camera: Boolean, val location: Boolean, val bgLocation: Boolean,
    val mic: Boolean, val notifications: Boolean, val deviceAdmin: Boolean, val guardOn: Boolean,
    val cameraService: Boolean, val traceOn: Boolean, val tracePhoto: Boolean, val audioOn: Boolean, val simOn: Boolean,
)

data class FeatureRow(val feature: String, val ok: Boolean, val needs: String, val fix: String)

/** Feature -> what it needs -> is it ready. Pure rules; the screen only shows the rows. */
object FeatureReadiness {
    fun rows(i: ReadyInputs): List<FeatureRow> {
        val l = mutableListOf<FeatureRow>()
        l.add(FeatureRow("Telegram alerts", i.telegramReady, "Bot token + Chat ID", if (i.telegramReady) "Theek" else "Telegram alerts me token/chat ID bharein"))
        l.add(FeatureRow("Motion guard", i.guardOn && i.deviceAdmin, "Guard ON + Device Admin (screen lock)", if (!i.guardOn) "Guard chalu karein" else if (!i.deviceAdmin) "Device Admin allow karein" else "Theek"))
        l.add(FeatureRow("Intruder / emergency photo", i.camera && i.cameraService, "Camera permission + Guard app se chalu kiya hua",
            if (!i.camera) "Camera permission dein" else if (!i.cameraService) "App kholkar Guard dobara ON karein (camera tabhi milta hai)" else "Theek"))
        l.add(FeatureRow("Location in alerts", i.location, "Location permission", if (i.location) "Theek" else "Location permission dein"))
        l.add(FeatureRow("Emergency Trace (screen off me)", i.traceOn && i.location && i.bgLocation, "Trace ON + Location 'Hamesha allow'",
            if (!i.traceOn) "Emergency & Smart me Trace chalu karein" else if (!i.location) "Location permission dein" else if (!i.bgLocation) "Location ko 'Hamesha allow' karein (Settings > App > Permissions)" else "Theek"))
        l.add(FeatureRow("Audio clip", !i.audioOn || (i.mic && i.cameraService), "Mic permission + Guard app se chalu kiya hua", if (!i.audioOn) "Band hai" else if (!i.mic) "Mic permission dein" else if (!i.cameraService) "Guard dobara ON karein" else "Theek"))
        l.add(FeatureRow("SIM alert", !i.simOn || i.guardOn, "Guard ON", if (!i.simOn) "Band hai" else if (!i.guardOn) "Guard chalu karein" else "Theek"))
        l.add(FeatureRow("Notifications", i.notifications, "Notification permission (guard dikhne ke liye)", if (i.notifications) "Theek" else "Notifications allow karein"))
        return l
    }
}
