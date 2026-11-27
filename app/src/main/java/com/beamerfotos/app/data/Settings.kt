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
)

const val LOCATION_INTERNAL = "intern"

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

    /** Drehung mitnehmen, wenn ein Foto verschoben wird. */
    fun moveRotation(from: String, to: String) {
        val deg = _rotations.value[from] ?: return
        _rotations.value = _rotations.value - from + (to to deg)
        prefs.edit().remove(ROT + from).putInt(ROT + to, deg).apply()
    }

    fun forgetRotation(path: String) {
        if (path !in _rotations.value) return
        _rotations.value = _rotations.value - path
        prefs.edit().remove(ROT + path).apply()
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
        )
    }

    private inline fun <reified E : Enum<E>> enumOr(name: String?, default: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: default

    private companion object {
        const val ROT = "drehung:"
    }
}
