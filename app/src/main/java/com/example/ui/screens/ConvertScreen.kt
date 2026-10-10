package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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

@Composable
fun ConvertScreen(
    initialUri: Uri? = null,
    onBack: () -> Unit,
    onResultReady: (ProcessedResult) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedUri by remember { mutableStateOf(initialUri) }
    var metadata by remember { mutableStateOf<ImageMetadata?>(null) }

    var targetFormat by remember { mutableStateOf(ImageFormat.WEBP) }
    var qualitySlider by remember { mutableFloatStateOf(85f) }
    var fillWhiteForTransparent by remember { mutableStateOf(true) }

    var isProcessing by remember { mutableStateOf(false) }

    LaunchedEffect(selectedUri) {
        val uri = selectedUri
        if (uri != null) {
            try {
                val meta = ImageEngine.getMetadata(context, uri)
                metadata = meta
                // Choose default target format different from current
                targetFormat = when (meta.format) {
                    ImageFormat.JPEG -> ImageFormat.WEBP
                    ImageFormat.PNG -> ImageFormat.WEBP
                    ImageFormat.WEBP -> ImageFormat.JPEG
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Image Converter",
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
                label = "Select Photo to Convert"
            )

            if (selectedUri != null && metadata != null) {
                val meta = metadata!!

                // Conversion Format Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Select Target Format",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            listOf(
                                Triple(ImageFormat.WEBP, "Modern web standard", "Best compression"),
                                Triple(ImageFormat.JPEG, "Universal compatibility", "Standard photo"),
                                Triple(ImageFormat.PNG, "Lossless clarity", "Alpha transparency")
                            ).forEach { (fmt, sub, badge) ->
                                val isSelected = targetFormat == fmt
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("chip_convert_${fmt.name.lowercase()}"),
                                    onClick = { targetFormat = fmt },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    border = BorderStroke(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = fmt.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = badge,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        if (targetFormat != ImageFormat.PNG) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Encoding Quality:", fontWeight = FontWeight.SemiBold)
                                Text("${qualitySlider.toInt()}%", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            Slider(
                                value = qualitySlider,
                                onValueChange = { qualitySlider = it },
                                valueRange = 30f..100f,
                                modifier = Modifier.fillMaxWidth().testTag("slider_convert_quality")
                            )
                        }

                        if (targetFormat == ImageFormat.JPEG && meta.format == ImageFormat.PNG) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Replace Transparency with White", fontWeight = FontWeight.SemiBold)
                                    Text("JPEG lacks alpha channel, fills transparent pixels with pure white.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(
                                    checked = fillWhiteForTransparent,
                                    onCheckedChange = { fillWhiteForTransparent = it },
                                    modifier = Modifier.testTag("switch_transparency")
                                )
                            }
                        }
                    }
                }

                // Format Comparison Preview
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Current Format", style = MaterialTheme.typography.labelSmall)
                            Text(meta.format.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(meta.formattedSize, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Default.ArrowForward, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Target Output", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Text(targetFormat.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                            Text(".${targetFormat.extension}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                // Convert Button
                Button(
                    onClick = {
                        val uri = selectedUri ?: return@Button
                        isProcessing = true

                        coroutineScope.launch {
                            try {
                                val (cacheFile, bytes) = ImageEngine.convertFormat(
                                    context = context,
                                    uri = uri,
                                    targetFormat = targetFormat,
                                    quality = qualitySlider.toInt(),
                                    backgroundColorHex = if (fillWhiteForTransparent) "#FFFFFF" else "#000000"
                                )

                                val resultUri = Uri.fromFile(cacheFile)
                                val resultMeta = ImageEngine.getMetadata(context, resultUri)

                                val result = ProcessedResult(
                                    tool = ToolType.CONVERT,
                                    originalUri = uri,
                                    originalSizeBytes = meta.fileSizeBytes,
                                    originalWidth = meta.width,
                                    originalHeight = meta.height,
                                    resultUri = resultUri,
                                    resultSizeBytes = cacheFile.length(),
                                    resultWidth = resultMeta.width,
                                    resultHeight = resultMeta.height,
                                    format = targetFormat,
                                    extraDetails = "Converted from ${meta.format.name} to ${targetFormat.name}"
                                )

                                isProcessing = false
                                onResultReady(result)
                            } catch (e: Exception) {
                                e.printStackTrace()
                                isProcessing = false
                                Toast.makeText(context, "Conversion failed: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("btn_start_convert"),
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
                        Text("Converting Format...", fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.Transform, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Convert to ${targetFormat.name}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}
