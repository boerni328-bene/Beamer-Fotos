package com.beamerfotos.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.beamerfotos.app.AppGraph
import com.beamerfotos.app.data.Album
import com.beamerfotos.app.data.Names
import com.beamerfotos.app.data.Order
import com.beamerfotos.app.data.Photo
import com.beamerfotos.app.data.ordered
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Anzeigename eines Absenders. */
fun senderLabel(sender: String): String = sender.ifEmpty { "Ohne Namen" }

private sealed interface AlbumDialog {
    data object DeleteAlbum : AlbumDialog
    data object DeleteSelected : AlbumDialog
    data object Rename : AlbumDialog
    data object Move : AlbumDialog
    data object NewAlbum : AlbumDialog
    data object RenameSender : AlbumDialog
}

/**
 * Ein Album: Fotos als Raster, oben nach Absender filtern.
 * „Auswählen“ schaltet in den Auswahlmodus: OK markiert Fotos, dann löschen oder verschieben.
 */
@Composable
fun AlbumScreen(
    graph: AppGraph,
    albumKey: String,
    onPlay: (albumKey: String, startKey: String?, sender: String?) -> Unit,
    onBack: () -> Unit,
    onRenamed: (newKey: String) -> Unit,
) {
    val ownAlbums by graph.store.albums.collectAsState()
    val mediaAlbums by graph.media.albums.collectAsState()
    val albums = ownAlbums
    val settings by graph.settings.state.collectAsState()
    val rotations by graph.settings.rotations.collectAsState()

    val key = albumKey
    val album = (ownAlbums + mediaAlbums).firstOrNull { it.key == key }
    var filter by remember { mutableStateOf<String?>(null) }
    var selecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var dialog by remember { mutableStateOf<AlbumDialog?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val firstFocus = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    if (album == null) {
        // Album gelöscht oder USB-Speicher entfernt (kurz warten: nach dem Umbenennen wird neu eingelesen).
        LaunchedEffect(key) {
            delay(2000)
            onBack()
        }
        Box(Modifier.fillMaxSize().background(Navy))
        return
    }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
    BackHandler(enabled = selecting) {
        selecting = false
        selected = emptySet()
    }

    // Filter gilt nur, solange es den Absender noch gibt.
    val readOnly = album.location.readOnly
    val done = if (readOnly) "kopiert" else "verschoben"
    val senders = album.senders
    val sender = filter?.takeIf { s -> senders.any { it.first == s } }
    val showFilter = senders.size > 1 || senders.any { it.first.isNotEmpty() }

    // Im Raster nie zufällig, sonst springt die Anordnung.
    val order = if (settings.order == Order.RANDOM) Order.CAPTURED else settings.order
    val photos = remember(album, order, sender) {
        ordered(album.photos.filter { sender == null || it.sender == sender }, order, 0L)
    }
    val chosen: List<Photo> = photos.filter { it.key in selected }

    Column(Modifier.fillMaxSize().background(Navy).padding(32.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(album.name, color = Gold, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (selecting) "${chosen.size} ausgewählt · OK markiert · Zurück beendet die Auswahl"
                    else mediaCount(photos) + (sender?.let { " von ${senderLabel(it)}" } ?: "") +
                        " · " + album.location.label,
                    color = Muted, fontSize = 16.sp,
                )
            }
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (selecting) {
                    val all = photos.isNotEmpty() && chosen.size == photos.size
                    TvButton(
                        if (all) "Keine" else "Alle",
                        onClick = { selected = if (all) emptySet() else photos.map { it.key }.toSet() },
                        icon = Icons.Filled.Check,
                        modifier = Modifier.focusRequester(firstFocus),
                    )
                    if (chosen.isNotEmpty()) {
                        TvButton(if (readOnly) "In Album kopieren" else "Verschieben", onClick = { dialog = AlbumDialog.Move })
                        if (!readOnly) {
                            TvButton("Löschen", onClick = { dialog = AlbumDialog.DeleteSelected }, icon = Icons.Filled.Delete)
                        }
                    }
                    TvButton("Fertig", onClick = {
                        selecting = false
                        selected = emptySet()
                    }, primary = true)
                } else {
                    TvButton(
                        "Diashow", onClick = { onPlay(album.key, null, sender) }, icon = Icons.Filled.PlayArrow, primary = true,
                        modifier = Modifier.focusRequester(firstFocus),
                    )
                    TvButton("Auswählen", onClick = { selecting = true }, icon = Icons.Filled.Check)
                    if (sender != null && !readOnly) {
                        TvButton("Name ändern", onClick = { dialog = AlbumDialog.RenameSender }, icon = Icons.Filled.Edit)
                    }
                    if (!readOnly) {
                        TvButton("Album umbenennen", onClick = { dialog = AlbumDialog.Rename }, icon = Icons.Filled.Edit)
                        TvButton("Album löschen", onClick = { dialog = AlbumDialog.DeleteAlbum }, icon = Icons.Filled.Delete)
                    }
                }
            }
        }
        LaunchedEffect(selecting) { runCatching { firstFocus.requestFocus() } }

        if (showFilter) {
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FilterChip("Alle (${album.photos.size})", sender == null) { filter = null }
                senders.forEach { (name, count) ->
                    FilterChip("${senderLabel(name)} ($count)", sender == name) { filter = name }
                }
            }
        }
        message?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, color = Sky, fontSize = 16.sp)
        }
        Spacer(Modifier.height(16.dp))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            contentPadding = PaddingValues(6.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(photos, key = { it.key }) { photo ->
                val isSelected = photo.key in selected
                Box(
                    Modifier
                        .tvFocus(RoundedCornerShape(8.dp))
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            if (selecting) {
                                selected = if (isSelected) selected - photo.key else selected + photo.key
                            } else {
                                onPlay(album.key, photo.key, sender)
                            }
                        }
                ) {
                    Thumb(photo.file, Modifier.fillMaxWidth().aspectRatio(4f / 3f), rotations[photo.key] ?: 0)
                    if (showFilter && sender == null && photo.sender.isNotEmpty()) {
                        Text(
                            photo.sender, color = Color.White, fontSize = 12.sp, maxLines = 1,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .background(Color(0xAA000000))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                    if (selecting) {
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) Gold else Color(0x66000000))
                                .border(2.dp, Color.White, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isSelected) Icon(Icons.Filled.Check, null, tint = Navy, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }

    fun finishSelection(text: String) {
        dialog = null
        selecting = false
        selected = emptySet()
        message = text
    }

    /** Verschieben bzw. kopieren im Hintergrund (vom USB-Stick kann das dauern). */
    fun transfer(target: File) {
        val list = chosen
        finishSelection("${photoCount(list.size)} werden ${if (readOnly) "kopiert" else "verschoben"} …")
        scope.launch {
            val n = withContext(Dispatchers.IO) { graph.store.move(list, target) }
            message = "${photoCount(n)} nach „${target.name}“ $done."
        }
    }

    when (dialog) {
        AlbumDialog.DeleteAlbum -> ConfirmDialog(
            title = "Album „${album.name}“ löschen?",
            text = "${photoCount(album.photos.size)} werden vom Beamer gelöscht. Das lässt sich nicht rückgängig machen.",
            confirm = "Löschen",
            onConfirm = {
                dialog = null
                graph.store.deleteAlbum(album)
            },
            onDismiss = { dialog = null },
        )
        AlbumDialog.DeleteSelected -> ConfirmDialog(
            title = "${photoCount(chosen.size)} löschen?",
            text = "Die ausgewählten Fotos werden vom Beamer gelöscht. Das lässt sich nicht rückgängig machen.",
            confirm = "Löschen",
            onConfirm = {
                graph.store.delete(chosen)
                finishSelection("${photoCount(chosen.size)} gelöscht.")
            },
            onDismiss = { dialog = null },
        )
        AlbumDialog.Rename -> TextInputDialog(
            title = "Album umbenennen",
            initial = album.name,
            confirm = "Umbenennen",
            onConfirm = { name ->
                graph.store.renameAlbum(album, name).also {
                    if (it == null) {
                        dialog = null
                        onRenamed(File(album.location.root, Names.clean(name)).absolutePath)
                    }
                }
            },
            onDismiss = { dialog = null },
        )
        AlbumDialog.Move -> MoveDialog(
            albums = albums.filter { it.key != album.key },
            count = chosen.size,
            copy = readOnly,
            onPick = { target -> transfer(target.dir) },
            onNew = { dialog = AlbumDialog.NewAlbum },
            onDismiss = { dialog = null },
        )
        AlbumDialog.NewAlbum -> TextInputDialog(
            title = "Neues Album",
            initial = "",
            confirm = if (readOnly) "Kopieren" else "Verschieben",
            onConfirm = { name ->
                val dir: File? = graph.store.albumDir(name, if (readOnly) null else album.location)
                if (dir == null) {
                    "Bitte einen Namen eingeben."
                } else {
                    transfer(dir)
                    null
                }
            },
            onDismiss = { dialog = null },
        )
        AlbumDialog.RenameSender -> {
            val old = sender ?: ""
            TextInputDialog(
                title = "Name ändern: ${senderLabel(old)}",
                initial = old,
                confirm = "Ändern",
                onConfirm = { name ->
                    graph.store.renameSender(album, old, name).also {
                        if (it == null) {
                            dialog = null
                            filter = Names.senderName(name)
                            message = "Fotos von „${senderLabel(old)}“ heißen jetzt „${Names.senderName(name)}“."
                        }
                    }
                },
                onDismiss = { dialog = null },
            )
        }
        null -> Unit
    }
}

@Composable
private fun FilterChip(text: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .tvFocus(RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(if (active) Gold else Panel)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(text, color = if (active) Navy else Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

/** Ziel zum Verschieben wählen: vorhandenes Album oder neues. */
@Composable
private fun MoveDialog(
    albums: List<Album>,
    count: Int,
    copy: Boolean,
    onPick: (Album) -> Unit,
    onNew: () -> Unit,
    onDismiss: () -> Unit,
) {
    val first = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Panel)
                .padding(28.dp)
        ) {
            Text("${photoCount(count)} ${if (copy) "kopieren" else "verschieben"} nach …", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            LazyColumn(Modifier.heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    TvButton("Neues Album …", onClick = onNew, icon = Icons.Filled.Edit, modifier = Modifier.focusRequester(first))
                }
                items(albums, key = { it.key }) { a ->
                    val where = if (a.location.removable) " · ${a.location.label}" else ""
                    TvButton("${a.name}  (${photoCount(a.photos.size)}$where)", onClick = { onPick(a) })
                }
            }
            Spacer(Modifier.height(16.dp))
            TvButton("Abbrechen", onClick = onDismiss)
        }
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}

/**
 * Texteingabe, mit der Fernbedienung bedienbar (OK öffnet die Bildschirmtastatur).
 * [onConfirm] liefert null bei Erfolg, sonst einen Hinweis, der angezeigt wird.
 */
@Composable
fun TextInputDialog(
    title: String,
    initial: String,
    confirm: String,
    onConfirm: (String) -> String?,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }
    val fieldFocus = remember { FocusRequester() }
    fun submit() {
        error = onConfirm(text)
    }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Panel)
                .padding(28.dp)
                .width(460.dp)
        ) {
            Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(60) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Gold,
                    unfocusedBorderColor = Muted,
                    cursorColor = Gold,
                ),
                modifier = Modifier.fillMaxWidth().focusRequester(fieldFocus),
            )
            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = Color(0xFFE57373), fontSize = 16.sp)
            }
            Spacer(Modifier.height(20.dp))
            Row {
                TvButton("Abbrechen", onClick = onDismiss)
                Spacer(Modifier.width(16.dp))
                TvButton(confirm, onClick = { submit() }, icon = Icons.Filled.Check, primary = true)
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { fieldFocus.requestFocus() } }
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
