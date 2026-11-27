package com.beamerfotos.app

import com.beamerfotos.app.data.Picture
import com.beamerfotos.app.data.SlideSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PictureTest {
    private fun near(expected: Float, actual: Float) = assertEquals(expected, actual, 0.5f)

    @Test
    fun neutralLaesstFarbenUnveraendert() {
        val m = Picture.matrix(SlideSettings())
        val (r, g, b) = Picture.apply(m, 10f, 128f, 240f)
        near(10f, r); near(128f, g); near(240f, b)
        assertTrue(Picture.isNeutral(SlideSettings()))
        assertTrue(Picture.slidersNeutral(SlideSettings(autoEnhance = true, sharpness = 50)))
    }

    @Test
    fun helligkeitHebtAlleKanaele() {
        val (r, g, b) = Picture.apply(Picture.matrix(SlideSettings(brightness = 20)), 100f, 100f, 100f)
        near(130f, r); near(130f, g); near(130f, b)
    }

    @Test
    fun graustufenBeiSaettigungMinus100() {
        val (r, g, b) = Picture.apply(Picture.matrix(SlideSettings(saturation = -100)), 200f, 50f, 50f)
        near(r, g); near(g, b)
    }

    @Test
    fun waermeMachtRoterUndWenigerBlau() {
        val (r, _, b) = Picture.apply(Picture.matrix(SlideSettings(warmth = 50)), 100f, 100f, 100f)
        assertTrue(r > 100f && b < 100f)
    }
}
