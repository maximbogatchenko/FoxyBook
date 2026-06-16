package com.foxybook.app.ui.components

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import java.io.File

private const val TAG = "COVER"

/**
 * Book cover that loads from URL, falling back to author initials/name
 * when the image is unavailable.
 *
 * @param onCoverClick Optional callback when the cover is tapped.
 *                     Receives the coverUrl so the caller can open a full-screen viewer.
 */
@Composable
fun CoverWithAuthor(
    coverUrl: String,
    author: String,
    contentDescription: String = "",
    width: Dp = 56.dp,
    height: Dp = 78.dp,
    showFullName: Boolean = false,
    onCoverClick: ((String) -> Unit)? = null
) {
    val shape = RoundedCornerShape(10.dp)
    val containerColor = MaterialTheme.colorScheme.primaryContainer
    val onContainerColor = MaterialTheme.colorScheme.onPrimaryContainer

    var imageLoadFailed by remember(coverUrl) { mutableStateOf(false) }
    var isLoading by remember(coverUrl) { mutableStateOf(false) }
    val showFallback = coverUrl.isBlank() || imageLoadFailed

    // Coil надёжнее работает с File для локальных файлов, чем с file:// строкой
    val coverModel: Any = remember(coverUrl) {
        when {
            coverUrl.startsWith("file://") -> File(coverUrl.removePrefix("file://"))
            else -> coverUrl
        }
    }

    if (coverUrl.isNotBlank()) {
        Log.d(TAG, "COVER_URL | url=$coverUrl author=$author")
    }

    Box(
        modifier = Modifier
            .size(width = width, height = height)
            .clip(shape)
            .background(containerColor)
            .then(
                if (onCoverClick != null && coverUrl.isNotBlank()) {
                    Modifier.clickable { onCoverClick(coverUrl) }
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (showFallback) {
            AuthorFallback(author = author, showFullName = showFullName, color = onContainerColor)
        } else {
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = coverModel,
                    contentDescription = contentDescription,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(shape),
                    contentScale = ContentScale.Crop,
                    onState = { state ->
                        when (state) {
                            is AsyncImagePainter.State.Loading -> {
                                isLoading = true
                            }
                            is AsyncImagePainter.State.Success -> {
                                isLoading = false
                                Log.d(TAG, "COVER_LOADED | url=$coverUrl")
                            }
                            is AsyncImagePainter.State.Error -> {
                                isLoading = false
                                Log.e(TAG, "COVER_ERROR | url=$coverUrl error=${state.result.throwable?.message}")
                                imageLoadFailed = true
                            }
                            else -> {
                                isLoading = false
                            }
                        }
                    }
                )

                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(24.dp)
                            .align(Alignment.Center),
                        strokeWidth = 2.dp,
                        color = onContainerColor.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

/**
 * Non-clickable label with author name or initials, used as cover fallback.
 */
@Composable
fun AuthorFallback(
    author: String,
    showFullName: Boolean = false,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onPrimaryContainer
) {
    val displayText = if (showFullName) {
        author.take(30)
    } else {
        author.split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.firstOrNull()?.uppercaseChar()?.toString() ?: "" }
            .ifBlank { "?" }
    }

    Text(
        text = displayText,
        color = color,
        style = if (showFullName) {
            MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.SemiBold,
                lineHeight = 14.sp
            )
        } else {
            MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold
            )
        },
        textAlign = TextAlign.Center,
        maxLines = if (showFullName) 3 else 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(horizontal = 6.dp)
    )
}
