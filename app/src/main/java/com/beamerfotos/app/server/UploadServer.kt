package com.beamerfotos.app.server

import android.content.Context
import com.beamerfotos.app.data.PhotoStore
import com.beamerfotos.app.data.SaveResult
import com.beamerfotos.app.data.SettingsStore
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
enum class RemoteCommand { NEXT, PREV, TOGGLE, START }

/**
 * Kleiner Webserver im WLAN. Er liefert die Upload-Seite für die Handys aus und nimmt die Fotos an.
 * Läuft nur, solange die App geöffnet ist. Ohne den Zugangscode aus dem QR-Code wird nichts angenommen.
 */
class UploadServer(
    private val context: Context,
    private val store: PhotoStore,
    private val settings: SettingsStore,
) {
    private val _commands = MutableSharedFlow<RemoteCommand>(extraBufferCapacity = 16)
    val commands: SharedFlow<RemoteCommand> = _commands

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
                )
            }
            (r.method == "PUT" || r.method == "POST") && r.path == "/api/upload" -> upload(r, input)
            r.method == "POST" && r.path == "/api/control" -> control(r)
            else -> Http.json(404, "ok" to false, "error" to "Nicht gefunden")
        }
    }

    private fun upload(r: Request, input: InputStream): Response {
        val length = r.contentLength
            ?: return Http.json(411, "ok" to false, "error" to "Größe der Datei fehlt.")
        if (length <= 0 || length > MAX_BYTES) {
            return Http.json(413, "ok" to false, "error" to "Datei zu groß (höchstens 300 MB).")
        }
        return when (val result = store.save(r.query["album"], r.query["name"], length, input)) {
            is SaveResult.Ok -> Http.json(200, "ok" to true, "name" to result.file.name)
            is SaveResult.Error -> Http.json(result.status, "ok" to false, "error" to result.message)
        }
    }

    private fun control(r: Request): Response {
        val command = when (r.query["cmd"]) {
            "next" -> RemoteCommand.NEXT
            "prev" -> RemoteCommand.PREV
            "toggle" -> RemoteCommand.TOGGLE
            "start" -> RemoteCommand.START
            else -> return Http.json(400, "ok" to false, "error" to "Unbekannter Befehl")
        }
        _commands.tryEmit(command)
        return Http.json(200, "ok" to true)
    }

    private companion object {
        val PORTS = (8080..8099).toList()
        const val MAX_BYTES = 300L * 1024 * 1024
    }
}
