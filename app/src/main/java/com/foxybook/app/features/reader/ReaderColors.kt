package com.foxybook.app.features.reader

import androidx.compose.ui.graphics.Color

data class ReaderColors(
    val background: Color,
    val text: Color,
    val textSecondary: Color,
    val quoteBackground: Color,
    val quoteBorder: Color,
    val selectionHighlight: Color
)

fun readerColors(darkTheme: Boolean): ReaderColors = if (darkTheme) {
    ReaderColors(
        background = Color(0xFF1A1A1A),
        text = Color(0xFFE0E0E0),
        textSecondary = Color(0xFFB0B0B0),
        quoteBackground = Color(0xFF2A2A2A),
        quoteBorder = Color(0xFF555555),
        selectionHighlight = Color(0x40FF8A65)
    )
} else {
    ReaderColors(
        background = Color(0xFFFFFBF5),
        text = Color(0xFF1A1A1A),
        textSecondary = Color(0xFF5A5A5A),
        quoteBackground = Color(0xFFF5F0EB),
        quoteBorder = Color(0xFFCCCCCC),
        selectionHighlight = Color(0x40FFB74D)
    )
}

fun amoledReaderColors(): ReaderColors = ReaderColors(
    background = Color(0xFF000000),
    text = Color(0xFFEEEEEE),
    textSecondary = Color(0xFFAAAAAA),
    quoteBackground = Color(0xFF111111),
    quoteBorder = Color(0xFF333333),
    selectionHighlight = Color(0x40FF8A65)
)
