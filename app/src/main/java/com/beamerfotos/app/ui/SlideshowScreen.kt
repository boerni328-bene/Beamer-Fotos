package com.beamerfotos.app.ui

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beamerfotos.app.AppGraph
import com.beamerfotos.app.data.Album
import com.beamerfotos.app.data.Background
import com.beamerfotos.app.data.Fit
import com.beamerfotos.app.data.Photo
import com.beamerfotos.app.data.Picture
import com.beamerfotos.app.data.Transition
import com.beamerfotos.app.data.LastShow
import com.beamerfotos.app.data.cycle
import com.beamerfotos.app.data.showIdOf
import com.beamerfotos.app.data.intervalLabel
import com.beamerfotos.app.data.nextInterval
import com.beamerfotos.app.data.ordered
import com.beamerfotos.app.image.ImageLoader
import com.beamerfotos.app.server.RemoteCommand
import com.beamerfotos.app.music.switchStation
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class Overlay { NONE, MENU, STRIP, PICTURE }

/** Besondere Diashow: alle Favoriten. */
const val FAVORITES = "favoriten"

private const val TRANSITION_MS = 900
private const val MAX_ZOOM = 6f
/** Zoom: Faktor e^1,1 ≈ 3× pro Sekunde gedrückter Taste. */
private const val ZOOM_SPEED = 1.1f
/** Verschieben: Anteil der Bildbreite pro Sekunde (bei 1×). */
private const val PAN_SPEED = 0.6f

@Composable
private fun rememberFull(photo: Photo?, width: Int, height: Int, auto: Boolean, sharpness: Int): ImageBitmap? {
    val state = remember(photo?.key) { mutableStateOf(photo?.let { ImageLoader.cachedFull(it.file, auto, sharpness) }) }
    // Ändert sich die Verbesserung, bleibt das bisherige Bild stehen, bis das neue berechnet ist.
    LaunchedEffect(photo?.key, auto, sharpness) {
        if (photo != null) ImageLoader.full(photo.file, width, height, auto, sharpness)?.let { state.value = it }
    }
    return state.value
}

/**
 * Diashow mit der Fernbedienung:
 * ◀ ▶ blättern · OK Pause/Weiter · ▲ Menü · ▼ Bildleiste · Zurück beendet.
 * Ist ein Foto vergrößert, verschieben die Pfeiltasten den Ausschnitt und OK zeigt wieder alles.
 */
