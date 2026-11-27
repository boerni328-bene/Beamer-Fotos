package com.beamerfotos.app

import com.beamerfotos.app.data.Picture
import com.beamerfotos.app.data.SlideSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PictureTest {
    private fun near(expected: Float, actual: Float) = assertEquals(expected, actual, 0.5f)

    @Test
    fun neutralLaesstFarbenUnveraendert() {
        val m = Picture.matrix(SlideSettings(), null)
        val (r, g, b) = Picture.apply(m, 10f, 128f, 240f)
        near(10f, r); near(128f, g); near(240f, b)
        assertTrue(Picture.isNeutral(SlideSettings()))
    }

    @Test
    fun helligkeitHebtAlleKanaele() {
        val (r, g, b) = Picture.apply(Picture.matrix(SlideSettings(brightness = 20), null), 100f, 100f, 100f)
        near(130f, r); near(130f, g); near(130f, b)
    }

    @Test
    fun graustufenBeiSaettigungMinus100() {
        val (r, g, b) = Picture.apply(Picture.matrix(SlideSettings(saturation = -100), null), 200f, 50f, 50f)
        near(r, g); near(g, b)
    }

    @Test
    fun waermeMachtRoterUndWenigerBlau() {
        val (r, _, b) = Picture.apply(Picture.matrix(SlideSettings(warmth = 50), null), 100f, 100f, 100f)
        assertTrue(r > 100f && b < 100f)
    }

    @Test
    fun automatischStrecktFlauesBild() {
        val s = SlideSettings(autoEnhance = true)
        val m = Picture.matrix(s, 60f to 190f)
        near(0f, Picture.apply(m, 60f, 60f, 60f).first)
        assertTrue(Picture.apply(m, 190f, 190f, 190f).first > 250f)
    }

    @Test
    fun grenzwerteAusPixeln() {
        val grey = IntArray(1000) { i -> val v = 60 + (i % 131); (0xFF shl 24) or (v shl 16) or (v shl 8) or v }
        val (low, high) = Picture.levels(grey)!!
        assertTrue(low in 59f..62f)
        assertTrue(high in 188f..191f)
        assertNull(Picture.levels(IntArray(0)))
    }
}
