package com.aurax.guard.ui

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Environment
import android.os.StatFs
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import com.aurax.guard.cleaner.SizeFormat
import com.aurax.guard.permissions.SettingsLinks

/**
 * Cleaner (step 1): real storage bar and safe shortcuts. The scan/preview/confirm
 * cleaner comes in a later step. This screen never deletes anything.
 */
class CleanerTab(private val act: Activity) {
    val root = ScrollView(act).apply { isFillViewport = true }
    private val body = LinearLayout(act).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(Ui.dp(act, 16), Ui.dp(act, 24), Ui.dp(act, 16), Ui.dp(act, 24))
    }

    init {
        root.addView(body)
        refresh()
    }

    fun refresh() {
        val c = act
        body.removeAllViews()
        body.addView(Ui.heading(c, "Cleaner"))
        body.addView(Ui.dimText(c, "Phone ki jagah aur safai"))

        val card = Ui.card(c, null, 14)
        try {
            val st = StatFs(Environment.getDataDirectory().path)
            val total = st.totalBytes
            val free = st.availableBytes
            val used = total - free
            val pct = if (total > 0) (used * 100 / total).toInt() else 0
            val color = if (pct >= 85) Ui.amber else Ui.emerald
            card.addView(Ui.text(c, "Storage $pct% bhara hai", 17f, bold = true))
            card.addView(Ui.dimText(c, "${SizeFormat.bytes(used)} istemal, ${SizeFormat.bytes(free)} khaali, kul ${SizeFormat.bytes(total)}"))
            val bar = LinearLayout(c).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = Ui.lp(c, 12).apply { height = Ui.dp(c, 14) }
                background = GradientDrawable().apply {
                    cornerRadius = Ui.dp(c, 7).toFloat()
                    setColor(Color.argb(40, 255, 255, 255))
                }
                clipToOutline = true
                addView(View(c).apply {
                    setBackgroundColor(color)
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, pct.coerceIn(1, 100).toFloat())
                })
                addView(View(c).apply {
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, (100 - pct).coerceAtLeast(0).toFloat())
                })
            }
            card.addView(bar)
        } catch (e: Exception) {
            card.addView(Ui.dimText(c, "Storage ki jaankari nahi mili."))
        }
        body.addView(card)

        val safe = Ui.card(c, Ui.emerald, 12)
        safe.addView(Ui.text(c, "Zero auto-delete", 17f, bold = true))
        safe.addView(Ui.dimText(c, "Ye app kuch bhi apne aap nahi mitati. Safai ka tareeka: Scan, phir preview (naam, size, wajah), phir aap chunte ho, phir Confirm."))
        safe.addView(Ui.button(c, "Junk scan (agle step me aa raha hai)") {}.apply {
            isEnabled = false
            alpha = 0.5f
        })
        body.addView(safe)

        val cache = Ui.card(c, null, 12)
        cache.addView(Ui.text(c, "App cache aur storage", 17f, bold = true))
        cache.addView(Ui.dimText(c, "Android doosri apps ka cache chup-chaap saaf karne nahi deta. Neeche ke button seedha system screen kholte hain, wahan aap khud chun kar saaf kar sakte ho."))
        cache.addView(Ui.button(c, "Phone storage screen", Ui.Btn.GHOST) {
            SettingsLinks.open(c, SettingsLinks.internalStorage(), SettingsLinks.allApps())
        })
        cache.addView(Ui.button(c, "Apps ki storage aur cache", Ui.Btn.GHOST) {
            SettingsLinks.open(c, SettingsLinks.allApps())
        })
        body.addView(cache)
    }
}
