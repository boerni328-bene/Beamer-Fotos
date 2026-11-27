package com.beamerfotos.app.ui

import android.graphics.Color as AndroidColor
import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import java.io.File

/** Zugriff auf den laufenden Player (Spulen) von außen. */
class VideoControl {
    var player: ExoPlayer? = null

    fun seekBy(ms: Long) {
        val p = player ?: return
        val target = (p.currentPosition + ms).coerceIn(0L, maxOf(0L, p.duration))
        p.seekTo(target)
    }
}

/**
 * Spielt ein Video bildschirmfüllend (Seitenverhältnis bleibt) ab, ohne eigene Bedienelemente.
 * MP4, MOV, MKV, AVI, WEBM; welche Codecs gehen, hängt vom Beamer ab.
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(
    file: File,
    paused: Boolean,
    volume: Float,
    control: VideoControl,
    onEnded: () -> Unit,
    onError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val ended by rememberUpdatedState(onEnded)
    val failed by rememberUpdatedState(onError)
    val player = remember(file.absolutePath) {
        val renderers = DefaultRenderersFactory(context).setEnableDecoderFallback(true)
        ExoPlayer.Builder(context, renderers).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            prepare()
        }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) ended()
            }

            override fun onPlayerError(error: PlaybackException) = failed()
        }
        player.addListener(listener)
        control.player = player
        onDispose {
            if (control.player === player) control.player = null
            player.removeListener(listener)
            player.release()
        }
    }
    LaunchedEffect(player, paused) { player.playWhenReady = !paused }
    LaunchedEffect(player, volume) { player.volume = volume }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                setShutterBackgroundColor(AndroidColor.TRANSPARENT)
                setKeepContentOnPlayerReset(true)
                isFocusable = false
                this.player = player
            }
        },
        update = { it.player = player },
        modifier = modifier,
    )
}
