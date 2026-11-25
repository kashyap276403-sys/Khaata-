package com.aurax.guard.ui

import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.Gravity
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Bottom dock: a see-through BLACK glass capsule (no white). A tinted pill slides under the selected item with a
 * springy bounce (overshoot + a little stretch), and the icon pops. Uses only view property animations (GPU, no lag).
 */
class BottomNav(
    private val c: Context,
    labels: List<String>,
    icons: List<Int>,
    private val onSelect: (Int) -> Unit,
) {
    private val padH = Ui.dp(c, 8)
    private val padV = Ui.dp(c, 6)
    private val n = labels.size

    private val pill = View(c).apply { visibility = View.INVISIBLE }
    private val row = LinearLayout(c).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(padH, padV, padH, padV)
    }
    val root = FrameLayout(c).apply {
        // transparent black glass: dark see-through body, faint rim (accent-free, never white)
        background = LayerDrawable(arrayOf(
            GradientDrawable().apply {
                cornerRadius = Ui.dp(c, 36).toFloat()
                setColor(Color.argb(150, 0, 0, 0))
                setStroke(Ui.dp(c, 1), Color.argb(70, 255, 255, 255))
            },
            GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Color.argb(40, 0, 0, 0), Color.argb(0, 0, 0, 0)))
                .apply { cornerRadius = Ui.dp(c, 36).toFloat() },
        ))
        addView(pill)
        addView(row, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
    }
    private val items = mutableListOf<Triple<LinearLayout, ImageView, TextView>>()
    private var index = 0
    private var placed = false

    init {
        labels.forEachIndexed { i, label ->
            val icon = ImageView(c).apply {
                setImageResource(icons[i])
                layoutParams = LinearLayout.LayoutParams(Ui.dp(c, 26), Ui.dp(c, 26))
            }
            val tv = TextView(c).apply {
                text = label
                textSize = 11f
                gravity = Gravity.CENTER
            }
            val box = LinearLayout(c).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(0, Ui.dp(c, 7), 0, Ui.dp(c, 7))
                addView(icon)
                addView(tv)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener { onSelect(i) }
            }
            items.add(Triple(box, icon, tv))
            row.addView(box)
        }
        pill.background = GradientDrawable().apply {
            cornerRadius = Ui.dp(c, 28).toFloat()
            // accent-tinted, see-through: no white
            setColor((Ui.emerald and 0x00FFFFFF) or (0x44 shl 24))
            setStroke(Ui.dp(c, 1), (Ui.emerald and 0x00FFFFFF) or (0x99 shl 24))
        }
        root.addOnLayoutChangeListener { _, l, t, r, b, ol, ot, orr, ob ->
            if (r - l != orr - ol || b - t != ob - ot || !placed) place(animate = false)
        }
        setSelected(0, animate = false)
    }

    private fun place(animate: Boolean) {
        val w = root.width
        val h = root.height
        if (w <= 0 || h <= 0) return
        val itemW = (w - 2 * padH) / n
        val lp = (pill.layoutParams as FrameLayout.LayoutParams)
        if (lp.width != itemW || lp.height != h - 2 * padV) {
            lp.width = itemW; lp.height = h - 2 * padV; lp.topMargin = padV
            pill.layoutParams = lp
        }
        val target = (padH + index * itemW).toFloat()
        pill.visibility = View.VISIBLE
        if (!animate || !placed) {
            pill.animate().cancel(); pill.translationX = target; pill.scaleX = 1f; pill.scaleY = 1f
        } else {
            pill.animate().cancel()
            pill.scaleX = 1.22f; pill.scaleY = 0.92f // stretched like a water drop, then springs back
            pill.animate().translationX(target).scaleX(1f).scaleY(1f).setDuration(480)
                .setInterpolator(OvershootInterpolator(2.2f)).start()
        }
        placed = true
    }

    fun setSelected(index: Int, animate: Boolean = true) {
        val changed = index != this.index
        this.index = index
        items.forEachIndexed { i, (box, icon, tv) ->
            val on = i == index
            val color = if (on) Ui.emerald else Ui.dim
            icon.setColorFilter(color, PorterDuff.Mode.SRC_IN)
            tv.setTextColor(color)
            box.background = null
            icon.animate().cancel()
            if (on && changed && animate) {
                icon.scaleX = 0.7f; icon.scaleY = 0.7f
                icon.animate().scaleX(1.12f).scaleY(1.12f).setDuration(420).setInterpolator(OvershootInterpolator(3.2f)).start()
            } else {
                icon.scaleX = if (on) 1.12f else 1f; icon.scaleY = icon.scaleX
            }
        }
        place(animate && changed)
    }
}
