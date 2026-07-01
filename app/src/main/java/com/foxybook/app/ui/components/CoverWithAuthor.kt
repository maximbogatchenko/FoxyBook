package com.foxybook.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Обложка книги с заглушкой. Если обложка есть — показывает её,
 * если нет — красивый градиент с первой буквой автора и иконкой.
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
    BookCover(
        coverUrl = coverUrl,
        title = "",
        author = author,
        contentDescription = contentDescription,
        width = width,
        height = height,
        onClick = if (onCoverClick != null && coverUrl.isNotBlank()) {
            { onCoverClick(coverUrl) }
        } else null
    )
}
