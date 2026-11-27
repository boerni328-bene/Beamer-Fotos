package com.beamerfotos.app.data

/**
 * Bildverbesserung als Farbmatrix (4 × 5, Werte 0–255, Aufbau wie android.graphics.ColorMatrix).
 * Ohne Android, getestet in PictureTest.
 */
object Picture {
    /** Bereiche der Regler. */
    val BRIGHTNESS = -50..50
    val CONTRAST = -50..50
    val SATURATION = -100..100
    val WARMTH = -50..50

    private val IDENTITY = floatArrayOf(
        1f, 0f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f, 0f,
        0f, 0f, 1f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )

    fun isNeutral(s: SlideSettings) =
        s.brightness == 0 && s.contrast == 0 && s.saturation == 0 && s.warmth == 0 && !s.autoEnhance

    /**
     * Matrix für die Einstellungen. [levels] = dunkelster und hellster Wert des Fotos (0–255) für „Automatisch“;
     * null, wenn noch nicht berechnet oder ausgeschaltet.
     */
    fun matrix(s: SlideSettings, levels: Pair<Float, Float>?): FloatArray {
        var m = IDENTITY.copyOf()
        if (s.autoEnhance && levels != null) {
            val (low, high) = levels
            if (high - low > 20f) {
                val k = (255f / (high - low)).coerceIn(1f, 2.5f)
                m = concat(scaleOffset(k, -low * k), m)
            }
        }
        val c = 1f + s.contrast / 100f
        m = concat(scaleOffset(c, 128f * (1f - c) + s.brightness * 1.5f), m)
        val sat = 1f + (s.saturation + if (s.autoEnhance) 8 else 0) / 100f
        m = concat(saturation(sat), m)
        if (s.warmth != 0) {
            val w = s.warmth / 250f
            m = concat(floatArrayOf(
                1f + w, 0f, 0f, 0f, 0f,
                0f, 1f, 0f, 0f, 0f,
                0f, 0f, 1f - w, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            ), m)
        }
        return m
    }

    /** Dunkelster und hellster Wert (0,5 % und 99,5 % der Helligkeiten) aus ARGB-Pixeln. */
    fun levels(pixels: IntArray): Pair<Float, Float>? {
        if (pixels.isEmpty()) return null
        val histogram = IntArray(256)
        for (p in pixels) {
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            histogram[(r * 54 + g * 183 + b * 19) shr 8]++
        }
        val cut = pixels.size / 200
        var sum = 0
        var low = 0
        while (low < 255 && sum + histogram[low] <= cut) sum += histogram[low++]
        sum = 0
        var high = 255
        while (high > 0 && sum + histogram[high] <= cut) sum += histogram[high--]
        return if (high > low) low.toFloat() to high.toFloat() else null
    }

    private fun scaleOffset(k: Float, offset: Float) = floatArrayOf(
        k, 0f, 0f, 0f, offset,
        0f, k, 0f, 0f, offset,
        0f, 0f, k, 0f, offset,
        0f, 0f, 0f, 1f, 0f,
    )

    private fun saturation(s: Float): FloatArray {
        val lr = 0.2126f * (1 - s)
        val lg = 0.7152f * (1 - s)
        val lb = 0.0722f * (1 - s)
        return floatArrayOf(
            lr + s, lg, lb, 0f, 0f,
            lr, lg + s, lb, 0f, 0f,
            lr, lg, lb + s, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
    }

    /** a · b: erst b, dann a anwenden. */
    fun concat(a: FloatArray, b: FloatArray): FloatArray {
        val out = FloatArray(20)
        for (row in 0 until 4) {
            for (col in 0 until 5) {
                var v = if (col == 4) a[row * 5 + 4] else 0f
                for (k in 0 until 4) v += a[row * 5 + k] * b[k * 5 + col]
                out[row * 5 + col] = v
            }
        }
        return out
    }

    /** Wendet die Matrix auf eine Farbe an (für Tests). */
    fun apply(m: FloatArray, r: Float, g: Float, b: Float): Triple<Float, Float, Float> {
        fun ch(row: Int) = (m[row * 5] * r + m[row * 5 + 1] * g + m[row * 5 + 2] * b + m[row * 5 + 4]).coerceIn(0f, 255f)
        return Triple(ch(0), ch(1), ch(2))
    }
}
