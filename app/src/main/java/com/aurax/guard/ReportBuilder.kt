package com.aurax.guard

/** Plain-text summary of recent alerts and the security snapshot. No secrets, no tokens. */
object ReportBuilder {
    fun build(
        nowMs: Long,
        alerts: List<EventRecord>,
        highRiskApps: Int?,
        mediumRiskApps: Int?,
        appsScanned: Int?,
    ): String = buildString {
        appendLine("auraX Guard - local report")
        appendLine("Banaya gaya: ${EventRecord.formatTime(nowMs)}")
        appendLine()
        appendLine("Security snapshot")
        if (appsScanned == null || highRiskApps == null || mediumRiskApps == null) {
            appendLine("- App permission scan: abhi nahi hua")
        } else {
            appendLine("- Apps scan hui: $appsScanned")
            appendLine("- High risk indicators: $highRiskApps")
            appendLine("- Medium risk indicators: $mediumRiskApps")
            appendLine("  (ye risk indicators hain, malware ka daawa nahi)")
        }
        appendLine("- Queue me alerts: ${alerts.size}")
        appendLine()
        if (alerts.isEmpty()) {
            appendLine("Abhi koi alert nahi.")
        } else {
            appendLine("Recent alerts (max 10)")
            alerts.take(10).forEachIndexed { i, a ->
                appendLine()
                appendLine("#${i + 1}")
                appendLine(a.report())
            }
        }
    }.trimEnd()
}
