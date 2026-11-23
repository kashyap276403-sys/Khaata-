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

/** Plain-language, offline explanation of why a file is shown. Advice only: it never selects or deletes. */
object FileExplain {
    fun explain(name: String, v: Verdict): String {
        val base = when {
            v.level == FileLevel.HIGH -> "Is file ke naam/type me shak wale sanket hain. Kholne ya install karne se pehle bharosemand source se match karein."
            v.kind == FileKind.APK -> "Ye ek app installer hai. Agar aapne khud download nahi kiya ya source anjaan hai to install na karein."
            v.kind == FileKind.TEMP -> "Aisi temporary/purani files aam taur par dobara ban jati hain, par pehle dekh lein ki zaroori to nahi."
            v.kind == FileKind.LARGE -> "Badi file jagah ghere hai. Dekhein ki ab bhi kaam ki hai ya kahin aur backup hai."
            v.level == FileLevel.MEDIUM -> "Ye chalne wali type ki file hai; zaroorat na ho to bhi pehle jaanch lein."
            else -> "Aam file, koi khaas sanket nahi."
        }
        return "Salah: $base App khud kuch delete nahi karti, aap chunoge aur confirm karoge."
    }

    /** One line about the whole scan, e.g. "2 APK, 1 badi, 5 temp". */
    fun overview(items: List<Verdict>): String {
        val apk = items.count { it.kind == FileKind.APK }
        val large = items.count { it.kind == FileKind.LARGE }
        val temp = items.count { it.kind == FileKind.TEMP }
        val risky = items.count { it.level == FileLevel.HIGH }
        val parts = mutableListOf<String>()
        if (risky > 0) parts.add("$risky risky")
        if (apk > 0) parts.add("$apk APK")
        if (large > 0) parts.add("$large badi")
        if (temp > 0) parts.add("$temp temp")
        return if (parts.isEmpty()) "Kuch khaas nahi mila." else "Mila: " + parts.joinToString(", ") + ". Pehle risky wali dekhein."
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
