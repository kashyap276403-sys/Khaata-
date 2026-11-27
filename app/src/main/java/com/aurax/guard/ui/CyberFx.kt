package com.aurax.guard.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.drawable.Drawable

/**
 * Cyber / Hacker background: near-black green-tinted gradient with a faint static grid.
 * Nothing moves: it is drawn only when the screen repaints, so it costs almost nothing.
 */
class CyberBackdrop(private val c: Context) : Drawable() {
    private val d = c.resources.displayMetrics.density
    private val bg = Paint()
    private val grid = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = Color.argb(16, 0, 255, 153); strokeWidth = d * 0.6f }
    private val path = Path()

    override fun onBoundsChange(b: Rect) {
        bg.shader = LinearGradient(0f, 0f, 0f, b.height().toFloat(), intArrayOf(Color.rgb(0, 18, 11), Color.rgb(0, 7, 5), Color.BLACK), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        path.reset()
        val step = 34 * d
        var x = 0f
        while (x <= b.width()) { path.moveTo(x, 0f); path.lineTo(x, b.height().toFloat()); x += step }
        var y = 0f
        while (y <= b.height()) { path.moveTo(0f, y); path.lineTo(b.width().toFloat(), y); y += step }
    }

    override fun draw(canvas: Canvas) {
        canvas.drawRect(bounds, bg)
        canvas.drawPath(path, grid)
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(cf: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.OPAQUE
}
