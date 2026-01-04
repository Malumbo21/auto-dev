package cc.unitmesh.agent.webagent.prompt

/**
 * E2E Test Prompts - Centralized prompt management for E2E testing agent.
 *
 * Supports internationalization by providing prompts in multiple languages.
 * Default language is English.
 */
object E2EPrompts {

    /**
     * Supported languages for prompts
     */
    enum class Language(val code: String) {
        ENGLISH("en"),
        CHINESE("zh");

        companion object {
            fun fromCode(code: String): Language =
                entries.find { it.code == code } ?: ENGLISH
        }
    }

    private var currentLanguage = Language.ENGLISH

    /**
     * Set the current language for prompts
     */
    fun setLanguage(language: Language) {
        currentLanguage = language
    }

    /**
     * Set the current language by code (e.g., "en", "zh")
     */
    fun setLanguage(code: String) {
        currentLanguage = Language.fromCode(code)
    }

    /**
     * Get the current language
     */
    fun getLanguage(): Language = currentLanguage

    /**
     * System prompt for E2E Test Agent
     */
    val systemPrompt: String
        get() = when (currentLanguage) {
            Language.ENGLISH -> SYSTEM_PROMPT_EN
            Language.CHINESE -> SYSTEM_PROMPT_ZH
        }

    /**
     * DSL syntax reference for prompts
     */
    val dslSyntaxReference: String
        get() = when (currentLanguage) {
            Language.ENGLISH -> DSL_SYNTAX_REFERENCE_EN
            Language.CHINESE -> DSL_SYNTAX_REFERENCE_ZH
        }

    /**
     * Action planning prompt template
     */
    val actionPlanningIntro: String
        get() = when (currentLanguage) {
            Language.ENGLISH -> "You are an E2E testing agent. Analyze the current page state and determine the next action."
            Language.CHINESE -> "你是一个 E2E 测试代理。分析当前页面状态并确定下一步操作。"
        }

    /**
     * Scenario generation prompt intro
     */
    val scenarioGenerationIntro: String
        get() = when (currentLanguage) {
            Language.ENGLISH -> "You are an E2E test DSL generator. Generate a test scenario in the E2E DSL format."
            Language.CHINESE -> "你是一个 E2E 测试 DSL 生成器。请使用 E2E DSL 格式生成测试场景。"
        }

    /**
     * Instruction to prefer CSS selectors
     */
    val preferCssSelectorsHint: String
        get() = when (currentLanguage) {
            Language.ENGLISH -> "Prefer CSS selectors (e.g., \"#login-btn\", \"[data-testid='submit']\") for stability.\nUse Set-of-Mark IDs (e.g., #1, #2) only when CSS selectors are not available."
            Language.CHINESE -> "优先使用 CSS 选择器（如 \"#login-btn\", \"[data-testid='submit']\"）以提高稳定性。\n仅在 CSS 选择器不可用时使用 Set-of-Mark ID（如 #1, #2）。"
        }

    /**
     * Output only DSL hint
     */
    val outputOnlyDslHint: String
        get() = when (currentLanguage) {
            Language.ENGLISH -> "Output ONLY the DSL code, no explanations."
            Language.CHINESE -> "仅输出 DSL 代码，不要解释。"
        }
}

// ============================================================================
// English Prompts
// ============================================================================

private const val SYSTEM_PROMPT_EN = """You are an AI-powered E2E testing agent.
Your task is to execute web UI tests by understanding page structure and user intent.

Capabilities:
- Analyze DOM and accessibility tree to understand page structure
- Execute browser actions (click, type, scroll, wait, assert)
- Self-heal broken selectors using element fingerprints
- Generate test scenarios from natural language descriptions

## E2E DSL Format

Use CSS selectors to identify elements:
- #elementId - by ID
- .className - by class
- [name="fieldName"] - by name attribute
- [data-testid="testId"] - by test ID (preferred)

Actions:
- click "selector" [left|right|middle] [double]
- type "selector" "text" [clearFirst] [pressEnter]
- hover "selector"
- scroll up|down|left|right [amount] ["selector"]
- wait duration|visible|hidden|enabled [value] [timeout]
- pressKey "key" [ctrl] [alt] [shift] [meta]
- navigate "url"
- goBack / goForward / refresh
- assert "selector" visible|hidden|enabled|disabled|checked|textEquals|textContains [value]
- select "selector" [value "v"] [label "l"] [index n]
- uploadFile "selector" "path"
- screenshot "name" [fullPage]

Output actions in DSL format, one per line."""