@Composable
fun SlideshowScreen(graph: AppGraph, albumKey: String?, startKey: String?, sender: String? = null) {
    val settings by graph.settings.state.collectAsState()
    val albums by graph.store.albums.collectAsState()
    val mediaAlbums by graph.media.albums.collectAsState()
    val rotations by graph.settings.rotations.collectAsState()
    val favorites by graph.settings.favorites.collectAsState()
    val hidden by graph.settings.hidden.collectAsState()
    val url = rememberUploadUrl(graph)
    val scope = rememberCoroutineScope()

    // Ohne Album: alle eigenen Fotos. „media:…“: alle Fotos eines Speichers (Gerät oder USB-Stick).
    fun source(own: List<Album>, media: List<Album>): List<Photo> = when {
        albumKey == null -> own.flatMap { it.photos }
        albumKey == FAVORITES -> (own + media).flatMap { it.photos }.filter { it.key in favorites }.distinctBy { it.key }
        albumKey.startsWith("media:") -> media.filter { it.location.id == albumKey }.flatMap { it.photos }
        else -> (own + media).filter { it.key == albumKey }.flatMap { it.photos }
    }.filter { (sender == null || it.sender == sender) && it.key !in hidden }

    val seed = remember { System.currentTimeMillis() }
    val photos = remember { mutableStateListOf<Photo>().apply { addAll(ordered(source(albums, mediaAlbums), settings.order, seed)) } }
    // Ohne bestimmtes Startfoto dort weitermachen, wo diese Diashow zuletzt war.
    val showId = showIdOf(albumKey, sender)
    val firstKey = startKey ?: graph.settings.resumeKey(showId)
    var index by remember { mutableIntStateOf(photos.indexOfFirst { it.key == firstKey }.coerceAtLeast(0)) }
    var previous by remember { mutableStateOf<Photo?>(null) }
    var previousKb by remember { mutableFloatStateOf(0f) }
    var direction by remember { mutableIntStateOf(1) }
    val transition = remember { Animatable(1f) }
    val kenBurns = remember { Animatable(0f) }
    val kbFor = remember { arrayOfNulls<String>(1) }
    var paused by remember { mutableStateOf(false) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var zoomMode by remember { mutableStateOf(false) }
    var panMode by remember { mutableStateOf(false) }
    var zoomDir by remember { mutableIntStateOf(0) }
    var panDir by remember { mutableStateOf(Offset.Zero) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var panel by remember { mutableStateOf(Overlay.NONE) }
    var pendingNew by remember { mutableIntStateOf(0) }
    val rootFocus = remember { FocusRequester() }
    val videoControl = remember { VideoControl() }
    var videoError by remember { mutableStateOf<String?>(null) }

    fun endZoom() {
        zoomMode = false
        panMode = false
        zoomDir = 0
        panDir = Offset.Zero
        zoom = 1f
        pan = Offset.Zero
    }

    fun goTo(target: Int, dir: Int) {
        if (photos.isEmpty()) return
        val newIndex = target.mod(photos.size)
        previous = photos.getOrNull(index)
        previousKb = kenBurns.value
        index = newIndex
        direction = dir
        endZoom()
        if (pendingNew > 0) pendingNew--
        val duration = if (settings.transition == Transition.NONE) 0 else TRANSITION_MS
        scope.launch {
            transition.snapTo(0f)
            transition.animateTo(1f, tween(duration))
        }
    }

    fun next(manual: Boolean) {
        when {
            photos.isEmpty() -> Unit
            index < photos.size - 1 -> goTo(index + 1, 1)
            settings.loop || manual -> goTo(0, 1)
            else -> paused = true
        }
    }

    fun prev() = goTo(index - 1, -1)

    fun togglePause() {
        if (paused && !settings.loop && index == photos.size - 1) {
            paused = false
            goTo(0, 1)
        } else {
            paused = !paused
        }
    }

    fun panBy(dx: Float, dy: Float) {
        val limit = (zoom - 1f) / 2f
        pan = Offset((pan.x + dx / zoom).coerceIn(-limit, limit), (pan.y + dy / zoom).coerceIn(-limit, limit))
    }

    // Neue und gelöschte Fotos übernehmen, ohne die laufende Diashow neu zu starten.
    LaunchedEffect(albums, mediaAlbums, hidden, favorites) {
        val all = source(albums, mediaAlbums)
        val allKeys = all.map { it.key }.toHashSet()
        if (photos.any { it.key !in allKeys }) {
            val currentKey = photos.getOrNull(index)?.key
            photos.removeAll { it.key !in allKeys }
            val stillThere = photos.indexOfFirst { it.key == currentKey }
            index = if (stillThere >= 0) stillThere else index.coerceAtMost(photos.size - 1).coerceAtLeast(0)
            previous = null
            pendingNew = 0
        }
        val known = photos.map { it.key }.toHashSet()
        val fresh = all.filter { it.key !in known }.sortedBy { it.arrived }
        if (fresh.isEmpty()) return@LaunchedEffect
        if (photos.isEmpty()) {
            photos.addAll(fresh)
            index = 0
            previous = null
            transition.snapTo(0f)
            transition.animateTo(1f, tween(TRANSITION_MS))
        } else if (settings.liveNew) {
            val position = (index + 1 + pendingNew).coerceAtMost(photos.size)
            photos.addAll(position, fresh)
            val first = pendingNew == 0
            pendingNew += fresh.size
            if (first && panel == Overlay.NONE && !zoomMode) goTo(index + 1, 1)
        } else {
            photos.addAll(fresh)
        }
    }

    // Position merken (Fortsetzen und „Weiter ansehen“ auf der Startseite).
    LaunchedEffect(photos.getOrNull(index)?.key) {
        val photo = photos.getOrNull(index) ?: return@LaunchedEffect
        val title = when {
            albumKey == null -> "Alle Fotos"
            albumKey == FAVORITES -> "Favoriten"
            albumKey.startsWith("media:") -> mediaAlbums.firstOrNull { it.location.id == albumKey }?.location?.label ?: "Gerät"
            else -> (albums + mediaAlbums).firstOrNull { it.key == albumKey }?.name ?: photo.album
        } + (sender?.let { " · ${senderLabel(it)}" } ?: "")
        graph.settings.saveResume(LastShow(albumKey, sender, photo.key, index + 1, photos.size, title))
    }

    // Weiterschalten nach der Anzeigedauer (nicht bei Pause, offenem Menü oder Vergrößerung).
    LaunchedEffect(photos.getOrNull(index)?.key, paused, settings.intervalSec, panel, zoomMode) {
        val isVideo = photos.getOrNull(index)?.isVideo == true
        if (!paused && settings.intervalSec > 0 && panel == Overlay.NONE && !zoomMode && photos.size > 1 && !isVideo) {
            delay(settings.intervalSec * 1000L + TRANSITION_MS)
            next(manual = false)
        }
    }

    // Langsamer Zoom über die ganze Anzeigedauer.
    val kbTotal = (if (settings.intervalSec > 0) settings.intervalSec * 1000 else 12_000) + TRANSITION_MS
    LaunchedEffect(photos.getOrNull(index)?.key, paused, kbTotal, settings.transition) {
        val key = photos.getOrNull(index)?.key
        if (kbFor[0] != key) {
            kenBurns.snapTo(0f)
            kbFor[0] = key
        }
        if (!paused && settings.transition == Transition.KEN_BURNS) {
            val remaining = ((1f - kenBurns.value) * kbTotal).roundToInt()
            if (remaining > 0) kenBurns.animateTo(1f, tween(remaining, easing = LinearEasing))
        }
    }

    // Fernbedienung auf der Handy-Seite.
    LaunchedEffect(Unit) {
        graph.server.commands.collect {
            when (it) {
                RemoteCommand.NEXT -> next(manual = true)
                RemoteCommand.PREV -> prev()
                RemoteCommand.TOGGLE -> togglePause()
                RemoteCommand.START -> paused = false
            }
        }
    }

    LaunchedEffect(panel) { if (panel == Overlay.NONE) runCatching { rootFocus.requestFocus() } }
    BackHandler(enabled = panel != Overlay.NONE) { panel = Overlay.NONE }
    BackHandler(enabled = panel == Overlay.NONE && zoomMode) { endZoom() }

    // Stufenloser Zoom und Verschieben, solange eine Taste gedrückt ist (Bild für Bild).
    LaunchedEffect(zoomDir, panDir) {
        if (zoomDir == 0 && panDir == Offset.Zero) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val dt = ((now - last) / 1e9f).coerceAtMost(0.1f)
            last = now
            if (zoomDir != 0) zoom = (zoom * exp(zoomDir * ZOOM_SPEED * dt)).coerceIn(1f, MAX_ZOOM)
            panBy(panDir.x * PAN_SPEED * dt, panDir.y * PAN_SPEED * dt)
        }
    }

    val current = photos.getOrNull(index)

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .onPreviewKeyEvent { e ->
                if (panel != Overlay.NONE) return@onPreviewKeyEvent false
                if (zoomMode) return@onPreviewKeyEvent zoomKey(
                    e.key, e.type == KeyEventType.KeyDown, e.nativeKeyEvent.repeatCount == 0,
                    panMode = panMode,
                    setZoomDir = { zoomDir = it },
                    setPanX = { panDir = Offset(it, panDir.y) },
                    setPanY = { panDir = Offset(panDir.x, it) },
                    togglePanMode = {
                        panMode = !panMode
                        zoomDir = 0
                        panDir = Offset.Zero
                    },
                )
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.MediaFastForward, Key.MediaRewind -> {
                        if (photos.getOrNull(index)?.isVideo == true) {
                            videoControl.seekBy(if (e.key == Key.MediaFastForward) 10_000L else -10_000L)
                        } else if (e.key == Key.MediaFastForward) next(manual = true) else prev()
                        true
                    }
                    Key.DirectionRight, Key.MediaNext -> {
                        next(manual = true)
                        true
                    }
                    Key.DirectionLeft, Key.MediaPrevious -> {
                        prev()
                        true
                    }
                    Key.DirectionUp -> {
                        panel = Overlay.MENU
                        true
                    }
                    Key.DirectionDown -> {
                        if (photos.isNotEmpty()) panel = Overlay.STRIP
                        true
                    }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.MediaPlayPause, Key.Spacebar -> {
                        togglePause()
                        true
                    }
                    Key.MediaPlay -> {
                        paused = false
                        true
                    }
                    Key.MediaPause -> {
                        paused = true
                        true
                    }
                    Key.Menu -> {
                        panel = Overlay.MENU
                        true
                    }
                    else -> false
                }
            }
            .focusable()
    ) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.roundToPx() }.coerceAtLeast(1)
        val heightPx = with(density) { maxHeight.roundToPx() }.coerceAtLeast(1)
        val auto = settings.autoEnhance
        val sharpness = settings.sharpness
        val currentImage = rememberFull(current, widthPx, heightPx, auto, sharpness)
        val previousImage = rememberFull(previous, widthPx, heightPx, auto, sharpness)

        // Nächstes Foto schon vorab laden (und verbessern).
        LaunchedEffect(index, photos.size, auto, sharpness) {
            photos.getOrNull((index + 1).mod(max(1, photos.size)))?.let {
                ImageLoader.full(it.file, widthPx, heightPx, auto, sharpness)
            }
        }

        val p = transition.value
        val mode = settings.transition
        val showPrevious = previous != null && p < 1f && mode != Transition.NONE
        fun kbZoom(v: Float) = if (mode == Transition.KEN_BURNS) 1f + 0.12f * v else 1f
        fun kbPan(photo: Photo?, v: Float): Offset {
            if (mode != Transition.KEN_BURNS || photo == null) return Offset.Zero
            val h = photo.key.hashCode()
            val dx = if (h and 1 == 0) 1f else -1f
            val dy = if (h and 2 == 0) 1f else -1f
            return Offset(dx * 0.03f * v, dy * 0.02f * v)
        }
        val alphaCurrent = if (mode == Transition.FADE || mode == Transition.KEN_BURNS) p else 1f
        val alphaPrevious = if (mode == Transition.FADE || mode == Transition.KEN_BURNS) 1f - p else 1f
        val slideCurrent = if (mode == Transition.SLIDE) direction * (1f - p) else 0f
        val slidePrevious = if (mode == Transition.SLIDE) -direction * p else 0f
        val rotCurrent = current?.let { rotations[it.key] } ?: 0
        val rotPrevious = previous?.let { rotations[it.key] } ?: 0

        // Unscharfer Hintergrund hinter Hochformat-Fotos (ab Android 12, sonst abgedunkelt).
        if (settings.fit == Fit.FIT && settings.background == Background.BLUR) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            renderEffect = BlurEffect(60f, 60f, TileMode.Clamp)
                        }
                    }
            ) {
                if (showPrevious && previousImage != null) {
                    drawPhoto(previousImage, rotPrevious, Fit.FILL, alphaPrevious * 0.55f, slidePrevious, 1.1f, Offset.Zero)
                }
                if (currentImage != null) {
                    drawPhoto(currentImage, rotCurrent, Fit.FILL, alphaCurrent * 0.55f, slideCurrent, 1.1f, Offset.Zero)
                }
            }
        }

        // Bildverbesserung: Farbmatrix je Foto (Videos bleiben unverändert).
        fun filterFor(photo: Photo?): ColorFilter? {
            if (photo == null || photo.isVideo || Picture.slidersNeutral(settings)) return null
            return ColorFilter.colorMatrix(ColorMatrix(Picture.matrix(settings)))
        }

        Canvas(Modifier.fillMaxSize()) {
            if (showPrevious && previousImage != null) {
                drawPhoto(
                    previousImage, rotPrevious, settings.fit, alphaPrevious, slidePrevious,
                    kbZoom(previousKb), kbPan(previous, previousKb), filterFor(previous),
                )
            }
            if (currentImage != null) {
                val kb = kenBurns.value
                val z = if (zoomMode) zoom else if (current?.isVideo == true) 1f else kbZoom(kb)
                val o = if (zoomMode) pan else if (current?.isVideo == true) Offset.Zero else kbPan(current, kb)
                drawPhoto(currentImage, rotCurrent, settings.fit, alphaCurrent, slideCurrent, z, o, filterFor(current))
            }
        }

        // Videos: über dem Standbild abspielen, sobald der Übergang fertig ist; am Ende weiter.
        if (current != null && current.isVideo && p >= 1f) {
            key(current.key) {
                DisposableEffect(Unit) {
                    graph.music.duck(true)
                    onDispose { graph.music.duck(false) }
                }
                VideoPlayer(
                    file = current.file,
                    paused = paused,
                    volume = 1f,
                    control = videoControl,
                    onEnded = { if (settings.intervalSec > 0 && !paused) next(manual = false) },
                    onError = { videoError = current.key },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        if (current != null && videoError == current.key) {
            Text(
                "Dieses Video kann der Beamer nicht abspielen (Format wird nicht unterstützt).",
                color = Color.White, fontSize = 20.sp, style = shadowText,
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xCC000000))
                    .padding(horizontal = 24.dp, vertical = 14.dp),
            )
            LaunchedEffect(current.key) {
                delay(4000)
                if (settings.intervalSec > 0 && !paused) next(manual = false)
            }
        }

        if (photos.isEmpty()) EmptyShow(url)

        // Einblendungen
        if (settings.showClock && panel == Overlay.NONE) Clock(Modifier.align(Alignment.TopStart).padding(28.dp))
        if (settings.showInfo && current != null && panel == Overlay.NONE) {
            Column(Modifier.align(Alignment.BottomStart).padding(28.dp)) {
                Text(
                    (if (current.key in favorites) "★ " else "") +
                        current.album + if (current.sender.isNotEmpty()) "  ·  ${current.sender}" else "",
                    color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, style = shadowText,
                )
                Text(
                    formatDate(current.captured) + "   ·   ${index + 1} / ${photos.size}",
                    color = Color.White, fontSize = 16.sp, style = shadowText,
                )
            }
        }
        if (paused || zoomMode) {
            val factor = String.format(Locale.GERMANY, "%.1f×", zoom)
            Text(
                when {
                    !zoomMode -> "Pause"
                    panMode -> "Zoom $factor · Pfeile verschieben · OK: zoomen · Zurück: fertig"
                    else -> "Zoom $factor · ▲ größer · ▼ kleiner · OK: verschieben · Zurück: fertig"
                },
                color = Color.White, fontSize = 18.sp, style = shadowText,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(24.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0x99000000))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        if (settings.showQr && url != null && photos.isNotEmpty() && panel == Overlay.NONE) {
            Column(
                Modifier.align(Alignment.BottomEnd).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                QrCode(url, Modifier.size(110.dp).clip(RoundedCornerShape(6.dp)))
                Text("Fotos senden", color = Color.White, fontSize = 14.sp, style = shadowText)
            }
        }
        ArrivalBanner(graph, Modifier.align(Alignment.TopCenter).padding(top = 20.dp))

        when (panel) {
            Overlay.MENU -> MenuPanel(
                graph = graph,
                paused = paused,
                hasPhoto = current != null && !current.isVideo,
                canDelete = current?.readOnly == false,
                onTogglePause = { togglePause() },
                onRotate = { current?.let { graph.settings.rotate(it.key) } },
                isFavorite = current?.let { it.key in favorites },
                onFavorite = { current?.let { graph.settings.setFavorite(it.key, it.key !in favorites) } },
                onHide = {
                    current?.let { graph.settings.setHidden(it.key, true) }
                    panel = Overlay.NONE
                },
                onPicture = { panel = Overlay.PICTURE },
                onZoom = {
                    zoomMode = true
                    panMode = false
                    panel = Overlay.NONE
                },
                onDelete = {
                    current?.let { graph.store.delete(it) }
                    panel = Overlay.NONE
                },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
            Overlay.STRIP -> Filmstrip(
                photos = photos,
                currentIndex = index,
                rotations = rotations,
                onPick = { i ->
                    panel = Overlay.NONE
                    if (i != index) goTo(i, if (i > index) 1 else -1)
                },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
            Overlay.PICTURE -> PicturePanel(
                graph = graph,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color(0xCC0F1826)),
            )
            Overlay.NONE -> Unit
        }
    }
}

/**
 * Tasten im Zoom-Modus. Zoomen: ▲ größer, ▼ kleiner. Verschieben: Pfeile.
 * OK wechselt zwischen beiden; gedrückt halten wirkt stufenlos, Loslassen stoppt. Zurück bleibt beim BackHandler.
 */
private fun zoomKey(
    key: Key,
    down: Boolean,
    firstPress: Boolean,
    panMode: Boolean,
    setZoomDir: (Int) -> Unit,
    setPanX: (Float) -> Unit,
    setPanY: (Float) -> Unit,
    togglePanMode: () -> Unit,
): Boolean = when (key) {
    Key.DirectionUp, Key.DirectionDown -> {
        val sign = if (key == Key.DirectionUp) 1 else -1
        if (panMode) setPanY(if (down) sign.toFloat() else 0f) else setZoomDir(if (down) sign else 0)
        true
    }
    Key.DirectionLeft, Key.DirectionRight -> {
        // Rechts zeigt mehr vom rechten Bildteil: das Bild wandert nach links.
        val sign = if (key == Key.DirectionLeft) 1f else -1f
        if (panMode) setPanX(if (down) sign else 0f)
        true
    }
    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
        if (down && firstPress) togglePanMode()
        true
    }
    else -> false
}

