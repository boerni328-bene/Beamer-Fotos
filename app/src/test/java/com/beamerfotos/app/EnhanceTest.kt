package com.beamerfotos.app

import com.beamerfotos.app.data.Enhance
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EnhanceTest {
    private fun rgb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    private fun r(p: Int) = (p shr 16) and 0xFF
    private fun g(p: Int) = (p shr 8) and 0xFF
    private fun b(p: Int) = p and 0xFF

    @Test
    fun ohneEinstellungBleibtAllesGleich() {
        val pixels = IntArray(100) { rgb(it, 2 * it, 255 - it) }
        val copy = pixels.copyOf()
        Enhance.apply(pixels, 10, 10, auto = false, sharpness = 0)
        assertArrayEquals(copy, pixels)
    }

    @Test
    fun flauesBildWirdGestreckt() {
        // Nur Grautöne zwischen 80 und 170.
        val pixels = IntArray(1000) { val v = 80 + it % 91; rgb(v, v, v) }
        Enhance.apply(pixels, 100, 10, auto = true, sharpness = 0)
        val values = pixels.map { r(it) }
        assertTrue("dunkelster Wert ${values.min()}", values.min() < 30)
        // Vorher 90 Stufen Umfang; höchstens doppelt so stark gestreckt (Rauschen).
        assertTrue("Umfang ${values.max() - values.min()}", values.max() - values.min() > 150)
    }

    @Test
    fun farbstichWirdVerringert() {
        // Bläulich: Blau deutlich höher als Rot. Maß: Blau-Rot-Abstand im Verhältnis zum Helligkeitsumfang.
        val pixels = IntArray(1000) { val v = 60 + it % 120; rgb(v, v + 10, minOf(255, v + 60)) }
        fun cast(px: IntArray): Double {
            val diff = px.map { b(it) - r(it) }.average()
            val greens = px.map { g(it) }
            return diff / (greens.max() - greens.min())
        }
        val before = cast(pixels)
        Enhance.apply(pixels, 100, 10, auto = true, sharpness = 0)
        val after = cast(pixels)
        assertTrue("Blaustich vorher $before, nachher $after", after < before * 0.8)
    }

    @Test
    fun zuDunklesBildWirdHeller() {
        val pixels = IntArray(1000) { val v = (it % 256) / 3; rgb(v, v, v) }
        val before = pixels.sumOf { g(it) }
        Enhance.apply(pixels, 100, 10, auto = true, sharpness = 0)
        assertTrue(pixels.sumOf { g(it) } > before * 1.5)
    }

    @Test
    fun schaerfenVerstaerktKanten() {
        // Senkrechte Kante: links 100, rechts 150.
        val w = 10
        val pixels = IntArray(100) { val x = it % w; if (x < 5) rgb(100, 100, 100) else rgb(150, 150, 150) }
        Enhance.apply(pixels, w, 10, auto = false, sharpness = 100)
        assertTrue("links der Kante dunkler", r(pixels[5 * w + 4]) < 100)
        assertTrue("rechts der Kante heller", r(pixels[5 * w + 5]) > 150)
        assertTrue("Flächen bleiben", r(pixels[5 * w + 1]) == 100)
    }
}
