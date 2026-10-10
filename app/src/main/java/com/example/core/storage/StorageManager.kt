package com.example.core.storage

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object StorageManager {

    suspend fun saveImageToGallery(
        context: Context,
        sourceUri: Uri,
        baseName: String,
        mimeType: String
    ): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val extension = when {
                mimeType.contains("png", ignoreCase = true) -> "png"
                mimeType.contains("webp", ignoreCase = true) -> "webp"
                else -> "jpg"
            }
            val fileName = "${baseName}_${System.currentTimeMillis()}.$extension"

            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/PocketPhotoTools")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }

            val itemUri = context.contentResolver.insert(collection, contentValues)
                ?: return@withContext Result.failure(Exception("Failed to create MediaStore entry"))

            context.contentResolver.openOutputStream(itemUri)?.use { outStream ->
                context.contentResolver.openInputStream(sourceUri)?.use { inStream ->
                    inStream.copyTo(outStream)
                } ?: return@withContext Result.failure(Exception("Failed to open source image"))
            } ?: return@withContext Result.failure(Exception("Failed to open output stream"))

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                context.contentResolver.update(itemUri, contentValues, null, null)
            }

            Result.success(itemUri)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    fun getShareableUri(context: Context, uri: Uri): Uri {
        return if (uri.scheme == "file") {
            val file = File(uri.path ?: "")
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } else {
            uri
        }
    }

    fun shareImage(context: Context, uri: Uri, mimeType: String = "image/jpeg", subject: String = "Processed with Pocket Photo Tools") {
        try {
            val shareUri = getShareableUri(context, uri)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, shareUri)
                putExtra(Intent.EXTRA_SUBJECT, subject)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(intent, "Share Photo via")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun shareMultipleImages(context: Context, uris: List<Uri>, mimeType: String = "image/jpeg") {
        try {
            val shareUris = ArrayList(uris.map { getShareableUri(context, it) })
            val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = mimeType
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, shareUris)
                putExtra(Intent.EXTRA_SUBJECT, "Photos processed with Pocket Photo Tools")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val chooser = Intent.createChooser(intent, "Share ${shareUris.size} Photos")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Copy bundled drawable asset to cached file Uri for instant sample demo
     */
    suspend fun createSampleUri(context: Context, drawableResId: Int, name: String): Uri = withContext(Dispatchers.IO) {
        val cacheDir = File(context.cacheDir, "samples").apply { mkdirs() }
        val targetFile = File(cacheDir, "$name.jpg")
        if (!targetFile.exists() || targetFile.length() == 0L) {
            context.resources.openRawResource(drawableResId).use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }
        }
        Uri.fromFile(targetFile)
    }
}
