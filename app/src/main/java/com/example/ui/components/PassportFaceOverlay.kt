package com.example.ui.components

import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput

@Composable
fun PassportFaceOverlay(
    aspectRatioWidth: Float,
    aspectRatioHeight: Float,
    onCropRectChanged: (RectF) -> Unit,
    modifier: Modifier = Modifier
) {
    // Crop position and zoom within preview
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(0.7f, 3.0f)
                    offsetX += pan.x
                    offsetY += pan.y
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasW = size.width
            val canvasH = size.height

            // Calculate centered passport bounding box based on aspect ratio
            val targetRatio = aspectRatioWidth / aspectRatioHeight
            val currentRatio = canvasW / canvasH

            val boxW: Float
            val boxH: Float

            if (currentRatio > targetRatio) {
                boxH = canvasH * 0.85f
                boxW = boxH * targetRatio
            } else {
                boxW = canvasW * 0.85f
                boxH = boxW / targetRatio
            }

            val boxLeft = (canvasW - boxW) / 2f
            val boxTop = (canvasH - boxH) / 2f

            // Report normalized crop rect back
            val normRect = RectF(
                (boxLeft / canvasW).coerceIn(0f, 1f),
                (boxTop / canvasH).coerceIn(0f, 1f),
                ((boxLeft + boxW) / canvasW).coerceIn(0f, 1f),
                ((boxTop + boxH) / canvasH).coerceIn(0f, 1f)
            )
            onCropRectChanged(normRect)

            // Dim outside areas
            drawRect(
                color = Color.Black.copy(alpha = 0.5f),
                topLeft = Offset(0f, 0f),
                size = Size(canvasW, boxTop)
            )
            drawRect(
                color = Color.Black.copy(alpha = 0.5f),
                topLeft = Offset(0f, boxTop + boxH),
                size = Size(canvasW, canvasH - (boxTop + boxH))
            )
            drawRect(
                color = Color.Black.copy(alpha = 0.5f),
                topLeft = Offset(0f, boxTop),
                size = Size(boxLeft, boxH)
            )
            drawRect(
                color = Color.Black.copy(alpha = 0.5f),
                topLeft = Offset(boxLeft + boxW, boxTop),
                size = Size(canvasW - (boxLeft + boxW), boxH)
            )

            // Passport border
            drawRect(
                color = Color.White,
                topLeft = Offset(boxLeft, boxTop),
                size = Size(boxW, boxH),
                style = Stroke(width = 3f)
            )

            // Head guide oval (standard 70-80% height)
            val headOvalW = boxW * 0.55f
            val headOvalH = boxH * 0.65f
            val headOvalLeft = boxLeft + (boxW - headOvalW) / 2f
            val headOvalTop = boxTop + (boxH * 0.15f)

            val dash = PathEffect.dashPathEffect(floatArrayOf(12f, 12f), 0f)

            drawOval(
                color = Color(0xFF64B5F6),
                topLeft = Offset(headOvalLeft, headOvalTop),
                size = Size(headOvalW, headOvalH),
                style = Stroke(width = 2.5f, pathEffect = dash)
            )

            // Eye line guide
            val eyeY = headOvalTop + (headOvalH * 0.42f)
            drawLine(
                color = Color(0xFF81C784),
                start = Offset(boxLeft + (boxW * 0.2f), eyeY),
                end = Offset(boxLeft + (boxW * 0.8f), eyeY),
                strokeWidth = 2f,
                pathEffect = dash
            )

            // Chin line guide
            val chinY = headOvalTop + headOvalH
            drawLine(
                color = Color(0xFFFFB74D),
                start = Offset(boxLeft + (boxW * 0.25f), chinY),
                end = Offset(boxLeft + (boxW * 0.75f), chinY),
                strokeWidth = 2f,
                pathEffect = dash
            )
        }
    }
}
