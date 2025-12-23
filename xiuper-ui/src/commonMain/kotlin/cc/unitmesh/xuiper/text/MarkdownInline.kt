package cc.unitmesh.xuiper.text

/**
 * Markdown inline parser that produces platform-agnostic spans.
 *
 * Supported:
 * - **bold**
 * - *italic* or _italic_
 * - `inline code`
 * - ~~strikethrough~~
 * - __underline__
 * - [link text](url)
 *
 * Notes:
 * - This parser is intentionally lightweight (no nested/escaped edge cases).
 * - Output is suitable for mapping to Compose, HTML, Swing, etc.
 */
object MarkdownInline {

    data class Span(
        val text: String,
        val style: Style = Style()
    )

    data class Style(
        val bold: Boolean = false,
        val italic: Boolean = false,
        val underline: Boolean = false,
        val strikethrough: Boolean = false,
        val code: Boolean = false,
        val linkUrl: String? = null
    )

    fun parse(text: String): List<Span> {
        val spans = mutableListOf<Span>()
        var i = 0
        val len = text.length

        fun appendPlain(char: Char) {
            spans.add(Span(text = char.toString()))
        }

        while (i < len) {
            when {
                text.startsWith("**", i) -> {
                    val end = text.indexOf("**", i + 2)
                    if (end != -1) {
                        spans.add(Span(text = text.substring(i + 2, end), style = Style(bold = true)))
                        i = end + 2
                    } else {
                        appendPlain(text[i])
                        i++
                    }
                }

                text.startsWith("~~", i) -> {
                    val end = text.indexOf("~~", i + 2)
                    if (end != -1) {
                        spans.add(Span(text = text.substring(i + 2, end), style = Style(strikethrough = true)))
                        i = end + 2
                    } else {
                        appendPlain(text[i])
                        i++
                    }
                }

                text.startsWith("__", i) -> {
                    val end = text.indexOf("__", i + 2)
                    if (end != -1) {
                        spans.add(Span(text = text.substring(i + 2, end), style = Style(underline = true)))
                        i = end + 2
                    } else {
                        appendPlain(text[i])
                        i++
                    }
                }

                text[i] == '`' -> {
                    val end = text.indexOf('`', i + 1)
                    if (end != -1) {
                        spans.add(Span(text = text.substring(i + 1, end), style = Style(code = true)))
                        i = end + 1
                    } else {
                        appendPlain(text[i])
                        i++
                    }
                }

                text[i] == '[' -> {
                    val endBracket = text.indexOf(']', i + 1)
                    if (endBracket != -1 && endBracket + 1 < len && text[endBracket + 1] == '(') {
                        val endParen = text.indexOf(')', endBracket + 2)
                        if (endParen != -1) {
                            val linkText = text.substring(i + 1, endBracket)
                            val url = text.substring(endBracket + 2, endParen)
                            spans.add(Span(text = linkText, style = Style(underline = true, linkUrl = url)))
                            i = endParen + 1
                        } else {
                            appendPlain(text[i])
                            i++
                        }
                    } else {
                        appendPlain(text[i])
                        i++
                    }
                }

                text[i] == '*' || text[i] == '_' -> {
                    val delimiter = text[i]
                    val end = text.indexOf(delimiter, i + 1)
                    if (end != -1) {
                        spans.add(Span(text = text.substring(i + 1, end), style = Style(italic = true)))
                        i = end + 1
                    } else {
                        appendPlain(text[i])
                        i++
                    }
                }

                else -> {
                    appendPlain(text[i])
                    i++
                }
            }
        }

        return mergeAdjacentPlain(spans)
    }

    private fun mergeAdjacentPlain(spans: List<Span>): List<Span> {
        if (spans.isEmpty()) return spans
        val merged = ArrayList<Span>(spans.size)
        var current = spans[0]

        fun isPlain(span: Span): Boolean = span.style == Style()

        for (idx in 1 until spans.size) {
            val next = spans[idx]
            if (isPlain(current) && isPlain(next)) {
                current = current.copy(text = current.text + next.text)
            } else {
                merged.add(current)
                current = next
            }
        }
        merged.add(current)
        return merged
    }
}
