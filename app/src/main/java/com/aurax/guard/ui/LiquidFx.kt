package com.aurax.guard.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.drawable.Drawable
import java.util.Random

// Plain-Int colour helpers so the palettes work in unit tests (android.graphics.Color is not available there).
private const val BLACK_I = 0xFF000000.toInt()
private fun argbI(a: Int, r: Int, g: Int, b: Int) = (a shl 24) or (r shl 16) or (g shl 8) or b
private fun rgbI(r: Int, g: Int, b: Int) = argbI(255, r, g, b)

/** Accent colour of the app (buttons, selected chips, switches, badges). [glowColors] = several colours for the multi-colour Aurora look. */
data class Accent(val key: String, val title: String, val main: Int, val deep: Int, val rim: Int, val glowColors: List<Int>? = null) {
    companion object {
        val EMERALD = Accent("emerald", "Emerald", rgbI(25, 211, 160), rgbI(25, 160, 120), rgbI(120, 255, 214))
        val OCEAN = Accent("ocean", "Ocean", rgbI(64, 156, 255), rgbI(30, 100, 200), rgbI(150, 205, 255))
        val VIOLET = Accent("violet", "Violet", rgbI(150, 120, 255), rgbI(100, 70, 205), rgbI(200, 185, 255))
        val SUNSET = Accent("sunset", "Sunset", rgbI(255, 150, 70), rgbI(205, 100, 35), rgbI(255, 200, 150))
        val ROSE = Accent("rose", "Rose", rgbI(255, 100, 150), rgbI(205, 55, 105), rgbI(255, 170, 200))
        val AURORA = Accent("aurora", "Aurora (multi-colour)", rgbI(70, 205, 230), rgbI(60, 120, 220), rgbI(180, 220, 255),
            listOf(rgbI(255, 105, 180), rgbI(140, 110, 255), rgbI(46, 196, 182), rgbI(70, 130, 255)))
        val ALL = listOf(EMERALD, OCEAN, VIOLET, SUNSET, ROSE, AURORA)
        fun of(key: String?) = ALL.firstOrNull { it.key == key } ?: EMERALD
    }
}

private fun withAlphaI(color: Int, a: Int) = (a shl 24) or (color and 0x00FFFFFF)

/** A glow behind the glass: centre and size as fractions of the screen width/height. */
data class Glow(val xFrac: Float, val yFrac: Float, val rFrac: Float, val argb: Int)

/** Colours and glass strength of one dark theme. Pure values, so they can be unit tested. */
data class ThemePalette(
    val key: String,
    val title: String,
    val baseTop: Int,
    val baseMid: Int,
    val baseBottom: Int,
    val glows: List<Glow>,
    val glassTopAlpha: Int,
    val glassStrongAlpha: Int,
    val ringScale: Float,
    val dialogBase: Int,
    val navBg: Int,
) {
    companion object {
        val DARK = ThemePalette(
            "dark", "Dark", rgbI(4, 36, 28), rgbI(3, 12, 14), BLACK_I,
            listOf(Glow(0.88f, 0.07f, 0.75f, argbI(95, 46, 196, 182)), Glow(0.06f, 0.46f, 0.65f, argbI(60, 25, 211, 160)), Glow(0.92f, 0.88f, 0.70f, argbI(55, 70, 130, 255))),
            44, 64, 1f, argbI(214, 10, 26, 22), argbI(235, 6, 14, 12),
        )
        val MIDNIGHT = ThemePalette(
            "midnight", "Midnight", rgbI(10, 12, 38), rgbI(5, 6, 20), BLACK_I,
            listOf(Glow(0.88f, 0.07f, 0.78f, argbI(95, 112, 92, 255)), Glow(0.06f, 0.46f, 0.65f, argbI(60, 60, 120, 255)), Glow(0.92f, 0.88f, 0.70f, argbI(48, 46, 196, 182))),
            42, 60, 1f, argbI(220, 10, 12, 30), argbI(238, 6, 8, 22),
        )
        /** Pure black for OLED screens: almost no glow and dimmer glass, so most pixels stay off. */
        val AMOLED = ThemePalette(
            "amoled", "AMOLED black", BLACK_I, BLACK_I, BLACK_I,
            listOf(Glow(0.90f, 0.05f, 0.6f, argbI(34, 46, 196, 182)), Glow(0.05f, 0.9f, 0.5f, argbI(16, 70, 130, 255))),
            28, 40, 0.7f, argbI(248, 0, 0, 0), argbI(255, 0, 0, 0),
        )
        val ALL = listOf(DARK, MIDNIGHT, AMOLED)
        fun of(key: String?) = ALL.firstOrNull { it.key == key } ?: DARK
    }

    /** Same theme, coloured by the chosen accent. Alpha (strength) of every glow is kept; AMOLED stays pure black. */
    fun withAccent(a: Accent): ThemePalette {
        val g = if (a.glowColors != null) {
            glows.mapIndexed { i, gl -> gl.copy(argb = withAlphaI(a.glowColors[i % a.glowColors.size], gl.argb ushr 24)) }
        } else {
            glows.mapIndexed { i, gl -> if (i == 0) gl.copy(argb = withAlphaI(a.main, gl.argb ushr 24)) else gl }
        }
        // the plain Dark theme takes its top colour from the accent (14% of it), the others keep their own base
        val top = if (key == "dark") {
            val m = a.glowColors?.get(0) ?: a.main
            rgbI(((m shr 16) and 255) * 14 / 100, ((m shr 8) and 255) * 14 / 100, (m and 255) * 14 / 100)
        } else baseTop
        return copy(glows = g, baseTop = top)
    }
}

