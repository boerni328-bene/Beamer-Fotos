package com.beamerfotos.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.beamerfotos.app.server.RemoteCommand
import com.beamerfotos.app.ui.AlbumScreen
import com.beamerfotos.app.ui.BeamerTheme
import com.beamerfotos.app.ui.DeviceScreen
import com.beamerfotos.app.ui.HomeScreen
import com.beamerfotos.app.ui.MusicScreen
import com.beamerfotos.app.ui.SettingsScreen
import com.beamerfotos.app.ui.SlideshowScreen

class MainActivity : ComponentActivity() {
    private lateinit var graph: AppGraph

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Kein Bildschirmschoner, solange die App läuft.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        graph = AppGraph.get(this)
        setContent { BeamerTheme { App(graph) } }
    }

    override fun onStart() {
        super.onStart()
        graph.server.start()
        graph.store.requestRefresh()
        graph.music.onForeground()
    }

    override fun onStop() {
        graph.server.stop()
        // Musik nur, solange die App sichtbar ist (kein Hintergrunddienst).
        graph.music.onBackground()
        super.onStop()
    }
}

sealed interface Screen {
    data object Home : Screen
    data class AlbumView(val albumKey: String) : Screen
    data class Show(val albumKey: String?, val startKey: String?, val sender: String? = null) : Screen
    data object Settings : Screen
    data object Device : Screen
    data object Music : Screen
}

@Composable
fun App(graph: AppGraph) {
    var stack by remember { mutableStateOf(listOf<Screen>(Screen.Home)) }
    fun push(s: Screen) {
        stack = stack + s
    }
    fun pop() {
        if (stack.size > 1) stack = stack.dropLast(1)
    }

    BackHandler(enabled = stack.size > 1) { pop() }

    // „Diashow starten“ auf der Handy-Seite.
    LaunchedEffect(Unit) {
        graph.server.commands.collect {
            if (it == RemoteCommand.START && stack.last() !is Screen.Show) push(Screen.Show(null, null))
        }
    }

    when (val screen = stack.last()) {
        Screen.Home -> HomeScreen(
            graph = graph,
            onAlbum = { push(Screen.AlbumView(it.key)) },
            onPlayAll = { push(Screen.Show(null, null)) },
            onSettings = { push(Screen.Settings) },
            onDevice = { push(Screen.Device) },
            onMusic = { push(Screen.Music) },
        )
        is Screen.AlbumView -> AlbumScreen(
            graph = graph,
            albumKey = screen.albumKey,
            onPlay = { key, startKey, sender -> push(Screen.Show(key, startKey, sender)) },
            onBack = { pop() },
            onRenamed = { newKey -> stack = stack.dropLast(1) + Screen.AlbumView(newKey) },
        )
        is Screen.Show -> SlideshowScreen(
            graph = graph,
            albumKey = screen.albumKey,
            startKey = screen.startKey,
            sender = screen.sender,
        )
        Screen.Settings -> SettingsScreen(graph = graph)
        Screen.Music -> MusicScreen(graph = graph)
        Screen.Device -> DeviceScreen(
            graph = graph,
            onAlbum = { push(Screen.AlbumView(it.key)) },
            onPlayLocation = { push(Screen.Show(it, null)) },
        )
    }
}
