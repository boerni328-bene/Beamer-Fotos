package com.beamerfotos.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.beamerfotos.app.AppGraph
import com.beamerfotos.app.data.Order
import com.beamerfotos.app.data.ordered

@Composable
fun AlbumScreen(graph: AppGraph, albumKey: String, onPlay: (startKey: String?) -> Unit, onBack: () -> Unit) {
    val albums by graph.store.albums.collectAsState()
    val settings by graph.settings.state.collectAsState()
    val rotations by graph.settings.rotations.collectAsState()
    val album = albums.firstOrNull { it.key == albumKey }
    var confirmDelete by remember { mutableStateOf(false) }
    val firstFocus = remember { FocusRequester() }

    if (album == null) {
        // Album gelöscht oder USB-Speicher entfernt.
        LaunchedEffect(Unit) { onBack() }
        return
    }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
    // Im Raster nie zufällig, sonst springt die Anordnung.
    val order = if (settings.order == Order.RANDOM) Order.CAPTURED else settings.order
    val photos = remember(album, order) { ordered(album.photos, order, 0L) }

    Column(Modifier.fillMaxSize().background(Navy).padding(32.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(album.name, color = Gold, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text(
                    photoCount(photos.size) + " · " + album.location.label,
                    color = Muted, fontSize = 16.sp,
                )
            }
            TvButton(
                "Diashow", onClick = { onPlay(null) }, icon = Icons.Filled.PlayArrow, primary = true,
                modifier = Modifier.focusRequester(firstFocus),
            )
            Spacer(Modifier.width(16.dp))
            TvButton("Album löschen", onClick = { confirmDelete = true }, icon = Icons.Filled.Delete)
        }
        Spacer(Modifier.height(20.dp))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            contentPadding = PaddingValues(6.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(photos, key = { it.key }) { photo ->
                Box(
                    Modifier
                        .tvFocus(RoundedCornerShape(8.dp))
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onPlay(photo.key) }
                ) {
                    Thumb(photo.file, Modifier.fillMaxWidth().aspectRatio(4f / 3f), rotations[photo.key] ?: 0)
                }
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Album „${album.name}“ löschen?",
            text = "${photoCount(photos.size)} werden vom Beamer gelöscht. Das lässt sich nicht rückgängig machen.",
            confirm = "Löschen",
            onConfirm = {
                confirmDelete = false
                graph.store.deleteAlbum(album)
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

/** Rückfrage, mit der Fernbedienung bedienbar; „Abbrechen“ ist vorausgewählt. */
@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val cancelFocus = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Panel)
                .padding(28.dp)
        ) {
            Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Text(text, color = Muted, fontSize = 17.sp)
            Spacer(Modifier.height(24.dp))
            Row {
                TvButton("Abbrechen", onClick = onDismiss, modifier = Modifier.focusRequester(cancelFocus))
                Spacer(Modifier.width(16.dp))
                TvButton(confirm, onClick = onConfirm, icon = Icons.Filled.Delete)
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { cancelFocus.requestFocus() } }
}
