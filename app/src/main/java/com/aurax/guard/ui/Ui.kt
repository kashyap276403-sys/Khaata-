package com.aurax.guard.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
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
    val dim = Color.argb(185, 255, 255, 255)

    enum class Btn { PRIMARY, GHOST, DANGER }

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
        setPadding(dp(c, 16), dp(c, 14), dp(c, 16), dp(c, 14))
        background = GradientDrawable().apply {
            cornerRadius = dp(c, 22).toFloat()
            setColor(if (accent != null) Color.argb(46, Color.red(accent), Color.green(accent), Color.blue(accent)) else Color.argb(26, 255, 255, 255))
            setStroke(dp(c, 1), if (accent != null) Color.argb(120, Color.red(accent), Color.green(accent), Color.blue(accent)) else Color.argb(46, 255, 255, 255))
        }
        layoutParams = lp(c, topDp)
    }

    fun button(c: Context, label: String, style: Btn = Btn.PRIMARY, topDp: Int = 8, onClick: () -> Unit) =
        Button(c).apply {
            text = label
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                cornerRadius = dp(c, 14).toFloat()
                when (style) {
                    Btn.PRIMARY -> setColor(Color.argb(150, 25, 160, 120))
                    Btn.GHOST -> {
                        setColor(Color.argb(20, 255, 255, 255))
                        setStroke(dp(c, 1), Color.argb(70, 255, 255, 255))
                    }
                    Btn.DANGER -> setColor(Color.argb(130, 255, 90, 90))
                }
            }
            setOnClickListener { onClick() }
            layoutParams = lp(c, topDp)
        }

    fun badge(c: Context, label: String, color: Int) = TextView(c).apply {
        text = label
        textSize = 12f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(color)
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
