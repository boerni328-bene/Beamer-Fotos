package com.beamerfotos.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.security.SecureRandom
import kotlin.random.Random

enum class Transition(val label: String) {
    NONE("Kein Übergang"), FADE("Blende"), SLIDE("Schieben"), KEN_BURNS("Blende mit langsamem Zoom")
}

enum class Order(val label: String) {
    CAPTURED("Aufnahmezeit"), ARRIVAL("Eingang am Beamer"), RANDOM("Zufall")
}

enum class Fit(val label: String) { FIT("Ganzes Foto"), FILL("Bild füllen") }

enum class Background(val label: String) { BLUR("Foto unscharf"), BLACK("Schwarz") }

/** Einstellungen der Diashow (gelten für alle Alben). */
data class SlideSettings(
    val intervalSec: Int = 6,
    val transition: Transition = Transition.KEN_BURNS,
    val order: Order = Order.CAPTURED,
    val fit: Fit = Fit.FIT,
    val background: Background = Background.BLUR,
    val showInfo: Boolean = true,
    val loop: Boolean = true,
    val liveNew: Boolean = true,
    val showQr: Boolean = false,
    val storageId: String = LOCATION_INTERNAL,
    // Bildverbesserung (gilt für alle Fotos, nicht für Videos)
    val brightness: Int = 0,
    val contrast: Int = 0,
    val saturation: Int = 0,
    val warmth: Int = 0,
    val autoEnhance: Boolean = false,
    val sharpness: Int = 0,
    // Bilderrahmen: Diashow nach so vielen Minuten ohne Bedienung (0 = aus), beim Öffnen starten, Uhrzeit zeigen
    val idleMinutes: Int = 0,
    val startWithShow: Boolean = false,
    val showClock: Boolean = false,
    /** Mehrere Fotos auf einem Bild: Hochformat paarweise oder Collage. */
    val layout: Layout = Layout.SINGLE,
)

const val LOCATION_INTERNAL = "intern"

/** Wählbare Wartezeit bis zum Bilderrahmen in Minuten; 0 = aus. */
val IDLE_MINUTES = listOf(0, 2, 5, 10, 30)

fun nextIdle(current: Int): Int = IDLE_MINUTES[(IDLE_MINUTES.indexOf(current) + 1).mod(IDLE_MINUTES.size)]

/** Zuletzt gesehene Diashow. */
data class LastShow(
    val albumKey: String?,
    val sender: String?,
    val photoKey: String,
    val position: Int,
    val total: Int,
    val title: String,
) {
    val showId: String get() = showIdOf(albumKey, sender)
}

fun showIdOf(albumKey: String?, sender: String?) = (albumKey ?: "alle") + "|" + (sender ?: "")

/** Wählbare Anzeigedauer je Foto in Sekunden; 0 = nur von Hand weiter. */
val INTERVALS = listOf(3, 5, 6, 8, 10, 15, 20, 30, 60, 0)

fun intervalLabel(seconds: Int): String = if (seconds == 0) "von Hand" else "$seconds s"

fun nextInterval(current: Int): Int = INTERVALS[(INTERVALS.indexOf(current) + 1).mod(INTERVALS.size)]

inline fun <reified E : Enum<E>> E.cycle(): E {
    val all = enumValues<E>()
    return all[(ordinal + 1) % all.size]
}

