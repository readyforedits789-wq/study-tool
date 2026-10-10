package com.example.ui.screens

import android.graphics.RectF
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.core.image.ImageEngine
import com.example.core.model.ImageFormat
import com.example.core.model.ImageMetadata
import com.example.core.model.ProcessedResult
import com.example.core.model.ToolType
import com.example.ui.components.AppTopBar
import com.example.ui.components.ImagePickerSection
import kotlinx.coroutines.launch

@Composable
fun CropRotateScreen(
    initialUri: Uri? = null,
    onBack: () -> Unit,
    onResultReady: (ProcessedResult) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedUri by remember { mutableStateOf(initialUri) }
    var metadata by remember { mutableStateOf<ImageMetadata?>(null) }

    var rotationDegrees by remember { mutableIntStateOf(0) }
    var flipHorizontal by remember { mutableStateOf(false) }
    var flipVertical by remember { mutableStateOf(false) }

    var isProcessing by remember { mutableStateOf(false) }

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
                title = "Crop & Rotate",
                onBack = onBack,
                actions = {
                    if (selectedUri != null && (rotationDegrees != 0 || flipHorizontal || flipVertical)) {
                        TextButton(
                            onClick = {
                                rotationDegrees = 0
                                flipHorizontal = false
                                flipVertical = false
                            }
                        ) {
                            Text("Reset")
                        }
                    }
                }
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
                onImageSelected = {
                    selectedUri = it
                    rotationDegrees = 0
                    flipHorizontal = false
                    flipVertical = false
                },
                label = "Select Photo to Transform"
            )

            if (selectedUri != null && metadata != null) {
                val meta = metadata!!

                // Live Preview with rotation & flip transform applied
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Transform Preview",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.Start)
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(280.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.Black),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(context).data(selectedUri).build(),
                                contentDescription = "Transform Preview",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        rotationZ = rotationDegrees.toFloat()
                                        scaleX = if (flipHorizontal) -1f else 1f
                                        scaleY = if (flipVertical) -1f else 1f
                                    },
                                contentScale = ContentScale.Fit
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Rotation: ${rotationDegrees}° • Flip H: ${if (flipHorizontal) "Yes" else "No"} • Flip V: ${if (flipVertical) "Yes" else "No"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Transform Controls
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Rotation & Flips",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    rotationDegrees = (rotationDegrees - 90 + 360) % 360
                                },
                                modifier = Modifier.weight(1f).testTag("btn_rotate_ccw"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.RotateLeft, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("-90°")
                            }

                            OutlinedButton(
                                onClick = {
                                    rotationDegrees = (rotationDegrees + 90) % 360
                                },
                                modifier = Modifier.weight(1f).testTag("btn_rotate_cw"),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.RotateRight, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("+90°")
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            FilterChip(
                                selected = flipHorizontal,
                                onClick = { flipHorizontal = !flipHorizontal },
                                label = { Text("Flip Horizontal ↔") },
                                modifier = Modifier.weight(1f).testTag("chip_flip_h")
                            )

                            FilterChip(
                                selected = flipVertical,
                                onClick = { flipVertical = !flipVertical },
                                label = { Text("Flip Vertical ↕") },
                                modifier = Modifier.weight(1f).testTag("chip_flip_v")
                            )
                        }
                    }
                }

                // Apply button
                Button(
                    onClick = {
                        val uri = selectedUri ?: return@Button
                        isProcessing = true

                        coroutineScope.launch {
                            try {
                                val (cacheFile, bytes) = ImageEngine.cropRotateFlip(
                                    context = context,
                                    uri = uri,
                                    rotationDegrees = rotationDegrees,
                                    flipHorizontal = flipHorizontal,
                                    flipVertical = flipVertical
                                )

                                val resultUri = Uri.fromFile(cacheFile)
                                val resultMeta = ImageEngine.getMetadata(context, resultUri)

                                val result = ProcessedResult(
                                    tool = ToolType.CROP_ROTATE,
                                    originalUri = uri,
                                    originalSizeBytes = meta.fileSizeBytes,
                                    originalWidth = meta.width,
                                    originalHeight = meta.height,
                                    resultUri = resultUri,
                                    resultSizeBytes = cacheFile.length(),
                                    resultWidth = resultMeta.width,
                                    resultHeight = resultMeta.height,
                                    format = ImageFormat.JPEG,
                                    extraDetails = "Rotated ${rotationDegrees}°, H:${flipHorizontal}, V:${flipVertical}"
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
                        .testTag("btn_apply_transform"),
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
                        Text("Applying Transform...", fontWeight = FontWeight.Bold)
                    } else {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Apply & Save Photo", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}
