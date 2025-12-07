package cc.unitmesh.devins.ui.compose.editor.highlighting

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import cc.unitmesh.devins.lexer.DevInsLexer
import cc.unitmesh.devins.token.DevInsTokenType
import cc.unitmesh.devins.ui.compose.editor.model.HighlightStyle
import cc.unitmesh.devins.ui.compose.theme.AutoDevColors

/**
 * DevIn 语法高亮器
 * 使用 mpp-core 的 DevInsLexer 进行词法分析
 */
class DevInSyntaxHighlighter {
    /**
     * 对文本进行语法高亮
     */
    fun highlight(text: String): AnnotatedString {
        if (text.isEmpty()) {
            return AnnotatedString(text)
        }

        return try {
            val lexer = DevInsLexer(text)
            val tokens = lexer.tokenize()

            buildAnnotatedString {
                append(text)

                tokens.forEach { token ->
                    val style = getStyleForTokenType(token.type)
                    if (style != null && token.startOffset < text.length) {
                        val endOffset = minOf(token.endOffset, text.length)
                        if (endOffset > token.startOffset) {
                            addStyle(
                                style.toSpanStyle(),
                                token.startOffset,
                                endOffset
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // 如果解析失败，返回原始文本
            AnnotatedString(text)
        }
    }

    /**
     * 获取 Token 类型对应的样式
     * 采用高对比度、易区分的现代配色方案
     */
    private fun getStyleForTokenType(type: DevInsTokenType): HighlightStyle? {
        // 使用 AutoDev 设计系统颜色
        // TODO: 根据实际主题模式动态切换颜色（暗色/亮色）
        val colors = AutoDevColors.Syntax.Dark

        return when (type) {
            // Agent 相关 - 使用电光青（用户意图）
            DevInsTokenType.AGENT_START ->
                HighlightStyle(
                    color = colors.agent,
                    bold = true
                )

            // Command 相关 - 使用高亮绿（命令）
            DevInsTokenType.COMMAND_START ->
                HighlightStyle(
                    color = colors.command,
                    bold = true
                )
            DevInsTokenType.COMMAND_PROP ->
                HighlightStyle(
                    color = colors.command.copy(alpha = 0.8f)
                )

            // Variable 相关 - 使用霓虹紫（AI/变量）
            DevInsTokenType.VARIABLE_START ->
                HighlightStyle(
                    color = colors.variable,
                    bold = true
                )

            // 代码块
            DevInsTokenType.CODE_BLOCK_START,
            DevInsTokenType.CODE_BLOCK_END ->
                HighlightStyle(
                    color = colors.keyword
                )
            DevInsTokenType.LANGUAGE_ID ->
                HighlightStyle(
                    color = colors.number
                )
            DevInsTokenType.CODE_CONTENT ->
                HighlightStyle(
                    color = colors.identifier
                )

            // 字符串
            DevInsTokenType.QUOTE_STRING ->
                HighlightStyle(
                    color = colors.string
                )

            // 注释
            DevInsTokenType.COMMENTS,
            DevInsTokenType.CONTENT_COMMENTS,
            DevInsTokenType.BLOCK_COMMENT ->
                HighlightStyle(
                    color = colors.comment,
                    italic = true
                )

            // 数字
            DevInsTokenType.NUMBER ->
                HighlightStyle(
                    color = colors.number
                )

            // 布尔值
            DevInsTokenType.BOOLEAN ->
                HighlightStyle(
                    color = colors.keyword
                )

            // FrontMatter - 使用电光青
            DevInsTokenType.FRONTMATTER_START,
            DevInsTokenType.FRONTMATTER_END ->
                HighlightStyle(
                    color = AutoDevColors.Energy.xiu,
                    bold = true
                )

            // 关键字
            DevInsTokenType.WHEN,
            DevInsTokenType.ON_STREAMING,
            DevInsTokenType.BEFORE_STREAMING,
            DevInsTokenType.AFTER_STREAMING,
            DevInsTokenType.ON_STREAMING_END ->
                HighlightStyle(
                    color = colors.keyword,
                    bold = true
                )

            // 标识符
            DevInsTokenType.IDENTIFIER ->
                HighlightStyle(
                    color = colors.identifier
                )

            // 其他不着色
            else -> null
        }
    }

    companion object {
        /**
         * 现代化高对比度配色方案
         * 参考：GitHub、Slack、VSCode 等现代应用
         * 使用设计系统颜色
         */
        object ModernColors {
            // 特殊符号 - 高对比度、易区分
            val AGENT = AutoDevColors.Energy.xiu      // 电光青 - Agent 提及（@）
            val COMMAND = AutoDevColors.Signal.success // 高亮绿 - 命令（/）
            val VARIABLE = AutoDevColors.Energy.ai    // 霓虹紫 - 变量（$）

            // 代码元素
            val KEYWORD = AutoDevColors.Signal.warn   // 赛博黄 - 关键字
            val STRING = AutoDevColors.Signal.success // 高亮绿 - 字符串
            val NUMBER = AutoDevColors.Signal.info    // 信息蓝 - 数字
            val COMMENT = AutoDevColors.Text.tertiary // 灰色 - 注释
            val IDENTIFIER = AutoDevColors.Text.secondary // 浅灰 - 标识符
            val CONSTANT = AutoDevColors.Signal.warn  // 赛博黄 - 常量
        }

        /**
         * 深色主题颜色方案 (保留向后兼容)
         * 使用设计系统颜色
         */
        object DarculaColors {
            val KEYWORD = AutoDevColors.Syntax.Dark.keyword
            val STRING = AutoDevColors.Signal.success
            val NUMBER = AutoDevColors.Signal.info
            val COMMENT = AutoDevColors.Text.tertiary
            val IDENTIFIER = AutoDevColors.Text.secondary
            val CONSTANT = AutoDevColors.Energy.ai
            val TEXT = AutoDevColors.Text.secondary
        }
    }
}