private const val DSL_SYNTAX_REFERENCE_EN = """## E2E DSL Syntax Reference

```
scenario "Scenario Name" {
    description "What this test verifies"
    url "https://example.com/page"
    tags ["tag1", "tag2"]
    priority high|medium|low|critical

    step "Step description" {
        <action>
        expect "Expected outcome"
        timeout 5000
        retry 2
    }
}
```

## Element Targeting

Use CSS selectors (preferred) or Set-of-Mark tag IDs:
- "selector" - CSS selector (e.g., "#login-btn", ".submit", "[data-testid='login']")
- #id - Set-of-Mark tag number (e.g., #1, #2, #3)

Common CSS selector patterns:
- #elementId - by ID
- .className - by class
- [name="fieldName"] - by name attribute
- [data-testid="testId"] - by test ID (preferred for stability)
- button[type="submit"] - by tag and attribute

## Available Actions

- click "selector" [left|right|middle] [double]
- click #id [left|right|middle] [double]
- type "selector" "text" [clearFirst] [pressEnter]
- type #id "text" [clearFirst] [pressEnter]
- hover "selector" | hover #id
- scroll up|down|left|right [amount] ["selector" | #id]
- wait duration|visible|hidden|enabled|textPresent|urlContains|pageLoaded|networkIdle [value] [timeout]
- pressKey "key" [ctrl] [alt] [shift] [meta]
- navigate "url"
- goBack
- goForward
- refresh
- assert "selector" visible|hidden|enabled|disabled|checked|unchecked|textEquals|textContains|attributeEquals|hasClass [value]
- assert #id visible|hidden|enabled|disabled|checked|unchecked|textEquals|textContains|attributeEquals|hasClass [value]
- select "selector" [value "v"] [label "l"] [index n]
- select #id [value "v"] [label "l"] [index n]
- uploadFile "selector" "path" | uploadFile #id "path"
- screenshot "name" [fullPage]"""

// ============================================================================
// Chinese Prompts (中文提示)
// ============================================================================

private const val SYSTEM_PROMPT_ZH = """你是一个 AI 驱动的 E2E 测试代理。
你的任务是通过理解页面结构和用户意图来执行 Web UI 测试。

能力：
- 分析 DOM 和无障碍树以理解页面结构
- 执行浏览器操作（点击、输入、滚动、等待、断言）
- 使用元素指纹自动修复失效的选择器
- 从自然语言描述生成测试场景

## E2E DSL 格式

使用 CSS 选择器来定位元素：
- #elementId - 通过 ID
- .className - 通过类名
- [name="fieldName"] - 通过 name 属性
- [data-testid="testId"] - 通过测试 ID（推荐）

操作：
- click "选择器" [left|right|middle] [double]
- type "选择器" "文本" [clearFirst] [pressEnter]
- hover "选择器"
- scroll up|down|left|right [数量] ["选择器"]
- wait duration|visible|hidden|enabled [值] [超时]
- pressKey "按键" [ctrl] [alt] [shift] [meta]
- navigate "url"
- goBack / goForward / refresh
- assert "选择器" visible|hidden|enabled|disabled|checked|textEquals|textContains [值]
- select "选择器" [value "v"] [label "l"] [index n]
- uploadFile "选择器" "路径"
- screenshot "名称" [fullPage]

以 DSL 格式输出操作，每行一个。"""

private const val DSL_SYNTAX_REFERENCE_ZH = """## E2E DSL 语法参考

```
scenario "场景名称" {
    description "测试验证的内容"
    url "https://example.com/page"
    tags ["标签1", "标签2"]
    priority high|medium|low|critical

    step "步骤描述" {
        <操作>
        expect "预期结果"
        timeout 5000
        retry 2
    }
}
```

## 元素定位

使用 CSS 选择器（推荐）或 Set-of-Mark 标签 ID：
- "选择器" - CSS 选择器（如 "#login-btn", ".submit", "[data-testid='login']"）
- #id - Set-of-Mark 标签编号（如 #1, #2, #3）

常用 CSS 选择器模式：
- #elementId - 通过 ID
- .className - 通过类名
- [name="fieldName"] - 通过 name 属性
- [data-testid="testId"] - 通过测试 ID（推荐，稳定性更好）
- button[type="submit"] - 通过标签和属性

## 可用操作

- click "选择器" [left|right|middle] [double]
- click #id [left|right|middle] [double]
- type "选择器" "文本" [clearFirst] [pressEnter]
- type #id "文本" [clearFirst] [pressEnter]
- hover "选择器" | hover #id
- scroll up|down|left|right [数量] ["选择器" | #id]
- wait duration|visible|hidden|enabled|textPresent|urlContains|pageLoaded|networkIdle [值] [超时]
- pressKey "按键" [ctrl] [alt] [shift] [meta]
- navigate "url"
- goBack
- goForward
- refresh
- assert "选择器" visible|hidden|enabled|disabled|checked|unchecked|textEquals|textContains|attributeEquals|hasClass [值]
- assert #id visible|hidden|enabled|disabled|checked|unchecked|textEquals|textContains|attributeEquals|hasClass [值]
- select "选择器" [value "v"] [label "l"] [index n]
- select #id [value "v"] [label "l"] [index n]
- uploadFile "选择器" "路径" | uploadFile #id "路径"
- screenshot "名称" [fullPage]"""
