package com.aurax.guard.ui

import android.app.Activity
import android.graphics.Color
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import com.aurax.guard.permissions.AppAssessment
import com.aurax.guard.permissions.AppAuditor
import com.aurax.guard.permissions.AuditStore
import com.aurax.guard.permissions.PermGroup
import com.aurax.guard.permissions.RiskLevel
import com.aurax.guard.permissions.SettingsLinks

/**
 * Guard: Permission Control Hub + app risk check. Read-only. Every app has a
 * plain-language "why this matters" and one-tap links into system settings.
 */
class GuardTab(private val act: Activity) {
    val root = ScrollView(act).apply { isFillViewport = true }
    private val body = LinearLayout(act).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(Ui.dp(act, 16), Ui.dp(act, 24), Ui.dp(act, 16), Ui.dp(act, 24))
    }

    private var mode = 0 // 0 permissions, 1 harmful app check, 2 files
    private var group: PermGroup? = null
    private var showSystem = false
    private var shown = PAGE
    private var scanning = false

    companion object {
        private const val PAGE = 25
    }

    init {
        root.addView(body)
    }

    fun onShow() {
        if (AuditStore.apps == null && !scanning) scan() else render()
    }

    private fun scan() {
        scanning = true
        render()
        Thread {
            val list = try { AppAuditor.audit(act) } catch (_: Exception) { emptyList() }
            AuditStore.set(list)
            act.runOnUiThread {
                scanning = false
                shown = PAGE
                render()
            }
        }.start()
    }

    private fun levelColor(l: RiskLevel) = when (l) {
        RiskLevel.HIGH -> Ui.red
        RiskLevel.MEDIUM -> Ui.amber
        RiskLevel.NORMAL -> Ui.emerald
    }

    private fun levelName(l: RiskLevel) = when (l) {
        RiskLevel.HIGH -> "High Risk"
        RiskLevel.MEDIUM -> "Medium"
        RiskLevel.NORMAL -> "Normal"
    }

    private fun render() {
        val c = act
        body.removeAllViews()
        body.addView(Ui.heading(c, "Guard"))
        body.addView(Ui.dimText(c, "Kaun si app phone par kya kar sakti hai"))

        body.addView(Ui.chipRow(c, listOf(
            Ui.chip(c, "App permissions", mode == 0) { mode = 0; shown = PAGE; render() },
            Ui.chip(c, "Harmful app check", mode == 1) { mode = 1; shown = PAGE; render() },
            Ui.chip(c, "Suspicious files", mode == 2) { mode = 2; render() },
        )))

        if (mode == 2) {
            val card = Ui.card(c, Ui.amber, 14)
            card.addView(Ui.text(c, "Jald aa raha hai", 17f, bold = true))
            card.addView(Ui.dimText(c, "Ye hissa leftover APK, shak wali files aur bade junk ko dhoondhega. Ye sirf dikhayega; kuch bhi apne aap nahi mitayega. Abhi tak ye bana nahi hai."))
            body.addView(card)
            return
        }

        val apps = AuditStore.apps
        if (scanning || apps == null) {
            val card = Ui.card(c, null, 14)
            card.addView(Ui.text(c, if (scanning) "Apps scan ho rahi hain..." else "Scan baaki hai", 17f, bold = true))
            card.addView(Ui.dimText(c, "Ye sirf padhta hai, kuch badalta nahi."))
            if (!scanning) card.addView(Ui.button(c, "Apps scan karein") { scan() })
            body.addView(card)
            return
        }

        val visible = apps.filter { showSystem || !it.facts.isSystem }
        val high = visible.count { it.result.level == RiskLevel.HIGH }
        val medium = visible.count { it.result.level == RiskLevel.MEDIUM }

        val sum = Ui.card(c, if (high > 0) Ui.red else if (medium > 0) Ui.amber else Ui.emerald, 14)
        sum.addView(Ui.text(c, "${visible.size} apps dekhi gayi", 17f, bold = true))
        sum.addView(Ui.dimText(c, "High Risk: $high   Medium: $medium   Normal: ${visible.size - high - medium}"))
        sum.addView(Ui.dimText(c, "Pehle High Risk apps dekhein. Zaroorat na ho to permission band karein ya app hatayein. " +
            "Ye risk indicators hain, malware ka daawa nahi."))
        body.addView(sum)

        if (mode == 0) {
            val chips = mutableListOf<View>()
            chips.add(Ui.chip(c, "Sab", group == null) { group = null; shown = PAGE; render() })
            PermGroup.values().forEach { g ->
                val n = visible.count { it.facts.has(g) }
                chips.add(Ui.chip(c, "${g.label} ($n)", group == g) { group = g; shown = PAGE; render() })
            }
            body.addView(Ui.chipRow(c, chips))
            group?.let { g -> body.addView(groupHelp(g)) }
        } else {
            body.addView(Ui.dimText(c, "Sirf wo apps jinme risk indicators hain.").apply { setPadding(0, Ui.dp(c, 10), 0, 0) })
        }

        body.addView(Ui.chipRow(c, listOf(
            Ui.chip(c, if (showSystem) "System apps: dikh rahi" else "System apps: chhupi", showSystem) {
                showSystem = !showSystem; shown = PAGE; render()
            },
            Ui.chip(c, "Dobara scan", false) { scan() },
        ), 8))

        var list = visible
        list = if (mode == 1) {
            list.filter { it.result.level != RiskLevel.NORMAL || it.facts.sideloaded }
        } else {
            val g = group
            if (g == null) list else list.filter { it.facts.has(g) }
        }
        if (list.isEmpty()) {
            body.addView(Ui.dimText(c, "Is filter me koi app nahi mili. Achhi baat hai.").apply { setPadding(0, Ui.dp(c, 16), 0, 0) })
            return
        }
        list.take(shown).forEach { body.addView(appCard(it)) }
        if (list.size > shown) {
            body.addView(Ui.button(c, "Aur dikhayein (${list.size - shown} baaki)", Ui.Btn.GHOST, 12) {
                shown += PAGE
                render()
            })
        }
    }

    private fun groupHelp(g: PermGroup): LinearLayout {
        val c = act
        val card = Ui.card(c, null, 10)
        val (why, open) = when (g) {
            PermGroup.CAMERA -> "Jo apps camera use kar sakti hain. Zaroorat na ho to band kar dein." to null
            PermGroup.MIC -> "Jo apps awaaz sun/record kar sakti hain." to null
            PermGroup.BG_LOCATION -> "Ye apps band hone par bhi location dekh sakti hain. Sirf zaroori apps ko 'Hamesha' rakhein." to null
            PermGroup.OVERLAY -> "Dusri apps ke upar dikhne wali apps. Nakli login screen ka risk." to ("Overlay settings" to SettingsLinks.overlay(act.packageName))
            PermGroup.ACCESSIBILITY -> "Sabse taqatwar access: screen padhna aur tap karna. Sirf bharose ki apps rakhein." to ("Accessibility settings" to SettingsLinks.accessibility())
            PermGroup.DEVICE_ADMIN -> "Admin apps ko hataana mushkil hota hai. Anjaan app ho to pehle admin band karein." to ("Device admin settings" to SettingsLinks.deviceAdmin())
            PermGroup.NOTIFICATIONS -> "Ye apps aapke saare notification (OTP samet) padh sakti hain." to ("Notification access settings" to SettingsLinks.notificationListeners())
            PermGroup.SMS_CALL -> "SMS, call ya saari files ka access. Bank, OTP aur private data ka risk." to null
        }
        card.addView(Ui.dimText(c, "Kyun zaroori: $why", 13f))
        if (open != null) {
            card.addView(Ui.button(c, open.first, Ui.Btn.GHOST) { SettingsLinks.open(c, open.second, SettingsLinks.security()) })
        }
        return card
    }

    private fun appCard(a: AppAssessment): LinearLayout {
        val c = act
        val f = a.facts
        val color = levelColor(a.result.level)
        val card = Ui.card(c, if (a.result.level == RiskLevel.HIGH) Ui.red else null, 10)

        val top = Ui.row(c)
        try {
            top.addView(ImageView(c).apply {
                setImageDrawable(c.packageManager.getApplicationIcon(f.packageName))
                layoutParams = LinearLayout.LayoutParams(Ui.dp(c, 40), Ui.dp(c, 40)).apply { rightMargin = Ui.dp(c, 12) }
            })
        } catch (_: Exception) {
        }
        val names = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(Ui.text(c, f.label, 16f, bold = true))
            addView(Ui.dimText(c, f.packageName, 11f))
        }
        top.addView(names)
        top.addView(Ui.badge(c, levelName(a.result.level), color))
        card.addView(top)

        val shownReasons = a.result.reasons.take(3)
        shownReasons.forEach { r ->
            card.addView(Ui.text(c, r.title, 13f, bold = true, color = Color.WHITE).apply { setPadding(0, Ui.dp(c, 8), 0, 0) })
            card.addView(Ui.dimText(c, "Kyun zaroori: ${r.why}", 12f))
        }
        if (a.result.reasons.size > 3) {
            card.addView(Ui.dimText(c, "+${a.result.reasons.size - 3} aur indicators", 12f))
        }
        if (a.result.reasons.isEmpty()) {
            card.addView(Ui.dimText(c, "Koi khaas risk indicator nahi.", 12f).apply { setPadding(0, Ui.dp(c, 6), 0, 0) })
        }

        if (!f.isSelf) {
            val btns = Ui.row(c, 10)
            btns.addView(Ui.button(c, "App info", Ui.Btn.GHOST, 0) {
                SettingsLinks.open(c, SettingsLinks.appInfo(f.packageName))
            }.apply { layoutParams = Ui.lp(c, 0, 1f).apply { rightMargin = Ui.dp(c, 6) } })
            btns.addView(Ui.button(c, "Permissions", Ui.Btn.GHOST, 0) {
                SettingsLinks.open(c, SettingsLinks.appPermissions(f.packageName), SettingsLinks.appInfo(f.packageName))
            }.apply { layoutParams = Ui.lp(c, 0, 1f).apply { rightMargin = Ui.dp(c, 6) } })
            if (!f.isSystem) {
                btns.addView(Ui.button(c, "Hatayein", Ui.Btn.GHOST, 0) {
                    SettingsLinks.open(c, SettingsLinks.uninstall(f.packageName), SettingsLinks.appInfo(f.packageName))
                }.apply { layoutParams = Ui.lp(c, 0, 1f) })
            }
            card.addView(btns)
        }
        return card
    }
}
