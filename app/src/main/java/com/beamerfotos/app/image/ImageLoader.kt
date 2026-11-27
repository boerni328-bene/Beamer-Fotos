package com.beamerfotos.app.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.beamerfotos.app.data.Names
import com.beamerfotos.app.data.Picture
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

    private val levelCache = LruCache<String, Pair<Float, Float>>(500)

    /** Dunkelster und hellster Wert des Fotos für „Automatisch verbessern“ (aus dem Vorschaubild). */
    suspend fun levels(f: File): Pair<Float, Float>? {
        val k = key(f)
        levelCache.get(k)?.let { return it }
        val bitmap = thumb(f)?.asAndroidBitmap() ?: return null
        return withContext(Dispatchers.Default) {
            runCatching {
                val pixels = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                Picture.levels(pixels)
            }.getOrNull()?.also { levelCache.put(k, it) }
        }
    }

    fun forget(f: File) {
        thumbs.remove(key(f))
        fulls.remove(key(f))
    }

    /** Verkleinert ganzzahlig, so dass das Bild mindestens die Zielgröße behält (Seitenverhältnis bleibt). */
    private fun decode(f: File, longSide: Int, shortSide: Int, software: Boolean): Bitmap? =
        if (Names.isVideo(f.name)) videoFrame(f, longSide) else decodeImage(f, longSide, shortSide, software)

    /** Ein Bild aus dem Video (nach einer Sekunde) als Vorschau. */
    private fun videoFrame(f: File, longSide: Int): Bitmap? = try {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(f.absolutePath)
            val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: longSide
            val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: longSide
            val scale = min(1f, longSide.toFloat() / max(w, h).coerceAtLeast(1))
            retriever.getScaledFrameAtTime(
                1_000_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                (w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1),
            ) ?: retriever.frameAtTime
        } finally {
            retriever.release()
        }
    } catch (e: Exception) {
        null
    } catch (e: OutOfMemoryError) {
        null
    }

    private fun decodeImage(f: File, longSide: Int, shortSide: Int, software: Boolean): Bitmap? = try {
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
