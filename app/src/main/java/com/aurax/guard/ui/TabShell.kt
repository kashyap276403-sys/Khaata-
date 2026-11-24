package com.aurax.guard.ui

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.aurax.guard.R

/**
 * Four tabs (Home, Guard, Cleaner, Activity). The old full settings screen is
 * kept as it was and opens from Home as "Protection settings".
 */
class TabShell(private val act: Activity, controls: View, actions: HomeActions) {
    val home = HomeTab(act, actions) { select(1) }
    private val guard = GuardTab(act)
    private val cleaner = CleanerTab(act)
    private val activity = ActivityTab(act) { actions.openQueue() }
    private val tabViews = listOf<View>(home.root, guard.root, cleaner.root, activity.root)

    private val controlsWrap = LinearLayout(act).apply {
        orientation = LinearLayout.VERTICAL
        addView(TextView(act).apply {
            text = "←  Home par wapas"
            textSize = 15f
            setTextColor(Ui.emerald)
            setPadding(Ui.dp(act, 16), Ui.dp(act, 14), Ui.dp(act, 16), Ui.dp(act, 10))
            setBackgroundColor(Color.argb(255, 4, 24, 18))
            setOnClickListener { select(0) }
        })
        addView(controls, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    private val nav = BottomNav(
        act,
        listOf("Home", "Guard", "Cleaner", "Activity"),
        listOf(R.drawable.ic_nav_home, R.drawable.ic_stat_guard, R.drawable.ic_nav_clean, R.drawable.ic_nav_activity),
    ) { select(it) }

    private val frame = FrameLayout(act).apply {
        tabViews.forEach { addView(it) }
        addView(controlsWrap)
    }

    val root = LinearLayout(act).apply {
        orientation = LinearLayout.VERTICAL
        background = if (Ui.glassOn(act)) Ui.backdrop(act) else Ui.flatGradient(act)
        addView(frame, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        addView(nav.root)
    }

    private var index = 0
    private var inControls = false

    init {
        select(0)
    }

    fun select(i: Int) {
        inControls = false
        index = i
        tabViews.forEachIndexed { j, v -> v.visibility = if (j == i) View.VISIBLE else View.GONE }
        controlsWrap.visibility = View.GONE
        nav.setSelected(i)
        refresh()
        if (i == 1) guard.onShow()
    }

    fun showControls() {
        inControls = true
        tabViews.forEach { it.visibility = View.GONE }
        controlsWrap.visibility = View.VISIBLE
    }

    /** Returns true if it handled "back". */
    fun back(): Boolean {
        if (inControls || index != 0) {
            select(0)
            return true
        }
        return false
    }

    fun refresh() {
        if (inControls) return
        when (index) {
            0 -> home.refresh()
            2 -> cleaner.refresh()
            3 -> activity.refresh()
        }
    }

    fun setSirenVisible(v: Boolean) = home.setSirenVisible(v)
}
