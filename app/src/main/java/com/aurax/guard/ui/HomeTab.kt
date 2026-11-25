package com.aurax.guard.ui

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import com.aurax.guard.EventStore
import com.aurax.guard.GuardAdminReceiver
import com.aurax.guard.LocationFetcher
import com.aurax.guard.Prefs
import com.aurax.guard.permissions.AuditStore
import com.aurax.guard.permissions.RiskLevel

/** Home: one clear status, quick actions, and a security snapshot. */
class HomeTab(
    private val act: Activity,
    private val actions: HomeActions,
    private val goGuard: () -> Unit,
) {
    val root = ScrollView(act).apply { isFillViewport = true }
    private val body = LinearLayout(act).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(Ui.dp(act, 16), Ui.dp(act, 24), Ui.dp(act, 16), Ui.dp(act, 24))
    }
    private var sirenBtn: Button? = null

    init {
        root.addView(body)
        refresh()
    }

    fun setSirenVisible(v: Boolean) {
        sirenBtn?.visibility = if (v) View.VISIBLE else View.GONE
    }

    fun refresh() = Ui.keepScroll(body) { refreshNow() }

    private fun refreshNow() {
        body.removeAllViews()
        val c = act
        val dpm = c.getSystemService(DevicePolicyManager::class.java)
        val adminOn = dpm.isAdminActive(ComponentName(c, GuardAdminReceiver::class.java))
        val camOn = c.checkSelfPermission(android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val locOn = LocationFetcher.hasPermission(c)
        val on = Prefs.guardOn(c)

        val missing = mutableListOf<String>()
        if (!adminOn) missing.add("Device Admin")
        if (!camOn) missing.add("Camera ijazat")
        if (!locOn) missing.add("Location ijazat")

        body.addView(Ui.heading(c, "auraX Guard"))
        body.addView(Ui.dimText(c, "Aapka phone, aapke control me"))

        val accent = when {
            !on -> Ui.red
            missing.isNotEmpty() -> Ui.amber
            else -> Ui.emerald
        }
        val hero = Ui.card(c, accent, 16)
        hero.addView(Ui.badge(c, when {
            !on -> "OFF"
            missing.isNotEmpty() -> "REVIEW CHAHIYE"
            else -> "ARMED"
        }, accent))
        hero.addView(Ui.text(c, when {
            !on -> "Protection band hai"
            missing.isNotEmpty() -> "Protection chalu, par adhoora"
            else -> "Protection chalu hai"
        }, 24f, bold = true).apply { setPadding(0, Ui.dp(c, 10), 0, Ui.dp(c, 2)) })
        hero.addView(Ui.dimText(c, when {
            !on -> "Galat password ki photo, jhatka aur siren abhi kaam nahi karenge."
            missing.isNotEmpty() -> "Baaki: " + missing.joinToString(", ") + ". Inke bina kuch features nahi chalenge."
            else -> "Galat password, jhatka, SIM aur charger par nigrani chalu hai."
        }, 14f))
        if (!on) {
            hero.addView(Ui.button(c, "Protection chalu karein") { actions.setGuard(true); refresh() })
        } else if (missing.isNotEmpty()) {
            hero.addView(Ui.button(c, "Ab theek karein") { actions.fixSetup() })
        }
        val siren = Ui.button(c, "SIREN BAND KAREIN", Ui.Btn.DANGER) { actions.stopSiren() }
        siren.visibility = View.GONE
        sirenBtn = siren
        hero.addView(siren)
        body.addView(hero)

        // ---- status at a glance ----
        try {
            val sm = com.aurax.guard.smart.SmartPrefs
            val items = com.aurax.guard.smart.StatusRules.items(
                on, com.aurax.guard.telegram.TelegramConfig.hasToken(c) && com.aurax.guard.telegram.TelegramConfig.chatId(c).isNotBlank(),
                com.aurax.guard.telegram.TelegramConfig.enabled(c),
                try { EventStore.pendingCount(c) } catch (_: Exception) { 0 },
                sm.traceOn(c), sm.state(c).active, com.aurax.guard.smart.BatterySaver.active(c), missing.isEmpty(),
            )
            val sc = Ui.card(c, null, 12, drops = 0)
            items.forEach { it0 ->
                val col = when (it0.kind) {
                    com.aurax.guard.smart.StatusKind.ACTIVE, com.aurax.guard.smart.StatusKind.READY -> Ui.emerald
                    com.aurax.guard.smart.StatusKind.QUEUED, com.aurax.guard.smart.StatusKind.ATTENTION -> Ui.amber
                    else -> Ui.dim
                }
                val row = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, Ui.dp(c, 4), 0, Ui.dp(c, 4)) }
                row.addView(Ui.text(c, it0.title, 14f).apply { layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f) })
                row.addView(Ui.badge(c, it0.label, col))
                sc.addView(row)
            }
            body.addView(sc)
        } catch (_: Exception) {
        }

        // ---- first-time setup: shown only until all 3 steps are done ----
        try {
            val steps = com.aurax.guard.smart.SetupRules.steps(
                com.aurax.guard.telegram.TelegramConfig.configured(c), on, missing.isEmpty(),
                try { EventStore.readAll(c).isNotEmpty() } catch (_: Exception) { false },
            )
            if (!com.aurax.guard.smart.SetupRules.allDone(steps)) {
                val sc = Ui.card(c, Ui.teal, 14)
                sc.addView(Ui.text(c, "Shuruaat: ${steps.count { it.done }}/${steps.size} ho gaya", 17f, bold = true))
                steps.forEach { st ->
                    sc.addView(Ui.text(c, (if (st.done) "OK  " else "${st.n}.  ") + st.title, 15f, bold = true, color = if (st.done) Ui.emerald else Color.WHITE).apply { setPadding(0, Ui.dp(c, 8), 0, 0) })
                    if (!st.done) sc.addView(Ui.dimText(c, st.hint))
                }
                val nx = com.aurax.guard.smart.SetupRules.next(steps)!!
                sc.addView(Ui.button(c, "Agla kadam: " + nx.title, Ui.Btn.PRIMARY, 10) {
                    when (nx.action) {
                        com.aurax.guard.smart.SetupAction.TELEGRAM -> actions.openTelegram()
                        com.aurax.guard.smart.SetupAction.PROTECTION -> if (!on) { actions.setGuard(true); refresh() } else actions.fixSetup()
                        com.aurax.guard.smart.SetupAction.TEST -> actions.testTrigger()
                    }
                })
                body.addView(sc)
            }
        } catch (_: Exception) {
        }

        // ---- one-tap modes ----
        try {
            val md = Ui.card(c, null, 12)
            md.addView(Ui.text(c, "Abhi kahan ho?", 15f, bold = true))
            val cur = com.aurax.guard.smart.SmartPrefs.mode(c)
            md.addView(Ui.chipRow(c, com.aurax.guard.smart.Mode.ALL.map { m ->
                Ui.chip(c, m.title, cur == m.key) { com.aurax.guard.smart.ModeProfiles.apply(c, m); refresh() }
            }, 8))
            md.addView(Ui.dimText(c, com.aurax.guard.smart.Mode.of(cur)?.desc ?: "Ek tap me sensitivity, grace aur siren set ho jate hain."))
            body.addView(md)
        } catch (_: Exception) {
        }

        // ---- quick tiles ----
        body.addView(Ui.text(c, "Quick actions", 17f, bold = true).apply { setPadding(0, Ui.dp(c, 20), 0, Ui.dp(c, 2)) })
        val parkOn = Prefs.parkOn(c)
        val saverOn = com.aurax.guard.smart.BatterySaver.active(c)
        val sections = listOf(
            "Rakshak (abhi ke kaam)" to listOf(
                Tile(android.R.drawable.ic_menu_mylocation, "Test Trigger", "Photo + GPS + log", false) { actions.testTrigger() },
                Tile(android.R.drawable.ic_lock_lock, "Quick Lock", "Abhi screen lock", false) { actions.quickLock() },
                Tile(android.R.drawable.ic_dialog_alert, "Panic", "Lock + siren", true) { actions.panic() },
                Tile(android.R.drawable.ic_menu_agenda, "Haath mat lagao", if (parkOn) "ON" else "OFF", false) { actions.togglePark(); refresh() },
            ),
            "Alerts aur history" to listOf(
                Tile(android.R.drawable.ic_menu_view, "Alert queue", "GPS, battery, samay", false) { actions.openQueue() },
                Tile(android.R.drawable.ic_menu_gallery, "Photos", "Intruder photos", false) { actions.openPhotos() },
                Tile(android.R.drawable.ic_menu_send, "Telegram alerts", com.aurax.guard.telegram.TelegramConfig.summary(c).take(26), false) { actions.openTelegram() },
                Tile(android.R.drawable.ic_lock_silent_mode, "Alert controls", if (Prefs.snoozed(c)) "Quiet chalu" else "Types, quiet timer", false) { actions.openAlerts() },
            ),
            "Smart aur battery" to listOf(
                Tile(android.R.drawable.ic_menu_compass, "Emergency & Smart", if (com.aurax.guard.smart.SmartPrefs.traceOn(c)) "Trace ON" else "Trace, modes, digest", false) { actions.openEmergency() },
                Tile(android.R.drawable.ic_menu_day, "Battery saver", if (saverOn) "Active" else "Off (tap: chalu)", false) { actions.toggleSaver(); refresh() },
                Tile(android.R.drawable.ic_menu_search, "APK / file check", "Download ki jaanch", false) { actions.openApkScan() },
                Tile(android.R.drawable.ic_menu_search, "Permission check", "Risky apps dekhein", false) { goGuard() },
            ),
            "Settings aur setup" to listOf(
                Tile(android.R.drawable.ic_menu_help, "Look & AI", if (com.aurax.guard.ai.AiConfig.ready(c)) "AI chalu" else "Glass, theme, rang", false) { actions.openAi() },
                Tile(android.R.drawable.ic_menu_info_details, "Phone readiness", "Background / OEM check", false) { actions.openReadiness() },
                Tile(android.R.drawable.ic_menu_manage, "Protection settings", "PIN, siren, privacy", false) { actions.openControls() },
            ),
        )
        sections.forEach { (title, tiles) ->
            body.addView(Ui.text(c, title, 14f, bold = true, color = Ui.dim).apply { setPadding(Ui.dp(c, 4), Ui.dp(c, 14), 0, Ui.dp(c, 2)) })
            tiles.chunked(2).forEach { pair ->
                val r = LinearLayout(c).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = Ui.lp(c, 10)
                }
                pair.forEachIndexed { i, t ->
                    r.addView(tile(t).apply {
                        (layoutParams as LinearLayout.LayoutParams).apply {
                            if (i == 0) rightMargin = Ui.dp(c, 5) else leftMargin = Ui.dp(c, 5)
                        }
                    })
                }
                body.addView(r)
            }
        }

        // ---- snapshot ----
        body.addView(Ui.text(c, "Security snapshot", 17f, bold = true).apply { setPadding(0, Ui.dp(c, 20), 0, Ui.dp(c, 2)) })
        val snap = Ui.card(c, null, 10)
        val high = AuditStore.count(RiskLevel.HIGH)
        val medium = AuditStore.count(RiskLevel.MEDIUM)
        val queue = try { EventStore.readAll(c).size } catch (_: Exception) { 0 }
        val stats = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
        stats.addView(stat("High risk apps", high?.toString() ?: "—", if ((high ?: 0) > 0) Ui.red else Ui.emerald))
        stats.addView(stat("Medium", medium?.toString() ?: "—", if ((medium ?: 0) > 0) Ui.amber else Ui.emerald))
        stats.addView(stat("Alerts", queue.toString(), Ui.teal))
        snap.addView(stats)
        snap.addView(Ui.dimText(c, if (high == null) {
            "Apps ka scan abhi nahi hua. Neeche button se karein."
        } else {
            "Ye risk indicators hain (app kya kar sakti hai), malware ka daawa nahi."
        }).apply { setPadding(0, Ui.dp(c, 10), 0, 0) })
        snap.addView(Ui.button(c, if (high == null) "Apps scan karein" else "Details dekhein", Ui.Btn.GHOST) { goGuard() })
        body.addView(snap)

        // ---- system & bot health ----
        body.addView(Ui.text(c, "System & Bot health", 17f, bold = true).apply { setPadding(0, Ui.dp(c, 20), 0, Ui.dp(c, 2)) })
        try {
            val checks = com.aurax.guard.health.HealthRules.build(com.aurax.guard.health.HealthCollector.collect(c))
            val worst = when {
                checks.any { it.level == com.aurax.guard.health.Level.BAD } -> Ui.red
                checks.any { it.level == com.aurax.guard.health.Level.WARN } -> Ui.amber
                else -> Ui.emerald
            }
            val hc = Ui.card(c, worst, 10)
            hc.addView(Ui.text(c, com.aurax.guard.health.HealthRules.headline(checks), 16f, bold = true))
            checks.forEach { k ->
                val col = when (k.level) { com.aurax.guard.health.Level.OK -> Ui.emerald; com.aurax.guard.health.Level.WARN -> Ui.amber; else -> Ui.red }
                val line = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL; setPadding(0, Ui.dp(c, 4), 0, 0) }
                line.addView(Ui.text(c, (when (k.level) { com.aurax.guard.health.Level.OK -> "OK  "; com.aurax.guard.health.Level.WARN -> "DHYAN  "; else -> "THEEK KAREIN  " }) + k.name, 14f, bold = true, color = col))
                line.addView(Ui.dimText(c, k.detail, 12f))
                hc.addView(line)
            }
            hc.addView(Ui.button(c, "Phone readiness kholein", Ui.Btn.GHOST, 10) { actions.openReadiness() })
            body.addView(hc)
        } catch (_: Exception) {
            body.addView(Ui.dimText(c, "Health check abhi nahi chala."))
        }
    }

    private data class Tile(val icon: Int, val title: String, val sub: String, val danger: Boolean, val onClick: () -> Unit)

    private fun tile(t: Tile): LinearLayout {
        val c = act
        val tint = if (t.danger) Ui.red else Ui.emerald
        return LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.START
            setPadding(Ui.dp(c, 14), Ui.dp(c, 14), Ui.dp(c, 14), Ui.dp(c, 14))
            background = Ui.ripple(c, 26, Ui.glass(c, 26, if (t.danger) Ui.red else null, strong = true, drops = 3))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(ImageView(c).apply {
                setImageResource(t.icon)
                setColorFilter(tint, PorterDuff.Mode.SRC_IN)
                layoutParams = LinearLayout.LayoutParams(Ui.dp(c, 26), Ui.dp(c, 26))
            })
            addView(Ui.text(c, t.title, 15f, bold = true).apply { setPadding(0, Ui.dp(c, 8), 0, 0); maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END })
            addView(Ui.dimText(c, t.sub, 12f).apply { maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END })
            setOnClickListener { t.onClick() }
        }
    }

    private fun stat(label: String, value: String, color: Int): LinearLayout {
        val c = act
        return LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            addView(Ui.text(c, value, 26f, bold = true, color = color))
            addView(Ui.dimText(c, label, 12f))
        }
    }
}
