package com.beamerfotos.app.data

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** Ein Jahr der Zeitleiste mit Anzahl Fotos je Monat (1..12). */
data class TimelineYear(val year: Int, val total: Int, val months: List<Pair<Int, Int>>)

/**
 * Zeitleiste nach Aufnahmedatum und „Heute vor x Jahren“.
 * Diashow-Schlüssel: „zeit:2023“, „zeit:2023-05“ und „heute“.
 */
object Timeline {
    const val TODAY = "heute"
    private const val PREFIX = "zeit:"

    val monthNames = listOf(
        "Januar", "Februar", "März", "April", "Mai", "Juni",
        "Juli", "August", "September", "Oktober", "November", "Dezember",
    )

    fun isTimelineKey(key: String?) = key != null && (key == TODAY || key.startsWith(PREFIX))

    fun yearKey(year: Int) = "$PREFIX$year"
    fun monthKey(year: Int, month: Int) = PREFIX + year + "-" + month.toString().padStart(2, '0')

    private fun calendar(ms: Long, zone: TimeZone): Calendar =
        Calendar.getInstance(zone, Locale.GERMANY).apply { timeInMillis = ms }

    /** Passt das Aufnahmedatum [ms] zum Schlüssel? */
    fun matches(key: String, ms: Long, now: Long = System.currentTimeMillis(), zone: TimeZone = TimeZone.getDefault()): Boolean {
        if (ms <= 0) return false
        if (key == TODAY) return yearsAgo(ms, now, zone) != null
        val spec = key.removePrefix(PREFIX)
        val c = calendar(ms, zone)
        val year = spec.substringBefore('-').toIntOrNull() ?: return false
        val month = spec.substringAfter('-', "").toIntOrNull()
        return c.get(Calendar.YEAR) == year && (month == null || c.get(Calendar.MONTH) + 1 == month)
    }

    /** Vor wie vielen Jahren am selben Kalendertag aufgenommen; null, wenn nicht heute oder in diesem Jahr. */
    fun yearsAgo(ms: Long, now: Long = System.currentTimeMillis(), zone: TimeZone = TimeZone.getDefault()): Int? {
        if (ms <= 0) return null
        val c = calendar(ms, zone)
        val t = calendar(now, zone)
        val years = t.get(Calendar.YEAR) - c.get(Calendar.YEAR)
        return if (years > 0 && c.get(Calendar.MONTH) == t.get(Calendar.MONTH) &&
            c.get(Calendar.DAY_OF_MONTH) == t.get(Calendar.DAY_OF_MONTH)
        ) years else null
    }

    /** Jahre (neueste zuerst) mit Monaten, die Fotos haben. */
    fun years(captured: List<Long>, zone: TimeZone = TimeZone.getDefault()): List<TimelineYear> {
        val counts = HashMap<Int, IntArray>()
        captured.filter { it > 0 }.forEach {
            val c = calendar(it, zone)
            counts.getOrPut(c.get(Calendar.YEAR)) { IntArray(12) }[c.get(Calendar.MONTH)]++
        }
        return counts.entries.sortedByDescending { it.key }.map { (year, m) ->
            TimelineYear(year, m.sum(), (1..12).filter { m[it - 1] > 0 }.map { it to m[it - 1] })
        }
    }

    /** Titel für Fortsetzen und Einblendung. */
    fun title(key: String): String {
        if (key == TODAY) return "Heute vor … Jahren"
        val spec = key.removePrefix(PREFIX)
        val year = spec.substringBefore('-')
        val month = spec.substringAfter('-', "").toIntOrNull()
        return if (month != null && month in 1..12) "${monthNames[month - 1]} $year" else year
    }

    /** Text für den Knopf „Heute vor x Jahren“ aus den gefundenen Abständen. */
    fun todayLabel(years: Collection<Int>, count: Int): String {
        val distinct = years.distinct().sorted()
        return when (distinct.size) {
            0 -> ""
            1 -> "Heute vor ${distinct[0]} ${if (distinct[0] == 1) "Jahr" else "Jahren"} ($count)"
            else -> "An diesem Tag ($count)"
        }
    }
}