/** Zeichnet ein Foto: gedreht, ganz oder füllend, mit Zoom, Verschiebung und Deckkraft. */
private fun DrawScope.drawPhoto(
    image: ImageBitmap,
    rotation: Int,
    fit: Fit,
    alpha: Float,
    slide: Float,
    zoom: Float,
    pan: Offset,
    colorFilter: ColorFilter? = null,
) {
    if (alpha <= 0f) return
    val rotated = rotation % 180 != 0
    val iw = image.width.toFloat()
    val ih = image.height.toFloat()
    val rw = if (rotated) ih else iw
    val rh = if (rotated) iw else ih
    val base = if (fit == Fit.FIT) min(size.width / rw, size.height / rh) else max(size.width / rw, size.height / rh)
    val scale = base * zoom
    val dw = (iw * scale).roundToInt()
    val dh = (ih * scale).roundToInt()
    val cx = size.width / 2f + slide * size.width + pan.x * size.width
    val cy = size.height / 2f + pan.y * size.height
    translate(cx, cy) {
        rotate(rotation.toFloat(), pivot = Offset.Zero) {
            drawImage(
                image,
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(image.width, image.height),
                dstOffset = IntOffset(-dw / 2, -dh / 2),
                dstSize = IntSize(dw, dh),
                alpha = alpha.coerceIn(0f, 1f),
                filterQuality = FilterQuality.High,
                colorFilter = colorFilter,
            )
        }
    }
}

