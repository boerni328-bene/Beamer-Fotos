package com.beamerfotos.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beamerfotos.app.AppGraph
import com.beamerfotos.app.data.Names
import com.beamerfotos.app.data.Photo
import com.beamerfotos.app.image.ImageLoader
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val Navy = Color(0xFF1B2A41)
val Panel = Color(0xFF26364F)
val PanelLight = Color(0xFF34486A)
val Gold = Color(0xFFF2C14E)
val Sky = Color(0xFF5FA8D3)
val Muted = Color(0xFFB8C4D6)

@Composable
fun BeamerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Gold, onPrimary = Navy, background = Navy, surface = Panel, onSurface = Color.White,
        ),
        content = content,
    )
}

val shadowText = TextStyle(shadow = Shadow(Color.Black, Offset(2f, 2f), 6f))

/** Rahmen und leichte Vergrößerung, wenn ein Element mit der Fernbedienung ausgewählt ist. */
fun Modifier.tvFocus(shape: RoundedCornerShape = RoundedCornerShape(12.dp)): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, label = "fokus")
    this
        .onFocusChanged { focused = it.isFocused }
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .border(if (focused) 3.dp else 0.dp, if (focused) Gold else Color.Transparent, shape)
}

@Composable
fun TvButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    primary: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val background = when {
        focused -> Gold
        primary -> PanelLight
        else -> Panel
    }
    val foreground = if (focused) Navy else Color.White
    Row(
        modifier
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = foreground, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = foreground, fontSize = 18.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun QrCode(text: String, modifier: Modifier = Modifier) {
    val matrix = remember(text) {
        runCatching {
            QRCodeWriter().encode(
                text, BarcodeFormat.QR_CODE, 0, 0,
                mapOf(EncodeHintType.MARGIN to 2, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M),
            )
        }.getOrNull()
    }
    Canvas(modifier.background(Color.White)) {
        val m = matrix ?: return@Canvas
        val cell = size.minDimension / m.width
        for (y in 0 until m.height) {
            for (x in 0 until m.width) {
                if (m.get(x, y)) drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell + 0.6f, cell + 0.6f))
            }
        }
    }
}

@Composable
fun rememberThumb(file: File): ImageBitmap? {
    val state = remember(file.absolutePath) { mutableStateOf(ImageLoader.cachedThumb(file)) }
    LaunchedEffect(file.absolutePath) {
        if (state.value == null) state.value = ImageLoader.thumb(file)
    }
    return state.value
}

@Composable
fun Thumb(file: File, modifier: Modifier = Modifier, rotation: Int = 0) {
    val bitmap = rememberThumb(file)
    Box(modifier.background(Panel)) {
        if (bitmap != null) {
            Image(
                bitmap, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().rotate(rotation.toFloat()),
            )
        }
        if (Names.isVideo(file.name)) {
            // Videos erkennt man am Abspielzeichen.
            Icon(
                Icons.Filled.PlayArrow, contentDescription = "Video", tint = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0x99000000))
                    .padding(4.dp),
            )
        }
    }
}

/** „12 Fotos · 3 Videos“ */
fun mediaCount(photos: List<Photo>): String {
    val videos = photos.count { it.isVideo }
    val images = photos.size - videos
    return when {
        videos == 0 -> photoCount(images)
        images == 0 -> if (videos == 1) "1 Video" else "$videos Videos"
        else -> photoCount(images) + " · " + (if (videos == 1) "1 Video" else "$videos Videos")
    }
}

/** Kurzer Hinweis, wenn neue Fotos ankommen („+3 Fotos in „Anna““). */
@Composable
fun ArrivalBanner(graph: AppGraph, modifier: Modifier = Modifier) {
    var text by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        var count = 0
        var lastAlbum = ""
        graph.store.arrivals.collectLatest { a ->
            val from = a.album + a.sender
            if (from != lastAlbum) count = 0
            count++
            lastAlbum = from
            val von = if (a.sender.isNotEmpty()) " von ${a.sender}" else ""
            text = (if (count == 1) "Neues Foto" else "$count neue Fotos") + "$von in „${a.album}“"
            delay(4000)
            text = null
            count = 0
        }
    }
    AnimatedVisibility(visible = text != null, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        Text(
            text ?: "",
            color = Navy,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(Gold)
                .padding(horizontal = 22.dp, vertical = 10.dp),
        )
    }
}

fun formatBytes(bytes: Long): String {
    val gb = bytes / 1_000_000_000.0
    return if (gb >= 1) String.format(Locale.GERMANY, "%.1f GB", gb)
    else String.format(Locale.GERMANY, "%d MB", bytes / 1_000_000)
}

fun formatDate(millis: Long): String =
    SimpleDateFormat("EEE, d. MMMM yyyy · HH:mm", Locale.GERMANY).format(Date(millis))

fun photoCount(n: Int): String = if (n == 1) "1 Foto" else "$n Fotos"
