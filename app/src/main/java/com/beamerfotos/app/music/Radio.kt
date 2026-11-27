package com.beamerfotos.app.music

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class Station(val name: String, val url: String, val info: String = "")

/**
 * Gespeicherte Radiosender. Neue Sender kommen über die Suche (Senderverzeichnis radio-browser.info,
 * nur auf ausdrücklichen Wunsch) oder über die Handy-Seite (Name und Adresse).
 */
class RadioStore(context: Context) {
    private val prefs = context.getSharedPreferences("radio", Context.MODE_PRIVATE)

    private val _stations = MutableStateFlow(read())
    val stations: StateFlow<List<Station>> = _stations

    private fun read(): List<Station> =
        (prefs.getString("sender", null) ?: "").lines().mapNotNull { line ->
            val parts = line.split('\t')
            if (parts.size >= 2 && parts[1].startsWith("http")) Station(parts[0], parts[1], parts.getOrElse(2) { "" }) else null
        }

    private fun write(list: List<Station>) {
        _stations.value = list
        prefs.edit().putString("sender", list.joinToString("\n") { "${clean(it.name)}\t${it.url}\t${clean(it.info)}" }).apply()
    }

    private fun clean(s: String) = s.replace('\t', ' ').replace('\n', ' ').trim()

    /** Fügt einen Sender hinzu (gleiche Adresse wird ersetzt); null bei Erfolg, sonst ein Hinweis. */
    fun add(nameRaw: String?, urlRaw: String?, info: String = ""): String? {
        val url = urlRaw?.trim().orEmpty()
        if (!url.startsWith("http://") && !url.startsWith("https://")) return "Die Adresse muss mit http:// oder https:// beginnen."
        val name = clean(nameRaw.orEmpty()).take(60).ifEmpty { url.substringAfter("://").substringBefore('/') }
        write(_stations.value.filter { it.url != url } + Station(name, url, info))
        return null
    }

    fun remove(station: Station) = write(_stations.value.filter { it.url != station.url })

    companion object {
        /** Sendersuche im freien Verzeichnis radio-browser.info (meistgehörte zuerst). */
        suspend fun search(query: String): Result<List<Station>> = withContext(Dispatchers.IO) {
            runCatching {
                val q = URLEncoder.encode(query.trim(), "UTF-8")
                val text = get("https://de1.api.radio-browser.info/json/stations/search?name=$q&limit=40&hidebroken=true&order=clickcount&reverse=true")
                val array = JSONArray(text)
                (0 until array.length()).mapNotNull { i ->
                    val o = array.getJSONObject(i)
                    val url = o.optString("url_resolved").ifEmpty { o.optString("url") }
                    if (!url.startsWith("http")) return@mapNotNull null
                    val info = listOf(o.optString("country"), o.optString("tags").split(',').take(3).joinToString(", "),
                        o.optInt("bitrate").takeIf { it > 0 }?.let { "$it kbit/s" } ?: "")
                        .filter { it.isNotBlank() }.joinToString(" · ")
                    Station(o.optString("name").trim().ifEmpty { url }, url, info)
                }.distinctBy { it.url }
            }
        }

        /**
         * Playlisten (.pls, .m3u) enthalten nur die eigentliche Adresse: diese herauslesen.
         * HLS (.m3u8) und direkte Streams bleiben, wie sie sind.
         */
        suspend fun resolve(url: String): String = withContext(Dispatchers.IO) {
            val path = url.substringBefore('?').lowercase()
            if (!path.endsWith(".pls") && !path.endsWith(".m3u")) return@withContext url
            runCatching {
                get(url).lines().map { it.trim() }
                    .map { if (it.lowercase().startsWith("file") && '=' in it) it.substringAfter('=') else it }
                    .firstOrNull { it.startsWith("http") }
            }.getOrNull() ?: url
        }

        private fun get(address: String): String {
            val c = URL(address).openConnection() as HttpURLConnection
            c.connectTimeout = 8000
            c.readTimeout = 8000
            c.setRequestProperty("User-Agent", "Beamer-Fotos")
            return try {
                c.inputStream.bufferedReader().use { it.readText().take(2_000_000) }
            } finally {
                c.disconnect()
            }
        }
    }
}
