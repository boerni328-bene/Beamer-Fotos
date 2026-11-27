package com.beamerfotos.app

import android.content.Context
import com.beamerfotos.app.data.MediaLibrary
import com.beamerfotos.app.data.PhotoStore
import com.beamerfotos.app.data.SettingsStore
import com.beamerfotos.app.image.ImageLoader
import com.beamerfotos.app.music.MusicLibrary
import com.beamerfotos.app.music.MusicPlayer
import com.beamerfotos.app.music.RadioStore
import com.beamerfotos.app.server.UploadServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Hält die Bausteine der App einmal für die ganze Laufzeit. */
class AppGraph private constructor(context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val settings = SettingsStore(context)
    val store = PhotoStore(context, settings, scope)
    val media = MediaLibrary(context, scope)
    val music = MusicPlayer(context)
    val musicLibrary = MusicLibrary(context, store)
    val radio = RadioStore(context)
    val server = UploadServer(context, store, settings, music, radio)

    companion object {
        @Volatile private var instance: AppGraph? = null

        fun get(context: Context): AppGraph = instance ?: synchronized(this) {
            instance ?: run {
                val app = context.applicationContext
                ImageLoader.init(app)
                AppGraph(app).also { instance = it }
            }
        }
    }
}
