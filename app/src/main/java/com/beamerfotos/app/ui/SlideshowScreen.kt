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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.beamerfotos.app.data.Transition
import com.beamerfotos.app.data.cycle
import com.beamerfotos.app.data.intervalLabel
import com.beamerfotos.app.data.nextInterval
import com.beamerfotos.app.data.ordered
import com.beamerfotos.app.image.ImageLoader
import com.beamerfotos.app.server.RemoteCommand
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private enum class Panel { NONE, MENU, STRIP }

private const val TRANSITION_MS = 900

@Composable
private fun rememberFull(photo: Photo?, width: Int, height: Int): ImageBitmap? {
    val state = remember(photo?.key) { mutableStateOf(photo?.let { ImageLoader.cachedFull(it.file) }) }
    LaunchedEffect(photo?.key) {
        if (state.value == null && photo != null) state.value = ImageLoader.full(photo.file, width, height)
    }
    return state.value
}

/**
 * Diashow mit der Fernbedienung:
 * ◀ ▶ blättern · OK Pause/Weiter · ▲ Menü · ▼ Bildleiste · Zurück beendet.
 * Ist ein Foto vergrößert, verschieben die Pfeiltasten den Ausschnitt und OK zeigt wieder alles.
 */
@Composable
fun SlideshowScreen(graph: AppGraph, albumKey: String?, startKey: String?) {
    val settings by graph.settings.state.collectAsState()
    val albums by graph.store.albums.collectAsState()
    val rotations by graph.settings.rotations.collectAsState()
    val url = rememberUploadUrl(graph)
    val scope = rememberCoroutineScope()

    fun source(list: List<Album>): List<Photo> =
        if (albumKey == null) list.flatMap { it.photos } else list.filter { it.key == albumKey }.flatMap { it.photos }

    val seed = remember { System.currentTimeMillis() }
    val photos = remember { mutableStateListOf<Photo>().apply { addAll(ordered(source(albums), settings.order, seed)) } }
    var index by remember { mutableIntStateOf(photos.indexOfFirst { it.key == startKey }.coerceAtLeast(0)) }
    var previous by remember { mutableStateOf<Photo?>(null) }
    var previousKb by remember { mutableFloatStateOf(0f) }
    var direction by remember { mutableIntStateOf(1) }
    val transition = remember { Animatable(1f) }
    val kenBurns = remember { Animatable(0f) }
    val kbFor = remember { arrayOfNulls<String>(1) }
    var paused by remember { mutableStateOf(false) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var panel by remember { mutableStateOf(Panel.NONE) }
    var pendingNew by remember { mutableIntStateOf(0) }
    val rootFocus = remember { FocusRequester() }

    fun goTo(target: Int, dir: Int) {
        if (photos.isEmpty()) return
        val newIndex = target.mod(photos.size)
        previous = photos.getOrNull(index)
        previousKb = kenBurns.value
        index = newIndex
        direction = dir
        zoom = 1f
        pan = Offset.Zero
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
    LaunchedEffect(albums) {
        val all = source(albums)
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
            if (first && panel == Panel.NONE && zoom == 1f) goTo(index + 1, 1)
        } else {
            photos.addAll(fresh)
        }
    }

    // Weiterschalten nach der Anzeigedauer (nicht bei Pause, offenem Menü oder Vergrößerung).
    LaunchedEffect(photos.getOrNull(index)?.key, paused, settings.intervalSec, panel, zoom) {
        if (!paused && settings.intervalSec > 0 && panel == Panel.NONE && zoom == 1f && photos.size > 1) {
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

    LaunchedEffect(panel) { if (panel == Panel.NONE) runCatching { rootFocus.requestFocus() } }
    BackHandler(enabled = panel != Panel.NONE) { panel = Panel.NONE }
    BackHandler(enabled = panel == Panel.NONE && zoom > 1f) {
        zoom = 1f
        pan = Offset.Zero
    }

    val current = photos.getOrNull(index)

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .onPreviewKeyEvent { e ->
                if (panel != Panel.NONE || e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.DirectionRight, Key.MediaNext, Key.MediaFastForward -> {
                        if (zoom > 1f) panBy(-0.1f, 0f) else next(manual = true)
                        true
                    }
                    Key.DirectionLeft, Key.MediaPrevious, Key.MediaRewind -> {
                        if (zoom > 1f) panBy(0.1f, 0f) else prev()
                        true
                    }
                    Key.DirectionUp -> {
                        if (zoom > 1f) panBy(0f, 0.1f) else panel = Panel.MENU
                        true
                    }
                    Key.DirectionDown -> {
                        if (zoom > 1f) panBy(0f, -0.1f) else if (photos.isNotEmpty()) panel = Panel.STRIP
                        true
                    }
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.MediaPlayPause, Key.Spacebar -> {
                        if (zoom > 1f) {
                            zoom = 1f
                            pan = Offset.Zero
                        } else {
                            togglePause()
                        }
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
                        panel = Panel.MENU
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
        val currentImage = rememberFull(current, widthPx, heightPx)
        val previousImage = rememberFull(previous, widthPx, heightPx)

        // Nächstes Foto schon vorab laden.
        LaunchedEffect(index, photos.size) {
            photos.getOrNull((index + 1).mod(max(1, photos.size)))?.let { ImageLoader.full(it.file, widthPx, heightPx) }
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

        Canvas(Modifier.fillMaxSize()) {
            if (showPrevious && previousImage != null) {
                drawPhoto(
                    previousImage, rotPrevious, settings.fit, alphaPrevious, slidePrevious,
                    kbZoom(previousKb), kbPan(previous, previousKb),
                )
            }
            if (currentImage != null) {
                val kb = kenBurns.value
                val z = if (zoom > 1f) zoom else kbZoom(kb)
                val o = if (zoom > 1f) pan else kbPan(current, kb)
                drawPhoto(currentImage, rotCurrent, settings.fit, alphaCurrent, slideCurrent, z, o)
            }
        }

        if (photos.isEmpty()) EmptyShow(url)

        // Einblendungen
        if (settings.showInfo && current != null && panel == Panel.NONE) {
            Column(Modifier.align(Alignment.BottomStart).padding(28.dp)) {
                Text(current.album, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, style = shadowText)
                Text(
                    formatDate(current.captured) + "   ·   ${index + 1} / ${photos.size}",
                    color = Color.White, fontSize = 16.sp, style = shadowText,
                )
            }
        }
        if (paused || zoom > 1f) {
            Text(
                if (zoom > 1f) "${zoom.roundToInt()}× · Pfeile verschieben · OK zurück" else "Pause",
                color = Color.White, fontSize = 18.sp, style = shadowText,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(24.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0x99000000))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        if (settings.showQr && url != null && photos.isNotEmpty() && panel == Panel.NONE) {
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
            Panel.MENU -> MenuPanel(
                graph = graph,
                paused = paused,
                zoom = zoom,
                hasPhoto = current != null,
                onTogglePause = { togglePause() },
                onRotate = { current?.let { graph.settings.rotate(it.key) } },
                onZoom = {
                    zoom = if (zoom >= 3f) 1f else zoom + 1f
                    pan = Offset.Zero
                    if (zoom > 1f) panel = Panel.NONE
                },
                onDelete = {
                    current?.let { graph.store.delete(it) }
                    panel = Panel.NONE
                },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
            Panel.STRIP -> Filmstrip(
                photos = photos,
                currentIndex = index,
                rotations = rotations,
                onPick = { i ->
                    panel = Panel.NONE
                    if (i != index) goTo(i, if (i > index) 1 else -1)
                },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
            Panel.NONE -> Unit
        }
    }
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
    zoom: Float,
    hasPhoto: Boolean,
    onTogglePause: () -> Unit,
    onRotate: () -> Unit,
    onZoom: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s by graph.settings.state.collectAsState()
    val first = remember { FocusRequester() }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    Column(
        modifier
            .fillMaxWidth()
            .background(Color(0xCC0F1826))
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Text("◀ ▶ auswählen · OK ändern · Zurück schließt", color = Muted, fontSize = 14.sp)
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TvButton(
                if (paused) "Weiter" else "Pause", onClick = onTogglePause, icon = Icons.Filled.PlayArrow,
                modifier = Modifier.focusRequester(first),
            )
            if (hasPhoto) {
                TvButton("Drehen", onClick = onRotate, icon = Icons.Filled.Refresh)
                TvButton("Zoom ${zoom.roundToInt()}×", onClick = onZoom)
            }
            TvButton("Dauer: ${intervalLabel(s.intervalSec)}", onClick = {
                graph.settings.update { it.copy(intervalSec = nextInterval(it.intervalSec)) }
            })
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
            if (hasPhoto) {
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
