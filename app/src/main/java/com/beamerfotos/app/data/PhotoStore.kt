package com.beamerfotos.app.data

import android.content.Context
import android.media.ExifInterface
import android.os.Environment
import com.beamerfotos.app.image.ImageLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

data class Photo(val file: File, val album: String, val captured: Long, val arrived: Long) {
    val key: String get() = file.absolutePath
}

/** Ein Speicherort: intern oder ein USB-Speicher. Fotos liegen in <root>/<Album>/<Datei>. */
data class Location(val id: String, val label: String, val root: File, val removable: Boolean) {
    fun free(): Long = runCatching { root.mkdirs(); root.usableSpace }.getOrDefault(0L)
}

data class Album(val dir: File, val name: String, val location: Location, val photos: List<Photo>) {
    val key: String get() = dir.absolutePath
    val latest: Long = photos.maxOfOrNull { it.arrived } ?: 0L
    val cover: Photo? get() = photos.maxByOrNull { it.arrived }
}

data class Arrival(val file: File, val album: String)

sealed interface SaveResult {
    data class Ok(val file: File) : SaveResult
    data class Error(val status: Int, val message: String) : SaveResult
}

class PhotoStore(
    private val context: Context,
    private val settings: SettingsStore,
    scope: CoroutineScope,
) {
    private val _albums = MutableStateFlow<List<Album>>(emptyList())
    val albums: StateFlow<List<Album>> = _albums

    private val _arrivals = MutableSharedFlow<Arrival>(extraBufferCapacity = 64)
    /** Jedes neu empfangene Foto (für Hinweise auf dem Bildschirm). */
    val arrivals: SharedFlow<Arrival> = _arrivals

    private val refreshRequests = Channel<Unit>(Channel.CONFLATED)
    private val capturedCache = ConcurrentHashMap<String, Pair<Long, Long>>()
    private val renameLock = Any()

    init {
        scope.launch(Dispatchers.IO) {
            for (request in refreshRequests) {
                _albums.value = scan()
                // Kommen viele Fotos auf einmal, höchstens etwa einmal pro Sekunde neu einlesen.
                delay(700)
            }
        }
        requestRefresh()
    }

    fun requestRefresh() {
        refreshRequests.trySend(Unit)
    }

    /** Intern immer; dazu jeder eingesteckte USB-Speicher (App-Ordner, braucht keine Berechtigung). */
    fun locations(): List<Location> {
        val list = mutableListOf(Location(LOCATION_INTERNAL, "Intern", File(context.filesDir, "Fotos"), false))
        val external = runCatching { context.getExternalFilesDirs(null).toList() }.getOrDefault(emptyList())
        // Eintrag 0 ist der eingebaute Speicher, alle weiteren sind Wechselspeicher (USB, SD-Karte).
        val removable = external.drop(1).filterNotNull().filter {
            runCatching { Environment.getExternalStorageState(it) == Environment.MEDIA_MOUNTED }.getOrDefault(false)
        }
        removable.forEachIndexed { i, dir ->
            val label = if (removable.size > 1) "USB-Speicher ${i + 1}" else "USB-Speicher"
            list += Location("usb:" + dir.absolutePath, label, File(dir, "Fotos"), true)
        }
        return list
    }

    /** Speicherort für neue Fotos; fehlt der gewählte USB-Speicher, wird intern gespeichert. */
    fun uploadLocation(): Location {
        val all = locations()
        return all.firstOrNull { it.id == settings.state.value.storageId } ?: all.first()
    }

    fun save(albumRaw: String?, nameRaw: String?, length: Long, input: InputStream): SaveResult {
        val name = Names.fileName(nameRaw)
            ?: return SaveResult.Error(415, "Nur Fotos (JPG, PNG, HEIC, WEBP, GIF) werden angenommen.")
        val location = uploadLocation()
        if (location.free() < length + RESERVE_BYTES) {
            return SaveResult.Error(507, "Der Speicher am Beamer ist voll.")
        }
        val albumName = Names.albumName(albumRaw, SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY).format(Date()))
        val dir = File(location.root, albumName)
        if (!dir.isDirectory && !dir.mkdirs()) return SaveResult.Error(500, "Ordner am Beamer nicht anlegbar.")

        val tmp = File(dir, ".${System.nanoTime()}.part")
        val target = try {
            tmp.outputStream().buffered(256 * 1024).use { out ->
                val buffer = ByteArray(64 * 1024)
                var left = length
                while (left > 0) {
                    val n = input.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
                    if (n < 0) throw IOException("Übertragung abgebrochen")
                    out.write(buffer, 0, n)
                    left -= n
                }
            }
            synchronized(renameLock) {
                val free = File(dir, Names.unique(name) { File(dir, it).exists() })
                if (!tmp.renameTo(free)) throw IOException("Umbenennen fehlgeschlagen")
                free
            }
        } catch (e: IOException) {
            tmp.delete()
            return SaveResult.Error(400, "Übertragung abgebrochen.")
        }
        _arrivals.tryEmit(Arrival(target, albumName))
        requestRefresh()
        return SaveResult.Ok(target)
    }

    fun delete(photo: Photo) {
        ImageLoader.forget(photo.file)
        photo.file.delete()
        settings.forgetRotation(photo.key)
        requestRefresh()
    }

    fun deleteAlbum(album: Album) {
        album.photos.forEach {
            ImageLoader.forget(it.file)
            settings.forgetRotation(it.key)
        }
        album.dir.deleteRecursively()
        requestRefresh()
    }

    fun deleteAll() {
        _albums.value.forEach { deleteAlbum(it) }
    }

    private fun scan(): List<Album> = locations().flatMap { location ->
        location.root.listFiles().orEmpty()
            .filter { it.isDirectory && !it.name.startsWith(".") }
            .map { dir ->
                val files = dir.listFiles().orEmpty()
                cleanUpLeftovers(files)
                val photos = files.filter { it.isFile && Names.isImage(it.name) }.map { photo(it, dir.name) }
                Album(dir, dir.name, location, photos)
            }
    }.filter { it.photos.isNotEmpty() }.sortedByDescending { it.latest }

    /** Reste abgebrochener Übertragungen (älter als eine Stunde) entfernen. */
    private fun cleanUpLeftovers(files: Array<out File>) {
        val limit = System.currentTimeMillis() - 60 * 60 * 1000L
        files.filter { it.name.endsWith(".part") && it.lastModified() < limit }.forEach { it.delete() }
    }

    private fun photo(file: File, album: String): Photo {
        val modified = file.lastModified()
        val cached = capturedCache[file.absolutePath]
        val captured = if (cached != null && cached.first == modified) {
            cached.second
        } else {
            (readCaptured(file) ?: modified).also { capturedCache[file.absolutePath] = modified to it }
        }
        return Photo(file, album, captured, modified)
    }

    /** Aufnahmezeit aus den EXIF-Daten (bleibt auch bei iPhone-Fotos erhalten). */
    private fun readCaptured(file: File): Long? = try {
        val exif = ExifInterface(file.absolutePath)
        val text = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
            ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
        text?.let { SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).parse(it)?.time }
    } catch (e: Exception) {
        null
    }

    private companion object {
        /** So viel bleibt immer frei, damit der Beamer selbst nicht voll läuft. */
        const val RESERVE_BYTES = 200L * 1024 * 1024
    }
}
