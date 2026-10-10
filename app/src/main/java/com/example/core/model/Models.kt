package com.example.core.model

import android.net.Uri

enum class ToolType(
    val title: String,
    val subtitle: String,
    val badge: String? = null
) {
    COMPRESS("Photo Compressor", "Reduce MB to exact target KB", "Most Popular"),
    RESIZE("Image Resizer", "Scale by pixels or percentage", null),
    PASSPORT("Passport Photo Maker", "ID & Visa presets with print sheets", "Pro"),
    CONVERT("Image Converter", "Switch between JPG, PNG & WebP", null),
    BATCH("Batch Processing", "Process multiple photos simultaneously", "Time Saver"),
    CROP_ROTATE("Crop & Rotate", "Aspect ratios, rotation & flips", null)
}

enum class ImageFormat(val extension: String, val mimeType: String) {
    JPEG("jpg", "image/jpeg"),
    PNG("png", "image/png"),
    WEBP("webp", "image/webp")
}

data class ImageMetadata(
    val width: Int,
    val height: Int,
    val fileSizeBytes: Long,
    val mimeType: String,
    val format: ImageFormat,
    val uri: Uri
) {
    val formattedSize: String
        get() = formatBytes(fileSizeBytes)

    companion object {
        fun formatBytes(bytes: Long): String {
            if (bytes <= 0) return "0 B"
            val kb = bytes / 1024.0
            val mb = kb / 1024.0
            return when {
                mb >= 1.0 -> String.format(java.util.Locale.US, "%.2f MB", mb)
                kb >= 1.0 -> String.format(java.util.Locale.US, "%.1f KB", kb)
                else -> "$bytes B"
            }
        }
    }
}

data class ProcessedResult(
    val tool: ToolType,
    val originalUri: Uri,
    val originalSizeBytes: Long,
    val originalWidth: Int,
    val originalHeight: Int,
    val resultUri: Uri,
    val resultSizeBytes: Long,
    val resultWidth: Int,
    val resultHeight: Int,
    val format: ImageFormat,
    val extraDetails: String = "",
    val timestamp: Long = System.currentTimeMillis()
) {
    val savingsBytes: Long
        get() = (originalSizeBytes - resultSizeBytes).coerceAtLeast(0L)

    val savingsPercent: Int
        get() {
            if (originalSizeBytes <= 0) return 0
            val diff = (originalSizeBytes - resultSizeBytes).toDouble()
            val pct = (diff / originalSizeBytes) * 100
            return pct.toInt()
        }

    val originalFormattedSize: String
        get() = ImageMetadata.formatBytes(originalSizeBytes)

    val resultFormattedSize: String
        get() = ImageMetadata.formatBytes(resultSizeBytes)
}

data class PassportPreset(
    val id: String,
    val country: String,
    val title: String,
    val widthMm: Int,
    val heightMm: Int,
    val targetPxWidth: Int,
    val targetPxHeight: Int,
    val description: String,
    val isPopular: Boolean = false
)

object PassportPresets {
    val list = listOf(
        PassportPreset(
            id = "us_passport",
            country = "United States",
            title = "US Passport / Visa (2x2 in)",
            widthMm = 51,
            heightMm = 51,
            targetPxWidth = 600,
            targetPxHeight = 600,
            description = "2x2 inches (51x51 mm) @ 300 DPI",
            isPopular = true
        ),
        PassportPreset(
            id = "schengen_eu",
            country = "Schengen / Europe",
            title = "Schengen / UK / EU (35x45 mm)",
            widthMm = 35,
            heightMm = 45,
            targetPxWidth = 413,
            targetPxHeight = 531,
            description = "35x45 mm standard for UK, Germany, France, EU",
            isPopular = true
        ),
        PassportPreset(
            id = "india_passport",
            country = "India",
            title = "India Passport (35x45 mm)",
            widthMm = 35,
            heightMm = 45,
            targetPxWidth = 413,
            targetPxHeight = 531,
            description = "35x45 mm Passport & Visa guidelines",
            isPopular = true
        ),
        PassportPreset(
            id = "pakistan_passport",
            country = "Pakistan",
            title = "Pakistan Passport & CNIC (35x45 mm)",
            widthMm = 35,
            heightMm = 45,
            targetPxWidth = 413,
            targetPxHeight = 531,
            description = "35x45 mm for Passport / NADRA CNIC",
            isPopular = true
        ),
        PassportPreset(
            id = "canada_passport",
            country = "Canada",
            title = "Canada Passport (50x70 mm)",
            widthMm = 50,
            heightMm = 70,
            targetPxWidth = 590,
            targetPxHeight = 826,
            description = "50x70 mm official Canadian passport photo",
            isPopular = false
        ),
        PassportPreset(
            id = "china_visa",
            country = "China",
            title = "China Visa (33x48 mm)",
            widthMm = 33,
            heightMm = 48,
            targetPxWidth = 390,
            targetPxHeight = 567,
            description = "33x48 mm official Chinese consular standard",
            isPopular = false
        ),
        PassportPreset(
            id = "stamp_size",
            country = "Universal",
            title = "Stamp Size / ID Badge (25x30 mm)",
            widthMm = 25,
            heightMm = 30,
            targetPxWidth = 295,
            targetPxHeight = 354,
            description = "25x30 mm for Student IDs, badges and job forms",
            isPopular = false
        )
    )
}

enum class BatchItemStatus {
    PENDING,
    PROCESSING,
    SUCCESS,
    FAILED
}

data class BatchItem(
    val id: String,
    val uri: Uri,
    val name: String,
    val originalSizeBytes: Long = 0L,
    val resultSizeBytes: Long = 0L,
    val resultUri: Uri? = null,
    val status: BatchItemStatus = BatchItemStatus.PENDING,
    val error: String? = null
)
