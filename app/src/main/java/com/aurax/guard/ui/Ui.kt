package com.aurax.guard.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import java.util.concurrent.atomic.AtomicInteger
import android.app.ActivityManager
import android.app.AlertDialog
import android.app.Dialog
import android.os.Build
import android.view.WindowManager
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView

/** Small view kit so every tab looks the same: dark, calm, rounded. */
object Ui {
    val emerald = Color.rgb(25, 211, 160)
    val teal = Color.rgb(46, 196, 182)
    val amber = Color.rgb(255, 200, 87)
    val red = Color.rgb(255, 107, 107)
    val dim = Color.argb(205, 255, 255, 255)

    enum class Btn { PRIMARY, GHOST, DANGER }

    // ---------- Liquid glass ----------
    /** Glass look can be switched off in "AI & Look" and is off by default on low-RAM phones (smooth, light fallback). */
    fun glassOn(c: Context): Boolean = try {
        val p = c.getSharedPreferences("look", Context.MODE_PRIVATE)
        if (p.contains("glass")) p.getBoolean("glass", true)
        else !c.getSystemService(ActivityManager::class.java).isLowRamDevice
    } catch (_: Exception) {
        true
    }

    fun setGlass(c: Context, on: Boolean) = c.getSharedPreferences("look", Context.MODE_PRIVATE).edit().putBoolean("glass", on).apply()

    /**
     * Two thin layers, no blur and no shaders, so it costs almost nothing:
     * a bright-to-faint hairline ring, and a frosted body that is lighter at the top (light catching the glass).
     * [tint] adds a soft colour glow. Flat fallback = the old look.
     */
    private val dropSeed = AtomicInteger(7)

    /** Soft pressed-water ripple on top of [content]. Uses Android's own ripple (no custom animation). Flat fallback = content. */
    fun ripple(c: Context, radiusDp: Int, content: android.graphics.drawable.Drawable): android.graphics.drawable.Drawable =
        if (!glassOn(c)) content else try {
            RippleDrawable(
                ColorStateList.valueOf(Color.argb(80, 220, 255, 245)), content,
                GradientDrawable().apply { cornerRadius = dp(c, radiusDp).toFloat(); setColor(Color.WHITE) },
            )
        } catch (_: Exception) {
            content
        }

    /** Window/screen background: soft colour glows behind the glass, or plain black when glass is off. */
    fun backdrop(c: Context): android.graphics.drawable.Drawable =
        if (glassOn(c)) LiquidBackdrop() else android.graphics.drawable.ColorDrawable(Color.BLACK)