@Composable
private fun EmptyShow(url: String?) {
    Column(
        Modifier.fillMaxSize().background(Navy),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Warte auf Fotos …", color = Gold, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        if (url != null) {
            QrCode(url, Modifier.size(240.dp).clip(RoundedCornerShape(8.dp)))
            Spacer(Modifier.height(12.dp))
            Text(
                "QR-Code scannen und Fotos auswählen. Sie erscheinen hier sofort.",
                color = Color.White, fontSize = 18.sp, textAlign = TextAlign.Center,
            )
        } else {
            Text("Kein WLAN verbunden.", color = Color.White, fontSize = 18.sp)
        }
    }
}

@Composable
private fun MenuPanel(
    graph: AppGraph,
    paused: Boolean,
    hasPhoto: Boolean,
    canDelete: Boolean,
    onTogglePause: () -> Unit,
    onRotate: () -> Unit,
    isFavorite: Boolean?,
    onFavorite: () -> Unit,
    onHide: () -> Unit,
    onPicture: () -> Unit,
    onZoom: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s by graph.settings.state.collectAsState()
    val first = remember { FocusRequester() }
    var confirmDelete by remember { mutableStateOf(false) }
    var musicHint by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(musicHint) {
        if (musicHint != null) {
            delay(3000)
            musicHint = null
        }
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    Column(
        modifier
            .fillMaxWidth()
            .background(Color(0xCC0F1826))
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Text(musicHint ?: "◀ ▶ auswählen · OK ändern · Zurück schließt · bei „Musik“: ▲ ▼ Sender wechseln", color = if (musicHint != null) Gold else Muted, fontSize = 14.sp)
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TvButton(
                if (paused) "Weiter" else "Pause", onClick = onTogglePause, icon = Icons.Filled.PlayArrow,
                modifier = Modifier.focusRequester(first),
            )
            if (isFavorite != null) {
                TvButton(if (isFavorite) "★ Favorit" else "☆ Favorit", onClick = onFavorite)
                TvButton("Nicht mehr zeigen", onClick = onHide)
            }
            if (hasPhoto) {
                TvButton("Drehen", onClick = onRotate, icon = Icons.Filled.Refresh)
                TvButton("Zoom", onClick = onZoom)
            }
            TvButton("Dauer: ${intervalLabel(s.intervalSec)}", onClick = {
                graph.settings.update { it.copy(intervalSec = nextInterval(it.intervalSec)) }
            })
            TvButton("Bild", onClick = onPicture)
            MusicMenuButton(graph, onHint = { musicHint = it })
            TvButton("Übergang: ${s.transition.label}", onClick = {
                graph.settings.update { it.copy(transition = it.transition.cycle()) }
            })
            TvButton(s.fit.label, onClick = { graph.settings.update { it.copy(fit = it.fit.cycle()) } })
            TvButton("Infos: ${if (s.showInfo) "an" else "aus"}", onClick = {
                graph.settings.update { it.copy(showInfo = !it.showInfo) }
            })
            TvButton("QR-Code: ${if (s.showQr) "an" else "aus"}", onClick = {
                graph.settings.update { it.copy(showQr = !it.showQr) }
            })
            if (canDelete) {
                TvButton(
                    if (confirmDelete) "Wirklich löschen?" else "Foto löschen",
                    onClick = { if (confirmDelete) onDelete() else confirmDelete = true },
                    icon = Icons.Filled.Delete,
                )
            }
        }
    }
}

