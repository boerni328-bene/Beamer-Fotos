package com.beamerfotos.app.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beamerfotos.app.AppGraph
import com.beamerfotos.app.data.Album
import kotlinx.coroutines.delay

/**
 * Fotos, die schon auf dem Beamer oder einem USB-Stick liegen, nach Ordnern.
 * Fragt beim ersten Öffnen nach der Berechtigung „Medien lesen“.
 */
@Composable
fun DeviceScreen(
    graph: AppGraph,
    onAlbum: (Album) -> Unit,
    onPlayLocation: (locationId: String) -> Unit,
) {
    val context = LocalContext.current
    val granted by graph.media.granted.collectAsState()
    val albums by graph.media.albums.collectAsState()
    var asked by remember { mutableStateOf(false) }
    val firstFocus = remember { FocusRequester() }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        asked = true
        graph.media.recheck()
    }

    LaunchedEffect(Unit) {
        graph.media.recheck()
        graph.media.refresh()
        if (!graph.media.granted.value) launcher.launch(graph.media.permissions)
    }
    // Nach der Rückkehr aus den Einstellungen des Beamers die Berechtigung erneut prüfen.
    LaunchedEffect(granted) {
        while (!granted) {
            delay(2000)
            graph.media.recheck()
        }
    }
    LaunchedEffect(granted, albums.isEmpty()) {
        delay(100)
        runCatching { firstFocus.requestFocus() }
    }

    Column(Modifier.fillMaxSize().background(Navy).padding(32.dp)) {
        Text("Gerät & USB-Stick", color = Gold, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(
            "Fotos, die schon auf dem Beamer oder einem eingesteckten USB-Stick liegen. Sie werden nur gezeigt, nie verändert.",
            color = Muted, fontSize = 16.sp,
        )
        Spacer(Modifier.height(20.dp))

        if (!granted) {
            val activity = context as? Activity
            val blocked = asked && graph.media.permissions.none { activity?.shouldShowRequestPermissionRationale(it) == true }
            Column(Modifier.widthIn(max = 720.dp)) {
                Text(
                    "Dafür braucht die App die Berechtigung, Fotos und Videos auf dem Beamer zu lesen.",
                    color = Color.White, fontSize = 19.sp,
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    TvButton(
                        "Zugriff erlauben", onClick = { launcher.launch(graph.media.permissions) }, primary = true,
                        modifier = Modifier.focusRequester(firstFocus),
                    )
                    if (blocked) {
                        TvButton("Einstellungen des Beamers öffnen", onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        })
                    }
                }
                if (blocked) {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "Der Beamer fragt nicht mehr nach. Unter Einstellungen → Apps → Beamer-Fotos → Berechtigungen „Fotos und Videos“ erlauben.",
                        color = Muted, fontSize = 16.sp,
                    )
                }
            }
            return@Column
        }

        if (albums.isEmpty()) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Keine Fotos gefunden.\nNach dem Einstecken eines USB-Sticks braucht der Beamer einen Moment, um die Fotos zu finden.",
                        color = Muted, fontSize = 19.sp, textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(16.dp))
                    TvButton("Neu suchen", onClick = { graph.media.refresh() }, icon = Icons.Filled.Refresh,
                        modifier = Modifier.focusRequester(firstFocus))
                }
            }
            return@Column
        }

        val groups = albums.groupBy { it.location }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(180.dp),
            contentPadding = PaddingValues(6.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            groups.entries.forEachIndexed { i, (location, list) ->
                item(key = "kopf:" + location.id, span = { GridItemSpan(maxLineSpan) }) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = if (i == 0) 0.dp else 12.dp)) {
                        Text(
                            location.label + " · " + photoCount(list.sumOf { it.photos.size }),
                            color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.width(20.dp))
                        TvButton(
                            "Alle zeigen", onClick = { onPlayLocation(location.id) }, icon = Icons.Filled.PlayArrow,
                            modifier = if (i == 0) Modifier.focusRequester(firstFocus) else Modifier,
                        )
                    }
                }
                items(list, key = { it.key }) { album -> AlbumCard(album, onClick = { onAlbum(album) }) }
            }
        }
    }
}
