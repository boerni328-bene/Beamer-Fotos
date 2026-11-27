package com.beamerfotos.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beamerfotos.app.AppGraph
import com.beamerfotos.app.data.Album
import com.beamerfotos.app.server.Network
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Adresse der Upload-Seite, wie sie im QR-Code steht; null ohne Netz oder Server. */
@Composable
fun rememberUploadUrl(graph: AppGraph): String? {
    val context = LocalContext.current
    val port by graph.server.port.collectAsState()
    val token by graph.settings.token.collectAsState()
    var address by remember { mutableStateOf(Network.address(context)) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(5000)
            address = Network.address(context)
        }
    }
    val a = address
    val p = port
    return if (a != null && p != null) "http://$a:$p/?t=$token" else null
}

@Composable
fun HomeScreen(
    graph: AppGraph,
    onAlbum: (Album) -> Unit,
    onPlayAll: () -> Unit,
    onPlay: (albumKey: String?, startKey: String?, sender: String?) -> Unit,
    onSettings: () -> Unit,
    onDevice: () -> Unit,
    onMusic: () -> Unit,
) {
    val albums by graph.store.albums.collectAsState()
    val settings by graph.settings.state.collectAsState()
    val url = rememberUploadUrl(graph)
    var free by remember { mutableLongStateOf(0L) }
    val location = remember(settings.storageId, albums) { graph.store.uploadLocation() }
    LaunchedEffect(location, albums) { free = withContext(Dispatchers.IO) { location.free() } }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
    val total = albums.sumOf { it.photos.size }
    val musicState by graph.music.state.collectAsState()
    val musicLabel = musicState.current?.let { "♪ " + it.title.take(22) } ?: "Musik"
    val lastShow by graph.settings.lastShow.collectAsState()
    val favorites by graph.settings.favorites.collectAsState()

    Box(Modifier.fillMaxSize().background(Navy)) {
        Row(Modifier.fillMaxSize().padding(32.dp)) {
            // Links: so kommen die Fotos auf den Beamer.
            Column(
                Modifier.width(300.dp).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Fotos senden", color = Gold, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                if (url != null) {
                    QrCode(url, Modifier.size(210.dp).clip(RoundedCornerShape(8.dp)))
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Handy ins selbe WLAN, QR-Code mit der Kamera scannen, Fotos auswählen – fertig. " +
                            "Klappt mit iPhone und Android, ohne App.",
                        color = Color.White, fontSize = 15.sp, textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(url.removePrefix("http://"), color = Muted, fontSize = 14.sp, textAlign = TextAlign.Center)
                } else {
                    Box(Modifier.size(210.dp).clip(RoundedCornerShape(8.dp)).background(Panel), Alignment.Center) {
                        Text("Kein WLAN", color = Gold, fontSize = 22.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Den Beamer mit einem WLAN verbinden (Einstellungen von Google TV). " +
                            "Die Handys müssen im selben WLAN sein.",
                        color = Color.White, fontSize = 15.sp, textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "Speicher: ${location.label} · ${formatBytes(free)} frei",
                    color = Muted, fontSize = 13.sp, textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.width(32.dp))

            // Rechts: Alben und Diashow.
            Column(Modifier.fillMaxSize()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    TvButton(
                        if (total > 0) "Alle zeigen (${total})" else "Diashow",
                        onClick = onPlayAll,
                        icon = Icons.Filled.PlayArrow,
                        primary = true,
                        modifier = Modifier.focusRequester(firstFocus),
                    )
                    Spacer(Modifier.width(16.dp))
                    lastShow?.let { last ->
                        TvButton("Weiter: ${last.title.take(22)} (${last.position}/${last.total})", onClick = {
                            onPlay(last.albumKey, last.photoKey, last.sender)
                        })
                        Spacer(Modifier.width(16.dp))
                    }
                    if (favorites.isNotEmpty()) {
                        TvButton("★ Favoriten (${favorites.size})", onClick = { onPlay(FAVORITES, null, null) })
                        Spacer(Modifier.width(16.dp))
                    }
                    TvButton("Gerät & USB", onClick = onDevice)
                    Spacer(Modifier.width(16.dp))
                    TvButton(musicLabel, onClick = onMusic)
                    Spacer(Modifier.width(16.dp))
                    TvButton("Einstellungen", onClick = onSettings, icon = Icons.Filled.Settings)
                }
                Spacer(Modifier.height(20.dp))
                if (albums.isEmpty()) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        Text(
                            "Noch keine Fotos.\nSobald jemand Fotos schickt, erscheinen sie hier als Album.",
                            color = Muted, fontSize = 20.sp, textAlign = TextAlign.Center,
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(180.dp),
                        contentPadding = PaddingValues(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        items(albums, key = { it.key }) { album -> AlbumCard(album, onClick = { onAlbum(album) }) }
                    }
                }
            }
        }
        ArrivalBanner(graph, Modifier.align(Alignment.TopCenter).padding(top = 16.dp))
    }
}

@Composable
fun AlbumCard(album: Album, onClick: () -> Unit) {
    Column(
        Modifier
            .tvFocus()
            .clip(RoundedCornerShape(12.dp))
            .background(Panel)
            .clickable(onClick = onClick)
    ) {
        val cover = album.cover
        if (cover != null) Thumb(cover.file, Modifier.fillMaxWidth().aspectRatio(16f / 10f))
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                album.name, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            val where = if (album.location.removable) " · ${album.location.label}" else ""
            Text(mediaCount(album.photos) + where, color = Muted, fontSize = 14.sp)
        }
    }
}
