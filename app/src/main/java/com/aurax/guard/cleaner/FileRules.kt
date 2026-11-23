package com.aurax.guard.cleaner

enum class FileKind { APK, TEMP, LARGE, OTHER }
enum class FileLevel(val label: String) { INFO("Info"), MEDIUM("Dhyan"), HIGH("Risky") }
data class Verdict(val kind: FileKind, val level: FileLevel, val reasons: List<String>)

/**
 * Looks only at a file's name and size. These are warning signs, NOT a malware verdict:
 * the app never decides for you, it only explains why a file is shown.
 */
object FileRules {
    const val LARGE_BYTES = 50L * 1024 * 1024

    private val EXEC = setOf("apk", "exe", "bat", "scr", "jar", "vbs", "js", "sh", "msi", "cmd", "apks", "xapk")
    private val DOCLIKE = setOf("pdf", "jpg", "jpeg", "png", "mp4", "mp3", "doc", "docx", "txt", "xls", "xlsx", "zip")
    private val TEMP = setOf("tmp", "temp", "log", "bak", "old", "cache", "crdownload", "part", "download")
    private val BAD_WORDS = listOf("crack", "keygen", "hack", "cheat", "unlocked", "patched", "loader", "modded", "mod_apk", "premium_free", "free_premium", "spy")

    fun ext(name: String): String = name.substringAfterLast('.', "").lowercase()

    fun assess(name: String, size: Long, largeBytes: Long = LARGE_BYTES): Verdict {
        val n = name.lowercase()
        val e = ext(n)
        val reasons = mutableListOf<String>()
        var level = FileLevel.INFO
        var kind = FileKind.OTHER

        fun raise(l: FileLevel) { if (l.ordinal > level.ordinal) level = l }

        val parts = n.split('.')
        if (parts.size >= 3 && parts[parts.size - 1] in EXEC && parts[parts.size - 2] in DOCLIKE) {
            reasons.add("Double extension (${parts[parts.size - 2]}.${parts.last()}): file kuch aur dikhne ki koshish kar rahi hai")
            raise(FileLevel.HIGH)
        }
        if (e in EXEC) {
            if (e == "apk" || e == "apks" || e == "xapk") {
                kind = FileKind.APK
                reasons.add("APK installer: sirf bharosemand source se install karein")
                raise(FileLevel.MEDIUM)
            } else {
                reasons.add("Chalne wali file type (.$e), phone par aam taur par zaroori nahi")
                raise(FileLevel.MEDIUM)
            }
            BAD_WORDS.firstOrNull { it in n }?.let {
                reasons.add("Naam me shak wala shabd: $it")
                raise(FileLevel.HIGH)
            }
        }
        if (e in TEMP && kind == FileKind.OTHER) {
            kind = FileKind.TEMP
            reasons.add("Temporary / purani file (.$e)")
        }
        if (size == 0L) reasons.add("Khaali file (0 B)")
        if (size >= largeBytes && kind == FileKind.OTHER) {
            kind = FileKind.LARGE
            reasons.add("Badi file: ${SizeFormat.bytes(size)}")
        }
        if (reasons.isEmpty()) reasons.add("Aam file")
        return Verdict(kind, level, reasons)
    }
}

/** What the user ticked. Starts EMPTY: nothing is ever pre-selected or deleted automatically. */
class Selection {
    private val ids = linkedSetOf<String>()
    val size get() = ids.size
    fun isSelected(id: String) = id in ids
    fun toggle(id: String) { if (!ids.remove(id)) ids.add(id) }
    fun clear() = ids.clear()
    fun ids(): Set<String> = ids.toSet()
    fun totalBytes(sizes: Map<String, Long>) = ids.sumOf { sizes[it] ?: 0L }
}
