package cc.unitmesh.devins.ui.compose.sketch

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cc.unitmesh.agent.Platform
import cc.unitmesh.devins.parser.CodeFence
import cc.unitmesh.devins.ui.compose.sketch.chart.ChartBlockRenderer
import cc.unitmesh.devins.ui.compose.sketch.letsplot.LetsPlotBlockRenderer
import kotlinx.datetime.Clock

/**
 * Sketch 渲染器 - 主渲染器
 *
 * 负责解析和分发不同类型的内容块到对应的子渲染器：
 * - Markdown/Text -> TextBlockRenderer
 * - Code -> CodeBlockRenderer
 * - Diff -> DiffSketchRenderer
 * - Thinking -> ThinkingBlockRenderer
 * - Walkthrough -> WalkthroughBlockRenderer
 *
 * Streaming Optimization:
 * - Uses throttled rendering to reduce recomposition frequency during streaming
 * - Caches parsed code fences to avoid redundant parsing
 * - Only re-renders when content changes significantly (new lines or blocks)
 */
object SketchRenderer : BaseContentRenderer() {
    /**
     * Throttle interval for streaming content updates (in milliseconds).
     * Lower values = more responsive but more recompositions.
     * Higher values = less flickering but delayed updates.
     */
    private const val STREAMING_THROTTLE_MS = 100L

    /**
     * Minimum character change threshold to trigger re-render during streaming.
     * Helps reduce flickering by batching small updates.
     */
    private const val MIN_CHAR_CHANGE_THRESHOLD = 20

    /**
     * 渲染 LLM 响应内容（向后兼容的方法）
     *
     * @param content 要渲染的内容
     * @param isComplete 是否渲染完成
     * @param onContentUpdate 内容更新回调（返回当前渲染的块数量，用于外层滚动控制）
     * @param modifier Compose Modifier
     */
    @Composable
    fun RenderResponse(
        content: String,
        isComplete: Boolean = false,
        onContentUpdate: ((blockCount: Int) -> Unit)? = null,
        modifier: Modifier = Modifier
    ) {
        // 包装为统一接口
        Render(
            content = content,
            isComplete = isComplete,
            onRenderUpdate = { metadata ->
                onContentUpdate?.invoke(metadata.blockCount)
            },
            modifier = modifier
        )
    }

