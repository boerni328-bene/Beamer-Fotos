package com.beamerfotos.app.data

import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Bildverbesserung direkt an den Pixeln (ARGB), ohne Android – getestet in EnhanceTest.
 *
 * „Automatisch“: Weißabgleich (Farbstich weg), Tonwertkorrektur (Schwarz- und Weißpunkt),
 * Helligkeit der Mitteltöne (zu dunkle Fotos aufhellen, zu helle abdunkeln), Lebendigkeit
 * (blasse Farben kräftiger, kräftige bleiben) und Schärfen.
 * „Schärfe“ (0–100) schärft zusätzlich, auch ohne „Automatisch“.
 */
object Enhance {
    /** Wie stark „Automatisch“ von sich aus schärft (0–1). */
    private const val AUTO_SHARPEN = 0.45f

    fun needed(auto: Boolean, sharpness: Int) = auto || sharpness > 0

    fun apply(pixels: IntArray, width: Int, height: Int, auto: Boolean, sharpness: Int) {
        if (pixels.size != width * height || pixels.isEmpty()) return
        if (auto) {
            val luts = autoLevels(pixels)
            vibranceAndLut(pixels, luts, 0.30f)
        }
        val amount = (if (auto) AUTO_SHARPEN else 0f) + sharpness.coerceIn(0, 100) / 100f * 1.2f
        if (amount > 0f) sharpen(pixels, width, height, amount)
    }

