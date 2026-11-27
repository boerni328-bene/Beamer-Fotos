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
    val SHARPNESS = 0..100

    private val IDENTITY = floatArrayOf(
        1f, 0f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f, 0f,
        0f, 0f, 1f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )

    fun isNeutral(s: SlideSettings) =
        s.brightness == 0 && s.contrast == 0 && s.saturation == 0 && s.warmth == 0 && !s.autoEnhance && s.sharpness == 0

    /** Wirken die Regler Helligkeit, Kontrast, Sättigung, Wärme (Farbmatrix)? „Automatisch“ und „Schärfe“ rechnen an den Pixeln (Enhance). */
    fun slidersNeutral(s: SlideSettings) = s.brightness == 0 && s.contrast == 0 && s.saturation == 0 && s.warmth == 0

    /** Farbmatrix für die Regler Helligkeit, Kontrast, Sättigung und Wärme. */
    fun matrix(s: SlideSettings): FloatArray {
        var m = IDENTITY.copyOf()
        val c = 1f + s.contrast / 100f
        m = concat(scaleOffset(c, 128f * (1f - c) + s.brightness * 1.5f), m)
        val sat = 1f + s.saturation / 100f
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
