package com.aurax.guard.ui

import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** Four-item bottom bar with a soft pill behind the selected item. */
class BottomNav(
    private val c: Context,
    labels: List<String>,
    icons: List<Int>,
    private val onSelect: (Int) -> Unit,
) {
    val root = LinearLayout(c).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(Ui.dp(c, 8), Ui.dp(c, 8), Ui.dp(c, 8), Ui.dp(c, 8))
        setBackgroundColor(Color.argb(235, 6, 14, 12))
    }
    private val items = mutableListOf<Triple<LinearLayout, ImageView, TextView>>()

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
                setPadding(0, Ui.dp(c, 6), 0, Ui.dp(c, 6))
                addView(icon)
                addView(tv)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener { onSelect(i) }
            }
            items.add(Triple(box, icon, tv))
            root.addView(box)
        }
        setSelected(0)
    }

    fun setSelected(index: Int) {
        items.forEachIndexed { i, (box, icon, tv) ->
            val on = i == index
            val color = if (on) Ui.emerald else Ui.dim
            icon.setColorFilter(color, PorterDuff.Mode.SRC_IN)
            tv.setTextColor(color)
            box.background = if (on) GradientDrawable().apply {
                cornerRadius = Ui.dp(c, 16).toFloat()
                setColor(Color.argb(45, 25, 211, 160))
            } else null
        }
    }
}
