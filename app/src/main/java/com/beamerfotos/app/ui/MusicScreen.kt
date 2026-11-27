package com.beamerfotos.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.beamerfotos.app.AppGraph
import com.beamerfotos.app.music.MusicFolder
import com.beamerfotos.app.music.RadioStore
import com.beamerfotos.app.music.Station
import com.beamerfotos.app.music.Track
import kotlinx.coroutines.launch

/**
 * Musik und Radio, mit oder ohne Diashow (läuft weiter, solange die App offen ist).
 * Oben: was gerade läuft. Darunter Radiosender, Sendersuche und eigene Musik.
 */
@Composable
fun MusicScreen(graph: AppGraph) {
    val state by graph.music.state.collectAsState()
    val stations by graph.radio.stations.collectAsState()
    val folders by graph.musicLibrary.folders.collectAsState()
    val musicChanged by graph.store.musicChanged.collectAsState()
    val scope = rememberCoroutineScope()
    var searching by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<Station>?>(null) }
    var searchMessage by remember { mutableStateOf<String?>(null) }
    var volume by remember { mutableStateOf(graph.music.currentVolume) }
    var granted by remember { mutableStateOf(graph.musicLibrary.granted()) }
    val firstFocus = remember { FocusRequester() }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = graph.musicLibrary.granted()
        scope.launch { graph.musicLibrary.refresh() }
    }

    LaunchedEffect(musicChanged, granted) { graph.musicLibrary.refresh() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }

    fun playStation(station: Station) {
        scope.launch {
            val url = RadioStore.resolve(station.url)
            graph.music.play(listOf(Track(station.name, station.info.ifEmpty { "Radio" }, url.toUri(), isRadio = true)))
        }
    }

    LazyColumn(
        Modifier.fillMaxSize().background(Navy).padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Spacer(Modifier.height(24.dp))
            Text("Musik & Radio", color = Gold, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(
                "Läuft auch während der Diashow weiter, solange die App geöffnet ist. Bei Videos pausiert die Musik.",
                color = Muted, fontSize = 15.sp,
            )
            Spacer(Modifier.height(12.dp))
        }

        // Was gerade läuft
        item {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Panel).padding(18.dp)
            ) {
                val current = state.current
                Text(
                    when {
                        current == null -> "Keine Musik"
                        state.loading -> "Verbinde … ${current.title}"
                        else -> (if (state.playing) "♪ " else "Pause · ") + current.title
                    },
                    color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                val sub = state.nowPlaying ?: current?.subtitle
                if (!sub.isNullOrBlank()) Text(sub, color = Muted, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                state.error?.let { Text(it, color = Color(0xFFE57373), fontSize = 16.sp) }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TvButton(
                        if (state.playing) "Pause" else "Abspielen", onClick = { graph.music.toggle() },
                        icon = Icons.Filled.PlayArrow, primary = true, modifier = Modifier.focusRequester(firstFocus),
                    )
                    if (current != null && !current.isRadio) {
                        TvButton("◀ Zurück", onClick = { graph.music.previous() })
                        TvButton("Weiter ▶", onClick = { graph.music.next() })
                        TvButton("Zufall: ${if (state.shuffle) "an" else "aus"}", onClick = { graph.music.setShuffle(!state.shuffle) })
                    }
                    if (current != null) TvButton("Stopp", onClick = { graph.music.stop() })
                }
                Spacer(Modifier.height(6.dp))
                AdjustRow(
                    "Lautstärke der Musik", "${(volume * 100).toInt()} %", volume,
                    onStep = { d ->
                        volume = (volume + d * 0.1f).coerceIn(0f, 1f)
                        graph.music.setVolume(volume)
                    },
                )
            }
        }

        // Radio
        item { Heading("Radio") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvButton("Sender suchen", onClick = { searching = true }, icon = Icons.Filled.Search)
                if (results != null) TvButton("Suche schließen", onClick = { results = null; searchMessage = null })
            }
            Text(
                "Eigene Sender lassen sich auch auf der Handy-Seite hinzufügen (Name und Adresse des Streams).",
                color = Muted, fontSize = 14.sp, modifier = Modifier.padding(top = 6.dp),
            )
        }
        searchMessage?.let { msg -> item { Text(msg, color = Sky, fontSize = 16.sp) } }
        results?.let { list ->
            items(list, key = { "s:" + it.url }) { station ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TvButton(station.name.take(40), onClick = { playStation(station) }, icon = Icons.Filled.PlayArrow)
                    TvButton("Merken", onClick = {
                        graph.radio.add(station.name, station.url, station.info)
                        searchMessage = "„${station.name}“ gemerkt."
                    })
                    Text(station.info, color = Muted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (stations.isEmpty() && results == null) {
            item { Text("Noch keine Sender gemerkt.", color = Muted, fontSize = 16.sp) }
        }
        items(stations, key = { "r:" + it.url }) { station ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val active = state.current?.isRadio == true && state.current?.title == station.name
                TvButton(
                    (if (active) "♪ " else "") + station.name.take(40), onClick = { playStation(station) },
                    icon = Icons.Filled.PlayArrow, primary = active,
                )
                TvButton("Entfernen", onClick = { graph.radio.remove(station) }, icon = Icons.Filled.Delete)
                Text(station.info, color = Muted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        // Eigene Musik
        item { Heading("Eigene Musik") }
        item {
            Text(
                "Musikstücke lassen sich über die Handy-Seite senden (MP3, M4A, FLAC …)." +
                    if (granted) "" else " Für Musik auf dem Beamer und dem USB-Stick braucht die App die Berechtigung „Musik“.",
                color = Muted, fontSize = 14.sp,
            )
            if (!granted) {
                Spacer(Modifier.height(8.dp))
                TvButton("Musik auf Gerät und USB erlauben", onClick = { launcher.launch(graph.musicLibrary.permission) })
            }
        }
        if (folders.isEmpty()) {
            item { Text("Keine Musik gefunden.", color = Muted, fontSize = 16.sp) }
        }
        items(folders, key = { "m:" + it.name + it.where }) { folder -> FolderRow(folder, graph) }
        item { Spacer(Modifier.height(32.dp)) }
    }

    if (searching) {
        TextInputDialog(
            title = "Radiosender suchen",
            initial = "",
            confirm = "Suchen",
            onConfirm = { query ->
                if (query.isBlank()) {
                    "Bitte einen Namen eingeben, z. B. „Bayern 3“."
                } else {
                    searching = false
                    searchMessage = "Suche „$query“ …"
                    scope.launch {
                        RadioStore.search(query).onSuccess {
                            results = it
                            searchMessage = if (it.isEmpty()) "Nichts gefunden." else "${it.size} Sender gefunden (Verzeichnis radio-browser.info)."
                        }.onFailure {
                            searchMessage = "Suche nicht möglich – ist der Beamer mit dem Internet verbunden?"
                        }
                    }
                    null
                }
            },
            onDismiss = { searching = false },
        )
    }
}

@Composable
private fun Heading(text: String) {
    Text(text, color = Gold, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp))
}

@Composable
private fun FolderRow(folder: MusicFolder, graph: AppGraph) {
    var open by remember { mutableStateOf(false) }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TvButton("${folder.name.take(36)} (${folder.tracks.size})", onClick = { graph.music.play(folder.tracks) }, icon = Icons.Filled.PlayArrow)
            TvButton("Zufällig", onClick = { graph.music.play(folder.tracks, (folder.tracks.indices).random(), shuffle = true) })
            TvButton(if (open) "Titel ausblenden" else "Titel", onClick = { open = !open })
            Text(folder.where, color = Muted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (open) {
            Column(Modifier.padding(start = 32.dp, top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                folder.tracks.forEachIndexed { i, track ->
                    TvButton(
                        track.title.take(50) + if (track.subtitle.isNotEmpty() && track.subtitle != "Gesendet") " · ${track.subtitle.take(30)}" else "",
                        onClick = { graph.music.play(folder.tracks, i) },
                    )
                }
            }
        }
    }
}
