package com.beamerfotos.app.music

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Ein Musikstück oder ein Radiosender. */
/** [file] ist gesetzt bei gesendeter Musik (lässt sich löschen und verschieben). */
data class Track(val title: String, val subtitle: String, val uri: Uri, val isRadio: Boolean = false, val file: java.io.File? = null)

data class MusicState(
    val current: Track? = null,
    val playing: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
    val shuffle: Boolean = false,
    /** Titel, den der Radiosender gerade sendet (falls er ihn mitschickt). */
    val nowPlaying: String? = null,
)

/**
 * Musik und Radio für die ganze App, mit oder ohne Diashow.
 * Läuft nur, solange die App im Vordergrund ist (Entscheidung des Inhabers, kein Hintergrunddienst).
 * Alle Aufrufe landen auf dem Hauptthread, wie es der Player verlangt.
 */
@OptIn(UnstableApi::class)
class MusicPlayer(private val context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private var player: ExoPlayer? = null
    private var tracks: List<Track> = emptyList()
    private var volume = 0.8f
    private var ducked = false
    private var resumeOnForeground = false

    private val _state = MutableStateFlow(MusicState())
    val state: StateFlow<MusicState> = _state

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else main.post(block)
    }

    private fun player(): ExoPlayer = player ?: ExoPlayer.Builder(context).build().also { p ->
        // Kein automatisches Audio-Fokus-Handling: Videos in der Diashow regeln die Musik selbst (duck).
        p.setAudioAttributes(
            AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
            false,
        )
        p.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) = publish()

            override fun onPlayerError(error: PlaybackException) {
                val radio = _state.value.current?.isRadio == true
                _state.value = _state.value.copy(
                    playing = false, loading = false,
                    error = if (radio) "Sender nicht erreichbar." else "Stück lässt sich nicht abspielen.",
                )
                // Bei einer Liste das nächste Stück versuchen.
                if (!radio && p.hasNextMediaItem()) {
                    p.seekToNextMediaItem()
                    p.prepare()
                    p.play()
                }
            }

            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                val title = mediaMetadata.title?.toString()?.takeIf { it.isNotBlank() }
                _state.value = _state.value.copy(nowPlaying = title)
            }
        })
        p.volume = volume
        player = p
    }

    private fun publish() {
        val p = player ?: return
        val index = p.currentMediaItemIndex
        val current = tracks.getOrNull(index)
        _state.value = _state.value.copy(
            current = current,
            playing = p.isPlaying,
            loading = p.playbackState == Player.STATE_BUFFERING,
            error = if (p.isPlaying) null else _state.value.error,
            shuffle = p.shuffleModeEnabled,
            nowPlaying = if (current?.isRadio == true) _state.value.nowPlaying else null,
        )
    }

    /** Spielt eine Liste ab Stück [start]; [shuffle] = zufällige Reihenfolge. Am Ende von vorn. */
    fun play(list: List<Track>, start: Int = 0, shuffle: Boolean = false) = onMain {
        if (list.isEmpty()) return@onMain
        tracks = list
        val p = player()
        _state.value = _state.value.copy(error = null, nowPlaying = null)
        p.setMediaItems(list.map { MediaItem.Builder().setUri(it.uri).setMediaId(it.uri.toString()).build() }, start.coerceIn(list.indices), 0L)
        p.shuffleModeEnabled = shuffle
        p.repeatMode = if (list.size == 1 && list[0].isRadio) Player.REPEAT_MODE_OFF else Player.REPEAT_MODE_ALL
        p.prepare()
        p.play()
        publish()
    }

    fun toggle() = onMain {
        val p = player ?: return@onMain
        if (p.isPlaying) {
            p.pause()
        } else {
            if (p.playbackState == Player.STATE_IDLE || p.playbackState == Player.STATE_ENDED) p.prepare()
            p.play()
        }
    }

    fun next() = onMain { player?.takeIf { it.hasNextMediaItem() }?.seekToNextMediaItem() }

    fun previous() = onMain { player?.takeIf { it.hasPreviousMediaItem() }?.seekToPreviousMediaItem() }

    fun stop() = onMain {
        player?.stop()
        player?.clearMediaItems()
        tracks = emptyList()
        _state.value = MusicState()
    }

    fun setShuffle(on: Boolean) = onMain { player?.shuffleModeEnabled = on }

    /** Lautstärke der Musik 0–1 (unabhängig von der Lautstärke des Beamers). */
    fun setVolume(v: Float) = onMain {
        volume = v.coerceIn(0f, 1f)
        if (!ducked) player?.volume = volume
    }

    val currentVolume: Float get() = volume

    /** Während ein Video läuft, schweigt die Musik. */
    fun duck(on: Boolean) = onMain {
        ducked = on
        player?.volume = if (on) 0f else volume
    }

    fun onBackground() = onMain {
        val p = player ?: return@onMain
        resumeOnForeground = p.isPlaying
        p.pause()
    }

    fun onForeground() = onMain {
        if (resumeOnForeground) {
            player?.let {
                // Radio: neu verbinden, sonst kommt alter Puffer.
                if (_state.value.current?.isRadio == true) it.seekToDefaultPosition()
                it.prepare()
                it.play()
            }
        }
        resumeOnForeground = false
    }
}
