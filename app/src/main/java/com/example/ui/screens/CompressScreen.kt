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
import androidx.compose.ui.graphics.Color
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

enum class CompressMode {
    TARGET_KB,
    QUALITY_PERCENT
}

@Composable
fun CompressScreen(
    initialUri: Uri? = null,
    onBack: () -> Unit,
    onResultReady: (ProcessedResult) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedUri by remember { mutableStateOf(initialUri) }
    var metadata by remember { mutableStateOf<ImageMetadata?>(null) }

    var compressMode by remember { mutableStateOf(CompressMode.TARGET_KB) }
    var targetKbText by remember { mutableStateOf("100") }
    var qualitySlider by remember { mutableFloatStateOf(75f) }

    var isProcessing by remember { mutableStateOf(false) }
    var warningMessage by remember { mutableStateOf<String?>(null) }

    // Load metadata whenever selectedUri changes
    LaunchedEffect(selectedUri) {
        val uri = selectedUri
        if (uri != null) {
            try {
                metadata = ImageEngine.getMetadata(context, uri)
                warningMessage = null
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val quickKbPresets = listOf(50, 100, 200, 500)

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Photo Compressor",
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
            // Image Picker Section
            ImagePickerSection(
                selectedUri = selectedUri,
                metadata = metadata,
                onImageSelected = { uri ->
                    selectedUri = uri
                },
                label = "Select Photo to Compress"
            )

            if (selectedUri != null) {
                // Compression Mode Selector Tab
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Compression Method",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            SegmentedButton(
                                selected = compressMode == CompressMode.TARGET_KB,
                                onClick = { compressMode = CompressMode.TARGET_KB },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                                modifier = Modifier.testTag("tab_target_kb")
                            ) {
                                Text("Target File Size (KB)")
                            }
                            SegmentedButton(
                                selected = compressMode == CompressMode.QUALITY_PERCENT,
                                onClick = { compressMode = CompressMode.QUALITY_PERCENT },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                                modifier = Modifier.testTag("tab_quality_percent")
                            ) {
                                Text("Quality Slider (%)")
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        if (compressMode == CompressMode.TARGET_KB) {
                            Text(
                                text = "Choose or enter target file size:",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            // Quick preset chips
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                quickKbPresets.forEach { kb ->
                                    val isSelected = targetKbText == kb.toString()
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { targetKbText = kb.toString() },
                                        label = { Text("$kb KB", fontWeight = FontWeight.SemiBold) },
                                        modifier = Modifier.testTag("chip_kb_$kb")
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            OutlinedTextField(
                                value = targetKbText,
                                onValueChange = { input ->
                                    if (input.all { it.isDigit() }) {
                                        targetKbText = input
                                    }
                                },
                                label = { Text("Target Size in Kilobytes (KB)") },
                                suffix = { Text("KB") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("input_target_kb"),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true
                            )

                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Tip: The compressor uses smart multi-pass binary search to fit strictly under your chosen size while preserving optimal sharpness.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            // Quality slider
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "JPEG Encoding Quality:",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "${qualitySlider.toInt()}%",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Slider(
                                value = qualitySlider,
                                onValueChange = { qualitySlider = it },
                                valueRange = 10f..95f,
                                steps = 16,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("slider_quality")
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Smallest size (10%)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                Text("Balanced (75%)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                Text("Maximum detail (95%)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                            }
                        }
                    }
                }

                // Metadata comparison teaser
                metadata?.let { meta ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatPill(
                            label = "Original Size",
                            value = meta.formattedSize,
                            modifier = Modifier.weight(1f)
                        )
                        StatPill(
                            label = "Resolution",
                            value = "${meta.width} × ${meta.height}",
                            modifier = Modifier.weight(1f)
                        )
                        StatPill(
                            label = "Target",
                            value = if (compressMode == CompressMode.TARGET_KB) "$targetKbText KB" else "${qualitySlider.toInt()}%",
                            isHighlighted = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Warning indicator if present
                warningMessage?.let { warning ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = warning,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                // Compress Action Button
                Button(
                    onClick = {
                        val uri = selectedUri ?: return@Button
                        val meta = metadata ?: return@Button
                        isProcessing = true
                        warningMessage = null

                        coroutineScope.launch {
                            try {
                                val targetKb = targetKbText.toIntOrNull() ?: 100
                                if (compressMode == CompressMode.TARGET_KB && targetKb < 10) {
                                    Toast.makeText(context, "Minimum target size is 10 KB", Toast.LENGTH_SHORT).show()
                                    isProcessing = false
                                    return@launch
                                }

                                val (cacheFile, bytes) = if (compressMode == CompressMode.TARGET_KB) {
                                    ImageEngine.compressToTargetKb(context, uri, targetKb)
                                } else {
                                    ImageEngine.compressByQuality(context, uri, qualitySlider.toInt(), ImageFormat.JPEG)
                                }

                                val resultUri = Uri.fromFile(cacheFile)
                                val resultMeta = ImageEngine.getMetadata(context, resultUri)

                                val result = ProcessedResult(
                                    tool = ToolType.COMPRESS,
                                    originalUri = uri,
                                    originalSizeBytes = meta.fileSizeBytes,
                                    originalWidth = meta.width,
                                    originalHeight = meta.height,
                                    resultUri = resultUri,
                                    resultSizeBytes = cacheFile.length(),
                                    resultWidth = resultMeta.width,
                                    resultHeight = resultMeta.height,
                                    format = ImageFormat.JPEG,
                                    extraDetails = if (compressMode == CompressMode.TARGET_KB) "Target: ${targetKb} KB" else "Quality: ${qualitySlider.toInt()}%"
                                )

                                isProcessing = false
                                onResultReady(result)
                            } catch (e: Exception) {
                                e.printStackTrace()
                                isProcessing = false
                                warningMessage = "Could not compress image: ${e.localizedMessage ?: "Unknown error"}"
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("btn_start_compression"),
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
                        Text("Optimizing & Compressing...", fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.Compress, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (compressMode == CompressMode.TARGET_KB) "Compress to $targetKbText KB" else "Compress at ${qualitySlider.toInt()}% Quality",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }
    }
}
