package com.beamerfotos.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beamerfotos.app.AppGraph
import com.beamerfotos.app.data.cycle
import com.beamerfotos.app.data.intervalLabel
import com.beamerfotos.app.data.nextInterval

@Composable
fun SettingsScreen(graph: AppGraph) {
    val s by graph.settings.state.collectAsState()
    val albums by graph.store.albums.collectAsState()
    var confirmDeleteAll by remember { mutableStateOf(false) }
    // Zählt hoch, damit die Liste der Speicherorte neu gelesen wird (USB-Stick eingesteckt).
    var locationTick by remember { mutableIntStateOf(0) }
    val locations = remember(locationTick, albums) { graph.store.locations() }
    val current = locations.firstOrNull { it.id == s.storageId } ?: locations.first()
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
    fun onOff(b: Boolean) = if (b) "an" else "aus"
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""
    }

    Column(Modifier.fillMaxSize().background(Navy).padding(horizontal = 48.dp, vertical = 28.dp)) {
        Text("Einstellungen", color = Gold, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text("OK ändert den Wert. Zurück schließt.", color = Muted, fontSize = 15.sp)
        Spacer(Modifier.height(16.dp))
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 6.dp, horizontal = 10.dp),
        ) {
            item { Section("Diashow") }
            item {
                SettingRow("Anzeigedauer je Foto", intervalLabel(s.intervalSec), Modifier.focusRequester(firstFocus)) {
                    graph.settings.update { it.copy(intervalSec = nextInterval(it.intervalSec)) }
                }
            }
            item {
                SettingRow("Übergang", s.transition.label) {
                    graph.settings.update { it.copy(transition = it.transition.cycle()) }
                }
            }
            item {
                SettingRow("Reihenfolge", s.order.label) { graph.settings.update { it.copy(order = it.order.cycle()) } }
            }
            item {
                SettingRow("Bildgröße", s.fit.label) { graph.settings.update { it.copy(fit = it.fit.cycle()) } }
            }
            item {
                SettingRow("Hintergrund bei Hochformat", s.background.label) {
                    graph.settings.update { it.copy(background = it.background.cycle()) }
                }
            }
            item {
                SettingRow("Album und Datum einblenden", onOff(s.showInfo)) {
                    graph.settings.update { it.copy(showInfo = !it.showInfo) }
                }
            }
            item {
                SettingRow("Am Ende von vorn beginnen", onOff(s.loop)) {
                    graph.settings.update { it.copy(loop = !it.loop) }
                }
            }
            item {
                SettingRow("Neue Fotos sofort zeigen", onOff(s.liveNew)) {
                    graph.settings.update { it.copy(liveNew = !it.liveNew) }
                }
            }
            item {
                SettingRow("QR-Code in der Diashow", onOff(s.showQr)) {
                    graph.settings.update { it.copy(showQr = !it.showQr) }
                }
            }

            item { Section("Speicher und Zugang") }
            item {
                SettingRow("Neue Fotos speichern auf", "${current.label} · ${formatBytes(current.free())} frei") {
                    locationTick++
                    val fresh = graph.store.locations()
                    val i = fresh.indexOfFirst { it.id == current.id }
                    val next = fresh[(i + 1).mod(fresh.size)]
                    graph.settings.update { it.copy(storageId = next.id) }
                }
            }
            if (locations.size == 1) {
                item {
                    Text(
                        "USB-Stick einstecken, dann erscheint er hier als weiterer Speicherort.",
                        color = Muted, fontSize = 14.sp, modifier = Modifier.padding(start = 16.dp),
                    )
                }
            }
            item {
                SettingRow("Neuen QR-Code erzeugen", "alter Code gilt nicht mehr") { graph.settings.renewToken() }
            }
            item {
                SettingRow("Alle Fotos löschen", photoCount(albums.sumOf { it.photos.size })) {
                    confirmDeleteAll = true
                }
            }
            item {
                Text(
                    "Beamer-Fotos $version",
                    color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp, start = 16.dp),
                )
            }
        }
    }

    if (confirmDeleteAll) {
        ConfirmDialog(
            title = "Alle Fotos löschen?",
            text = "Alle Alben werden vom Beamer und vom USB-Speicher gelöscht. Das lässt sich nicht rückgängig machen.",
            confirm = "Alle löschen",
            onConfirm = {
                confirmDeleteAll = false
                graph.store.deleteAll()
            },
            onDismiss = { confirmDeleteAll = false },
        )
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title, color = Sky, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 10.dp, start = 4.dp),
    )
}

@Composable
private fun SettingRow(label: String, value: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(10.dp))
            .background(if (focused) Gold else Panel)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = if (focused) Navy else Color.White, fontSize = 18.sp, modifier = Modifier.weight(1f))
        Text(value, color = if (focused) Navy else Gold, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    }
}