    fun glass(c: Context, radiusDp: Int, tint: Int? = null, strong: Boolean = false, drops: Int = 0): android.graphics.drawable.Drawable {
        val r = dp(c, radiusDp).toFloat()
        val tr = if (tint != null) Color.red(tint) else 255
        val tg = if (tint != null) Color.green(tint) else 255
        val tb = if (tint != null) Color.blue(tint) else 255
        if (!glassOn(c)) {
            return GradientDrawable().apply {
                cornerRadius = r
                setColor(if (tint != null) Color.argb(46, tr, tg, tb) else Color.argb(26, 255, 255, 255))
                setStroke(dp(c, 1), if (tint != null) Color.argb(120, tr, tg, tb) else Color.argb(46, 255, 255, 255))
            }
        }
        val ring = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(
            Color.argb(if (tint != null) 170 else 110, tr, tg, tb), Color.argb(26, 255, 255, 255), Color.argb(if (tint != null) 90 else 55, tr, tg, tb),
        )).apply { cornerRadius = r }
        val topA = if (strong) 64 else 44
        val body = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(
            Color.argb(topA, 255, 255, 255), Color.argb(if (tint != null) 30 else 16, tr, tg, tb), Color.argb(10, 255, 255, 255),
        )).apply { cornerRadius = (r - dp(c, 1)).coerceAtLeast(0f) }
        val layers = if (drops > 0) arrayOf<android.graphics.drawable.Drawable>(ring, body, DropsDrawable(c, dropSeed.addAndGet(13).toLong(), drops))
        else arrayOf<android.graphics.drawable.Drawable>(ring, body)
        return LayerDrawable(layers).apply { setLayerInset(1, dp(c, 1), dp(c, 1), dp(c, 1), dp(c, 1)) }
    }

    /** Shows a dialog with a frosted look. Blur-behind only where Android allows it (12+), harmless elsewhere. */
    fun showGlass(b: AlertDialog.Builder): AlertDialog {
        val d = b.create()
        d.setOnShowListener { styleDialog(d) }
        d.show()
        return d
    }

    fun styleDialog(d: Dialog) {
        try {
            val c = d.context
            val w = d.window ?: return
            val base = GradientDrawable().apply {
                cornerRadius = dp(c, 24).toFloat()
                setColor(Color.argb(if (glassOn(c)) 214 else 245, 10, 26, 22))
            }
            w.setBackgroundDrawable(LayerDrawable(arrayOf(base, glass(c, 24, strong = true))))
            if (glassOn(c) && Build.VERSION.SDK_INT >= 31) {
                w.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                w.attributes = w.attributes.apply { blurBehindRadius = 24 }
            }
            (d as? AlertDialog)?.let {
                for (id in intArrayOf(android.R.id.button1, android.R.id.button2, android.R.id.button3)) it.findViewById<android.widget.Button>(id)?.setTextColor(emerald)
                it.findViewById<TextView>(android.R.id.message)?.setTextColor(Color.WHITE)
            }
            val tid = c.resources.getIdentifier("alertTitle", "id", "android")
            if (tid != 0) d.findViewById<TextView>(tid)?.setTextColor(Color.WHITE)
        } catch (_: Exception) {
            // keep the default dialog
        }
    }

    /** Low / Medium / High pill from the local smart engine (-1 = not assessed). */
    fun riskBadge(c: Context, risk: Int) = when (risk) {
        0 -> badge(c, "Risk: Low", emerald)
        1 -> badge(c, "Risk: Medium", amber)
        2 -> badge(c, "Risk: High", red)
        else -> null
    }

    /** Sent / Queued / Offline / Error pill for one alert. */
    fun statusBadge(c: Context, b: com.aurax.guard.telegram.Badge) = badge(c, b.label, when (b.kind) {
        com.aurax.guard.telegram.BadgeKind.SENT -> emerald
        com.aurax.guard.telegram.BadgeKind.QUEUED -> amber
        com.aurax.guard.telegram.BadgeKind.OFFLINE -> Color.rgb(140, 180, 255)
        com.aurax.guard.telegram.BadgeKind.ERROR -> red
        com.aurax.guard.telegram.BadgeKind.LOCAL -> Color.argb(200, 255, 255, 255)
    })

    fun dp(c: Context, v: Int) = (v * c.resources.displayMetrics.density).toInt()

    fun lp(c: Context, topDp: Int = 0, weight: Float = 0f): LinearLayout.LayoutParams =
        if (weight > 0f) {
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight).apply { topMargin = dp(c, topDp) }
        } else {
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(c, topDp) }
        }

    fun text(c: Context, s: String, size: Float = 14f, bold: Boolean = false, color: Int = Color.WHITE) =
        TextView(c).apply {
            text = s
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(c, 3), 0, dp(c, 3))
        }

    fun dimText(c: Context, s: String, size: Float = 13f) = text(c, s, size, color = dim)

    fun heading(c: Context, s: String) = text(c, s, 24f, bold = true).apply { setPadding(0, dp(c, 4), 0, dp(c, 2)) }

    fun card(c: Context, accent: Int? = null, topDp: Int = 12): LinearLayout = LinearLayout(c).apply {
        orientation = LinearLayout.VERTICAL
        val pad = if (c.resources.configuration.screenWidthDp < 360) 12 else 16
        setPadding(dp(c, pad), dp(c, 14), dp(c, pad), dp(c, 14))
        background = glass(c, 22, accent, drops = if (accent != null) 3 else 2)
        layoutParams = lp(c, topDp)
    }

    fun button(c: Context, label: String, style: Btn = Btn.PRIMARY, topDp: Int = 8, onClick: () -> Unit) =
        Button(c).apply {
            text = label
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = ripple(c, 14, GradientDrawable().apply {
                cornerRadius = dp(c, 14).toFloat()
                when (style) {
                    Btn.PRIMARY -> {
                        setColor(Color.argb(150, 25, 160, 120))
                        setStroke(dp(c, 1), Color.argb(140, 120, 255, 214))
                    }
                    Btn.GHOST -> {
                        setColor(Color.argb(20, 255, 255, 255))
                        setStroke(dp(c, 1), Color.argb(70, 255, 255, 255))
                    }
                    Btn.DANGER -> setColor(Color.argb(130, 255, 90, 90))
                }
            })
            setOnClickListener { onClick() }
            layoutParams = lp(c, topDp)
        }

    fun badge(c: Context, label: String, color: Int) = TextView(c).apply {
        text = label
        textSize = 12f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(color)
        maxLines = 1
        setPadding(dp(c, 10), dp(c, 3), dp(c, 10), dp(c, 3))
        background = GradientDrawable().apply {
            cornerRadius = dp(c, 12).toFloat()
            setColor(Color.argb(45, Color.red(color), Color.green(color), Color.blue(color)))
            setStroke(dp(c, 1), Color.argb(130, Color.red(color), Color.green(color), Color.blue(color)))
        }
    }

    fun chip(c: Context, label: String, selected: Boolean, onClick: () -> Unit) = TextView(c).apply {
        text = label
        textSize = 13f
        setTextColor(if (selected) Color.WHITE else dim)
        if (selected) setTypeface(typeface, Typeface.BOLD)
        setPadding(dp(c, 14), dp(c, 8), dp(c, 14), dp(c, 8))
        background = GradientDrawable().apply {
            cornerRadius = dp(c, 18).toFloat()
            setColor(if (selected) Color.argb(140, 25, 160, 120) else Color.argb(20, 255, 255, 255))
            setStroke(dp(c, 1), if (selected) Color.argb(200, 25, 211, 160) else Color.argb(50, 255, 255, 255))
        }
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { rightMargin = dp(c, 8) }
        setOnClickListener { onClick() }
    }

    fun chipRow(c: Context, chips: List<View>, topDp: Int = 10): HorizontalScrollView =
        HorizontalScrollView(c).apply {
            isHorizontalScrollBarEnabled = false
            addView(LinearLayout(c).apply {
                orientation = LinearLayout.HORIZONTAL
                chips.forEach { addView(it) }
            })
            layoutParams = lp(c, topDp)
        }

    fun row(c: Context, topDp: Int = 0) = LinearLayout(c).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = lp(c, topDp)
    }
}
