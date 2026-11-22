package com.aurax.guard.telegram

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.aurax.guard.EventStore
import com.aurax.guard.Gate
import com.aurax.guard.Prefs
import com.aurax.guard.ui.Ui
import java.util.concurrent.Executors

/**
 * Telegram alerts settings. BOT_TOKEN and CHAT_ID are typed HERE, inside the app,
 * and stay on this phone only (token encrypted). Nothing secret lives in the source code.
 */
class TelegramSettingsActivity : Activity() {
    private lateinit var token: EditText
    private lateinit var chat: EditText
    private lateinit var label: EditText
    private lateinit var status: TextView
    private lateinit var queue: TextView
    private lateinit var master: Switch
    private var showing = false
    private val io = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Gate.isOpen(this)) { finish(); return }
        if (Prefs.secureScreen(this)) window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        val c = this
        val root = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(Ui.dp(c, 16), Ui.dp(c, 24), Ui.dp(c, 16), Ui.dp(c, 48))
        }
        root.addView(Ui.heading(c, "Telegram alerts"))
        root.addView(Ui.dimText(c, "Alert (photo, location, battery, samay) aapke Telegram bot me jayega. Internet na ho to queue me ruka rahega aur internet aate hi apne aap jayega."))

        val form = Ui.card(c, null, 14)
        form.addView(Ui.text(c, "Kahan daalna hai", 15f, bold = true))
        form.addView(Ui.dimText(c, "BOT_TOKEN: @BotFather se mila token.\nCHAT_ID: aapka Telegram ID, jaise 8241507452."))
        form.addView(Ui.text(c, "BOT_TOKEN", 13f, color = Ui.dim).apply { setPadding(0, Ui.dp(c, 10), 0, 0) })
        token = field(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD, "123456789:AA... (save hone par yahan nahi dikhta)")
        form.addView(token)
        form.addView(Ui.button(c, "Token dikhayein / chhupayein", Ui.Btn.GHOST, 4) { toggleShow() })
        form.addView(Ui.text(c, "CHAT_ID", 13f, color = Ui.dim).apply { setPadding(0, Ui.dp(c, 10), 0, 0) })
        chat = field(InputType.TYPE_CLASS_TEXT, "8241507452")
        form.addView(chat)
        form.addView(Ui.text(c, "Device Label", 13f, color = Ui.dim).apply { setPadding(0, Ui.dp(c, 10), 0, 0) })
        label = field(InputType.TYPE_CLASS_TEXT, "Adi-Main / Mummy-Phone / Adi-Second")
        form.addView(label)
        form.addView(Ui.button(c, "Save", Ui.Btn.PRIMARY, 12) { save() })
        root.addView(form)

        val sw = Ui.card(c, null, 12)
        master = Switch(c).apply {
            text = "Telegram alerts chalu"
            setTextColor(Color.WHITE)
            setOnCheckedChangeListener { v, on ->
                if (!v.isPressed) return@setOnCheckedChangeListener
                if (on && !(TelegramConfig.hasToken(c) && TelegramRules.isValidChatId(TelegramConfig.chatId(c)))) {
                    v.isChecked = false
                    toast("Pehle BOT_TOKEN aur CHAT_ID save karein")
                } else {
                    TelegramConfig.setEnabled(c, on)
                    if (on) TelegramDispatcher.kick(c)
                    render()
                }
            }
        }
        sw.addView(master)
        sw.addView(Ui.dimText(c, "Chalu karne ke baad ki nayi alerts hi bheji jati hain."))
        root.addView(sw)

        val st = Ui.card(c, null, 12)
        st.addView(Ui.text(c, "Status", 15f, bold = true))
        status = Ui.dimText(c, "")
        queue = Ui.dimText(c, "")
        st.addView(status)
        st.addView(queue)
        st.addView(Ui.button(c, "Send Test Alert", Ui.Btn.PRIMARY, 10) { sendTest() })
        st.addView(Ui.button(c, "Ruki hui alerts dobara bhejein", Ui.Btn.GHOST, 6) { retryNow() })
        st.addView(Ui.button(c, "Saved token hatayein", Ui.Btn.DANGER, 6) { forgetToken() })
        root.addView(st)
        root.addView(Ui.dimText(c, "Dhyan: alert me location aur photo Telegram ke through jati hai (end-to-end encrypted nahi). Dusre ke phone par ye sirf unki jaankari me lagayein.").apply { setPadding(0, Ui.dp(c, 12), 0, 0) })
        setContentView(ScrollView(c).apply { setBackgroundColor(Color.BLACK); isFillViewport = true; addView(root) })
        chat.setText(TelegramConfig.chatId(c))
        label.setText(TelegramConfig.rawLabel(c))
    }

    private fun field(type: Int, hint: String) = EditText(this).apply {
        inputType = type
        this.hint = hint
        setTextColor(Color.WHITE)
        setHintTextColor(Color.argb(100, 255, 255, 255))
        isSingleLine = true
    }

    private fun toggleShow() {
        showing = !showing
        token.inputType = InputType.TYPE_CLASS_TEXT or
            (if (showing) InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD else InputType.TYPE_TEXT_VARIATION_PASSWORD)
        token.setSelection(token.text.length)
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()

    private fun save() {
        val t = token.text.toString()
        val id = chat.text.toString()
        if (t.isNotBlank()) {
            if (!TelegramRules.isValidToken(t)) { toast("BOT_TOKEN ka format galat lag raha hai (123456789:AA...)"); return }
            if (!TelegramConfig.setToken(this, t)) { toast("Phone ki secure storage use nahi ho payi"); return }
            token.setText("")
        } else if (!TelegramConfig.hasToken(this)) {
            toast("BOT_TOKEN daalein"); return
        }
        if (!TelegramRules.isValidChatId(id)) { toast("CHAT_ID sirf number hota hai, jaise 8241507452"); return }
        TelegramConfig.setChatId(this, id)
        TelegramConfig.setLabel(this, label.text.toString())
        toast("Save ho gaya. Ab 'Send Test Alert' dabayein.")
        render()
    }

    private fun sendTest() {
        val app = applicationContext
        val tk = TelegramConfig.token(app)
        val id = TelegramConfig.chatId(app)
        if (tk == null || !TelegramRules.isValidChatId(id)) { toast("Pehle BOT_TOKEN aur CHAT_ID save karein"); return }
        status.text = "Test bheja ja raha hai..."
        io.execute {
            val res = TelegramClient.sendMessage(tk, id, TelegramFormat.test(TelegramConfig.label(app), System.currentTimeMillis()))
            val msg = when (res) {
                is TgResult.Ok -> "Test alert gaya. Connection theek hai."
                is TgResult.Retry -> "Nahi gaya: " + TelegramRules.redact(res.why, tk)
                is TgResult.Fail -> "Fail: " + TelegramRules.redact(res.why, tk)
            }
            TelegramConfig.setStatus(app, msg)
            runOnUiThread { if (!isDestroyed) render() }
        }
    }

    private fun retryNow() {
        if (!TelegramConfig.configured(this)) { toast("Telegram chalu karein"); return }
        val since = TelegramConfig.sinceMs(this)
        EventStore.readAll(this).filter { TelegramRules.isFailed(it, since) }.forEach { r ->
            EventStore.update(this, r.id) { it.copy(attempts = 0, lastError = "", nextTryMs = 0) }
        }
        TelegramDispatcher.kick(this, force = true)
        toast("Bhejna shuru")
        render()
    }

    private fun forgetToken() {
        AlertDialog.Builder(this).setMessage("Saved token is phone se hata dein? Alerts band ho jayenge.")
            .setPositiveButton("Haan, hatayein") { _, _ ->
                TelegramConfig.clearToken(this)
                TelegramConfig.setEnabled(this, false)
                render()
            }.setNegativeButton("Nahi", null).show()
    }

    override fun onResume() {
        super.onResume()
        if (!Gate.isOpen(this)) { finish(); return }
        render()
    }

    private fun render() {
        val c = this
        master.isChecked = TelegramConfig.enabled(c)
        val since = TelegramConfig.sinceMs(c)
        val all = try { EventStore.readAll(c) } catch (_: Exception) { emptyList() }
        val waiting = all.count { TelegramRules.isWaiting(it, since) }
        val failed = all.count { TelegramRules.isFailed(it, since) }
        val tokenLine = if (TelegramConfig.hasToken(c)) "Token: saved (...${TelegramConfig.tokenTail(c)})" else "Token: saved nahi"
        status.text = TelegramConfig.summary(c) + "\n" + tokenLine + "\nAakhri: " + TelegramConfig.status(c)
        queue.text = "Queue me baaki: $waiting   |   Fail: $failed"
    }
}
