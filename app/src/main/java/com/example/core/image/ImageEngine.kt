package com.example.core.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.media.ExifInterface
import android.net.Uri
import com.example.core.model.ImageFormat
import com.example.core.model.ImageMetadata
import com.example.core.model.PassportPreset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.math.roundToInt

object ImageEngine {

    suspend fun getMetadata(context: Context, uri: Uri): ImageMetadata = withContext(Dispatchers.IO) {
        var width = 0
        var height = 0
        var mimeType = "image/jpeg"
        var sizeBytes = 0L

        // Try getting size via OpenFileDescriptor / AssetFileDescriptor
        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                sizeBytes = pfd.statSize
            }
        } catch (_: Exception) {
            // fallback
        }

        if (sizeBytes <= 0) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    sizeBytes = stream.available().toLong()
                }
            } catch (_: Exception) {}
        }

        // Get bounds
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
                width = options.outWidth
                height = options.outHeight
                options.outMimeType?.let { mimeType = it }
            }
        } catch (_: Exception) {}

        // Check EXIF rotation for dimension swap
        val orientation = getExifOrientation(context, uri)
        if (orientation == 90 || orientation == 270) {
            val tmp = width
            width = height
            height = tmp
        }

        val format = when {
            mimeType.contains("png", ignoreCase = true) -> ImageFormat.PNG
            mimeType.contains("webp", ignoreCase = true) -> ImageFormat.WEBP
            else -> ImageFormat.JPEG
        }

        ImageMetadata(
            width = width.coerceAtLeast(1),
            height = height.coerceAtLeast(1),
            fileSizeBytes = sizeBytes.coerceAtLeast(1024),
            mimeType = mimeType,
            format = format,
            uri = uri
        )
    }

    suspend fun loadCorrectlyOrientedBitmap(
        context: Context,
        uri: Uri,
        maxDimension: Int = 3000
    ): Bitmap? = withContext(Dispatchers.IO) {
        try {
            // Check original size
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOptions)
            }

            var inSampleSize = 1
            val maxOriginal = maxOf(boundsOptions.outWidth, boundsOptions.outHeight)
            if (maxOriginal > maxDimension) {
                while ((maxOriginal / (inSampleSize * 2)) >= maxDimension) {
                    inSampleSize *= 2
                }
            }

            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            val decodedBitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: return@withContext null

            val rotationDegrees = getExifOrientation(context, uri)
            if (rotationDegrees != 0) {
                val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                val rotated = Bitmap.createBitmap(
                    decodedBitmap, 0, 0,
                    decodedBitmap.width, decodedBitmap.height,
                    matrix, true
                )
                if (rotated != decodedBitmap) {
                    decodedBitmap.recycle()
                }
                rotated
            } else {
                decodedBitmap
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun getExifOrientation(context: Context, uri: Uri): Int {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } ?: 0
        } catch (_: Exception) {
            0
        }
    }

    /**
     * Compress to an exact or closest target KB.
     * Uses binary search on JPEG quality and downscales if target KB is very small.
     */
    suspend fun compressToTargetKb(
        context: Context,
        uri: Uri,
        targetKb: Int,
        onProgress: (String) -> Unit = {}
    ): Pair<File, ByteArray> = withContext(Dispatchers.IO) {
        val targetBytes = targetKb * 1024L
        val originalBitmap = loadCorrectlyOrientedBitmap(context, uri)
            ?: throw IllegalStateException("Could not load image bitmap")

        var workingBitmap = originalBitmap
        var bestBytes: ByteArray = ByteArray(0)
        var bestQuality = 85

        // Binary search for optimal quality
        var lowQuality = 8
        var highQuality = 98
        var attempts = 0

        while (lowQuality <= highQuality && attempts < 7) {
            attempts++
            val midQuality = (lowQuality + highQuality) / 2
            val stream = ByteArrayOutputStream()
            workingBitmap.compress(Bitmap.CompressFormat.JPEG, midQuality, stream)
            val currentBytes = stream.toByteArray()

            if (currentBytes.size <= targetBytes) {
                bestBytes = currentBytes
                bestQuality = midQuality
                lowQuality = midQuality + 1 // try higher quality if it still fits
            } else {
                highQuality = midQuality - 1
            }
        }

        // If even lowest quality is larger than target KB, scale down the image resolution
        if (bestBytes.isEmpty() || bestBytes.size > targetBytes) {
            var scale = 0.85f
            var scaleAttempts = 0
            while (scaleAttempts < 5) {
                scaleAttempts++
                val newW = (workingBitmap.width * scale).roundToInt().coerceAtLeast(100)
                val newH = (workingBitmap.height * scale).roundToInt().coerceAtLeast(100)
                val scaled = Bitmap.createScaledBitmap(workingBitmap, newW, newH, true)

                val stream = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, 45, stream)
                val candidateBytes = stream.toByteArray()

                if (scaled != workingBitmap && workingBitmap != originalBitmap) {
                    workingBitmap.recycle()
                }
                workingBitmap = scaled
                bestBytes = candidateBytes

                if (candidateBytes.size <= targetBytes) {
                    break
                }
                scale *= 0.75f
            }
        }

        // If bestBytes is still empty, fallback to quality 60
        if (bestBytes.isEmpty()) {
            val stream = ByteArrayOutputStream()
            workingBitmap.compress(Bitmap.CompressFormat.JPEG, 60, stream)
            bestBytes = stream.toByteArray()
        }

        // Save to cache file
        val cacheFile = createTempCacheFile(context, "compressed_${targetKb}kb", "jpg")
        FileOutputStream(cacheFile).use { fos ->
            fos.write(bestBytes)
            fos.flush()
        }

        if (workingBitmap != originalBitmap) {
            workingBitmap.recycle()
        }
        originalBitmap.recycle()

        Pair(cacheFile, bestBytes)
    }

    /**
     * Compress by quality slider (1 - 100%)
     */
    suspend fun compressByQuality(
        context: Context,
        uri: Uri,
        qualityPercent: Int,
        format: ImageFormat = ImageFormat.JPEG
    ): Pair<File, ByteArray> = withContext(Dispatchers.IO) {
        val bitmap = loadCorrectlyOrientedBitmap(context, uri)
            ?: throw IllegalStateException("Failed to decode image")

        val stream = ByteArrayOutputStream()
        val compressFormat = when (format) {
            ImageFormat.PNG -> Bitmap.CompressFormat.PNG
            ImageFormat.WEBP -> Bitmap.CompressFormat.WEBP
            ImageFormat.JPEG -> Bitmap.CompressFormat.JPEG
        }

        bitmap.compress(compressFormat, qualityPercent.coerceIn(1, 100), stream)
        val bytes = stream.toByteArray()

        val cacheFile = createTempCacheFile(context, "quality_${qualityPercent}", format.extension)
        FileOutputStream(cacheFile).use { fos ->
            fos.write(bytes)
            fos.flush()
        }

        bitmap.recycle()
        Pair(cacheFile, bytes)
    }

    /**
     * Resize image by exact pixel width and height
     */
    suspend fun resizeImage(
        context: Context,
        uri: Uri,
        targetWidth: Int,
        targetHeight: Int,
        format: ImageFormat,
        quality: Int = 90
    ): Pair<File, ByteArray> = withContext(Dispatchers.IO) {
        val originalBitmap = loadCorrectlyOrientedBitmap(context, uri)
            ?: throw IllegalStateException("Could not load image")

        val finalW = targetWidth.coerceIn(16, 8000)
        val finalH = targetHeight.coerceIn(16, 8000)

        val resizedBitmap = Bitmap.createScaledBitmap(originalBitmap, finalW, finalH, true)

        val stream = ByteArrayOutputStream()
        val compressFormat = when (format) {
            ImageFormat.PNG -> Bitmap.CompressFormat.PNG
            ImageFormat.WEBP -> Bitmap.CompressFormat.WEBP
            ImageFormat.JPEG -> Bitmap.CompressFormat.JPEG
        }

        // If converting to JPEG, ensure white background for any transparency
        val outputBitmap = if (format == ImageFormat.JPEG && resizedBitmap.hasAlpha()) {
            val opaque = Bitmap.createBitmap(finalW, finalH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(opaque)
            canvas.drawColor(AndroidColor.WHITE)
            canvas.drawBitmap(resizedBitmap, 0f, 0f, null)
            opaque
        } else {
            resizedBitmap
        }

        outputBitmap.compress(compressFormat, quality.coerceIn(1, 100), stream)
        val bytes = stream.toByteArray()

        val cacheFile = createTempCacheFile(context, "resized_${finalW}x${finalH}", format.extension)
        FileOutputStream(cacheFile).use { fos ->
            fos.write(bytes)
            fos.flush()
        }

        if (outputBitmap != resizedBitmap) outputBitmap.recycle()
        if (resizedBitmap != originalBitmap) resizedBitmap.recycle()
        originalBitmap.recycle()

        Pair(cacheFile, bytes)
    }

    /**
     * Passport Photo Maker
     * - Crops face with preset aspect ratio (e.g. 35x45 mm or 2x2 in)
     * - Optional solid background matte (White, Off-white, Light Blue, Light Grey)
     * - If copiesCount == 1: Exports single high-res passport photo
     * - If copiesCount > 1: Arranges copies on a 4x6 inch (1200x1800 px @ 300 DPI) print sheet with scissor guidelines!
     */
    suspend fun generatePassportPhoto(
        context: Context,
        uri: Uri,
        preset: PassportPreset,
        cropRectNorm: RectF, // Normalized 0..1 crop coordinates
        backgroundColorHex: String? = null,
        copiesCount: Int = 1,
        includeCutLines: Boolean = true
    ): Pair<File, ByteArray> = withContext(Dispatchers.IO) {
        val sourceBitmap = loadCorrectlyOrientedBitmap(context, uri)
            ?: throw IllegalStateException("Could not load portrait")

        // 1. Crop to selected area
        val srcW = sourceBitmap.width
        val srcH = sourceBitmap.height

        val cropLeft = (cropRectNorm.left * srcW).roundToInt().coerceIn(0, srcW - 10)
        val cropTop = (cropRectNorm.top * srcH).roundToInt().coerceIn(0, srcH - 10)
        val cropW = (cropRectNorm.width() * srcW).roundToInt().coerceIn(10, srcW - cropLeft)
        val cropH = (cropRectNorm.height() * srcH).roundToInt().coerceIn(10, srcH - cropTop)

        val cropped = Bitmap.createBitmap(sourceBitmap, cropLeft, cropTop, cropW, cropH)

        // 2. Scale cropped head to target preset size
        val singlePassport = Bitmap.createScaledBitmap(
            cropped,
            preset.targetPxWidth,
            preset.targetPxHeight,
            true
        )

        // 3. Optional background color application / frame
        val styledSingle = if (backgroundColorHex != null) {
            val result = Bitmap.createBitmap(preset.targetPxWidth, preset.targetPxHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(result)
            val color = try {
                AndroidColor.parseColor(backgroundColorHex)
            } catch (_: Exception) {
                AndroidColor.WHITE
            }
            canvas.drawColor(color)
            canvas.drawBitmap(singlePassport, 0f, 0f, null)
            result
        } else {
            singlePassport
        }

        // 4. Single vs Multi-print Sheet (4x6 inch = 1200 x 1800 px @ 300 DPI)
        val finalBitmap: Bitmap
        if (copiesCount <= 1) {
            finalBitmap = styledSingle
        } else {
            // Create 4x6 inch sheet at 300 DPI: 1200 x 1800 px (or landscape 1800 x 1200)
            val sheetW = 1200
            val sheetH = 1800
            finalBitmap = Bitmap.createBitmap(sheetW, sheetH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(finalBitmap)
            canvas.drawColor(AndroidColor.WHITE) // Clean print paper

            val (cols, rows) = when {
                copiesCount <= 2 -> Pair(1, 2)
                copiesCount <= 4 -> Pair(2, 2)
                copiesCount <= 6 -> Pair(2, 3)
                else -> Pair(2, 4) // 8 copies
            }

            val marginX = 80
            val marginY = 80
            val availableW = sheetW - (2 * marginX)
            val availableH = sheetH - (2 * marginY)

            val photoW = (availableW / cols) - 40
            val photoH = (photoW * preset.targetPxHeight) / preset.targetPxWidth
            val scaledForSheet = Bitmap.createScaledBitmap(styledSingle, photoW, photoH, true)

            val dashPaint = Paint().apply {
                this.color = AndroidColor.LTGRAY
                style = Paint.Style.STROKE
                strokeWidth = 3f
                pathEffect = DashPathEffect(floatArrayOf(15f, 15f), 0f)
            }

            val solidBorderPaint = Paint().apply {
                this.color = AndroidColor.argb(60, 0, 0, 0)
                style = Paint.Style.STROKE
                strokeWidth = 2f
            }

            val textPaint = Paint().apply {
                this.color = AndroidColor.GRAY
                textSize = 28f
                isAntiAlias = true
            }

            var index = 0
            for (r in 0 until rows) {
                for (c in 0 until cols) {
                    if (index >= copiesCount) break

                    val x = marginX + (c * (photoW + 40)) + 20
                    val y = marginY + (r * (photoH + 50)) + 20

                    canvas.drawBitmap(scaledForSheet, x.toFloat(), y.toFloat(), null)
                    canvas.drawRect(
                        x.toFloat(),
                        y.toFloat(),
                        (x + photoW).toFloat(),
                        (y + photoH).toFloat(),
                        solidBorderPaint
                    )

                    if (includeCutLines) {
                        // Subtle cut line box
                        canvas.drawRect(
                            (x - 8).toFloat(),
                            (y - 8).toFloat(),
                            (x + photoW + 8).toFloat(),
                            (y + photoH + 8).toFloat(),
                            dashPaint
                        )
                    }

                    index++
                }
            }

            // Print info footer on the 4x6 sheet
            canvas.drawText(
                "Pocket Photo Tools • ${preset.title} • 4x6\" Print Sheet (300 DPI)",
                60f,
                sheetH - 40f,
                textPaint
            )

            if (scaledForSheet != styledSingle) scaledForSheet.recycle()
        }

        val stream = ByteArrayOutputStream()
        finalBitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)
        val bytes = stream.toByteArray()

        val cacheFile = createTempCacheFile(
            context,
            "passport_${preset.id}_${copiesCount}copies",
            "jpg"
        )
        FileOutputStream(cacheFile).use { fos ->
            fos.write(bytes)
            fos.flush()
        }

        // Cleanup
        if (finalBitmap != styledSingle) finalBitmap.recycle()
        if (styledSingle != singlePassport) styledSingle.recycle()
        if (singlePassport != cropped) singlePassport.recycle()
        cropped.recycle()
        sourceBitmap.recycle()

        Pair(cacheFile, bytes)
    }

    /**
     * Image Format Conversion
     */
    suspend fun convertFormat(
        context: Context,
        uri: Uri,
        targetFormat: ImageFormat,
        quality: Int = 90,
        backgroundColorHex: String = "#FFFFFF"
    ): Pair<File, ByteArray> = withContext(Dispatchers.IO) {
        val originalBitmap = loadCorrectlyOrientedBitmap(context, uri)
            ?: throw IllegalStateException("Could not load image")

        val outputBitmap = if (targetFormat == ImageFormat.JPEG && originalBitmap.hasAlpha()) {
            val opaque = Bitmap.createBitmap(originalBitmap.width, originalBitmap.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(opaque)
            val fillCol = try {
                AndroidColor.parseColor(backgroundColorHex)
            } catch (_: Exception) {
                AndroidColor.WHITE
            }
            canvas.drawColor(fillCol)
            canvas.drawBitmap(originalBitmap, 0f, 0f, null)
            opaque
        } else {
            originalBitmap
        }

        val stream = ByteArrayOutputStream()
        val compressFormat = when (targetFormat) {
            ImageFormat.PNG -> Bitmap.CompressFormat.PNG
            ImageFormat.WEBP -> Bitmap.CompressFormat.WEBP
            ImageFormat.JPEG -> Bitmap.CompressFormat.JPEG
        }

        outputBitmap.compress(compressFormat, quality.coerceIn(1, 100), stream)
        val bytes = stream.toByteArray()

        val cacheFile = createTempCacheFile(context, "converted", targetFormat.extension)
        FileOutputStream(cacheFile).use { fos ->
            fos.write(bytes)
            fos.flush()
        }

        if (outputBitmap != originalBitmap) outputBitmap.recycle()
        originalBitmap.recycle()

        Pair(cacheFile, bytes)
    }

    /**
     * Crop, Rotate, and Flip
     */
    suspend fun cropRotateFlip(
        context: Context,
        uri: Uri,
        rotationDegrees: Int,
        flipHorizontal: Boolean,
        flipVertical: Boolean,
        cropRectNorm: RectF? = null,
        format: ImageFormat = ImageFormat.JPEG,
        quality: Int = 92
    ): Pair<File, ByteArray> = withContext(Dispatchers.IO) {
        val originalBitmap = loadCorrectlyOrientedBitmap(context, uri)
            ?: throw IllegalStateException("Could not load image")

        val matrix = Matrix()
        if (rotationDegrees != 0) {
            matrix.postRotate(rotationDegrees.toFloat())
        }
        val scaleX = if (flipHorizontal) -1f else 1f
        val scaleY = if (flipVertical) -1f else 1f
        if (flipHorizontal || flipVertical) {
            matrix.postScale(scaleX, scaleY)
        }

        val transformed = Bitmap.createBitmap(
            originalBitmap,
            0,
            0,
            originalBitmap.width,
            originalBitmap.height,
            matrix,
            true
        )

        val finalBitmap = if (cropRectNorm != null) {
            val w = transformed.width
            val h = transformed.height
            val left = (cropRectNorm.left * w).roundToInt().coerceIn(0, w - 10)
            val top = (cropRectNorm.top * h).roundToInt().coerceIn(0, h - 10)
            val cropW = (cropRectNorm.width() * w).roundToInt().coerceIn(10, w - left)
            val cropH = (cropRectNorm.height() * h).roundToInt().coerceIn(10, h - top)
            val cropped = Bitmap.createBitmap(transformed, left, top, cropW, cropH)
            if (cropped != transformed) transformed.recycle()
            cropped
        } else {
            transformed
        }

        val stream = ByteArrayOutputStream()
        val compressFormat = when (format) {
            ImageFormat.PNG -> Bitmap.CompressFormat.PNG
            ImageFormat.WEBP -> Bitmap.CompressFormat.WEBP
            ImageFormat.JPEG -> Bitmap.CompressFormat.JPEG
        }

        finalBitmap.compress(compressFormat, quality, stream)
        val bytes = stream.toByteArray()

        val cacheFile = createTempCacheFile(context, "edited", format.extension)
        FileOutputStream(cacheFile).use { fos ->
            fos.write(bytes)
            fos.flush()
        }

        if (finalBitmap != originalBitmap) finalBitmap.recycle()
        originalBitmap.recycle()

        Pair(cacheFile, bytes)
    }

    private fun createTempCacheFile(context: Context, prefix: String, extension: String): File {
        val cacheDir = File(context.cacheDir, "images").apply { mkdirs() }
        val filename = "${prefix}_${System.currentTimeMillis()}.$extension"
        return File(cacheDir, filename)
    }
}
