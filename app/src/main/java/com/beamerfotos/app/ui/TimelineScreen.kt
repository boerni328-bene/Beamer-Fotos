package com.beamerfotos.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beamerfotos.app.AppGraph
import com.beamerfotos.app.data.Photo
import com.beamerfotos.app.data.Timeline

/** Alle Fotos (eigene, Gerät und USB) ohne Ausgeblendete, jedes nur einmal. */
@Composable
fun rememberAllPhotos(graph: AppGraph): List<Photo> {
    val albums by graph.store.albums.collectAsState()
    val media by graph.media.albums.collectAsState()
    val hidden by graph.settings.hidden.collectAsState()
    return remember(albums, media, hidden) {
        (albums + media).flatMap { it.photos }.filter { it.key !in hidden }.distinctBy { it.key }
    }
}

/** Fotos, die heute vor einem oder mehreren Jahren aufgenommen wurden. */
fun todayPhotos(all: List<Photo>): List<Photo> = all.filter { Timeline.yearsAgo(it.captured) != null }

/** Zeitleiste: Jahre und Monate nach Aufnahmedatum, jeweils als Diashow. */
@Composable
fun TimelineScreen(graph: AppGraph, onPlay: (String) -> Unit) {
    val all = rememberAllPhotos(graph)
    val years = remember(all) { Timeline.years(all.map { it.captured }) }
    val today = remember(all) { todayPhotos(all) }
    val undated = remember(all) { all.count { it.captured <= 0 } }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(years.isNotEmpty(), today.isNotEmpty()) { runCatching { firstFocus.requestFocus() } }

    LazyColumn(
        Modifier.fillMaxSize().background(Navy).padding(horizontal = 48.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Spacer(Modifier.height(28.dp))
            Text("Zeitleiste", color = Gold, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text("Nach Aufnahmedatum. OK startet die Diashow.", color = Muted, fontSize = 15.sp)
            Spacer(Modifier.height(8.dp))
        }
        if (today.isNotEmpty()) {
            item {
                TvButton(
                    Timeline.todayLabel(today.mapNotNull { Timeline.yearsAgo(it.captured) }, today.size),
                    onClick = { onPlay(Timeline.TODAY) }, icon = Icons.Filled.PlayArrow,
                    modifier = Modifier.focusRequester(firstFocus),
                )
            }
        }
        if (years.isEmpty()) {
            item { Text("Keine Fotos mit Aufnahmedatum.", color = Muted, fontSize = 18.sp) }
        }
        items(years, key = { it.year }) { y ->
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TvButton(
                    "${y.year} (${y.total})", onClick = { onPlay(Timeline.yearKey(y.year)) },
                    icon = Icons.Filled.PlayArrow, primary = true,
                    modifier = if (y == years.first() && today.isEmpty()) Modifier.focusRequester(firstFocus) else Modifier,
                )
                y.months.forEach { (month, count) ->
                    TvButton("${Timeline.monthNames[month - 1]} ($count)", onClick = { onPlay(Timeline.monthKey(y.year, month)) })
                }
            }
        }
        if (undated > 0) {
            item { Text("${photoCount(undated)} ohne Aufnahmedatum sind hier nicht aufgeführt.", color = Muted, fontSize = 14.sp) }
        }
        item { Spacer(Modifier.height(32.dp)) }
    }
}
