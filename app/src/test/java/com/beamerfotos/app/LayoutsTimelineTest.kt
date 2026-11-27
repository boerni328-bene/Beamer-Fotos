package com.beamerfotos.app

import com.beamerfotos.app.data.Layout
import com.beamerfotos.app.data.Layouts
import com.beamerfotos.app.data.Timeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class LayoutsTimelineTest {
    // p = Hochformat, l = Querformat, v = Video
    private fun size(s: String, start: Int, layout: Layout) =
        Layouts.groupSize(start, s.length, layout, { s[it] == 'v' }, { s[it] == 'p' })

    private fun prev(s: String, start: Int, layout: Layout) =
        Layouts.previousStart(start, s.length, layout, { s[it] == 'v' }, { s[it] == 'p' })

    @Test fun pairsOnlyForTwoPortraits() {
        assertEquals(2, size("pplp", 0, Layout.PAIRS))
        assertEquals(1, size("pplp", 1, Layout.PAIRS))
        assertEquals(1, size("pplp", 3, Layout.PAIRS))
        assertEquals(1, size("pvp", 0, Layout.PAIRS))
        assertEquals(1, size("pp", 0, Layout.SINGLE))
    }

    @Test fun collageStopsAtVideoAndEnd() {
        assertEquals(4, size("llllll", 0, Layout.COLLAGE))
        assertEquals(2, size("llllll", 4, Layout.COLLAGE))
        assertEquals(2, size("llvll", 0, Layout.COLLAGE))
        assertEquals(1, size("llvll", 2, Layout.COLLAGE))
    }

    @Test fun backwardsMatchesForwards() {
        assertEquals(0, prev("ppll", 2, Layout.PAIRS))
        assertEquals(2, prev("ppll", 3, Layout.PAIRS))
        assertEquals(0, prev("llllllll", 4, Layout.COLLAGE))
        assertEquals(4, prev("llllll", 0, Layout.COLLAGE))
        assertEquals(2, prev("llpp", 0, Layout.PAIRS))
        assertEquals(3, prev("abcd", 0, Layout.SINGLE))
    }

    @Test fun cellsStayInside() {
        for (n in 1..4) {
            val cells = Layouts.cells(n, 16f / 9f)
            assertEquals(n, cells.size)
            cells.forEach { assertTrue(it.x >= 0 && it.y >= 0 && it.x + it.w <= 1.0001f && it.y + it.h <= 1.0001f) }
        }
    }

    private val zone = TimeZone.getTimeZone("Europe/Berlin")
    private fun ms(y: Int, m: Int, d: Int) = Calendar.getInstance(zone).apply { clear(); set(y, m - 1, d, 12, 0) }.timeInMillis

    @Test fun onThisDay() {
        val now = ms(2026, 11, 27)
        assertEquals(3, Timeline.yearsAgo(ms(2023, 11, 27), now, zone))
        assertNull(Timeline.yearsAgo(ms(2023, 11, 26), now, zone))
        assertNull(Timeline.yearsAgo(ms(2026, 11, 27), now, zone))
        assertTrue(Timeline.matches(Timeline.TODAY, ms(2020, 11, 27), now, zone))
    }

    @Test fun yearAndMonthKeys() {
        assertTrue(Timeline.matches(Timeline.monthKey(2023, 5), ms(2023, 5, 3), zone = zone))
        assertFalse(Timeline.matches(Timeline.monthKey(2023, 5), ms(2023, 6, 3), zone = zone))
        assertTrue(Timeline.matches(Timeline.yearKey(2023), ms(2023, 6, 3), zone = zone))
        assertEquals("Mai 2023", Timeline.title(Timeline.monthKey(2023, 5)))
        val years = Timeline.years(listOf(ms(2023, 5, 1), ms(2023, 5, 9), ms(2021, 1, 1), 0L), zone)
        assertEquals(listOf(2023, 2021), years.map { it.year })
        assertEquals(listOf(5 to 2), years[0].months)
    }

    @Test fun todayLabel() {
        assertEquals("Heute vor 1 Jahr (2)", Timeline.todayLabel(listOf(1, 1), 2))
        assertEquals("An diesem Tag (5)", Timeline.todayLabel(listOf(1, 4), 5))
    }
}