@Composable
private fun Filmstrip(
    photos: List<Photo>,
    currentIndex: Int,
    rotations: Map<String, Int>,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = max(0, currentIndex - 3))
    val currentFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(50)
        runCatching { currentFocus.requestFocus() }
    }
    Column(
        modifier
            .fillMaxWidth()
            .background(Color(0xCC0F1826))
            .padding(vertical = 14.dp)
    ) {
        Text(
            "Foto wählen · OK springt hin · Zurück schließt",
            color = Muted, fontSize = 14.sp, modifier = Modifier.padding(start = 24.dp, bottom = 8.dp),
        )
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(photos, key = { _, p -> p.key }) { i, photo ->
                val base = if (i == currentIndex) Modifier.focusRequester(currentFocus) else Modifier
                Box(
                    base
                        .tvFocus(RoundedCornerShape(8.dp))
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onPick(i) }
                ) {
                    Thumb(photo.file, Modifier.width(160.dp).height(100.dp), rotations[photo.key] ?: 0)
                    if (i == currentIndex) {
                        Box(Modifier.align(Alignment.BottomStart).background(Gold).padding(horizontal = 6.dp)) {
                            Text("jetzt", color = Navy, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Menüpunkt Musik: OK = Pause/Weiter (ohne Musik startet der erste Sender),
 * ▲ ▼ = voriger/nächster gemerkter Radiosender (bei eigener Musik: Stück).
 */
@Composable
private fun MusicMenuButton(graph: AppGraph, onHint: (String) -> Unit) {
    val music by graph.music.state.collectAsState()
    val stations by graph.radio.stations.collectAsState()
    val scope = rememberCoroutineScope()
    val track = music.current
    val label = when {
        track == null -> "Musik: Radio"
        music.loading -> "Musik: verbinde …"
        else -> (if (music.playing) "♪ " else "Pause · ") + track.title.take(26)
    }
    fun switch(delta: Int) {
        scope.launch { onHint(graph.music.switchStation(stations, delta)) }
    }
    TvButton(
        label,
        onClick = { if (track == null) switch(1) else graph.music.toggle() },
        modifier = Modifier.onPreviewKeyEvent { e ->
            if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (e.key) {
                Key.DirectionUp -> { switch(-1); true }
                Key.DirectionDown -> { switch(1); true }
                else -> false
            }
        },
    )
}

/** Uhrzeit und Datum für den Bilderrahmen-Betrieb. */
@Composable
private fun Clock(modifier: Modifier = Modifier) {
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(10_000)
        }
    }
    Column(modifier) {
        Text(
            SimpleDateFormat("HH:mm", Locale.GERMANY).format(now),
            color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.Light, style = shadowText,
        )
        Text(
            SimpleDateFormat("EEEE, d. MMMM", Locale.GERMANY).format(now),
            color = Color.White, fontSize = 18.sp, style = shadowText,
        )
    }
}
