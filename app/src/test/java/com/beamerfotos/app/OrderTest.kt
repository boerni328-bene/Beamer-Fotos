package com.beamerfotos.app

import com.beamerfotos.app.data.Order
import com.beamerfotos.app.data.Photo
import com.beamerfotos.app.data.ordered
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class OrderTest {
    private fun photo(name: String, captured: Long, arrived: Long) = Photo(File("/fotos/$name"), "A", "", captured, arrived)

    private val a = photo("a.jpg", captured = 300, arrived = 1)
    private val b = photo("b.jpg", captured = 100, arrived = 3)
    private val c = photo("c.jpg", captured = 200, arrived = 2)

    @Test
    fun nachAufnahmezeit() {
        assertEquals(listOf(b, c, a), ordered(listOf(a, b, c), Order.CAPTURED, 0))
    }

    @Test
    fun nachEingang() {
        assertEquals(listOf(a, c, b), ordered(listOf(b, a, c), Order.ARRIVAL, 0))
    }

    @Test
    fun zufallIstMitGleichemStartwertGleich() {
        val eins = ordered(listOf(a, b, c), Order.RANDOM, 42)
        val zwei = ordered(listOf(c, a, b), Order.RANDOM, 42)
        assertEquals(eins, zwei)
        assertEquals(setOf(a, b, c), eins.toSet())
    }
}