    /**
     * Je Farbkanal eine Tabelle 0–255: Schwarz- und Weißpunkt (0,5 % / 99,5 %), dabei den Farbstich
     * nur zur Hälfte ausgleichen, damit Stimmungen (Sonnenuntergang) erhalten bleiben; dann Mitteltöne
     * so anheben oder absenken, dass die mittlere Helligkeit Richtung 45 % geht.
     */
    fun autoLevels(pixels: IntArray): Array<IntArray> {
        val hist = Array(3) { IntArray(256) }
        val lum = IntArray(256)
        // Bei großen Bildern reicht jedes vierte Pixel für die Statistik.
        val step = if (pixels.size > 400_000) 4 else 1
        var count = 0
        var i = 0
        while (i < pixels.size) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            hist[0][r]++; hist[1][g]++; hist[2][b]++
            lum[(r * 54 + g * 183 + b * 19) shr 8]++
            count++
            i += step
        }
        // Weißabgleich („Grauwelt“): jeden Kanal so verstärken, dass sein Mittel zur Helligkeit passt –
        // nur zur Hälfte, damit Stimmungen (Sonnenuntergang, Kerzenlicht) erhalten bleiben.
        val gains = whiteBalanceGains(hist, lum)
        var (low, high) = percentiles(lum, count)
        if (high - low < 40f) { low = 0f; high = 255f } // fast einfarbig: nicht strecken
        // Nicht zu stark strecken (höchstens Faktor 2), sonst wird Rauschen sichtbar.
        val span = max(high - low, 255f / 2f)
        val luts = Array(3) { c ->
            IntArray(256) { v -> ((v * gains[c] - low) / span * 255f).coerceIn(0f, 255f).roundToInt() }
        }
        // Mitteltöne: mittlere Helligkeit nach dem Strecken bestimmen.
        var sum = 0.0
        for (v in 0..255) {
            val mapped = (luts[0][v] * 54 + luts[1][v] * 183 + luts[2][v] * 19) shr 8
            sum += lum[v].toDouble() * mapped
        }
        val mean = (sum / max(1, count) / 255.0).coerceIn(0.05, 0.95)
        val gamma = (ln(0.45) / ln(mean)).coerceIn(0.7, 1.3)
        if (kotlin.math.abs(gamma - 1.0) > 0.03) {
            val curve = IntArray(256) { v -> (255.0 * (v / 255.0).pow(gamma)).roundToInt().coerceIn(0, 255) }
            for (lut in luts) for (v in 0..255) lut[v] = curve[lut[v]]
        }
        return luts
    }

    /** Tabellen anwenden und blasse Farben kräftiger machen (bis [strength], kräftige kaum). */
    fun vibranceAndLut(pixels: IntArray, luts: Array<IntArray>, strength: Float) {
        val lr = luts[0]; val lg = luts[1]; val lb = luts[2]
        for (i in pixels.indices) {
            val p = pixels[i]
            var r = lr[(p shr 16) and 0xFF].toFloat()
            var g = lg[(p shr 8) and 0xFF].toFloat()
            var b = lb[p and 0xFF].toFloat()
            val mx = max(r, max(g, b))
            val mn = min(r, min(g, b))
            val boost = strength * (1f - (mx - mn) / 255f)
            if (boost > 0.01f) {
                val mean = (r + g + b) / 3f
                r = mean + (r - mean) * (1f + boost)
                g = mean + (g - mean) * (1f + boost)
                b = mean + (b - mean) * (1f + boost)
            }
            pixels[i] = (p and 0xFF000000.toInt()) or (clamp(r) shl 16) or (clamp(g) shl 8) or clamp(b)
        }
    }

    /**
     * Unscharfe Maske: Differenz zum leicht weichgezeichneten Bild (3 × 3) verstärken.
     * Kleine Unterschiede (Rauschen) bleiben unangetastet.
     */
    fun sharpen(pixels: IntArray, width: Int, height: Int, amount: Float) {
        if (width < 3 || height < 3) return
        val src = pixels.copyOf()
        val threshold = 4
        for (y in 0 until height) {
            val y0 = max(0, y - 1) * width
            val y1 = y * width
            val y2 = min(height - 1, y + 1) * width
            for (x in 0 until width) {
                val x0 = max(0, x - 1)
                val x2 = min(width - 1, x + 1)
                var sr = 0; var sg = 0; var sb = 0
                for (row in intArrayOf(y0, y1, y2)) {
                    for (col in intArrayOf(x0, x, x2)) {
                        val q = src[row + col]
                        sr += (q shr 16) and 0xFF
                        sg += (q shr 8) and 0xFF
                        sb += q and 0xFF
                    }
                }
                val p = src[y1 + x]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                pixels[y1 + x] = (p and 0xFF000000.toInt()) or
                    (boost(r, sr / 9, amount, threshold) shl 16) or
                    (boost(g, sg / 9, amount, threshold) shl 8) or
                    boost(b, sb / 9, amount, threshold)
            }
        }
    }

    private fun boost(v: Int, blurred: Int, amount: Float, threshold: Int): Int {
        val d = v - blurred
        return if (d in -threshold..threshold) v else clamp(v + d * amount)
    }

    /** Verstärkung je Kanal (Rot, Grün, Blau) für den halben Weißabgleich, begrenzt auf 0,8–1,25. */
    fun whiteBalanceGains(hist: Array<IntArray>, lum: IntArray): FloatArray {
        fun mean(h: IntArray): Double {
            var s = 0.0
            var n = 0L
            for (v in 0..255) { s += v.toDouble() * h[v]; n += h[v] }
            return if (n == 0L) 128.0 else s / n
        }
        val target = mean(lum)
        return FloatArray(3) { c ->
            val m = mean(hist[c]).coerceAtLeast(1.0)
            (1.0 + (target / m - 1.0) * 0.5).toFloat().coerceIn(0.8f, 1.25f)
        }
    }

    private fun percentiles(hist: IntArray, count: Int): Pair<Float, Float> {
        val cut = count / 200
        var sum = 0
        var low = 0
        while (low < 255 && sum + hist[low] <= cut) sum += hist[low++]
        sum = 0
        var high = 255
        while (high > 0 && sum + hist[high] <= cut) sum += hist[high--]
        return low.toFloat() to max(high, low + 1).toFloat()
    }

    private fun clamp(v: Float): Int = v.roundToInt().coerceIn(0, 255)
}
