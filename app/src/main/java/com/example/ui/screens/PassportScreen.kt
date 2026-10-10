package com.example.ui.screens

import android.graphics.RectF
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.example.core.image.ImageEngine
import com.example.core.model.*
import com.example.ui.components.AppTopBar
import com.example.ui.components.ImagePickerSection
import com.example.ui.components.PassportFaceOverlay
import kotlinx.coroutines.launch

@Composable
fun PassportScreen(
    initialUri: Uri? = null,
    onBack: () -> Unit,
    onResultReady: (ProcessedResult) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedUri by remember { mutableStateOf(initialUri) }
    var metadata by remember { mutableStateOf<ImageMetadata?>(null) }

    var selectedPreset by remember { mutableStateOf(PassportPresets.list[0]) }
    var cropRectNorm by remember { mutableStateOf(RectF(0.1f, 0.1f, 0.9f, 0.9f)) }

    var selectedBgColor by remember { mutableStateOf<String?>("#FFFFFF") }
    var copiesCount by remember { mutableIntStateOf(4) }
    var includeCutLines by remember { mutableStateOf(true) }

    var isProcessing by remember { mutableStateOf(false) }

    val bgColors = listOf(
        Pair("White", "#FFFFFF"),
        Pair("Off-White", "#F5F5F5"),
        Pair("Light Blue", "#E3F2FD"),
        Pair("Light Grey", "#EEEEEE")
    )

    LaunchedEffect(selectedUri) {
        val uri = selectedUri
        if (uri != null) {
            try {
                metadata = ImageEngine.getMetadata(context, uri)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Passport Photo Maker",
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
            // Photo Picker
            ImagePickerSection(
                selectedUri = selectedUri,
                metadata = metadata,
                onImageSelected = { selectedUri = it },
                label = "Select Portrait for ID / Passport",
                recommendPortrait = true
            )

            if (selectedUri != null) {
                // Preset Selector Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Document & Country Standard",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "${selectedPreset.widthMm} × ${selectedPreset.heightMm} mm",
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(PassportPresets.list) { preset ->
                                val isSelected = selectedPreset.id == preset.id
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedPreset = preset },
                                    label = {
                                        Text(
                                            text = preset.country,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    leadingIcon = if (preset.isPopular) {
                                        { Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                    } else null,
                                    modifier = Modifier.testTag("preset_chip_${preset.id}")
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "${selectedPreset.title} • ${selectedPreset.description}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // Interactive Face Crop Guide Box
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Face Positioning & Head Guide",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Position your head within the oval guide for official compliance.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(320.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.Black)
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(selectedUri)
                                    .build(),
                                contentDescription = "Portrait for passport",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )

                            PassportFaceOverlay(
                                aspectRatioWidth = selectedPreset.widthMm.toFloat(),
                                aspectRatioHeight = selectedPreset.heightMm.toFloat(),
                                onCropRectChanged = { rect ->
                                    cropRectNorm = rect
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            LegendItem(color = Color(0xFF64B5F6), label = "Head Oval")
                            LegendItem(color = Color(0xFF81C784), label = "Eye Line")
                            LegendItem(color = Color(0xFFFFB74D), label = "Chin Level")
                        }
                    }
                }

                // Background Color Options
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Background Color",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            bgColors.forEach { (name, hex) ->
                                val isSelected = selectedBgColor == hex
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { selectedBgColor = hex }
                                        .testTag("bg_color_$name"),
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                    border = BorderStroke(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier.padding(vertical = 10.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .background(Color(android.graphics.Color.parseColor(hex)))
                                                .border(1.dp, Color.Gray, CircleShape)
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(name, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                    }
                }

                // Copies & Print Sheet Layout
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Print Layout & Multiple Copies",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Arrange multiple photos on standard 4×6 inch (10×15 cm) print sheet",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(
                                Pair(1, "Single"),
                                Pair(2, "2 Photos"),
                                Pair(4, "4 Photos"),
                                Pair(6, "6 Photos"),
                                Pair(8, "8 Photos")
                            ).forEach { (count, label) ->
                                val isSelected = copiesCount == count
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { copiesCount = count },
                                    label = { Text(label, fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                                    modifier = Modifier.weight(1f).testTag("chip_copies_$count")
                                )
                            }
                        }

                        if (copiesCount > 1) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Add Scissor Cutting Guidelines", style = MaterialTheme.typography.bodyMedium)
                                Switch(
                                    checked = includeCutLines,
                                    onCheckedChange = { includeCutLines = it },
                                    modifier = Modifier.testTag("switch_cut_lines")
                                )
                            }
                        }
                    }
                }

                // Generate Passport Action Button
                Button(
                    onClick = {
                        val uri = selectedUri ?: return@Button
                        val meta = metadata ?: return@Button
                        isProcessing = true

                        coroutineScope.launch {
                            try {
                                val (cacheFile, bytes) = ImageEngine.generatePassportPhoto(
                                    context = context,
                                    uri = uri,
                                    preset = selectedPreset,
                                    cropRectNorm = cropRectNorm,
                                    backgroundColorHex = selectedBgColor,
                                    copiesCount = copiesCount,
                                    includeCutLines = includeCutLines
                                )

                                val resultUri = Uri.fromFile(cacheFile)
                                val resultMeta = ImageEngine.getMetadata(context, resultUri)

                                val result = ProcessedResult(
                                    tool = ToolType.PASSPORT,
                                    originalUri = uri,
                                    originalSizeBytes = meta.fileSizeBytes,
                                    originalWidth = meta.width,
                                    originalHeight = meta.height,
                                    resultUri = resultUri,
                                    resultSizeBytes = cacheFile.length(),
                                    resultWidth = resultMeta.width,
                                    resultHeight = resultMeta.height,
                                    format = ImageFormat.JPEG,
                                    extraDetails = "${selectedPreset.title} • ${if (copiesCount == 1) "Single Photo" else "$copiesCount Copies on 4x6\" Sheet"}"
                                )

                                isProcessing = false
                                onResultReady(result)
                            } catch (e: Exception) {
                                e.printStackTrace()
                                isProcessing = false
                                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("btn_generate_passport"),
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
                        Text("Rendering Passport Studio Photo...", fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.Print, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (copiesCount == 1) "Generate Single Photo" else "Generate 4x6\" Print Sheet ($copiesCount Copies)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}
