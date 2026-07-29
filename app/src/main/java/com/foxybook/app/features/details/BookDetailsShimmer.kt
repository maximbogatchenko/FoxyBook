package com.foxybook.app.features.details

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.foxybook.app.R
import androidx.compose.ui.unit.dp

/**
 * Shimmer loading placeholder for book details skeleton screen.
 */
@Composable
fun ShimmerLoadingCard() {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val shimmerTranslate = infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1000f,
        animationSpec = infiniteRepeatable(animation = tween(1200, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Restart),
        label = "shimmer"
    )

    Card(modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 100.dp, height = 140.dp).clip(RoundedCornerShape(8.dp)).background(shimmerBrush(shimmerTranslate.value)))
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f)) {
                Box(Modifier.fillMaxWidth(0.9f).height(24.dp).clip(RoundedCornerShape(4.dp)).background(shimmerBrush(shimmerTranslate.value)))
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth(0.6f).height(18.dp).clip(RoundedCornerShape(4.dp)).background(shimmerBrush(shimmerTranslate.value)))
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth(0.4f).height(16.dp).clip(RoundedCornerShape(4.dp)).background(shimmerBrush(shimmerTranslate.value)))
            }
        }
    }
}

@Composable
private fun shimmerBrush(translate: Float) = Brush.linearGradient(
    colors = listOf(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
    start = Offset(translate - 200f, translate - 200f), end = Offset(translate, translate)
)

/**
 * Full-screen loading animation — pulsing book icon with animated dots.
 */
@Composable
fun BookLoadingAnimation() {
    val infiniteTransition = rememberInfiniteTransition(label = "loading")
    val scale by infiniteTransition.animateFloat(initialValue = 0.85f, targetValue = 1.15f,
        animationSpec = infiniteRepeatable(animation = tween(1200, easing = FastOutSlowInEasing), repeatMode = RepeatMode.Reverse), label = "scale")
    val rotation by infiniteTransition.animateFloat(initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(animation = tween(4000, easing = LinearEasing), repeatMode = RepeatMode.Restart), label = "rotation")
    val dotsCount by infiniteTransition.animateFloat(initialValue = 0f, targetValue = 4f,
        animationSpec = infiniteRepeatable(animation = tween(1400, easing = LinearEasing), repeatMode = RepeatMode.Restart), label = "dots")
    val dots = ".".repeat(dotsCount.toInt().coerceIn(0, 4))

    Box(modifier = Modifier.fillMaxSize().background(Brush.radialGradient(
        colors = listOf(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f), MaterialTheme.colorScheme.surface), radius = 800f)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(progress = { 0f }, modifier = Modifier.size(120.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), strokeWidth = 2.dp, trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f))
                Icon(Icons.Default.AutoStories, stringResource(R.string.loading), modifier = Modifier.size(64.dp).scale(scale), tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(32.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Text(stringResource(R.string.book_details_loading), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                Text(dots, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.book_details_loading_info), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
        }
    }
}
