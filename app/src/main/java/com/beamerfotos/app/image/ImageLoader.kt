package com.beamerfotos.app.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.min

/**
 * Lädt Fotos passend verkleinert (ImageDecoder liest auch HEIC und dreht nach den EXIF-Angaben).
 * Vorschaubilder werden zusätzlich im Cache-Ordner abgelegt, damit Alben schnell erscheinen.
 */
object ImageLoader {
    private var thumbDir: File? = null

    private val thumbs = object : LruCache<String, ImageBitmap>(48 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = max(1, value.width * value.height * 4 / 1024)
    }
    private val fulls = LruCache<String, ImageBitmap>(5)
    private val thumbLimit = Semaphore(3)
    private val fullLimit = Semaphore(2)

    fun init(context: Context) {
        thumbDir = File(context.cacheDir, "vorschau").apply { mkdirs() }
    }

    private fun key(f: File) = f.absolutePath + "@" + f.lastModified()

    fun cachedThumb(f: File): ImageBitmap? = thumbs.get(key(f))

    fun cachedFull(f: File): ImageBitmap? = fulls.get(key(f))

    suspend fun thumb(f: File): ImageBitmap? = withContext(Dispatchers.IO) {
        thumbLimit.withPermit {
            val k = key(f)
            thumbs.get(k) ?: run {
                val disk = thumbDir?.let { File(it, k.hashCode().toUInt().toString(16) + "_" + f.length() + ".jpg") }
                val fromDisk = disk?.takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.absolutePath) }
                val bitmap = fromDisk ?: decode(f, 480, 320, software = true)?.also { b ->
                    if (disk != null) runCatching { disk.outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, 85, it) } }
                }
                bitmap?.asImageBitmap()?.also { thumbs.put(k, it) }
            }
        }
    }

    suspend fun full(f: File, screenWidth: Int, screenHeight: Int): ImageBitmap? = withContext(Dispatchers.IO) {
        fullLimit.withPermit {
            val k = key(f)
            fulls.get(k) ?: decode(f, max(screenWidth, screenHeight), min(screenWidth, screenHeight), software = false)
                ?.asImageBitmap()?.also { fulls.put(k, it) }
        }
    }

    fun forget(f: File) {
        thumbs.remove(key(f))
        fulls.remove(key(f))
    }

    /** Verkleinert ganzzahlig, so dass das Bild mindestens die Zielgröße behält (Seitenverhältnis bleibt). */
    private fun decode(f: File, longSide: Int, shortSide: Int, software: Boolean): Bitmap? = try {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(f)) { decoder, info, _ ->
            val w = info.size.width
            val h = info.size.height
            val ratio = min(max(w, h).toFloat() / longSide, min(w, h).toFloat() / shortSide)
            decoder.setTargetSampleSize(max(1, ratio.toInt()))
            if (software) decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            // Teilweise beschädigte Fotos trotzdem zeigen statt abzubrechen.
            decoder.setOnPartialImageListener { true }
        }
    } catch (e: Exception) {
        null
    } catch (e: OutOfMemoryError) {
        null
    }
}
