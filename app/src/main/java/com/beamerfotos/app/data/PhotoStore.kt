package com.beamerfotos.app.data

import android.content.Context
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
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
import java.util.TimeZone
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/** Ein Foto; [sender] ist der Name des Handys, von dem es kam (leer, wenn unbekannt). */
data class Photo(
    val file: File,
    val album: String,
    val sender: String,
    val captured: Long,
    val arrived: Long,
    /** Liegt auf dem Gerät oder USB-Stick, nicht über die App gekommen: nur zeigen, nie ändern. */
    val readOnly: Boolean = false,
) {
    val key: String get() = file.absolutePath
    val isVideo: Boolean get() = Names.isVideo(file.name)
}

/** Ein Speicherort: intern oder ein USB-Speicher. Fotos liegen in <root>/<Album>/<Datei>. */
data class Location(
    val id: String,
    val label: String,
    val root: File,
    val removable: Boolean,
    val readOnly: Boolean = false,
    /** Gesendete Musik; ohne Angabe neben den Fotos: <Speicher>/Musik. */
    val musicRoot: File = File(root.parentFile ?: root, "Musik"),
) {
    fun free(): Long = runCatching { root.mkdirs(); root.usableSpace }.getOrDefault(0L)
}

data class Album(val dir: File, val name: String, val location: Location, val photos: List<Photo>) {
    val key: String get() = dir.absolutePath
    val latest: Long = photos.maxOfOrNull { it.arrived } ?: 0L
    val cover: Photo? get() = photos.maxByOrNull { it.arrived }
    /** Absender mit Anzahl ihrer Fotos, nach Name sortiert („“ = ohne Absender). */
    val senders: List<Pair<String, Int>> =
        photos.groupingBy { it.sender }.eachCount().toList().sortedBy { it.first.lowercase() }
}

data class Arrival(val file: File, val album: String, val sender: String)

sealed interface SaveResult {
    data class Ok(val file: File) : SaveResult
    data class Error(val status: Int, val message: String) : SaveResult
    /** Gleiche Datei gibt es schon: nicht erneut gespeichert. */
    data class Duplicate(val existing: File) : SaveResult
}

