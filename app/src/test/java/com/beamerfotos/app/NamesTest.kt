package com.beamerfotos.app

import com.beamerfotos.app.data.Names
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NamesTest {
    @Test
    fun fotosWerdenErkannt() {
        assertTrue(Names.isImage("IMG_0001.JPG"))
        assertTrue(Names.isImage("foto.heic"))
        assertFalse(Names.isImage("video.mov"))
        assertFalse(Names.isImage(".versteckt.jpg"))
        assertFalse(Names.isImage(".123.part"))
    }

    @Test
    fun pfadeFuehrenNieAusDemOrdner() {
        assertEquals("passwd.jpg", Names.fileName("../../etc/passwd.jpg"))
        assertEquals("x.jpg", Names.fileName("C:\\Users\\x.jpg"))
        assertNull(Names.fileName(".."))
        assertNull(Names.fileName("skript.sh"))
    }

    @Test
    fun albumnamen() {
        assertEquals("Anna", Names.albumName("  Anna  ", "26.11.2026"))
        assertEquals("Fotos 26.11.2026", Names.albumName("", "26.11.2026"))
        assertEquals("Fotos 26.11.2026", Names.albumName(null, "26.11.2026"))
        assertEquals("Urlaub_Kreta", Names.albumName("Urlaub/Kreta", "x"))
        assertEquals("Grüße aus Köln", Names.albumName("Grüße  aus Köln", "x"))
        assertEquals("Fotos x", Names.albumName("...", "x"))
    }

    @Test
    fun doppelteNamenBekommenNummern() {
        val vorhanden = setOf("bild.jpg", "bild (2).jpg")
        assertEquals("bild (3).jpg", Names.unique("bild.jpg") { it in vorhanden })
        assertEquals("neu.jpg", Names.unique("neu.jpg") { it in vorhanden })
    }

    @Test
    fun absenderWirdBereinigt() {
        assertEquals("Anna", Names.senderName("  Anna "))
        assertEquals("", Names.senderName(null))
        assertEquals("_etc_passwd", Names.senderName("/etc/passwd"))
        assertFalse(Names.senderName("../../geheim").contains("/"))
        assertTrue(Names.senderName("x".repeat(100)).length <= 40)
    }
}
