package com.beamerfotos.app.server

import android.content.Context
import com.beamerfotos.app.data.Photo
import com.beamerfotos.app.data.PhotoStore
import com.beamerfotos.app.image.ImageLoader
import com.beamerfotos.app.music.playStation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import com.beamerfotos.app.data.SaveResult
import com.beamerfotos.app.data.SettingsStore
import com.beamerfotos.app.music.MusicPlayer
import com.beamerfotos.app.music.RadioStore
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import kotlin.concurrent.thread

/** Befehle der Fernbedienung auf der Handy-Seite. */
sealed interface RemoteCommand {
    data object Next : RemoteCommand
    data object Prev : RemoteCommand
    data object Toggle : RemoteCommand
    data object Start : RemoteCommand
    /** Zu Foto Nummer [index] (ab 0) der laufenden Diashow. */
    data class GoTo(val index: Int) : RemoteCommand
    /** Vergrößerung mit den Fingern: Faktor und Verschiebung (Anteil der Bildfläche). */
    data class ZoomTo(val zoom: Float, val x: Float, val y: Float) : RemoteCommand
}

/** Was die laufende Diashow gerade zeigt (für die Handy-Seite). */
data class ShowState(val photos: List<Photo>, val index: Int, val group: Int, val paused: Boolean, val title: String)

/**
 * Kleiner Webserver im WLAN. Er liefert die Upload-Seite für die Handys aus und nimmt die Fotos an.
 * Läuft nur, solange die App geöffnet ist. Ohne den Zugangscode aus dem QR-Code wird nichts angenommen.
 */
