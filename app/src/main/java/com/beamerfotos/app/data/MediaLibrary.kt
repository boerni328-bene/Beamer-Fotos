package com.beamerfotos.app.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.storage.StorageManager
import android.provider.MediaStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Fotos, die schon auf dem Beamer oder einem USB-Stick liegen (nicht über die App gekommen).
 * Gelesen über den Medienspeicher von Android; braucht die Berechtigung „Medien lesen“.
 * Diese Fotos werden nur gezeigt, nie verändert oder gelöscht. Jeder Ordner wird zu einem Album.
 */
class MediaLibrary(private val context: Context, scope: CoroutineScope) {
    private val _albums = MutableStateFlow<List<Album>>(emptyList())
    val albums: StateFlow<List<Album>> = _albums

    private val _granted = MutableStateFlow(hasPermission())
    val granted: StateFlow<Boolean> = _granted

    private val requests = Channel<Unit>(Channel.CONFLATED)
    private var observing = false

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = refresh()
    }

    init {
        scope.launch(Dispatchers.IO) {
            for (request in requests) {
                _albums.value = if (hasPermission()) runCatching { query() }.getOrDefault(emptyList()) else emptyList()
                // Beim Einstecken eines Sticks meldet Android viele Änderungen kurz hintereinander.
                delay(1500)
            }
        }
        if (_granted.value) startObserving()
        refresh()
    }

    /** Die Berechtigungen, die beim Beamer angefragt werden. */
    val permissions: Array<String>
        get() = if (Build.VERSION.SDK_INT >= 33) {
            arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

    private fun hasPermission(): Boolean {
        val needed = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
        return context.checkSelfPermission(needed) == PackageManager.PERMISSION_GRANTED
    }

    /** Nach der Rückfrage oder der Rückkehr aus den Einstellungen erneut prüfen. */
    fun recheck() {
        val now = hasPermission()
        if (now != _granted.value) {
            _granted.value = now
            if (now) startObserving()
            refresh()
        }
    }

    fun refresh() {
        requests.trySend(Unit)
    }

    private fun startObserving() {
        if (observing) return
        observing = runCatching {
            context.contentResolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, observer)
            context.contentResolver.registerContentObserver(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, observer)
        }.isSuccess
    }

    @Suppress("DEPRECATION") // DATA (Dateipfad) ist veraltet, funktioniert mit der Berechtigung aber auf allen Versionen.
    private fun query(): List<Album> {
        val projection = arrayOf(
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_MODIFIED,
        )
        val byFolder = HashMap<File, MutableList<Photo>>()
        // „external“ umfasst alle eingebauten und eingesteckten Speicher.
        for (uri in listOf(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)) context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
            val iData = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
            val iTaken = c.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN)
            val iModified = c.getColumnIndex(MediaStore.Images.Media.DATE_MODIFIED)
            while (c.moveToNext()) {
                val path = c.getString(iData) ?: continue
                if ("/Android/data/" in path) continue
                val file = File(path)
                if (!Names.isMedia(file.name)) continue
                val dir = file.parentFile ?: continue
                val modified = if (iModified >= 0) c.getLong(iModified) * 1000 else 0L
                val taken = if (iTaken >= 0) c.getLong(iTaken) else 0L
                val photo = Photo(
                    file = file,
                    album = dir.name,
                    sender = "",
                    captured = if (taken > 0) taken else modified,
                    arrived = modified,
                    readOnly = true,
                )
                byFolder.getOrPut(dir) { mutableListOf() } += photo
            }
        }
        val locations = HashMap<String, Location>()
        return byFolder.map { (dir, photos) ->
            val location = locationOf(dir).let { locations.getOrPut(it.id) { it } }
            Album(dir, dir.name, location, photos)
        }.sortedWith(compareBy<Album> { it.location.removable }.thenByDescending { it.latest })
    }

    /** Speicher eines Ordners: der eingebaute („Beamer“) oder ein USB-Stick (/storage/<Kennung>/…). */
    private fun locationOf(dir: File): Location {
        val path = dir.absolutePath
        val primary = Environment.getExternalStorageDirectory().absolutePath
        if (path.startsWith(primary)) return Location("media:$primary", "Beamer", File(primary), removable = false, readOnly = true)
        val parts = path.split('/').filter { it.isNotEmpty() }
        val root = if (parts.size >= 2) "/${parts[0]}/${parts[1]}" else path
        val label = runCatching {
            val manager = context.getSystemService(StorageManager::class.java)
            manager.storageVolumes.firstOrNull { it.uuid != null && it.uuid.equals(parts.getOrNull(1), ignoreCase = true) }
                ?.getDescription(context)
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: "USB-Speicher"
        return Location("media:$root", label, File(root), removable = true, readOnly = true)
    }
}