/** Fotos in die gewählte Reihenfolge bringen (ohne Android, getestet in OrderTest). */
fun ordered(photos: List<Photo>, order: Order, seed: Long): List<Photo> = when (order) {
    Order.CAPTURED -> photos.sortedWith(compareBy<Photo> { it.captured }.thenBy { it.arrived }.thenBy { it.key })
    Order.ARRIVAL -> photos.sortedWith(compareBy<Photo> { it.arrived }.thenBy { it.key })
    Order.RANDOM -> photos.sortedBy { it.key }.shuffled(Random(seed))
}

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("einstellungen", Context.MODE_PRIVATE)

    private val _state = MutableStateFlow(read())
    val state: StateFlow<SlideSettings> = _state

    private val _token = MutableStateFlow(prefs.getString("zugang", null) ?: newToken())
    /** Zugangscode im QR-Code; ohne ihn nimmt der Beamer keine Fotos an. */
    val token: StateFlow<String> = _token

    private val _rotations = MutableStateFlow(
        prefs.all.filterKeys { it.startsWith(ROT) }
            .mapNotNull { (k, v) -> (v as? Int)?.let { k.removePrefix(ROT) to it } }
            .toMap()
    )
    /** Von Hand gedrehte Fotos: Pfad → Grad (90, 180, 270). */
    val rotations: StateFlow<Map<String, Int>> = _rotations

    fun update(change: (SlideSettings) -> SlideSettings) {
        val n = change(_state.value)
        _state.value = n
        prefs.edit()
            .putInt("intervall", n.intervalSec)
            .putString("uebergang", n.transition.name)
            .putString("reihenfolge", n.order.name)
            .putString("anpassen", n.fit.name)
            .putString("hintergrund", n.background.name)
            .putBoolean("infos", n.showInfo)
            .putBoolean("wiederholen", n.loop)
            .putBoolean("neue_sofort", n.liveNew)
            .putBoolean("qr_in_diashow", n.showQr)
            .putString("speicherort", n.storageId)
            .putInt("helligkeit", n.brightness)
            .putInt("kontrast", n.contrast)
            .putInt("saettigung", n.saturation)
            .putInt("waerme", n.warmth)
            .putBoolean("auto_verbessern", n.autoEnhance)
            .putInt("schaerfe", n.sharpness)
            .putInt("leerlauf_minuten", n.idleMinutes)
            .putBoolean("start_mit_diashow", n.startWithShow)
            .putBoolean("uhr", n.showClock)
            .putString("anordnung", n.layout.name)
            .apply()
    }

    fun renewToken() {
        _token.value = newToken()
    }

    fun rotate(path: String) {
        val deg = ((_rotations.value[path] ?: 0) + 90) % 360
        _rotations.value = if (deg == 0) _rotations.value - path else _rotations.value + (path to deg)
        prefs.edit().apply { if (deg == 0) remove(ROT + path) else putInt(ROT + path, deg) }.apply()
    }

    /** Drehung, Favorit und Ausblendung mitnehmen, wenn ein Foto verschoben wird. */
    fun moveRotation(from: String, to: String) {
        copyRotation(from, to)
        forgetRotation(from)
    }

    fun copyRotation(from: String, to: String) {
        _rotations.value[from]?.let { deg ->
            _rotations.value = _rotations.value + (to to deg)
            prefs.edit().putInt(ROT + to, deg).apply()
        }
        if (from in _favorites.value) setMark(_favorites, FAV, to, true)
        if (from in _hidden.value) setMark(_hidden, HIDDEN, to, true)
    }

    fun forgetRotation(path: String) {
        if (path in _rotations.value) {
            _rotations.value = _rotations.value - path
            prefs.edit().remove(ROT + path).apply()
        }
        setMark(_favorites, FAV, path, false)
        setMark(_hidden, HIDDEN, path, false)
    }

    // --- Favoriten und ausgeblendete Fotos (Pfade) ---

    private val _favorites = MutableStateFlow(prefs.getStringSet(FAV, emptySet())?.toSet() ?: emptySet())
    val favorites: StateFlow<Set<String>> = _favorites

    private val _hidden = MutableStateFlow(prefs.getStringSet(HIDDEN, emptySet())?.toSet() ?: emptySet())
    /** Fotos, die in der Diashow nicht mehr gezeigt werden (bleiben gespeichert). */
    val hidden: StateFlow<Set<String>> = _hidden

    private fun setMark(flow: MutableStateFlow<Set<String>>, key: String, path: String, on: Boolean) {
        if ((path in flow.value) == on) return
        flow.value = if (on) flow.value + path else flow.value - path
        prefs.edit().putStringSet(key, flow.value).apply()
    }

    fun setFavorite(path: String, on: Boolean) = setMark(_favorites, FAV, path, on)
    fun setHidden(path: String, on: Boolean) = setMark(_hidden, HIDDEN, path, on)

    fun showAllAgain() {
        _hidden.value = emptySet()
        prefs.edit().remove(HIDDEN).apply()
    }

    // --- Fortsetzen: letzte Position je Diashow ---

    /** Zuletzt gezeigtes Foto einer Diashow ([showId] = Album bzw. „alle“, Absender). */
    fun resumeKey(showId: String): String? = prefs.getString(RESUME + showId, null)

    private val _lastShow = MutableStateFlow(readLastShow())
    /** Die zuletzt gesehene Diashow (für „Weiter ansehen“ auf der Startseite). */
    val lastShow: StateFlow<LastShow?> = _lastShow

    fun saveResume(show: LastShow) {
        _lastShow.value = show
        prefs.edit()
            .putString(RESUME + show.showId, show.photoKey)
            .putString(
                "letzte_diashow",
                listOf(show.albumKey ?: "", show.sender ?: "", show.photoKey, show.position, show.total, show.title)
                    .joinToString("\t"),
            )
            .apply()
    }

    private fun readLastShow(): LastShow? {
        val p = (prefs.getString("letzte_diashow", null) ?: return null).split('\t')
        if (p.size < 6) return null
        return LastShow(p[0].ifEmpty { null }, p[1].ifEmpty { null }, p[2], p[3].toIntOrNull() ?: 0, p[4].toIntOrNull() ?: 0, p[5])
    }

    private fun newToken(): String {
        val alphabet = "abcdefghjkmnpqrstuvwxyz23456789"
        val random = SecureRandom()
        val t = (1..6).map { alphabet[random.nextInt(alphabet.length)] }.joinToString("")
        prefs.edit().putString("zugang", t).apply()
        return t
    }

    private fun read(): SlideSettings {
        val d = SlideSettings()
        return SlideSettings(
            intervalSec = prefs.getInt("intervall", d.intervalSec).takeIf { it in INTERVALS } ?: d.intervalSec,
            transition = enumOr(prefs.getString("uebergang", null), d.transition),
            order = enumOr(prefs.getString("reihenfolge", null), d.order),
            fit = enumOr(prefs.getString("anpassen", null), d.fit),
            background = enumOr(prefs.getString("hintergrund", null), d.background),
            showInfo = prefs.getBoolean("infos", d.showInfo),
            loop = prefs.getBoolean("wiederholen", d.loop),
            liveNew = prefs.getBoolean("neue_sofort", d.liveNew),
            showQr = prefs.getBoolean("qr_in_diashow", d.showQr),
            storageId = prefs.getString("speicherort", null) ?: d.storageId,
            brightness = prefs.getInt("helligkeit", 0).coerceIn(Picture.BRIGHTNESS),
            contrast = prefs.getInt("kontrast", 0).coerceIn(Picture.CONTRAST),
            saturation = prefs.getInt("saettigung", 0).coerceIn(Picture.SATURATION),
            warmth = prefs.getInt("waerme", 0).coerceIn(Picture.WARMTH),
            autoEnhance = prefs.getBoolean("auto_verbessern", false),
            sharpness = prefs.getInt("schaerfe", 0).coerceIn(Picture.SHARPNESS),
            idleMinutes = prefs.getInt("leerlauf_minuten", 0).takeIf { it in IDLE_MINUTES } ?: 0,
            startWithShow = prefs.getBoolean("start_mit_diashow", false),
            showClock = prefs.getBoolean("uhr", false),
            layout = enumOr(prefs.getString("anordnung", null), d.layout),
        )
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: default

    private companion object {
        const val ROT = "drehung:"
        const val FAV = "favoriten"
        const val HIDDEN = "ausgeblendet"
        const val RESUME = "weiter:"
    }
}
