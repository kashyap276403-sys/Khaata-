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
    @Volatile var accent: Accent = Accent.EMERALD
    private var accentLoaded = false
    /** The accent colour (name kept as 'emerald' so every existing screen follows the chosen accent). */
    val emerald: Int get() = accent.main

    fun loadAccent(c: Context) {
        try {
            accent = Accent.of(c.getSharedPreferences("look", Context.MODE_PRIVATE).getString("accent", "emerald"))
        } catch (_: Exception) {
        }
        accentLoaded = true
    }

    fun setAccent(c: Context, key: String) {
        c.getSharedPreferences("look", Context.MODE_PRIVATE).edit().putString("accent", Accent.of(key).key).apply()
        accent = Accent.of(key)
    }

    /** Keeps the scroll position while a tab rebuilds its content (no jump to the top). */
    inline fun keepScroll(body: View, block: () -> Unit) {
        var p: android.view.ViewParent? = body.parent
        while (p != null && p !is android.widget.ScrollView) p = p.parent
        val sv = p as? android.widget.ScrollView
        val y = sv?.scrollY ?: 0
        block()
        if (sv != null && y > 0) sv.post { sv.scrollTo(0, y) }
    }
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

    fun themeKey(c: Context): String =
        try { c.getSharedPreferences("look", Context.MODE_PRIVATE).getString("theme", "dark") ?: "dark" } catch (_: Exception) { "dark" }

    fun setTheme(c: Context, key: String) = c.getSharedPreferences("look", Context.MODE_PRIVATE).edit().putString("theme", ThemePalette.of(key).key).apply()
    fun palette(c: Context) = ThemePalette.of(themeKey(c))

    /** Dark gradient used where a flat (non-glass) background is needed. */
    fun flatGradient(c: Context): GradientDrawable {
        if (!accentLoaded) loadAccent(c)
        val p = palette(c).withAccent(accent)
        return GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(p.baseTop, p.baseMid, p.baseBottom))
    }

    /** Glass clarity: 0 = Clear (almost fully transparent), 1 = Soft, 2 = Frosted. Default Clear. */
    fun clarity(c: Context): Int = try { c.getSharedPreferences("look", Context.MODE_PRIVATE).getInt("clarity", 0).coerceIn(0, 2) } catch (_: Exception) { 0 }
    fun setClarity(c: Context, v: Int) = c.getSharedPreferences("look", Context.MODE_PRIVATE).edit().putInt("clarity", v.coerceIn(0, 2)).apply()

    /** Water drops/bubbles on glass: OFF by default, only if the person turns them on. */
    fun dropsOn(c: Context): Boolean = try { c.getSharedPreferences("look", Context.MODE_PRIVATE).getBoolean("drops", false) } catch (_: Exception) { false }
    fun setDropsOn(c: Context, v: Boolean) = c.getSharedPreferences("look", Context.MODE_PRIVATE).edit().putBoolean("drops", v).apply()

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
    fun backdrop(c: Context): android.graphics.drawable.Drawable {
        if (!accentLoaded) loadAccent(c)
        return if (glassOn(c)) LiquidBackdrop(palette(c).withAccent(accent)) else android.graphics.drawable.ColorDrawable(Color.BLACK)
    }

    fun glass(c: Context, radiusDp: Int, tint: Int? = null, strong: Boolean = false, drops: Int = 0): android.graphics.drawable.Drawable {
        if (!accentLoaded) loadAccent(c)
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
        val pal = palette(c)
        val rs = pal.ringScale
        val ring = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(
            Color.argb(((if (tint != null) 190 else 150) * rs).toInt(), tr, tg, tb), Color.argb((18 * rs).toInt(), 255, 255, 255), Color.argb(((if (tint != null) 110 else 80) * rs).toInt(), tr, tg, tb),
        )).apply { cornerRadius = r }
        val k = when (clarity(c)) { 0 -> 0.30f; 1 -> 0.65f; else -> 1f } // body opacity: Clear is see-through
        val topA = ((if (strong) pal.glassStrongAlpha else pal.glassTopAlpha) * k).toInt()
        val body = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(
            Color.argb(topA, 255, 255, 255), Color.argb(((if (tint != null) 30 else 16) * k).toInt(), tr, tg, tb), Color.argb((10 * k).toInt(), 255, 255, 255),
        )).apply { cornerRadius = (r - dp(c, 1)).coerceAtLeast(0f) }
        // thin gloss along the top edge, like light sliding over curved glass
        val sheen = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(
            Color.argb(((if (strong) 54 else 38) * rs).toInt(), 255, 255, 255), Color.argb(0, 255, 255, 255),
        )).apply { cornerRadius = (r - dp(c, 4)).coerceAtLeast(0f) }
        val layers = ArrayList<android.graphics.drawable.Drawable>()
        layers.add(ring); layers.add(body); layers.add(sheen)
        if (drops > 0 && dropsOn(c)) layers.add(DropsDrawable(c, dropSeed.addAndGet(13).toLong(), drops))
        return LayerDrawable(layers.toTypedArray()).apply {
            setLayerInset(1, dp(c, 1), dp(c, 1), dp(c, 1), dp(c, 1))
            setLayerInset(2, dp(c, 6), dp(c, 3), dp(c, 6), 0)
            setLayerGravity(2, android.view.Gravity.TOP or android.view.Gravity.FILL_HORIZONTAL)
            setLayerHeight(2, dp(c, 26))
        }
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
                cornerRadius = dp(c, 30).toFloat()
                setColor(if (glassOn(c)) palette(c).dialogBase else Color.argb(245, 10, 26, 22))
            }
            w.setBackgroundDrawable(LayerDrawable(arrayOf(base, glass(c, 30, strong = true))))
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
    fun riskBadge(c: Context, risk: Int, threat: Int = -1): TextView? {
        val t = if (threat >= 0) " $threat/100" else ""
        return when (risk) {
        0 -> badge(c, "Risk: Low$t", emerald)
        1 -> badge(c, "Risk: Medium$t", amber)
        2 -> badge(c, "Risk: High$t", red)
        else -> null
        }
    }

    /** Sent / Queued / Offline / Error pill for one alert. */
    fun statusBadge(c: Context, b: com.aurax.guard.telegram.Badge) = badge(c, b.label, when (b.kind) {
        com.aurax.guard.telegram.BadgeKind.SENT -> emerald
        com.aurax.guard.telegram.BadgeKind.QUEUED -> amber
        com.aurax.guard.telegram.BadgeKind.OFFLINE -> Color.rgb(140, 180, 255)
        com.aurax.guard.telegram.BadgeKind.ERROR -> red
        com.aurax.guard.telegram.BadgeKind.LOCAL -> Color.argb(200, 255, 255, 255)
    })

    /** iOS-style switch: bright thumb, emerald glass track when on, faint glass track when off. */
    fun tintSwitch(sw: android.widget.Switch): android.widget.Switch {
        try {
            val on = intArrayOf(android.R.attr.state_checked)
            val off = intArrayOf(-android.R.attr.state_checked)
            sw.thumbTintList = ColorStateList(arrayOf(on, off), intArrayOf(Color.WHITE, Color.argb(235, 220, 230, 228)))
            sw.trackTintList = ColorStateList(arrayOf(on, off), intArrayOf((210 shl 24) or (accent.main and 0xFFFFFF), Color.argb(70, 255, 255, 255)))
        } catch (_: Exception) {
        }
        return sw
    }

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

    /** Large title in the iOS / OriginOS 7 style. */
    fun heading(c: Context, s: String) = text(c, s, 30f, bold = true).apply {
        letterSpacing = -0.02f
        setPadding(dp(c, 2), dp(c, 8), 0, dp(c, 4))
    }

    fun card(c: Context, accent: Int? = null, topDp: Int = 12, drops: Int = -1): LinearLayout = LinearLayout(c).apply {
        orientation = LinearLayout.VERTICAL
        val pad = if (c.resources.configuration.screenWidthDp < 360) 12 else 16
        setPadding(dp(c, pad), dp(c, 14), dp(c, pad), dp(c, 14))
        background = glass(c, 28, accent, drops = if (drops >= 0) drops else if (accent != null) 3 else 2)
        layoutParams = lp(c, topDp)
    }

    fun button(c: Context, label: String, style: Btn = Btn.PRIMARY, topDp: Int = 8, onClick: () -> Unit) =
        Button(c).apply {
            text = label
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = ripple(c, 28, GradientDrawable().apply {
                cornerRadius = dp(c, 28).toFloat() // pill: Android caps this at half the height
                when (style) {
                    Btn.PRIMARY -> {
                        setColor((150 shl 24) or (accent.deep and 0xFFFFFF))
                        setStroke(dp(c, 1), (140 shl 24) or (accent.rim and 0xFFFFFF))
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
            cornerRadius = dp(c, 40).toFloat()
            setColor(if (selected) (140 shl 24) or (accent.deep and 0xFFFFFF) else Color.argb(20, 255, 255, 255))
            setStroke(dp(c, 1), if (selected) (200 shl 24) or (accent.main and 0xFFFFFF) else Color.argb(50, 255, 255, 255))
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
