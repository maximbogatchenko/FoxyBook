package com.foxybook.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import kotlin.math.abs

/**
 * Full-screen cover viewer with:
 * - Dark background
 * - Pinch-to-zoom
 * - Double-tap to zoom / reset
 * - Panning when zoomed in
 * - Swipe down to dismiss
 * - Close via back button, X button, or tap outside image
 * - Smooth open/close animations
 */
@Composable
fun CoverViewer(
    coverUrl: String,
    onDismiss: () -> Unit
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }

    BackHandler(onBack = onDismiss)

    AnimatedVisibility(
        visible = true,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = (0.92f * (1f - abs(dragOffsetY) / 1000f)).coerceIn(0f, 0.92f)))
        ) {
            // Tap-outside-to-close area (behind the image)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(onClick = onDismiss)
            )

            // Zoomable image with swipe-to-dismiss
            AsyncImage(
                model = coverUrl,
                contentDescription = "Обложка",
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offsetX
                        translationY = offsetY + dragOffsetY
                        alpha = (1f - abs(dragOffsetY) / 800f).coerceIn(0f, 1f)
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = { _ ->
                                if (scale > 1.05f) {
                                    scale = 1f
                                    offsetX = 0f
                                    offsetY = 0f
                                } else {
                                    scale = 2.5f
                                }
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val newScale = (scale * zoom).coerceIn(1f, 5f)
                            scale = newScale
                            if (newScale > 1f) {
                                offsetX += pan.x
                                offsetY += pan.y
                                dragOffsetY = 0f
                            } else {
                                offsetX = 0f
                                offsetY = 0f
                            }
                        }
                    }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragEnd = {
                                if (scale <= 1.05f && abs(dragOffsetY) > 200f) {
                                    onDismiss()
                                } else {
                                    dragOffsetY = 0f
                                }
                            },
                            onDrag = { change, dragAmount ->
                                if (scale <= 1.05f) {
                                    change.consume()
                                    dragOffsetY += dragAmount.y
                                }
                            }
                        )
                    },
                contentScale = ContentScale.Fit,
                alignment = Alignment.Center
            )

            // Close button with better design
            IconButton(
                onClick = {
                    scale = 1f
                    offsetX = 0f
                    offsetY = 0f
                    dragOffsetY = 0f
                    onDismiss()
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(16.dp)
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f))
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Закрыть",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}
