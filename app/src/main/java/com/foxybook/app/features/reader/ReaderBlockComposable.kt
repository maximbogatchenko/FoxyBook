package com.foxybook.app.features.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import com.foxybook.app.core.reader.ContentBlock

@Composable
fun BlockComposable(
    block: ContentBlock,
    fontSize: Int,
    lineHeight: Float,
    colors: ReaderColors,
    isSelectionMode: Boolean = false,
    isCurrentTtsBlock: Boolean = false,
    onTtsClick: () -> Unit = {},
    onToggleImmersive: () -> Unit = {}
) {
    val bottomPadding = if (block.isSplitAtBottom) 0.dp else (fontSize * 0.4).dp
    val highlightColor = if (isCurrentTtsBlock) colors.selectionHighlight else Color.Transparent

    Box(modifier = Modifier.background(highlightColor)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(isSelectionMode) {
                    detectTapGestures(
                        onTap = {
                            if (isSelectionMode) onTtsClick()
                            else onToggleImmersive()
                        }
                    )
                }
        ) {
            when (block) {
                is ContentBlock.Heading -> {
                    val sizes = mapOf(1 to 28, 2 to 24, 3 to 21, 4 to 19, 5 to 17, 6 to 15)
                    val size = sizes[block.level] ?: 18
                    Text(
                        text = block.text,
                        fontSize = size.sp,
                        lineHeight = (size * lineHeight * 0.85).sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.text,
                        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                    )
                }
                is ContentBlock.Paragraph -> {
                    androidx.compose.foundation.text.selection.SelectionContainer {
                        Text(
                            text = block.text,
                            fontSize = fontSize.sp,
                            lineHeight = (fontSize * lineHeight).sp,
                            color = colors.text,
                            textAlign = TextAlign.Justify,
                            modifier = Modifier.padding(bottom = bottomPadding)
                        )
                    }
                }
                is ContentBlock.Quote -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.quoteBackground, RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp))
                            .padding(vertical = 6.dp)
                            .drawWithContent {
                                drawContent()
                                drawRect(color = colors.quoteBorder, topLeft = Offset(0f, 0f), size = Size(3f, size.height))
                            }
                            .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = if (block.isSplitAtBottom) 0.dp else 8.dp)
                    ) {
                        Column {
                            Text(
                                text = block.text,
                                fontSize = (fontSize - 1).sp,
                                lineHeight = (fontSize * lineHeight).sp,
                                color = colors.text.copy(alpha = 0.85f),
                                fontStyle = FontStyle.Italic,
                                textAlign = TextAlign.Justify
                            )
                            if (block.author != null) {
                                Text(
                                    text = block.author,
                                    fontSize = (fontSize - 2).sp,
                                    color = colors.textSecondary,
                                    modifier = Modifier.padding(top = 4.dp).align(Alignment.End)
                                )
                            }
                        }
                    }
                }
                is ContentBlock.Poem -> {
                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).padding(start = 24.dp)) {
                        if (block.title != null) {
                            Text(text = block.title, fontSize = (fontSize - 1).sp, fontWeight = FontWeight.SemiBold,
                                color = colors.text, modifier = Modifier.padding(bottom = 6.dp))
                        }
                        block.lines.forEach { line ->
                            Text(text = line, fontSize = (fontSize - 1).sp,
                                lineHeight = (fontSize * lineHeight * 0.9).sp, color = colors.text)
                        }
                        if (block.author != null) {
                            Text(text = block.author, fontSize = (fontSize - 2).sp, color = colors.textSecondary,
                                modifier = Modifier.padding(top = 6.dp).align(Alignment.End))
                        }
                    }
                }
                is ContentBlock.EmptyLine -> Spacer(modifier = Modifier.height(block.height.dp))
                is ContentBlock.Image -> {
                    val imgModel: Any? = remember(block.src) {
                        when {
                            block.src.isBlank() -> null
                            block.src.startsWith("file://") -> java.io.File(block.src.removePrefix("file://"))
                            else -> block.src
                        }
                    }
                    SubcomposeAsyncImage(
                        model = imgModel,
                        contentDescription = block.alt,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp),
                        loading = {
                            Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            }
                        },
                        error = {
                            Box(modifier = Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                                Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(48.dp),
                                    tint = colors.text.copy(alpha = 0.2f))
                            }
                        }
                    )
                }
            }
        }
    }
}
