package com.aurax.guard.copy

enum class Verify(val ok: Boolean, val text: String) {
    OK(true, "Theek: size aur checksum match"),
    SIZE_OK_ONLY(true, "Size match (checksum nahi hua: badi file)"),
    SIZE_MISMATCH(false, "GADBAD: size match nahi hua"),
    CRC_MISMATCH(false, "GADBAD: checksum match nahi hua"),
}

/** Rules for "Fast Imp Files Copy". Copy only: nothing is ever deleted or overwritten. */
object CopyRules {
    private val IMPORTANT = setOf(
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv", "json", "html", "htm", "zip", "rar", "7z",
        "jpg", "jpeg", "png", "heic", "webp", "vcf", "odt", "ods", "rtf", "epub", "mp4", "opus",
    )
    const val MAX_FILES = 300
    const val CRC_LIMIT = 300L * 1024 * 1024

    fun ext(name: String) = name.substringAfterLast('.', "").lowercase()
    fun isImportant(name: String, size: Long) = size > 0 && ext(name) in IMPORTANT
    fun isChatExport(name: String) = ext(name) in setOf("zip", "txt", "json", "html", "htm") &&
        listOf("chat", "whatsapp", "telegram", "messages", "backup").any { name.lowercase().contains(it) }

    fun mime(name: String) = when (ext(name)) {
        "pdf" -> "application/pdf"; "jpg", "jpeg" -> "image/jpeg"; "png" -> "image/png"; "txt" -> "text/plain"
        "zip" -> "application/zip"; "json" -> "application/json"; "html", "htm" -> "text/html"; "mp4" -> "video/mp4"
        else -> "application/octet-stream"
    }

    /** srcSize/destSize < 0 mean unknown. destCrc null means it was not computed. */
    fun verdict(srcSize: Long, copied: Long, destSize: Long, srcCrc: Long, destCrc: Long?): Verify {
        if (srcSize >= 0 && copied != srcSize) return Verify.SIZE_MISMATCH
        if (destSize >= 0 && destSize != copied) return Verify.SIZE_MISMATCH
        if (destCrc != null && destCrc != srcCrc) return Verify.CRC_MISMATCH
        return if (destCrc == null) Verify.SIZE_OK_ONLY else Verify.OK
    }

    /** Destination must be a different folder from the source (same folder would only make duplicates). */
    fun sameFolder(src: String, dest: String) = src.trim() == dest.trim()

    fun summary(results: List<Pair<String, Verify>>): String {
        val bad = results.count { !it.second.ok }
        return "${results.size} copy hui: ${results.size - bad} theek" + if (bad > 0) ", $bad me GADBAD (file hatayi nahi gayi, dobara copy karein)" else ""
    }
}
