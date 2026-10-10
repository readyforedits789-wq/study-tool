package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.image.ImageEngine
import com.example.core.model.ImageFormat
import com.example.core.model.ImageMetadata
import com.example.core.model.ProcessedResult
import com.example.core.model.ToolType
import com.example.ui.components.AppTopBar
import com.example.ui.components.ImagePickerSection
import com.example.ui.components.StatPill
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class ResizeMode {
    PERCENTAGE,
    CUSTOM_PIXELS
}

@Composable
fun ResizeScreen(
    initialUri: Uri? = null,
    onBack: () -> Unit,
    onResultReady: (ProcessedResult) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedUri by remember { mutableStateOf(initialUri) }
    var metadata by remember { mutableStateOf<ImageMetadata?>(null) }

    var resizeMode by remember { mutableStateOf(ResizeMode.PERCENTAGE) }
    var percentageSlider by remember { mutableFloatStateOf(50f) }

    var widthText by remember { mutableStateOf("") }
    var heightText by remember { mutableStateOf("") }
    var lockAspectRatio by remember { mutableStateOf(true) }

    var selectedFormat by remember { mutableStateOf(ImageFormat.JPEG) }
    var qualitySlider by remember { mutableFloatStateOf(85f) }

    var isProcessing by remember { mutableStateOf(false) }

    // Update dimensions on metadata load
    LaunchedEffect(selectedUri) {
        val uri = selectedUri
        if (uri != null) {
            try {
                val meta = ImageEngine.getMetadata(context, uri)
                metadata = meta
                widthText = (meta.width * 0.5f).roundToInt().toString()
                heightText = (meta.height * 0.5f).roundToInt().toString()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Auto-update width/height if percentage changes
    LaunchedEffect(percentageSlider, resizeMode) {
        if (resizeMode == ResizeMode.PERCENTAGE) {
            metadata?.let { meta ->
                val factor = percentageSlider / 100f
                widthText = (meta.width * factor).roundToInt().coerceAtLeast(16).toString()
                heightText = (meta.height * factor).roundToInt().coerceAtLeast(16).toString()
            }
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Image Resizer",
                onBack = onBack
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ImagePickerSection(
                selectedUri = selectedUri,
                metadata = metadata,
                onImageSelected = { selectedUri = it },
                label = "Select Photo to Resize"
            )

            if (selectedUri != null && metadata != null) {
                val meta = metadata!!

                // Resize Mode Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Resize Dimensions",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            SegmentedButton(
                                selected = resizeMode == ResizeMode.PERCENTAGE,
                                onClick = { resizeMode = ResizeMode.PERCENTAGE },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                                modifier = Modifier.testTag("tab_resize_percent")
                            ) {
                                Text("Percentage (%)")
                            }
                            SegmentedButton(
                                selected = resizeMode == ResizeMode.CUSTOM_PIXELS,
                                onClick = { resizeMode = ResizeMode.CUSTOM_PIXELS },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                                modifier = Modifier.testTag("tab_resize_pixels")
                            ) {
                                Text("Custom Pixels (px)")
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        if (resizeMode == ResizeMode.PERCENTAGE) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Scale by: ${percentageSlider.toInt()}%", fontWeight = FontWeight.SemiBold)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf(25, 50, 75).forEach { pct ->
                                        FilterChip(
                                            selected = percentageSlider.toInt() == pct,
                                            onClick = { percentageSlider = pct.toFloat() },
                                            label = { Text("$pct%") }
                                        )
                                    }
                                }
                            }

                            Slider(
                                value = percentageSlider,
                                onValueChange = { percentageSlider = it },
                                valueRange = 10f..100f,
                                steps = 17,
                                modifier = Modifier.fillMaxWidth().testTag("slider_resize_percent")
                            )
                        } else {
                            // Custom Width x Height
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedTextField(
                                    value = widthText,
                                    onValueChange = { newW ->
                                        if (newW.all { it.isDigit() }) {
                                            widthText = newW
                                            if (lockAspectRatio) {
                                                val wVal = newW.toIntOrNull() ?: 0
                                                if (wVal > 0 && meta.width > 0) {
                                                    val ratio = meta.height.toFloat() / meta.width
                                                    heightText = (wVal * ratio).roundToInt().toString()
                                                }
                                            }
                                        }
                                    },
                                    label = { Text("Width (px)") },
                                    modifier = Modifier.weight(1f).testTag("input_resize_width"),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true
                                )

                                IconButton(
                                    onClick = { lockAspectRatio = !lockAspectRatio },
                                    modifier = Modifier.testTag("btn_lock_aspect_ratio")
                                ) {
                                    Icon(
                                        imageVector = if (lockAspectRatio) Icons.Default.Lock else Icons.Default.LockOpen,
                                        contentDescription = "Lock Aspect Ratio",
                                        tint = if (lockAspectRatio) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                    )
                                }

                                OutlinedTextField(
                                    value = heightText,
                                    onValueChange = { newH ->
                                        if (newH.all { it.isDigit() }) {
                                            heightText = newH
                                            if (lockAspectRatio) {
                                                val hVal = newH.toIntOrNull() ?: 0
                                                if (hVal > 0 && meta.height > 0) {
                                                    val ratio = meta.width.toFloat() / meta.height
                                                    widthText = (hVal * ratio).roundToInt().toString()
                                                }
                                            }
                                        }
                                    },
                                    label = { Text("Height (px)") },
                                    modifier = Modifier.weight(1f).testTag("input_resize_height"),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Output Resolution Preview Card
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("Original", style = MaterialTheme.typography.labelSmall)
                                    Text("${meta.width} × ${meta.height} px", fontWeight = FontWeight.Bold)
                                }
                                Icon(Icons.Default.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Column {
                                    Text("New Target", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    Text("${widthText.ifBlank { "0" }} × ${heightText.ifBlank { "0" }} px", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }

                // Output Format & Quality Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Export Format & Quality",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ImageFormat.values().forEach { fmt ->
                                val isSelected = selectedFormat == fmt
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedFormat = fmt },
                                    label = { Text(fmt.name, fontWeight = FontWeight.Bold) },
                                    modifier = Modifier.weight(1f).testTag("chip_format_${fmt.name.lowercase()}")
                                )
                            }
                        }

                        if (selectedFormat != ImageFormat.PNG) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Quality:", fontWeight = FontWeight.SemiBold)
                                Text("${qualitySlider.toInt()}%", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            Slider(
                                value = qualitySlider,
                                onValueChange = { qualitySlider = it },
                                valueRange = 20f..100f,
                                modifier = Modifier.fillMaxWidth().testTag("slider_resize_quality")
                            )
                        }
                    }
                }

                // Resize Action Button
                Button(
                    onClick = {
                        val uri = selectedUri ?: return@Button
                        val targetW = widthText.toIntOrNull() ?: 100
                        val targetH = heightText.toIntOrNull() ?: 100

                        if (targetW < 16 || targetH < 16) {
                            Toast.makeText(context, "Minimum dimensions are 16x16 px", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        isProcessing = true
                        coroutineScope.launch {
                            try {
                                val (cacheFile, bytes) = ImageEngine.resizeImage(
                                    context = context,
                                    uri = uri,
                                    targetWidth = targetW,
                                    targetHeight = targetH,
                                    format = selectedFormat,
                                    quality = qualitySlider.toInt()
                                )

                                val resultUri = Uri.fromFile(cacheFile)
                                val resultMeta = ImageEngine.getMetadata(context, resultUri)

                                val result = ProcessedResult(
                                    tool = ToolType.RESIZE,
                                    originalUri = uri,
                                    originalSizeBytes = meta.fileSizeBytes,
                                    originalWidth = meta.width,
                                    originalHeight = meta.height,
                                    resultUri = resultUri,
                                    resultSizeBytes = cacheFile.length(),
                                    resultWidth = resultMeta.width,
                                    resultHeight = resultMeta.height,
                                    format = selectedFormat,
                                    extraDetails = "Resized to ${targetW}×${targetH} px"
                                )

                                isProcessing = false
                                onResultReady(result)
                            } catch (e: Exception) {
                                e.printStackTrace()
                                isProcessing = false
                                Toast.makeText(context, "Resize failed: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("btn_start_resize"),
                    shape = RoundedCornerShape(14.dp),
                    enabled = !isProcessing
                ) {
                    if (isProcessing) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.5.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Resizing Photo...", fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.AspectRatio, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Resize to ${widthText} × ${heightText} px", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}