class PhotoStore(
    private val context: Context,
    private val settings: SettingsStore,
    scope: CoroutineScope,
) {
    private val _albums = MutableStateFlow<List<Album>>(emptyList())
    val albums: StateFlow<List<Album>> = _albums

    private val _musicChanged = MutableStateFlow(0L)
    /** Zeitpunkt der letzten gesendeten Musik (Musikseite liest dann neu ein). */
    val musicChanged: StateFlow<Long> = _musicChanged

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

    /**
     * Intern immer; dazu jeder eingesteckte USB-Speicher. Bevorzugt sichtbar unter
     * <Stick>/Pictures/Beamer-Fotos (Musik unter <Stick>/Music/Beamer-Fotos): bleibt beim Deinstallieren
     * erhalten und ist am PC sofort zu finden. Geht das nicht, der App-Ordner Android/data/… auf dem Stick.
     */
    fun locations(): List<Location> {
        val list = mutableListOf(Location(LOCATION_INTERNAL, "Intern", File(context.filesDir, "Fotos"), false))
        val appDirs = runCatching { context.getExternalFilesDirs(null).toList() }.getOrDefault(emptyList())
            .drop(1).filterNotNull()
        val volumes = usbVolumes(appDirs)
        volumes.forEachIndexed { i, (volume, description) ->
            val label = when {
                description.isNotBlank() && volumes.count { it.second == description } == 1 -> description
                volumes.size > 1 -> "USB-Speicher ${i + 1}"
                else -> "USB-Speicher"
            }
            val public = File(volume, "Pictures/$PUBLIC_FOLDER")
            val appDir = appDirs.firstOrNull { it.absolutePath.startsWith(volume.absolutePath + "/") }
            list += when {
                Build.VERSION.SDK_INT >= 30 && (public.isDirectory || public.mkdirs()) ->
                    Location("usb:" + volume.absolutePath, label, public, true, musicRoot = File(volume, "Music/$PUBLIC_FOLDER"))
                appDir != null -> Location("usb:" + volume.absolutePath, label, File(appDir, "Fotos"), true)
                else -> return@forEachIndexed
            }
        }
        return list
    }

    /** Was Android an Speichern meldet (Hilfe, falls ein Stick nicht erscheint). */
    fun storageReport(): String = runCatching {
        val sm = context.getSystemService(StorageManager::class.java)
        sm.storageVolumes.joinToString(" · ") { v ->
            val path = if (Build.VERSION.SDK_INT >= 30) v.directory?.absolutePath ?: "kein Pfad" else ""
            "${v.getDescription(context)} (${if (v.isRemovable) "wechselbar" else "fest"}, ${v.state}${if (path.isNotEmpty()) ", $path" else ""})"
        }
    }.getOrDefault("")

    /** Eingesteckte Wechselspeicher: Wurzelordner und Name (z. B. „SanDisk USB-Speicher“). */
    private fun usbVolumes(appDirs: List<File>): List<Pair<File, String>> {
        if (Build.VERSION.SDK_INT >= 30) {
            val sm = context.getSystemService(StorageManager::class.java)
            val found = runCatching {
                sm.storageVolumes
                    .filter { it.isRemovable && it.state == Environment.MEDIA_MOUNTED }
                    .mapNotNull { v -> v.directory?.let { it to (v.getDescription(context) ?: "") } }
            }.getOrDefault(emptyList())
            if (found.isNotEmpty()) return found
        }
        // Ältere Geräte: Wurzel aus dem App-Ordner <Stick>/Android/data/<Paket>/files ableiten.
        return appDirs.filter {
            runCatching { Environment.getExternalStorageState(it) == Environment.MEDIA_MOUNTED }.getOrDefault(false)
        }.map { File(it.absolutePath.substringBefore("/Android/data/")) to "" }
    }

    /** Speicherort für neue Fotos; fehlt der gewählte USB-Speicher, wird intern gespeichert. */
    fun uploadLocation(): Location {
        val all = locations()
        return all.firstOrNull { it.id == settings.state.value.storageId } ?: all.first()
    }

    /** Speichert ein Foto in <Album>/<Absender>/; ohne Absender direkt im Album. */
    fun save(albumRaw: String?, senderRaw: String?, nameRaw: String?, length: Long, input: InputStream): SaveResult {
        val name = Names.fileName(nameRaw)
            ?: return SaveResult.Error(415, "Nur Fotos (JPG, PNG, HEIC …), Videos (MP4, MOV, MKV, AVI) und Musik (MP3, M4A, FLAC …) werden angenommen.")
        val location = uploadLocation()
        if (location.free() < length + RESERVE_BYTES) {
            return SaveResult.Error(507, "Der Speicher am Beamer ist voll.")
        }
        val albumName = Names.albumName(albumRaw, SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY).format(Date()))
        val sender = Names.senderName(senderRaw)
        val albumDir = File(location.root, albumName)
        val isMusic = Names.isAudio(name)
        val dir = when {
            isMusic -> musicDir(location)
            sender.isEmpty() -> albumDir
            else -> File(albumDir, sender)
        }
        if (!dir.isDirectory && !dir.mkdirs()) return SaveResult.Error(500, "Ordner am Beamer nicht anlegbar.")

        // Endung behalten: auf dem USB-Stick (Pictures/, Music/) nimmt Android nur passende Dateitypen an.
        val tmp = File(dir, ".${System.nanoTime()}.part." + name.substringAfterLast('.'))
        val digest = MessageDigest.getInstance("SHA-256")
        val target = try {
            tmp.outputStream().buffered(256 * 1024).use { out ->
                val buffer = ByteArray(64 * 1024)
                var left = length
                while (left > 0) {
                    val n = input.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
                    if (n < 0) throw IOException("Übertragung abgebrochen")
                    out.write(buffer, 0, n)
                    digest.update(buffer, 0, n)
                    left -= n
                }
            }
            // Dasselbe Foto schon vorhanden (z. B. von einem anderen Gast weitergeleitet)? Dann nicht doppelt speichern.
            if (!isMusic) {
                duplicateOf(length, digest.digest())?.let { existing ->
                    tmp.delete()
                    return SaveResult.Duplicate(existing)
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
        if (isMusic) {
            _musicChanged.value = System.currentTimeMillis()
            return SaveResult.Ok(target)
        }
        _arrivals.tryEmit(Arrival(target, albumName, sender))
        requestRefresh()
        return SaveResult.Ok(target)
    }

    private val hashCache = ConcurrentHashMap<String, Pair<Long, String>>()

    /** Ein eigenes Foto mit gleicher Größe und gleichem Inhalt (SHA-256), sonst null. */
    private fun duplicateOf(length: Long, hash: ByteArray): File? {
        val wanted = hash.joinToString("") { "%02x".format(it) }
        return _albums.value.asSequence().flatMap { it.photos }.map { it.file }
            .filter { it.length() == length }
            .firstOrNull { file -> hashOf(file) == wanted }
    }

    private fun hashOf(file: File): String? {
        val modified = file.lastModified()
        hashCache[file.absolutePath]?.let { (m, h) -> if (m == modified) return h }
        return runCatching {
            val d = MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered(256 * 1024).use { s ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val n = s.read(buffer)
                    if (n < 0) break
                    d.update(buffer, 0, n)
                }
            }
            d.digest().joinToString("") { "%02x".format(it) }
        }.getOrNull()?.also { hashCache[file.absolutePath] = modified to it }
    }

    fun delete(photo: Photo) = delete(listOf(photo))

    fun delete(photos: Collection<Photo>) {
        photos.filterNot { it.readOnly }.forEach { photo ->
            ImageLoader.forget(photo.file)
            photo.file.delete()
            settings.forgetRotation(photo.key)
            pruneEmpty(photo.file.parentFile)
        }
        requestRefresh()
    }

    /**
     * Verschiebt Fotos in ein anderes Album (Ordner [targetDir]); der Absender bleibt erhalten.
     * Liefert die Anzahl der verschobenen Fotos.
     */
    fun move(photos: Collection<Photo>, targetDir: File): Int {
        var moved = 0
        photos.forEach { photo ->
            val dir = if (photo.sender.isEmpty()) targetDir else File(targetDir, photo.sender)
            if (photo.file.parentFile == dir) return@forEach
            if (!dir.isDirectory && !dir.mkdirs()) return@forEach
            val target = synchronized(renameLock) {
                File(dir, Names.unique(photo.file.name) { File(dir, it).exists() })
            }
            if (photo.readOnly) {
                // Fotos vom Gerät oder USB-Stick werden nur kopiert, das Original bleibt.
                if (dir.usableSpace < photo.file.length() + RESERVE_BYTES) return@forEach
                val copied = runCatching {
                    photo.file.copyTo(target)
                    target.setLastModified(photo.file.lastModified())
                }.isSuccess
                if (copied) {
                    settings.copyRotation(photo.key, target.absolutePath)
                    moved++
                } else {
                    target.delete()
                }
                return@forEach
            }
            val ok = photo.file.renameTo(target) || runCatching {
                // Anderer Speicher (intern ↔ USB): kopieren, dann löschen.
                photo.file.copyTo(target)
                target.setLastModified(photo.file.lastModified())
                photo.file.delete()
            }.getOrDefault(false)
            if (ok) {
                ImageLoader.forget(photo.file)
                settings.moveRotation(photo.key, target.absolutePath)
                pruneEmpty(photo.file.parentFile)
                moved++
            }
        }
        requestRefresh()
        return moved
    }

    /** Neuer Ordner für ein Album am selben Speicherort wie [near] (bzw. am Upload-Speicherort). */
    fun albumDir(nameRaw: String, near: Location?): File? {
        val name = Names.clean(nameRaw)
        if (name.isEmpty()) return null
        return File((near ?: uploadLocation()).root, name)
    }

    /** Benennt ein Album um; null bei Erfolg, sonst ein Hinweis. */
    fun renameAlbum(album: Album, newRaw: String): String? {
        val name = Names.clean(newRaw)
        if (name.isEmpty()) return "Bitte einen Namen eingeben."
        if (name == album.name) return null
        val target = File(album.location.root, name)
        if (target.exists()) return "Ein Album „$name“ gibt es schon."
        if (!album.dir.renameTo(target)) return "Umbenennen nicht möglich."
        val oldPrefix = album.dir.absolutePath
        album.photos.forEach {
            ImageLoader.forget(it.file)
            settings.moveRotation(it.key, target.absolutePath + it.key.removePrefix(oldPrefix))
        }
        requestRefresh()
        return null
    }

    /**
     * Benennt einen Absender im Album um (oder fasst zwei zusammen, wenn es den Namen schon gibt).
     * null bei Erfolg, sonst ein Hinweis.
     */
    fun renameSender(album: Album, old: String, newRaw: String): String? {
        val name = Names.senderName(newRaw)
        if (name.isEmpty()) return "Bitte einen Namen eingeben."
        if (name == old) return null
        val target = File(album.dir, name)
        val photos = album.photos.filter { it.sender == old }
        if (old.isNotEmpty() && !target.exists()) {
            val source = File(album.dir, old)
            if (!source.renameTo(target)) return "Umbenennen nicht möglich."
            photos.forEach {
                ImageLoader.forget(it.file)
                settings.moveRotation(it.key, File(target, it.file.name).absolutePath)
            }
        } else {
            if (!target.isDirectory && !target.mkdirs()) return "Umbenennen nicht möglich."
            photos.forEach { photo ->
                val dest = synchronized(renameLock) {
                    File(target, Names.unique(photo.file.name) { File(target, it).exists() })
                }
                if (photo.file.renameTo(dest)) {
                    ImageLoader.forget(photo.file)
                    settings.moveRotation(photo.key, dest.absolutePath)
                    pruneEmpty(photo.file.parentFile)
                }
            }
        }
        requestRefresh()
        return null
    }

    /** Leere Absender- und Albumordner entfernen (nie den Speicherort selbst). */
    private fun pruneEmpty(dir: File?) {
        val roots = locations().map { it.root.absoluteFile }.toSet()
        var d = dir?.absoluteFile
        while (d != null && d !in roots && d.isDirectory && d.list()?.isEmpty() == true) {
            d.delete()
            d = d.parentFile
        }
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

    /** Löscht gesendete Musikstücke; Anzahl gelöschter. */
    fun deleteMusic(files: Collection<File>): Int {
        val n = files.count { it.delete() }
        _musicChanged.value = System.currentTimeMillis()
        return n
    }

    /** Verschiebt gesendete Musik an einen anderen Speicherort; Anzahl verschobener. */
    fun moveMusic(files: Collection<File>, target: Location): Int {
        val dir = musicDir(target)
        if (!dir.isDirectory && !dir.mkdirs()) return 0
        var moved = 0
        files.forEach { file ->
            if (file.parentFile == dir) return@forEach
            if (dir.usableSpace < file.length() + RESERVE_BYTES) return@forEach
            val to = synchronized(renameLock) { File(dir, Names.unique(file.name) { File(dir, it).exists() }) }
            val ok = file.renameTo(to) || runCatching {
                file.copyTo(to)
                to.setLastModified(file.lastModified())
                file.delete()
            }.getOrDefault(false)
            if (ok) moved++ else to.delete()
        }
        _musicChanged.value = System.currentTimeMillis()
        return moved
    }

    /** Alle Alben und alle gesendete Musik von den übrigen eigenen Speicherorten nach [target]. */
    fun moveAllTo(target: Location): Pair<Int, Int> {
        var photos = 0
        var music = 0
        val others = locations().filter { it.id != target.id }
        _albums.value.filter { a -> others.any { it.id == a.location.id } }.forEach { album ->
            photos += move(album.photos, File(target.root, album.name))
            pruneEmpty(album.dir)
        }
        others.forEach { from ->
            val files = musicDir(from).listFiles().orEmpty().filter { it.isFile && !it.name.startsWith(".") && Names.isAudio(it.name) }
            if (files.isNotEmpty()) music += moveMusic(files, target)
        }
        requestRefresh()
        return photos to music
    }

    private fun scan(): List<Album> = locations().flatMap { location ->
        location.root.listFiles().orEmpty()
            .filter { it.isDirectory && !it.name.startsWith(".") }
            .map { dir ->
                val files = dir.listFiles().orEmpty()
                val photos = photosIn(files, dir.name, "") +
                    files.filter { it.isDirectory && !it.name.startsWith(".") }
                        .flatMap { sub -> photosIn(sub.listFiles().orEmpty(), dir.name, sub.name) }
                Album(dir, dir.name, location, photos)
            }
    }.filter { it.photos.isNotEmpty() }.sortedByDescending { it.latest }

    private fun photosIn(files: Array<out File>, album: String, sender: String): List<Photo> {
        cleanUpLeftovers(files)
        return files.filter { it.isFile && !it.name.startsWith(".") && Names.isMedia(it.name) }.map { photo(it, album, sender) }
    }

    /** Reste abgebrochener Übertragungen (älter als eine Stunde) entfernen. */
    private fun cleanUpLeftovers(files: Array<out File>) {
        val limit = System.currentTimeMillis() - 60 * 60 * 1000L
        files.filter { it.name.startsWith(".") && ".part" in it.name && it.lastModified() < limit }.forEach { it.delete() }
    }

    private fun photo(file: File, album: String, sender: String): Photo {
        val modified = file.lastModified()
        val cached = capturedCache[file.absolutePath]
        val captured = if (cached != null && cached.first == modified) {
            cached.second
        } else {
            (readCaptured(file) ?: modified).also { capturedCache[file.absolutePath] = modified to it }
        }
        return Photo(file, album, sender, captured, modified)
    }

    /** Aufnahmezeit aus den EXIF-Daten (bleibt auch bei iPhone-Fotos erhalten). */
    private fun readCaptured(file: File): Long? = if (Names.isVideo(file.name)) videoCaptured(file) else try {
        val exif = ExifInterface(file.absolutePath)
        val text = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
            ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
        text?.let { SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).parse(it)?.time }
    } catch (e: Exception) {
        null
    }

    /** Aufnahmezeit eines Videos (z. B. „20261127T143000.000Z“). */
    private fun videoCaptured(file: File): Long? = try {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val text = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)
            text?.takeIf { it.length >= 15 && !it.startsWith("1904") }?.let {
                SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
                    .parse(it.take(15))?.time
            }
        } finally {
            retriever.release()
        }
    } catch (e: Exception) {
        null
    }

    companion object {
        fun musicDir(location: Location): File = location.musicRoot

        /** Ordnername auf dem USB-Stick (Pictures/… und Music/…); die Gerätebibliothek blendet ihn aus. */
        const val PUBLIC_FOLDER = "Beamer-Fotos"

        /** So viel bleibt immer frei, damit der Beamer selbst nicht voll läuft. */
        private const val RESERVE_BYTES = 200L * 1024 * 1024
    }
}
