package cc.unitmesh.devins.ui.nano

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.graphics.Color
import cc.unitmesh.xuiper.text.MarkdownInline

/**
 * Parses markdown inline elements and returns an AnnotatedString with appropriate styles.
 * Supports:
 * - **bold**
 * - *italic* or _italic_
 * - `inline code`
 * - ~~strikethrough~~
 * - __underline__
 * - [link text](url) - displayed as colored/underlined text
 */
fun parseMarkdownInline(
    text: String,
    baseStyle: TextStyle,
    linkColor: Color,
    codeBackground: Color
): AnnotatedString {
    return buildAnnotatedString {
        MarkdownInline.parse(text).forEach { span ->
            val style = span.style
            val url = style.linkUrl
            when {
                url != null -> {
                    pushStringAnnotation(tag = "URL", annotation = url)
                    withStyle(
                        SpanStyle(
                            color = linkColor,
                            textDecoration = TextDecoration.Underline
                        )
                    ) {
                        append(span.text)
                    }
                    pop()
                }

                style.code -> {
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = codeBackground,
                            fontSize = baseStyle.fontSize * 0.9
                        )
                    ) {
                        append(span.text)
                    }
                }

                style.bold || style.italic || style.underline || style.strikethrough -> {
                    withStyle(
                        SpanStyle(
                            fontWeight = if (style.bold) FontWeight.Bold else null,
                            fontStyle = if (style.italic) FontStyle.Italic else null,
                            textDecoration = when {
                                style.underline && style.strikethrough -> TextDecoration.combine(
                                    listOf(TextDecoration.Underline, TextDecoration.LineThrough)
                                )
                                style.underline -> TextDecoration.Underline
                                style.strikethrough -> TextDecoration.LineThrough
                                else -> null
                            }
                        )
                    ) {
                        append(span.text)
                    }
                }

                else -> append(span.text)
            }
        }
    }
}
