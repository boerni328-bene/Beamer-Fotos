package com.beamerfotos.app.music

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.beamerfotos.app.data.Names
import com.beamerfotos.app.data.PhotoStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/** Eine Gruppe von Stücken (Ordner bzw. Album). */
data class MusicFolder(val name: String, val where: String, val tracks: List<Track>)

/**
 * Musik zum Abspielen: über die Handy-Seite gesendete Stücke (Ordner „Musik“ neben den Fotos)
 * und – mit der Berechtigung „Musik lesen“ – Musik auf dem Beamer und auf USB-Sticks.
 */
class MusicLibrary(private val context: Context, private val store: PhotoStore) {
    private val _folders = MutableStateFlow<List<MusicFolder>>(emptyList())
    val folders: StateFlow<List<MusicFolder>> = _folders

    val permission: String
        get() = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE

    fun granted(): Boolean = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    suspend fun refresh() {
        _folders.value = withContext(Dispatchers.IO) { runCatching { own() + device() }.getOrDefault(emptyList()) }
    }

    private fun own(): List<MusicFolder> = store.locations().mapNotNull { location ->
        val dir = PhotoStore.musicDir(location)
        val tracks = dir.listFiles().orEmpty()
            .filter { it.isFile && Names.isAudio(it.name) }
            .sortedBy { it.name.lowercase() }
            .map { Track(it.nameWithoutExtension, "Gesendet", Uri.fromFile(it)) }
        if (tracks.isEmpty()) null else MusicFolder("Gesendete Musik", location.label, tracks)
    }

    private fun device(): List<MusicFolder> {
        if (!granted()) return emptyList()
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
        )
        val byAlbum = LinkedHashMap<String, MutableList<Track>>()
        context.contentResolver.query(
            uri, projection, "${MediaStore.Audio.Media.IS_MUSIC} != 0", null,
            "${MediaStore.Audio.Media.ALBUM} COLLATE NOCASE, ${MediaStore.Audio.Media.TRACK}",
        )?.use { c ->
            val iId = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val iTitle = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val iArtist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val iAlbum = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val iDuration = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            while (c.moveToNext()) {
                // Klingeltöne und kurze Systemklänge auslassen.
                if (c.getLong(iDuration) in 1 until 30_000) continue
                val artist = c.getString(iArtist)?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: ""
                val album = c.getString(iAlbum)?.takeIf { it.isNotBlank() } ?: "Ohne Album"
                val track = Track(
                    title = c.getString(iTitle) ?: "Ohne Titel",
                    subtitle = artist,
                    uri = ContentUris.withAppendedId(uri, c.getLong(iId)),
                )
                byAlbum.getOrPut(album) { mutableListOf() } += track
            }
        }
        return byAlbum.map { (album, tracks) ->
            val artists = tracks.map { it.subtitle }.filter { it.isNotEmpty() }.distinct()
            MusicFolder(album, if (artists.size == 1) artists[0] else "Gerät/USB", tracks)
        }
    }

}
