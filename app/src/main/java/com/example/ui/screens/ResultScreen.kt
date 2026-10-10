package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.model.ProcessedResult
import com.example.core.model.ToolType
import com.example.core.storage.StorageManager
import com.example.ui.components.AppTopBar
import com.example.ui.components.BeforeAfterComparison
import com.example.ui.components.StatPill
import com.example.ui.theme.GreenAccent
import kotlinx.coroutines.launch

@Composable
fun ResultScreen(
    result: ProcessedResult,
    onBackToHome: () -> Unit,
    onEditAgain: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isSaving by remember { mutableStateOf(false) }
    var isSavedToGallery by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Processing Result",
                onBack = onBackToHome,
                actions = {
                    IconButton(
                        onClick = {
                            StorageManager.shareImage(
                                context = context,
                                uri = result.resultUri,
                                mimeType = result.format.mimeType
                            )
                        },
                        modifier = Modifier.testTag("btn_share_result_top")
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share")
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
            // Visual comparison card (Interactive Toggle or Side-by-Side)
            BeforeAfterComparison(
                originalUri = result.originalUri,
                resultUri = result.resultUri,
                originalSize = result.originalFormattedSize,
                resultSize = result.resultFormattedSize
            )

            // Grand Savings & Stats Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("card_result_stats"),
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
                        Column {
                            Text(
                                text = result.tool.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (result.extraDetails.isNotBlank()) {
                                Text(
                                    text = result.extraDetails,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        if (result.savingsPercent > 0) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = GreenAccent
                            ) {
                                Text(
                                    text = "-${result.savingsPercent}% SAVED",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatPill(
                            label = "Original Size",
                            value = result.originalFormattedSize,
                            modifier = Modifier.weight(1f)
                        )
                        StatPill(
                            label = "Output Size",
                            value = result.resultFormattedSize,
                            isHighlighted = true,
                            modifier = Modifier.weight(1f)
                        )
                        StatPill(
                            label = "Resolution",
                            value = "${result.resultWidth} × ${result.resultHeight}",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Saved indicator banner
            if (isSavedToGallery) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Photo saved to Pictures/PocketPhotoTools in your device gallery!",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // Action Buttons
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        isSaving = true
                        coroutineScope.launch {
                            val res = StorageManager.saveImageToGallery(
                                context = context,
                                sourceUri = result.resultUri,
                                baseName = "pocket_photo_${result.tool.name.lowercase()}",
                                mimeType = result.format.mimeType
                            )
                            isSaving = false
                            if (res.isSuccess) {
                                isSavedToGallery = true
                                Toast.makeText(context, "Saved to Device Gallery!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Save failed: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("btn_save_to_gallery"),
                    shape = RoundedCornerShape(14.dp),
                    enabled = !isSaving
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.5.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Saving to Gallery...", fontWeight = FontWeight.Bold)
                    } else {
                        Icon(
                            imageVector = if (isSavedToGallery) Icons.Default.Check else Icons.Default.Download,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isSavedToGallery) "Saved to Gallery (Save Again)" else "Save to Phone Gallery",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilledTonalButton(
                        onClick = {
                            StorageManager.shareImage(
                                context = context,
                                uri = result.resultUri,
                                mimeType = result.format.mimeType
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("btn_share_result"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Share Photo")
                    }

                    OutlinedButton(
                        onClick = onEditAgain,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("btn_edit_again"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Adjust Settings")
                    }
                }

                TextButton(
                    onClick = onBackToHome,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_back_to_home")
                ) {
                    Icon(Icons.Default.Home, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Return to Studio Home")
                }
            }
        }
    }
}
