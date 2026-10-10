package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.core.image.ImageEngine
import com.example.core.model.*
import com.example.core.storage.StorageManager
import com.example.ui.components.AppTopBar
import com.example.ui.theme.GreenAccent
import kotlinx.coroutines.launch

enum class BatchToolMode {
    COMPRESS,
    RESIZE,
    CONVERT
}

@Composable
fun BatchScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var batchItems by remember { mutableStateOf<List<BatchItem>>(emptyList()) }
    var batchMode by remember { mutableStateOf(BatchToolMode.COMPRESS) }

    // Compress settings
    var targetKb by remember { mutableIntStateOf(100) }
    // Resize settings
    var resizePercent by remember { mutableIntStateOf(50) }
    // Convert settings
    var convertFormat by remember { mutableStateOf(ImageFormat.WEBP) }

    var isProcessing by remember { mutableStateOf(false) }
    var currentProcessingIndex by remember { mutableIntStateOf(-1) }
    var savedAllToGallery by remember { mutableStateOf(false) }

    val multiPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            coroutineScope.launch {
                val newItems = uris.mapIndexed { index, uri ->
                    val meta = try { ImageEngine.getMetadata(context, uri) } catch (_: Exception) { null }
                    BatchItem(
                        id = "item_${System.currentTimeMillis()}_$index",
                        uri = uri,
                        name = "Photo ${index + 1}",
                        originalSizeBytes = meta?.fileSizeBytes ?: 0L,
                        status = BatchItemStatus.PENDING
                    )
                }
                batchItems = batchItems + newItems
            }
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Batch Processing",
                onBack = onBack,
                actions = {
                    if (batchItems.isNotEmpty()) {
                        IconButton(
                            onClick = { batchItems = emptyList() },
                            modifier = Modifier.testTag("btn_clear_batch")
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Clear all")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Queue header / Action cards
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Batch Configuration",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            SegmentedButton(
                                selected = batchMode == BatchToolMode.COMPRESS,
                                onClick = { batchMode = BatchToolMode.COMPRESS },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                                modifier = Modifier.testTag("tab_batch_compress")
                            ) {
                                Text("Compress")
                            }
                            SegmentedButton(
                                selected = batchMode == BatchToolMode.RESIZE,
                                onClick = { batchMode = BatchToolMode.RESIZE },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                                modifier = Modifier.testTag("tab_batch_resize")
                            ) {
                                Text("Resize")
                            }
                            SegmentedButton(
                                selected = batchMode == BatchToolMode.CONVERT,
                                onClick = { batchMode = BatchToolMode.CONVERT },
                                shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                                modifier = Modifier.testTag("tab_batch_convert")
                            ) {
                                Text("Convert")
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        when (batchMode) {
                            BatchToolMode.COMPRESS -> {
                                Text("Target Size for all photos: $targetKb KB", fontWeight = FontWeight.SemiBold)
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(50, 100, 200, 500).forEach { kb ->
                                        FilterChip(
                                            selected = targetKb == kb,
                                            onClick = { targetKb = kb },
                                            label = { Text("$kb KB") }
                                        )
                                    }
                                }
                            }
                            BatchToolMode.RESIZE -> {
                                Text("Scale all photos by: $resizePercent%", fontWeight = FontWeight.SemiBold)
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf(25, 50, 75).forEach { pct ->
                                        FilterChip(
                                            selected = resizePercent == pct,
                                            onClick = { resizePercent = pct },
                                            label = { Text("$pct%") }
                                        )
                                    }
                                }
                            }
                            BatchToolMode.CONVERT -> {
                                Text("Convert all photos to:", fontWeight = FontWeight.SemiBold)
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    ImageFormat.values().forEach { fmt ->
                                        FilterChip(
                                            selected = convertFormat == fmt,
                                            onClick = { convertFormat = fmt },
                                            label = { Text(fmt.name) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Selection controls
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            multiPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("btn_add_photos"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Photos")
                    }

                    OutlinedButton(
                        onClick = {
                            coroutineScope.launch {
                                val u1 = StorageManager.createSampleUri(context, R.drawable.sample_portrait, "batch_portrait")
                                val u2 = StorageManager.createSampleUri(context, R.drawable.sample_landscape, "batch_landscape")
                                val m1 = ImageEngine.getMetadata(context, u1)
                                val m2 = ImageEngine.getMetadata(context, u2)

                                val sampleItems = listOf(
                                    BatchItem("sample_1_${System.currentTimeMillis()}", u1, "Sample Portrait", m1.fileSizeBytes),
                                    BatchItem("sample_2_${System.currentTimeMillis()}", u2, "Sample Landscape", m2.fileSizeBytes)
                                )
                                batchItems = batchItems + sampleItems
                            }
                        },
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("btn_add_samples"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Samples")
                    }
                }
            }

            // Queue count & Process button
            if (batchItems.isNotEmpty()) {
                val successfulCount = batchItems.count { it.status == BatchItemStatus.SUCCESS }

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "Queue: ${batchItems.size} Photos",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "$successfulCount of ${batchItems.size} completed",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            }

                            if (isProcessing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(28.dp),
                                    strokeWidth = 3.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                // Primary Start Processing Button
                item {
                    Button(
                        onClick = {
                            isProcessing = true
                            savedAllToGallery = false

                            coroutineScope.launch {
                                val updatedList = batchItems.toMutableList()

                                for (i in updatedList.indices) {
                                    val current = updatedList[i]
                                    if (current.status == BatchItemStatus.SUCCESS) continue

                                    currentProcessingIndex = i
                                    updatedList[i] = current.copy(status = BatchItemStatus.PROCESSING)
                                    batchItems = updatedList.toList()

                                    try {
                                        val meta = ImageEngine.getMetadata(context, current.uri)
                                        val (cacheFile, bytes) = when (batchMode) {
                                            BatchToolMode.COMPRESS -> {
                                                ImageEngine.compressToTargetKb(context, current.uri, targetKb)
                                            }
                                            BatchToolMode.RESIZE -> {
                                                val targetW = (meta.width * (resizePercent / 100f)).toInt().coerceAtLeast(16)
                                                val targetH = (meta.height * (resizePercent / 100f)).toInt().coerceAtLeast(16)
                                                ImageEngine.resizeImage(context, current.uri, targetW, targetH, ImageFormat.JPEG, 85)
                                            }
                                            BatchToolMode.CONVERT -> {
                                                ImageEngine.convertFormat(context, current.uri, convertFormat, 85)
                                            }
                                        }

                                        updatedList[i] = current.copy(
                                            status = BatchItemStatus.SUCCESS,
                                            originalSizeBytes = meta.fileSizeBytes,
                                            resultSizeBytes = cacheFile.length(),
                                            resultUri = Uri.fromFile(cacheFile)
                                        )
                                    } catch (e: Exception) {
                                        updatedList[i] = current.copy(
                                            status = BatchItemStatus.FAILED,
                                            error = e.message ?: "Failed"
                                        )
                                    }

                                    batchItems = updatedList.toList()
                                }

                                isProcessing = false
                                currentProcessingIndex = -1
                                Toast.makeText(context, "Batch processing complete!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("btn_start_batch"),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !isProcessing
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isProcessing) "Processing Queue..." else "Start Batch Processing",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }

                // Batch Save All / Share All if any succeeded
                if (successfulCount > 0) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        val successItems = batchItems.filter { it.status == BatchItemStatus.SUCCESS && it.resultUri != null }
                                        var savedCount = 0
                                        for (item in successItems) {
                                            val res = StorageManager.saveImageToGallery(
                                                context = context,
                                                sourceUri = item.resultUri!!,
                                                baseName = "batch_pocket_${item.name}",
                                                mimeType = "image/jpeg"
                                            )
                                            if (res.isSuccess) savedCount++
                                        }
                                        savedAllToGallery = true
                                        Toast.makeText(context, "Saved $savedCount photos to Gallery!", Toast.LENGTH_LONG).show()
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("btn_save_all_batch"),
                                colors = ButtonDefaults.buttonColors(containerColor = GreenAccent),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.DownloadDone, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Save All ($successfulCount)", fontWeight = FontWeight.Bold)
                            }

                            FilledTonalButton(
                                onClick = {
                                    val uris = batchItems.filter { it.status == BatchItemStatus.SUCCESS && it.resultUri != null }.map { it.resultUri!! }
                                    StorageManager.shareMultipleImages(context, uris)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("btn_share_all_batch"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Share All")
                            }
                        }
                    }
                }
            }

            // Items list
            itemsIndexed(batchItems) { index, item ->
                BatchItemRow(
                    item = item,
                    onRemove = {
                        batchItems = batchItems.filter { it.id != item.id }
                    }
                )
            }
        }
    }
}

@Composable
fun BatchItemRow(
    item: BatchItem,
    onRemove: () -> Unit
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(item.resultUri ?: item.uri)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyMedium
                )

                if (item.originalSizeBytes > 0L) {
                    val origStr = ImageMetadata.formatBytes(item.originalSizeBytes)
                    if (item.status == BatchItemStatus.SUCCESS && item.resultSizeBytes > 0L) {
                        val resStr = ImageMetadata.formatBytes(item.resultSizeBytes)
                        val pct = (((item.originalSizeBytes - item.resultSizeBytes).toDouble() / item.originalSizeBytes) * 100).toInt()
                        Text(
                            text = "$origStr → $resStr (-$pct%)",
                            style = MaterialTheme.typography.bodySmall,
                            color = GreenAccent,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Text(
                            text = origStr,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Status chip
                when (item.status) {
                    BatchItemStatus.PENDING -> {
                        Text("Queued", fontSize = 11.sp, color = MaterialTheme.colorScheme.outline)
                    }
                    BatchItemStatus.PROCESSING -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Processing...", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                    BatchItemStatus.SUCCESS -> {
                        Text("Completed ✓", fontSize = 11.sp, color = GreenAccent, fontWeight = FontWeight.Bold)
                    }
                    BatchItemStatus.FAILED -> {
                        Text("Error: ${item.error}", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Close, contentDescription = "Remove", tint = MaterialTheme.colorScheme.outline)
            }
        }
    }
}
