package com.aurax.guard

/** Daily quiet window (for example 07:00-09:00 while commuting). Pure logic, handles windows that cross midnight. */
object QuietRules {
    fun inWindow(nowMin: Int, fromMin: Int, toMin: Int): Boolean {
        if (fromMin == toMin) return false
        return if (fromMin < toMin) nowMin in fromMin until toMin else nowMin >= fromMin || nowMin < toMin
    }

    fun fmt(min: Int): String = "%02d:%02d".format((min / 60).coerceIn(0, 23), (min % 60).coerceIn(0, 59))
}
