package cc.unitmesh.devins.ui.nano

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle

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
fun parseMarkdownInline(text: String, baseStyle: TextStyle): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        val len = text.length

        while (i < len) {
            when {
                // **bold**
                text.startsWith("**", i) -> {
                    val end = text.indexOf("**", i + 2)
                    if (end != -1) {
                        val boldText = text.substring(i + 2, end)
                        withStyle(SpanStyle(fontWeight = FontWeight.Companion.Bold)) {
                            append(boldText)
                        }
                        i = end + 2
                    } else {
                        append(text[i])
                        i++
                    }
                }
                // ~~strikethrough~~
                text.startsWith("~~", i) -> {
                    val end = text.indexOf("~~", i + 2)
                    if (end != -1) {
                        val strikeText = text.substring(i + 2, end)
                        withStyle(SpanStyle(textDecoration = TextDecoration.Companion.LineThrough)) {
                            append(strikeText)
                        }
                        i = end + 2
                    } else {
                        append(text[i])
                        i++
                    }
                }
                // __underline__
                text.startsWith("__", i) -> {
                    val end = text.indexOf("__", i + 2)
                    if (end != -1) {
                        val underlineText = text.substring(i + 2, end)
                        withStyle(SpanStyle(textDecoration = TextDecoration.Companion.Underline)) {
                            append(underlineText)
                        }
                        i = end + 2
                    } else {
                        append(text[i])
                        i++
                    }
                }
                // `inline code`
                text[i] == '`' -> {
                    val end = text.indexOf('`', i + 1)
                    if (end != -1) {
                        val codeText = text.substring(i + 1, end)
                        withStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Companion.Monospace,
                                background = Color(0xFFEEEEEE),
                                fontSize = baseStyle.fontSize * 0.9
                            )
                        ) {
                            append(codeText)
                        }
                        i = end + 1
                    } else {
                        append(text[i])
                        i++
                    }
                }
                // [link text](url)
                text[i] == '[' -> {
                    val endBracket = text.indexOf(']', i + 1)
                    if (endBracket != -1 && endBracket + 1 < len && text[endBracket + 1] == '(') {
                        val endParen = text.indexOf(')', endBracket + 2)
                        if (endParen != -1) {
                            val linkText = text.substring(i + 1, endBracket)
                            val url = text.substring(endBracket + 2, endParen)

                            // Store the URL as annotation for potential click handling
                            pushStringAnnotation(tag = "URL", annotation = url)
                            withStyle(
                                SpanStyle(
                                    color = Color(0xFF2196F3),
                                    textDecoration = TextDecoration.Companion.Underline
                                )
                            ) {
                                append(linkText)
                            }
                            pop()
                            i = endParen + 1
                        } else {
                            append(text[i])
                            i++
                        }
                    } else {
                        append(text[i])
                        i++
                    }
                }
                // *italic* or _italic_ (must be after ** check)
                text[i] == '*' || text[i] == '_' -> {
                    val delimiter = text[i]
                    val end = text.indexOf(delimiter, i + 1)
                    if (end != -1) {
                        val italicText = text.substring(i + 1, end)
                        withStyle(SpanStyle(fontStyle = FontStyle.Companion.Italic)) {
                            append(italicText)
                        }
                        i = end + 1
                    } else {
                        append(text[i])
                        i++
                    }
                }
                // Regular character
                else -> {
                    append(text[i])
                    i++
                }
            }
        }
    }
}