/** One water drop: position as a fraction of the card, radius in dp. */
data class Drop(val xFrac: Float, val yFrac: Float, val rDp: Float)

/** Decides where drops sit. Pure and repeatable for the same seed, so it can be unit tested. */
object DropLayout {
    fun generate(seed: Long, count: Int): List<Drop> {
        val rnd = Random(seed)
        val out = mutableListOf<Drop>()
        var tries = 0
        while (out.size < count && tries < 60) {
            tries++
            val d = Drop(0.66f + rnd.nextFloat() * 0.29f, 0.14f + rnd.nextFloat() * 0.72f, 2.2f + rnd.nextFloat() * 3.4f)
            // keep drops apart so they look like separate beads, and away from the left side where text sits
            if (out.none { Math.hypot((it.xFrac - d.xFrac).toDouble(), (it.yFrac - d.yFrac).toDouble()) < 0.11 }) out.add(d)
        }
        return out
    }
}

/**
 * A few tiny water beads on the glass: soft shadow, clear body, bright rim and a highlight spot.
 * Drawn once per frame without blur or animation, so it costs almost nothing.
 */
class DropsDrawable(private val c: Context, seed: Long, count: Int) : Drawable() {
    private val drops = DropLayout.generate(seed, count)
    private val density = c.resources.displayMetrics.density
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(46, 0, 0, 0) }
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Color.argb(80, 255, 255, 255); strokeWidth = density * 0.8f }
    private val spec = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(215, 255, 255, 255) }
    private val body = Paint(Paint.ANTI_ALIAS_FLAG)
    private var shaders: List<RadialGradient> = emptyList()

    override fun onBoundsChange(b: Rect) {
        shaders = drops.map { d ->
            val r = d.rDp * density
            val cx = b.left + d.xFrac * b.width()
            val cy = b.top + d.yFrac * b.height()
            RadialGradient(cx - r * 0.3f, cy - r * 0.35f, r * 1.25f,
                intArrayOf(Color.argb(150, 255, 255, 255), Color.argb(46, 200, 255, 245), Color.argb(22, 120, 230, 210)),
                floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
        }
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.width() < 110 * density || shaders.size != drops.size) return
        drops.forEachIndexed { i, d ->
            val r = d.rDp * density
            val cx = b.left + d.xFrac * b.width()
            val cy = b.top + d.yFrac * b.height()
            canvas.drawCircle(cx + r * 0.25f, cy + r * 0.5f, r, shadow)
            body.shader = shaders[i]
            canvas.drawCircle(cx, cy, r, body)
            canvas.drawCircle(cx, cy, r, rim)
            canvas.drawCircle(cx - r * 0.38f, cy - r * 0.4f, r * 0.2f, spec)
        }
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(cf: ColorFilter?) {}
    @Deprecated("Required by Drawable")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}

/** Dark base with three soft colour glows behind everything, so the glass has something to refract. Static. */
class LiquidBackdrop(private val pal: ThemePalette = ThemePalette.DARK) : Drawable() {
    private val base = Paint()
    private val orb = Paint(Paint.ANTI_ALIAS_FLAG)
    private var orbs: List<Triple<Float, Float, RadialGradient>> = emptyList()

    override fun onBoundsChange(b: Rect) {
        val w = b.width().toFloat()
        val h = b.height().toFloat()
        base.shader = LinearGradient(0f, 0f, 0f, h, intArrayOf(pal.baseTop, pal.baseMid, pal.baseBottom), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        fun g(cx: Float, cy: Float, r: Float, col: Int) =
            Triple(cx, cy, RadialGradient(cx, cy, r, intArrayOf(col, Color.argb(0, Color.red(col), Color.green(col), Color.blue(col))), null, Shader.TileMode.CLAMP))
        orbs = pal.glows.map { g(w * it.xFrac, h * it.yFrac, w * it.rFrac, it.argb) }
    }

    override fun draw(canvas: Canvas) {
        canvas.drawRect(bounds, base)
        orbs.forEach { (_, _, sh) -> orb.shader = sh; canvas.drawRect(bounds, orb) }
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(cf: ColorFilter?) {}
    @Deprecated("Required by Drawable")
    override fun getOpacity() = PixelFormat.OPAQUE
}
