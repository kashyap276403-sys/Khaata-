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
class LiquidBackdrop : Drawable() {
    private val base = Paint()
    private val orb = Paint(Paint.ANTI_ALIAS_FLAG)
    private var orbs: List<Triple<Float, Float, RadialGradient>> = emptyList()

    override fun onBoundsChange(b: Rect) {
        val w = b.width().toFloat()
        val h = b.height().toFloat()
        base.shader = LinearGradient(0f, 0f, 0f, h, intArrayOf(Color.rgb(4, 36, 28), Color.rgb(3, 12, 14), Color.BLACK), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        fun g(cx: Float, cy: Float, r: Float, col: Int) =
            Triple(cx, cy, RadialGradient(cx, cy, r, intArrayOf(col, Color.argb(0, Color.red(col), Color.green(col), Color.blue(col))), null, Shader.TileMode.CLAMP))
        orbs = listOf(
            g(w * 0.88f, h * 0.07f, w * 0.75f, Color.argb(95, 46, 196, 182)),
            g(w * 0.06f, h * 0.46f, w * 0.65f, Color.argb(60, 25, 211, 160)),
            g(w * 0.92f, h * 0.88f, w * 0.7f, Color.argb(55, 70, 130, 255)),
        )
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
