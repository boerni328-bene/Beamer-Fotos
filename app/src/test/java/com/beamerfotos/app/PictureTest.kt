package com.beamerfotos.app

import com.beamerfotos.app.data.Adjust
import com.beamerfotos.app.data.Picture
import com.beamerfotos.app.data.adjust
import com.beamerfotos.app.data.withAdjust
import com.beamerfotos.app.data.SlideSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test
    fun bildeinstellungJeFotoSpeichernUndLesen() {
        val a = Adjust(brightness = 10, contrast = -5, saturation = -20, warmth = 15, autoEnhance = true, sharpness = 30)
        assertEquals(a, Adjust.decode(a.encode()))
        assertEquals(Adjust(brightness = 50), Adjust.decode("999,0,0,0,0,0"))
        assertNull(Adjust.decode("1,2,3"))
        assertNull(Adjust.decode("a,b,c,d,e,f"))
    }

    @Test
    fun einstellungFuerAlleUndJeFotoPassenZusammen() {
        val a = Adjust(brightness = 20, sharpness = 40)
        val s = SlideSettings().withAdjust(a)
        assertEquals(a, s.adjust)
        assertTrue(Picture.isNeutral(Adjust()))
    }
}
