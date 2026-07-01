package com.foxybook.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import java.io.File

/**
 * Компонент обложки книги с красивой заглушкой, если обложка отсутствует.
 * Показывает градиент, первую букву автора и иконку книги.
 */
@Composable
fun BookCover(
    coverUrl: String,
    title: String,
    author: String,
    contentDescription: String = "",
    width: Dp = 72.dp,
    height: Dp = 100.dp,
    onClick: (() -> Unit)? = null
) {
    val shape = RoundedCornerShape(12.dp)

    Box(
        modifier = Modifier
            .size(width = width, height = height)
            .clip(shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        // Placeholder (always visible under the image)
        CoverPlaceholder(
            title = title,
            author = author,
            width = width,
            height = height
        )

        // Cover image (draws on top; if it loads, it covers the placeholder)
        if (coverUrl.isNotBlank()) {
            val coverModel: Any = if (coverUrl.startsWith("file://")) {
                File(coverUrl.removePrefix("file://"))
            } else {
                coverUrl
            }
            AsyncImage(
                model = coverModel,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize().clip(shape),
                contentScale = ContentScale.Crop
            )
        }
    }
}

/**
 * Красивая заглушка обложки с градиентом.
 */
@Composable
private fun CoverPlaceholder(
    title: String,
    author: String,
    width: Dp,
    height: Dp
) {
    val initial = author.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    val colors = placeholderColors(initial)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = colors,
                    start = Offset(0f, 0f),
                    end = Offset(width.value * 2f, height.value * 2f)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(6.dp)
        ) {
            // Первая буква автора
            Text(
                text = initial,
                fontSize = if (height.value < 90f) 20.sp else if (height.value < 150f) 28.sp else 36.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.9f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(2.dp))

            // Иконка книги
            Icon(
                imageVector = Icons.Default.AutoStories,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.size(if (height.value < 90f) 16.dp else if (height.value < 150f) 20.dp else 24.dp)
            )
        }
    }
}

/**
 * Генерирует красивую градиентную палитру на основе первой буквы автора.
 * Каждая группа букв имеет свою цветовую тему.
 */
private fun placeholderColors(initial: String): List<Color> {
    return when (initial.uppercase()) {
        "A", "B", "C", "D", "E" -> listOf(Color(0xFF5C4033), Color(0xFF8B5E3C), Color(0xFFA0522D))
        "F", "G", "H", "I", "J" -> listOf(Color(0xFF2E4057), Color(0xFF3B5998), Color(0xFF4A6FA5))
        "K", "L", "M", "N", "O" -> listOf(Color(0xFF6B3A5A), Color(0xFF8B4C7A), Color(0xFFA0608E))
        "P", "Q", "R", "S", "T" -> listOf(Color(0xFF1A5B4A), Color(0xFF2D7D6A), Color(0xFF3F9A85))
        "U", "V", "W", "X", "Y", "Z" -> listOf(Color(0xFF4A3F6B), Color(0xFF6B5B8B), Color(0xFF8A7AA5))
        // Русские буквы
        "А", "Б", "В", "Г", "Д" -> listOf(Color(0xFF5C4033), Color(0xFF8B5E3C), Color(0xFFA0522D))
        "Е", "Ё", "Ж", "З", "И" -> listOf(Color(0xFF2E4057), Color(0xFF3B5998), Color(0xFF4A6FA5))
        "К", "Л", "М", "Н", "О" -> listOf(Color(0xFF6B3A5A), Color(0xFF8B4C7A), Color(0xFFA0608E))
        "П", "Р", "С", "Т", "У" -> listOf(Color(0xFF1A5B4A), Color(0xFF2D7D6A), Color(0xFF3F9A85))
        "Ф", "Х", "Ц", "Ч", "Ш" -> listOf(Color(0xFF7C4A1E), Color(0xFF9C6B2E), Color(0xFFB8863E))
        "Щ", "Ъ", "Ы", "Ь", "Э" -> listOf(Color(0xFF4A3F6B), Color(0xFF6B5B8B), Color(0xFF8A7AA5))
        "Ю", "Я" -> listOf(Color(0xFF8B2252), Color(0xFFA0325E), Color(0xFFB8456E))
        else -> listOf(Color(0xFF4A4A4A), Color(0xFF6B6B6B), Color(0xFF8A8A8A))
    }
}
