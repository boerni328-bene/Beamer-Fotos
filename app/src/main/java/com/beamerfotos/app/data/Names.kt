package com.beamerfotos.app.data

/** Datei- und Albumnamen aus dem Browser bereinigen (ohne Android, getestet in NamesTest). */
object Names {
    val imageExtensions = setOf("jpg", "jpeg", "png", "heic", "heif", "webp", "gif", "bmp")

    fun extensionOf(name: String): String = name.substringAfterLast('.', "").lowercase()

    fun isImage(name: String): Boolean = !name.startsWith(".") && extensionOf(name) in imageExtensions

    /**
     * Erlaubt Buchstaben (auch Umlaute), Ziffern, Leerzeichen und - _ . ( ); alles andere wird zu „_“.
     * Schrägstriche verschwinden dabei, ein Name kann also nie aus seinem Ordner herausführen.
     */
    fun clean(raw: String, maxLength: Int = 60): String {
        val sb = StringBuilder()
        for (c in raw.trim()) {
            sb.append(if (c.isLetterOrDigit() || c in " -_.()") c else '_')
        }
        var s = sb.toString().trim().trimStart('.')
        while ("  " in s) s = s.replace("  ", " ")
        if (s.length > maxLength) s = s.take(maxLength).trim()
        return s
    }

    /** Name des Albums: der eingegebene Name, sonst „Fotos <Datum>“. */
    fun albumName(raw: String?, today: String): String = clean(raw ?: "").ifEmpty { "Fotos $today" }

    /** Dateiname eines Fotos oder null, wenn es kein unterstütztes Bild ist. */
    fun fileName(raw: String?): String? {
        val base = clean(raw?.substringAfterLast('/')?.substringAfterLast('\\') ?: "", 100)
        return if (isImage(base)) base else null
    }

    /** Hängt „(2)“, „(3)“ … an, bis der Name frei ist. */
    fun unique(name: String, exists: (String) -> Boolean): String {
        if (!exists(name)) return name
        val dot = name.lastIndexOf('.')
        val stem = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var i = 2
        while (exists("$stem ($i)$ext")) i++
        return "$stem ($i)$ext"
    }
}
