package com.beamerfotos.app.data

/** Wie viele Fotos gleichzeitig auf dem Bild stehen. */
enum class Layout(val label: String) {
    SINGLE("Ein Foto"), PAIRS("Hochformat paarweise"), COLLAGE("Collage (bis 4 Fotos)")
}

/** Ein Feld der Aufteilung, Werte als Anteil von Breite und Höhe (0..1). */
data class Cell(val x: Float, val y: Float, val w: Float, val h: Float)

/**
 * Aufteilung der Diashow in Bilder mit einem oder mehreren Fotos.
 * Videos stehen immer allein. Paare nur, wenn beide Fotos Hochformat sind.
 */
object Layouts {
    const val COLLAGE_SIZE = 4

    /** Anzahl Fotos im Bild, das bei [start] beginnt. */
    fun groupSize(start: Int, count: Int, layout: Layout, isVideo: (Int) -> Boolean, isPortrait: (Int) -> Boolean): Int {
        if (start !in 0 until count || isVideo(start)) return 1
        return when (layout) {
            Layout.SINGLE -> 1
            Layout.PAIRS -> if (start + 1 < count && !isVideo(start + 1) && isPortrait(start) && isPortrait(start + 1)) 2 else 1
            Layout.COLLAGE -> {
                var n = 1
                while (n < COLLAGE_SIZE && start + n < count && !isVideo(start + n)) n++
                n
            }
        }
    }

    /** Beginn des Bildes vor dem Bild bei [start] (rückwärts dieselbe Aufteilung wie vorwärts, soweit möglich). */
    fun previousStart(start: Int, count: Int, layout: Layout, isVideo: (Int) -> Boolean, isPortrait: (Int) -> Boolean): Int {
        if (count == 0) return 0
        if (layout == Layout.SINGLE) return if (start <= 0) count - 1 else start - 1
        // Die Bilder von vorn durchgehen; das letzte, das vor [start] beginnt (am Anfang: das letzte überhaupt).
        val limit = if (start <= 0) count else start
        var s = 0
        var last = 0
        while (s < limit) {
            last = s
            s += groupSize(s, count, layout, isVideo, isPortrait)
        }
        return last
    }

    /** Felder für [n] Fotos mit Abstand [gap] (Anteil der Breite); Bild im Format [aspect] (Breite/Höhe). */
    fun cells(n: Int, aspect: Float, gap: Float = 0.008f): List<Cell> {
        val gx = gap
        val gy = gap * aspect
        val halfW = (1f - gx) / 2f
        val halfH = (1f - gy) / 2f
        return when (n) {
            2 -> listOf(Cell(0f, 0f, halfW, 1f), Cell(halfW + gx, 0f, halfW, 1f))
            3 -> listOf(
                Cell(0f, 0f, halfW, 1f),
                Cell(halfW + gx, 0f, halfW, halfH),
                Cell(halfW + gx, halfH + gy, halfW, halfH),
            )
            4 -> listOf(
                Cell(0f, 0f, halfW, halfH),
                Cell(halfW + gx, 0f, halfW, halfH),
                Cell(0f, halfH + gy, halfW, halfH),
                Cell(halfW + gx, halfH + gy, halfW, halfH),
            )
            else -> listOf(Cell(0f, 0f, 1f, 1f))
        }
    }
}
