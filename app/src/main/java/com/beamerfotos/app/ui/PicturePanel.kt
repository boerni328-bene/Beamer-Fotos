package com.beamerfotos.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beamerfotos.app.AppGraph
import com.beamerfotos.app.data.Picture
import com.beamerfotos.app.data.SlideSettings

/** Regler für die Bildverbesserung (Diashow-Menü und Einstellungen). Gilt für alle Fotos. */
@Composable
fun PicturePanel(graph: AppGraph, modifier: Modifier = Modifier, requestFocus: Boolean = true) {
    val s by graph.settings.state.collectAsState()
    val first = remember { FocusRequester() }
    if (requestFocus) LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    fun set(change: (SlideSettings) -> SlideSettings) = graph.settings.update(change)

    Column(modifier.padding(horizontal = 24.dp, vertical = 14.dp)) {
        Text("Bild · ▲ ▼ wählen · ◀ ▶ ändern · OK an/aus · Zurück schließt", color = Muted, fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        AdjustRow("Automatisch verbessern", if (s.autoEnhance) "an" else "aus", null, Modifier.focusRequester(first),
            onStep = { set { it.copy(autoEnhance = !it.autoEnhance) } },
            onClick = { set { it.copy(autoEnhance = !it.autoEnhance) } })
        AdjustRow("Helligkeit", signed(s.brightness), fraction(s.brightness, Picture.BRIGHTNESS),
            onStep = { d -> set { it.copy(brightness = (it.brightness + d * 5).coerceIn(Picture.BRIGHTNESS)) } })
        AdjustRow("Kontrast", signed(s.contrast), fraction(s.contrast, Picture.CONTRAST),
            onStep = { d -> set { it.copy(contrast = (it.contrast + d * 5).coerceIn(Picture.CONTRAST)) } })
        AdjustRow("Farbsättigung", signed(s.saturation), fraction(s.saturation, Picture.SATURATION),
            onStep = { d -> set { it.copy(saturation = (it.saturation + d * 10).coerceIn(Picture.SATURATION)) } })
        AdjustRow("Farbwärme", when {
            s.warmth > 0 -> "wärmer +${s.warmth}"
            s.warmth < 0 -> "kühler ${s.warmth}"
            else -> "0"
        }, fraction(s.warmth, Picture.WARMTH),
            onStep = { d -> set { it.copy(warmth = (it.warmth + d * 5).coerceIn(Picture.WARMTH)) } })
        AdjustRow("Zurücksetzen", if (Picture.isNeutral(s)) "–" else "OK drücken", null,
            onStep = {},
            onClick = { set { it.copy(brightness = 0, contrast = 0, saturation = 0, warmth = 0, autoEnhance = false) } })
    }
}

private fun signed(v: Int) = if (v > 0) "+$v" else "$v"

private fun fraction(v: Int, range: IntRange) = (v - range.first).toFloat() / (range.last - range.first)

/** Eine Zeile mit Wert; ◀ ▶ ändern, OK löst [onClick] aus. [bar] = Anteil 0–1 oder null ohne Balken. */
@Composable
fun AdjustRow(
    label: String,
    value: String,
    bar: Float?,
    modifier: Modifier = Modifier,
    onStep: (Int) -> Unit,
    onClick: () -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier
            .padding(vertical = 3.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (focused) PanelLight else Color.Transparent)
            .onFocusChanged { focused = it.isFocused }
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.DirectionLeft -> { onStep(-1); true }
                    Key.DirectionRight -> { onStep(1); true }
                    else -> false
                }
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = if (focused) Gold else Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.width(260.dp))
        if (bar != null) {
            Box(Modifier.width(240.dp).height(8.dp).clip(RoundedCornerShape(4.dp)).background(Navy)) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(bar.coerceIn(0f, 1f)).background(if (focused) Gold else Sky))
            }
            Spacer(Modifier.width(16.dp))
        }
        Text(value, color = Muted, fontSize = 17.sp)
    }
}
