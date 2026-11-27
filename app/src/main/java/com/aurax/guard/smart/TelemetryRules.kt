package com.aurax.guard.smart

enum class Tag(val label: String) { CMD("CMD"), COPY("COPY"), TAMPER("TAMPER"), GUARD("GUARD"), NET("NET"), SYS("SYS") }

/** Sorts plain log lines into stream tags (for colour). Pure and unit tested. */
object TelemetryRules {
    fun tag(msg: String): Tag {
        val m = msg.lowercase()
        return when {
            m.contains("remote") || m.contains("/loc") || m.contains("/photo") || m.contains("lockdown") -> Tag.CMD
            m.contains("copy") -> Tag.COPY
            m.contains("sim") || m.contains("charger") || m.contains("usb") || m.contains("password") || m.contains("tamper") || m.contains("jhatka") -> Tag.TAMPER
            m.contains("guard") || m.contains("watchdog") || m.contains("saver") || m.contains("trace") || m.contains("mode") || m.contains("protection") || m.contains("suraksha") -> Tag.GUARD
            m.contains("telegram") || m.contains("queue") || m.contains("network") || m.contains("internet") -> Tag.NET
            else -> Tag.SYS
        }
    }

    /** Oldest first, so new lines appear at the bottom like a terminal. */
    fun lines(newestFirst: List<String>, max: Int): List<Pair<Tag, String>> =
        newestFirst.take(max).reversed().map { tag(it) to it.trim() }

    fun changed(old: Int, text: String) = text.hashCode() != old
}
