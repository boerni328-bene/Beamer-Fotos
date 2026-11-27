package com.beamerfotos.app.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.beamerfotos.app.data.Names
import com.beamerfotos.app.data.Enhance
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
    // Bis zu zwei Collagen (je 4 Fotos) plus Vorabladen.
    private val fulls = LruCache<String, ImageBitmap>(10)
    private val thumbLimit = Semaphore(3)
    private val fullLimit = Semaphore(2)

    fun init(context: Context) {
        thumbDir = File(context.cacheDir, "vorschau").apply { mkdirs() }
    }

    private fun key(f: File) = f.absolutePath + "@" + f.lastModified()

    fun cachedThumb(f: File): ImageBitmap? = thumbs.get(key(f))


    suspend fun thumb(f: File): ImageBitmap? = withContext(Dispatchers.IO) {
        thumbLimit.withPermit {
            val k = key(f)
            thumbs.get(k) ?: run {
                val disk = diskThumb(f)
                val fromDisk = disk?.takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.absolutePath) }
                val bitmap = fromDisk ?: decode(f, 480, 320, software = true)?.also { b ->
                    if (disk != null) runCatching { disk.outputStream().use { b.compress(Bitmap.CompressFormat.JPEG, 85, it) } }
                }
                bitmap?.asImageBitmap()?.also { thumbs.put(k, it) }
            }
        }
    }

    private fun diskThumb(f: File): File? =
        thumbDir?.let { File(it, key(f).hashCode().toUInt().toString(16) + "_" + f.length() + ".jpg") }

    /** Vorschaubild als JPEG (für die Handy-Seite). */
    suspend fun thumbJpeg(f: File): ByteArray? {
        val disk = diskThumb(f) ?: return null
        if (!disk.exists()) {
            thumbs.remove(key(f))
            thumb(f)
        }
        return withContext(Dispatchers.IO) { runCatching { disk.readBytes() }.getOrNull() }
    }

    private fun fullKey(f: File, auto: Boolean, sharpness: Int) = key(f) + "|" + auto + "|" + sharpness

    fun cachedFull(f: File, auto: Boolean, sharpness: Int): ImageBitmap? = fulls.get(fullKey(f, auto, sharpness))

    /**
     * Foto in Bildschirmgröße. Mit „Automatisch verbessern“ oder „Schärfe“ wird es an den Pixeln bearbeitet
     * (Weißabgleich, Tonwerte, Schärfen) – das dauert auf dem Beamer einen Moment, daher im Hintergrund.
     */
    suspend fun full(f: File, screenWidth: Int, screenHeight: Int, auto: Boolean = false, sharpness: Int = 0): ImageBitmap? =
        withContext(Dispatchers.IO) {
            fullLimit.withPermit {
                val k = fullKey(f, auto, sharpness)
                fulls.get(k) ?: run {
                    val process = !Names.isVideo(f.name) && Enhance.needed(auto, sharpness)
                    val bitmap = decode(f, max(screenWidth, screenHeight), min(screenWidth, screenHeight), software = process)
                        ?: return@run null
                    val result = if (process) enhanced(bitmap, auto, sharpness) else bitmap
                    result.asImageBitmap().also { fulls.put(k, it) }
                }
            }
        }

    private fun enhanced(source: Bitmap, auto: Boolean, sharpness: Int): Bitmap = try {
        val bitmap = if (source.config == Bitmap.Config.ARGB_8888 && source.isMutable) source
        else source.copy(Bitmap.Config.ARGB_8888, true)
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        Enhance.apply(pixels, w, h, auto, sharpness)
        bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
        // Zum Zeichnen in den Grafikspeicher (schneller); geht das nicht, bleibt das normale Bild.
        bitmap.copy(Bitmap.Config.HARDWARE, false) ?: bitmap
    } catch (e: Exception) {
        source
    } catch (e: OutOfMemoryError) {
        source
    }

    private val portraits = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    /** Hochformat (nach EXIF-Drehung)? Liest nur den Dateikopf, Ergebnis wird gemerkt. */
    fun isPortrait(f: File): Boolean = portraits.getOrPut(key(f)) {
        try {
            val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(f.absolutePath, o)
            val turned = runCatching {
                android.media.ExifInterface(f.absolutePath).getAttributeInt(
                    android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL,
                ) in 5..8
            }.getOrDefault(false)
            val w = if (turned) o.outHeight else o.outWidth
            val h = if (turned) o.outWidth else o.outHeight
            w in 1 until h
        } catch (e: Exception) {
            false
        }
    }

    fun forget(f: File) {
        thumbs.remove(key(f))
        fulls.snapshot().keys.filter { it.startsWith(key(f)) }.forEach { fulls.remove(it) }
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
            if (software) {
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.isMutableRequired = true
            }
            // Teilweise beschädigte Fotos trotzdem zeigen statt abzubrechen.
            decoder.setOnPartialImageListener { true }
        }
    } catch (e: Exception) {
        null
    } catch (e: OutOfMemoryError) {
        null
    }
}