class UploadServer(
    private val context: Context,
    private val store: PhotoStore,
    private val settings: SettingsStore,
    private val music: MusicPlayer,
    private val radio: RadioStore,
) {
    private val _commands = MutableSharedFlow<RemoteCommand>(extraBufferCapacity = 16)
    val commands: SharedFlow<RemoteCommand> = _commands

    /** Von der Diashow gesetzt, solange sie läuft. */
    val show = MutableStateFlow<ShowState?>(null)
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _port = MutableStateFlow<Int?>(null)
    val port: StateFlow<Int?> = _port

    @Volatile private var serverSocket: ServerSocket? = null
    private var pool: ExecutorService? = null
    private val page: ByteArray by lazy { context.assets.open("web/index.html").use { it.readBytes() } }

    @Synchronized
    fun start() {
        if (serverSocket != null) return
        val socket = PORTS.firstNotNullOfOrNull { p ->
            try {
                ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(p))
                }
            } catch (e: IOException) {
                null
            }
        } ?: return
        val executor = Executors.newFixedThreadPool(6)
        serverSocket = socket
        pool = executor
        _port.value = socket.localPort
        thread(name = "upload-server", isDaemon = true) {
            while (!socket.isClosed) {
                val client = try {
                    socket.accept()
                } catch (e: IOException) {
                    break
                }
                try {
                    executor.execute { handle(client) }
                } catch (e: RejectedExecutionException) {
                    runCatching { client.close() }
                }
            }
        }
    }

    @Synchronized
    fun stop() {
        runCatching { serverSocket?.close() }
        serverSocket = null
        pool?.shutdownNow()
        pool = null
        _port.value = null
    }

    private fun handle(client: Socket) {
        try {
            client.soTimeout = 30_000
            val input = BufferedInputStream(client.getInputStream(), 64 * 1024)
            val out = BufferedOutputStream(client.getOutputStream())
            val request = Http.readRequest(input) ?: return
            Http.writeResponse(out, route(request, input))
        } catch (e: Exception) {
            // Verbindung abgebrochen (Handy gesperrt, WLAN weg …) – nichts zu tun.
        } finally {
            runCatching { client.close() }
        }
    }

    private fun route(r: Request, input: InputStream): Response {
        val tokenOk = r.query["t"] == settings.token.value
        return when {
            r.method == "GET" && (r.path == "/" || r.path == "/index.html") ->
                Response(200, "text/html; charset=utf-8", page)
            r.path == "/favicon.ico" -> Response(204, "text/plain", ByteArray(0))
            r.path.startsWith("/api/") && !tokenOk ->
                Http.json(403, "ok" to false, "error" to "Zugang abgelaufen. Bitte den QR-Code am Beamer neu scannen.")
            r.method == "GET" && r.path == "/api/info" -> {
                val location = store.uploadLocation()
                Http.json(
                    200,
                    "ok" to true,
                    "free" to location.free(),
                    "location" to location.label,
                    "albums" to store.albums.value.map { it.name }.distinct(),
                    "music" to (music.state.value.current?.let { (if (music.state.value.playing) "♪ " else "Pause · ") + it.title } ?: ""),
                )
            }
            (r.method == "PUT" || r.method == "POST") && r.path == "/api/upload" -> upload(r, input)
            r.method == "POST" && r.path == "/api/control" -> control(r)
            r.method == "GET" && r.path == "/api/show" -> {
                val s = show.value
                if (s == null) Http.json(200, "ok" to true, "active" to false)
                else Http.json(
                    200, "ok" to true, "active" to true, "index" to s.index, "total" to s.photos.size,
                    "group" to s.group, "paused" to s.paused, "title" to s.title,
                    "video" to (s.photos.getOrNull(s.index)?.isVideo == true),
                )
            }
            r.method == "GET" && r.path == "/api/thumb" -> thumb(r)
            r.method == "GET" && r.path == "/api/stations" -> {
                val current = music.state.value.current?.takeIf { it.isRadio }?.title ?: ""
                Http.json(200, "ok" to true, "stations" to radio.stations.value.map { it.name }, "current" to current)
            }
            r.method == "POST" && r.path == "/api/radio" -> radio.add(r.query["name"], r.query["url"])
                ?.let { Http.json(400, "ok" to false, "error" to it) } ?: Http.json(200, "ok" to true)
            else -> Http.json(404, "ok" to false, "error" to "Nicht gefunden")
        }
    }

    private fun upload(r: Request, input: InputStream): Response {
        val length = r.contentLength
            ?: return Http.json(411, "ok" to false, "error" to "Größe der Datei fehlt.")
        if (length <= 0 || length > MAX_BYTES) {
            return Http.json(413, "ok" to false, "error" to "Datei zu groß (höchstens 8 GB).")
        }
        return when (val result = store.save(r.query["album"], r.query["sender"], r.query["name"], length, input, r.query["exifdate"])) {
            is SaveResult.Ok -> Http.json(200, "ok" to true, "name" to result.file.name)
            is SaveResult.Error -> Http.json(result.status, "ok" to false, "error" to result.message)
            is SaveResult.Duplicate -> Http.json(200, "ok" to true, "duplicate" to true, "name" to result.existing.name)
        }
    }

    private fun control(r: Request): Response {
        val command = when (r.query["cmd"]) {
            "next" -> RemoteCommand.Next
            "prev" -> RemoteCommand.Prev
            "toggle" -> RemoteCommand.Toggle
            "start" -> RemoteCommand.Start
            "goto" -> RemoteCommand.GoTo(r.query["i"]?.toIntOrNull() ?: return Http.json(400, "ok" to false))
            "zoom" -> RemoteCommand.ZoomTo(
                r.query["z"]?.toFloatOrNull()?.takeIf { it.isFinite() } ?: 1f,
                r.query["x"]?.toFloatOrNull()?.takeIf { it.isFinite() } ?: 0f,
                r.query["y"]?.toFloatOrNull()?.takeIf { it.isFinite() } ?: 0f,
            )
            "station" -> {
                val station = radio.stations.value.getOrNull(r.query["i"]?.toIntOrNull() ?: -1)
                    ?: return Http.json(400, "ok" to false, "error" to "Sender nicht gefunden")
                mainScope.launch { runCatching { music.playStation(station) } }
                return Http.json(200, "ok" to true)
            }
            "music" -> { music.toggle(); return Http.json(200, "ok" to true) }
            "music_next" -> { music.next(); return Http.json(200, "ok" to true) }
            else -> return Http.json(400, "ok" to false, "error" to "Unbekannter Befehl")
        }
        _commands.tryEmit(command)
        return Http.json(200, "ok" to true)
    }

    /** Vorschaubild von Foto Nummer i der laufenden Diashow (JPEG). */
    private fun thumb(r: Request): Response {
        val photo = show.value?.photos?.getOrNull(r.query["i"]?.toIntOrNull() ?: -1)
            ?: return Http.json(404, "ok" to false, "error" to "Kein Foto")
        val bytes = runBlocking { ImageLoader.thumbJpeg(photo.file) }
            ?: return Http.json(404, "ok" to false, "error" to "Kein Vorschaubild")
        return Response(200, "image/jpeg", bytes)
    }

    private companion object {
        val PORTS = (8080..8099).toList()
        const val MAX_BYTES = 8L * 1024 * 1024 * 1024
    }
}