    /**
     * 实现 ContentRenderer 接口的渲染方法
     * 
     * Optimized for streaming with throttled updates:
     * - During streaming (isComplete=false): throttle updates to reduce flickering
     * - After completion (isComplete=true): render immediately with full formatting
     */
    @Composable
    override fun Render(
        content: String,
        isComplete: Boolean,
        onRenderUpdate: ((RenderMetadata) -> Unit)?,
        modifier: Modifier
    ) {
        val blockSpacing = if (Platform.isJvm && !Platform.isAndroid) 4.dp else 8.dp

        // Throttled content for streaming - reduces recomposition frequency
        val throttledContent = rememberThrottledContent(
            content = content,
            isComplete = isComplete,
            throttleMs = STREAMING_THROTTLE_MS,
            minCharChange = MIN_CHAR_CHANGE_THRESHOLD
        )

        // Cache parsed code fences to avoid redundant parsing
        val codeFences = remember(throttledContent) {
            CodeFence.parseAll(throttledContent)
        }

        Column(modifier = modifier) {

            // 通知外层当前渲染的块数量和最后一个块类型
            if (codeFences.isNotEmpty()) {
                notifyRenderUpdate(
                    onRenderUpdate = onRenderUpdate,
                    blockCount = codeFences.size,
                    lastBlockType = codeFences.lastOrNull()?.languageId
                )
            }

            codeFences.forEachIndexed { index, fence ->
                val isLastBlock = index == codeFences.lastIndex
                val blockIsComplete = fence.isComplete && (isComplete || !isLastBlock)
                var rendered = false

                when (fence.languageId.lowercase()) {
                    "markdown", "md", "" -> {
                        if (fence.text.isNotBlank()) {
                            // Desktop markdown rendering (mikepenz/markdown-compose) can crash during streaming updates
                            // with out-of-bounds ranges inside AnnotatedString spans (seen as StringIndexOutOfBoundsException
                            // from ParagraphBuilder on Skiko). To keep streaming robust and lightweight, we render
                            // incomplete (streaming) markdown blocks as plain text, and only enable full markdown
                            // rendering once the block becomes stable/complete.
                            if (blockIsComplete) {
                                MarkdownSketchRenderer.RenderMarkdown(fence.text, isComplete = true)
                            } else {
                                MarkdownSketchRenderer.RenderPlainText(fence.text)
                            }
                            rendered = true
                        }
                    }

                    "diff", "patch" -> {
                        DiffSketchRenderer.RenderDiff(
                            diffContent = fence.text,
                            modifier = Modifier.fillMaxWidth()
                        )
                        rendered = true
                    }

                    "thinking" -> {
                        if (fence.text.isNotBlank()) {
                            ThinkingBlockRenderer(
                                thinkingContent = fence.text,
                                isComplete = blockIsComplete,
                                modifier = Modifier.fillMaxWidth()
                            )
                            rendered = true
                        }
                    }

                    "walkthrough" -> {
                        if (fence.text.isNotBlank()) {
                            WalkthroughBlockRenderer(
                                walkthroughContent = fence.text,
                                modifier = Modifier.fillMaxWidth(),
                                isComplete = fence.isComplete
                            )
                            rendered = true
                        }
                    }

                    "mermaid", "mmd" -> {
                        if (fence.text.isNotBlank()) {
                            MermaidBlockRenderer(
                                mermaidCode = fence.text,
                                modifier = Modifier.fillMaxWidth()
                            )
                            rendered = true
                        }
                    }

                    "nanodsl", "nano" -> {
                        if (fence.text.isNotBlank()) {
                            NanoDSLBlockRenderer(
                                nanodslCode = fence.text,
                                isComplete = blockIsComplete,
                                modifier = Modifier.fillMaxWidth()
                            )
                            rendered = true
                        }
                    }

                    "plotdsl", "plot" -> {
                        if (fence.text.isNotBlank()) {
                            LetsPlotBlockRenderer(
                                plotCode = fence.text,
                                isComplete = blockIsComplete,
                                modifier = Modifier.fillMaxWidth()
                            )
                            rendered = true
                        }
                    }

                    "devin" -> {
                        if (fence.text.isNotBlank()) {
                            DevInBlockRenderer(
                                devinContent = fence.text,
                                isComplete = blockIsComplete,
                                modifier = Modifier.fillMaxWidth()
                            )
                            rendered = true
                        }
                    }

                    "chart", "graph" -> {
                        if (fence.text.isNotBlank()) {
                            ChartBlockRenderer(
                                chartCode = fence.text,
                                modifier = Modifier.fillMaxWidth()
                            )
                            rendered = true
                        }
                    }

                    else -> {
                        CodeBlockRenderer(
                            code = fence.text,
                            language = fence.languageId,
                            displayName = CodeFence.displayNameByExt(fence.extension ?: fence.languageId)
                        )
                        rendered = true
                    }
                }

                // Only add spacing between blocks (avoid trailing space after the last block).
                if (rendered && !isLastBlock) {
                    Spacer(modifier = Modifier.height(blockSpacing))
                }
            }
        }
    }
}

/**
 * Composable that throttles content updates during streaming to reduce flickering.
 * 
 * During streaming (isComplete=false):
 * - Only updates when content changes significantly (new lines or exceeds char threshold)
 * - Uses time-based throttling to batch rapid updates
 * 
 * After completion (isComplete=true):
 * - Returns content immediately without throttling
 *
 * @param content The raw content string
 * @param isComplete Whether streaming is complete
 * @param throttleMs Minimum time between updates in milliseconds
 * @param minCharChange Minimum character change to trigger update
 * @return Throttled content string
 */
@Composable
private fun rememberThrottledContent(
    content: String,
    isComplete: Boolean,
    throttleMs: Long,
    minCharChange: Int
): String {
    // When complete, always return the full content immediately
    if (isComplete) {
        return content
    }

    // Use a data class to hold throttle state for cleaner updates
    data class ThrottleState(
        val renderedContent: String = "",
        val updateTime: Long = 0L,
        val lineCount: Int = 0
    )

    var throttleState by remember { mutableStateOf(ThrottleState()) }

    // Calculate current metrics using kotlinx-datetime for KMP compatibility
    val currentTime = Clock.System.now().toEpochMilliseconds()
    val currentLineCount = content.count { it == '\n' }
    val charDiff = content.length - throttleState.renderedContent.length

    // Determine if we should update based on:
    // 1. New line added (important for markdown structure)
    // 2. Significant character change
    // 3. Time threshold exceeded
    // 4. Content became shorter (user edit or reset)
    // 5. First render (empty state)
    val newLineAdded = currentLineCount > throttleState.lineCount
    val significantChange = charDiff >= minCharChange || charDiff < 0
    val timeThresholdMet = (currentTime - throttleState.updateTime) >= throttleMs
    val shouldUpdate = newLineAdded || 
                       (significantChange && timeThresholdMet) || 
                       throttleState.renderedContent.isEmpty()

    // Use SideEffect to update state after composition
    SideEffect {
        if (shouldUpdate) {
            throttleState = ThrottleState(
                renderedContent = content,
                updateTime = currentTime,
                lineCount = currentLineCount
            )
        }
    }

    return throttleState.renderedContent.ifEmpty { content }
}
