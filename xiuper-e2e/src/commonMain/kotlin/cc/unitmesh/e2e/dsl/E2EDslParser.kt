package cc.unitmesh.e2e.dsl

import cc.unitmesh.agent.e2etest.model.*

/**
 * Parser for E2E Test DSL.
 *
 * Converts DSL text into TestScenario objects.
 */
class E2EDslParser {
    private var currentLine = 0
    private var currentColumn = 0
    private val errors = mutableListOf<DslError>()
    private val warnings = mutableListOf<String>()

    /**
     * Parse DSL text into a TestScenario
     */
    fun parse(dsl: String): DslParseResult {
        errors.clear()
        warnings.clear()
        currentLine = 0
        currentColumn = 0

        val lines = dsl.lines()
        val tokens = tokenize(lines)

        return try {
            val scenario = parseScenario(tokens)
            DslParseResult(
                success = errors.isEmpty(),
                scenario = scenario,
                errors = errors.toList(),
                warnings = warnings.toList()
            )
        } catch (e: Exception) {
            errors.add(DslError(currentLine, currentColumn, e.message ?: "Unknown parse error"))
            DslParseResult(success = false, errors = errors.toList(), warnings = warnings.toList())
        }
    }

    private fun tokenize(lines: List<String>): List<Token> {
        val tokens = mutableListOf<Token>()
        lines.forEachIndexed { lineNum, line ->
            currentLine = lineNum + 1
            val lineTokens = tokenizeLine(line, lineNum + 1)
            tokens.addAll(lineTokens)
        }
        return tokens
    }

    private fun tokenizeLine(line: String, lineNum: Int): List<Token> {
        val tokens = mutableListOf<Token>()
        var i = 0
        val trimmed = line.trim()

        // Skip empty lines and comments
        if (trimmed.isEmpty() || trimmed.startsWith("//") || trimmed.startsWith("#")) {
            return tokens
        }

        while (i < line.length) {
            when {
                line[i].isWhitespace() -> i++
                line[i] == '"' -> {
                    val (str, endIdx) = parseString(line, i)
                    tokens.add(Token(TokenType.STRING, str, lineNum, i))
                    i = endIdx
                }
                line[i] == '#' && i + 1 < line.length && line[i + 1].isDigit() -> {
                    val (id, endIdx) = parseTargetId(line, i)
                    tokens.add(Token(TokenType.TARGET_ID, id, lineNum, i))
                    i = endIdx
                }
                line[i] == '[' -> {
                    val (arr, endIdx) = parseArray(line, i)
                    tokens.add(Token(TokenType.ARRAY, arr, lineNum, i))
                    i = endIdx
                }
                line[i] == '{' -> {
                    tokens.add(Token(TokenType.BRACE_OPEN, "{", lineNum, i))
                    i++
                }
                line[i] == '}' -> {
                    tokens.add(Token(TokenType.BRACE_CLOSE, "}", lineNum, i))
                    i++
                }
                line[i].isDigit() || (line[i] == '-' && i + 1 < line.length && line[i + 1].isDigit()) -> {
                    val (num, endIdx) = parseNumber(line, i)
                    tokens.add(Token(TokenType.NUMBER, num, lineNum, i))
                    i = endIdx
                }
                line[i].isLetter() || line[i] == '_' -> {
                    val (word, endIdx) = parseWord(line, i)
                    tokens.add(Token(TokenType.KEYWORD, word, lineNum, i))
                    i = endIdx
                }
                else -> i++
            }
        }
        return tokens
    }

    private fun parseString(line: String, start: Int): Pair<String, Int> {
        val sb = StringBuilder()
        var i = start + 1
        while (i < line.length && line[i] != '"') {
            if (line[i] == '\\' && i + 1 < line.length) {
                when (line[i + 1]) {
                    'n' -> sb.append('\n')
                    't' -> sb.append('\t')
                    '"' -> sb.append('"')
                    '\\' -> sb.append('\\')
                    else -> sb.append(line[i + 1])
                }
                i += 2
            } else {
                sb.append(line[i])
                i++
            }
        }
        return sb.toString() to (i + 1)
    }

    private fun parseTargetId(line: String, start: Int): Pair<String, Int> {
        var i = start + 1
        val sb = StringBuilder()
        while (i < line.length && line[i].isDigit()) {
            sb.append(line[i])
            i++
        }
        return sb.toString() to i
    }

    private fun parseArray(line: String, start: Int): Pair<String, Int> {
        var i = start + 1
        val sb = StringBuilder("[")
        var depth = 1
        while (i < line.length && depth > 0) {
            when (line[i]) {
                '[' -> depth++
                ']' -> depth--
            }
            sb.append(line[i])
            i++
        }
        return sb.toString() to i
    }

    private fun parseNumber(line: String, start: Int): Pair<String, Int> {
        var i = start
        val sb = StringBuilder()
        if (line[i] == '-') {
            sb.append('-')
            i++
        }
        while (i < line.length && (line[i].isDigit() || line[i] == '.')) {
            sb.append(line[i])
            i++
        }
        return sb.toString() to i
    }

    private fun parseWord(line: String, start: Int): Pair<String, Int> {
        var i = start
        val sb = StringBuilder()
        while (i < line.length && (line[i].isLetterOrDigit() || line[i] == '_')) {
            sb.append(line[i])
            i++
        }
        return sb.toString() to i
    }

    private fun parseScenario(tokens: List<Token>): TestScenario? {
        val iterator = tokens.iterator()
        var name = ""
        var description = ""
        var startUrl = ""
        var tags = listOf<String>()
        var priority = TestPriority.MEDIUM
        val steps = mutableListOf<TestStep>()
        var stepIndex = 0

        while (iterator.hasNext()) {
            val token = iterator.next()
            if (token.type != TokenType.KEYWORD) continue

            when (token.value.lowercase()) {
                E2EDsl.Keywords.SCENARIO -> {
                    if (iterator.hasNext()) {
                        val nameToken = iterator.next()
                        if (nameToken.type == TokenType.STRING) {
                            name = nameToken.value
                        }
                    }
                }
                E2EDsl.Keywords.DESCRIPTION -> {
                    if (iterator.hasNext()) {
                        val descToken = iterator.next()
                        if (descToken.type == TokenType.STRING) {
                            description = descToken.value
                        }
                    }
                }
                E2EDsl.Keywords.URL -> {
                    if (iterator.hasNext()) {
                        val urlToken = iterator.next()
                        if (urlToken.type == TokenType.STRING) {
                            startUrl = urlToken.value
                        }
                    }
                }
                E2EDsl.Keywords.TAGS -> {
                    if (iterator.hasNext()) {
                        val arrToken = iterator.next()
                        if (arrToken.type == TokenType.ARRAY) {
                            tags = parseTagsArray(arrToken.value)
                        }
                    }
                }
                E2EDsl.Keywords.PRIORITY -> {
                    if (iterator.hasNext()) {
                        val prioToken = iterator.next()
                        if (prioToken.type == TokenType.KEYWORD) {
                            priority = parsePriority(prioToken.value)
                        }
                    }
                }
                E2EDsl.Keywords.STEP -> {
                    val step = parseStep(iterator, stepIndex++)
                    if (step != null) {
                        steps.add(step)
                    }
                }
            }
        }

        if (name.isEmpty()) {
            errors.add(DslError(1, 0, "Scenario name is required"))
            return null
        }

        return TestScenario(
            id = "scenario_${System.currentTimeMillis()}",
            name = name,
            description = description,
            startUrl = startUrl,
            steps = steps,
            tags = tags,
            priority = priority
        )
    }

    private fun parseStep(iterator: Iterator<Token>, index: Int): TestStep? {
        var description = ""
        var action: TestAction? = null
        var expectedOutcome: String? = null
        var timeoutMs: Long? = null
        var retryCount = 0
        var continueOnFailure = false

        // Get step description
        if (iterator.hasNext()) {
            val descToken = iterator.next()
            if (descToken.type == TokenType.STRING) {
                description = descToken.value
            }
        }

        // Parse step body
        val stepTokens = mutableListOf<Token>()
        var braceDepth = 0
        var foundOpen = false

        while (iterator.hasNext()) {
            val token = iterator.next()
            when (token.type) {
                TokenType.BRACE_OPEN -> {
                    foundOpen = true
                    braceDepth++
                }
                TokenType.BRACE_CLOSE -> {
                    braceDepth--
                    if (braceDepth == 0) break
                }
                else -> if (foundOpen) stepTokens.add(token)
            }
        }

        // Parse step content using index-based iteration
        var i = 0
        while (i < stepTokens.size) {
            val token = stepTokens[i]
            if (token.type != TokenType.KEYWORD) {
                i++
                continue
            }

            when (token.value.lowercase()) {
                E2EDsl.Keywords.EXPECT -> {
                    if (i + 1 < stepTokens.size && stepTokens[i + 1].type == TokenType.STRING) {
                        expectedOutcome = stepTokens[i + 1].value
                        i += 2
                    } else {
                        i++
                    }
                }
                E2EDsl.Keywords.TIMEOUT -> {
                    if (i + 1 < stepTokens.size && stepTokens[i + 1].type == TokenType.NUMBER) {
                        timeoutMs = stepTokens[i + 1].value.toLongOrNull()
                        i += 2
                    } else {
                        i++
                    }
                }
                E2EDsl.Keywords.RETRY -> {
                    if (i + 1 < stepTokens.size && stepTokens[i + 1].type == TokenType.NUMBER) {
                        retryCount = stepTokens[i + 1].value.toIntOrNull() ?: 0
                        i += 2
                    } else {
                        i++
                    }
                }
                E2EDsl.Keywords.CONTINUE_ON_FAILURE, "continueonfailure" -> {
                    continueOnFailure = true
                    i++
                }
                else -> {
                    // Parse action - collect tokens until we hit a step-level keyword
                    val actionTokens = mutableListOf<Token>()
                    actionTokens.add(token)
                    var j = i + 1
                    while (j < stepTokens.size) {
                        val nextToken = stepTokens[j]
                        if (nextToken.type == TokenType.KEYWORD && nextToken.value.lowercase() in listOf(
                                E2EDsl.Keywords.EXPECT, E2EDsl.Keywords.TIMEOUT,
                                E2EDsl.Keywords.RETRY, E2EDsl.Keywords.CONTINUE_ON_FAILURE, "continueonfailure"
                            )) {
                            break
                        }
                        actionTokens.add(nextToken)
                        j++
                    }

                    if (action == null) {
                        action = parseActionFromTokens(actionTokens)
                    }
                    i = j
                }
            }
        }

        if (action == null) {
            errors.add(DslError(0, 0, "Step '$description' has no action"))
            return null
        }

        return TestStep(
            id = "step_$index",
            description = description,
            action = action,
            expectedOutcome = expectedOutcome,
            timeoutMs = timeoutMs,
            retryCount = retryCount,
            continueOnFailure = continueOnFailure
        )
    }

    private fun parseAction(actionToken: Token, iterator: Iterator<Token>): TestAction? {
        return when (actionToken.value.lowercase()) {
            E2EDsl.Keywords.CLICK -> parseClickAction(iterator)
            E2EDsl.Keywords.TYPE -> parseTypeAction(iterator)
            E2EDsl.Keywords.HOVER -> parseHoverAction(iterator)
            E2EDsl.Keywords.SCROLL -> parseScrollAction(iterator)
            E2EDsl.Keywords.WAIT -> parseWaitAction(iterator)
            E2EDsl.Keywords.PRESS_KEY, "presskey" -> parsePressKeyAction(iterator)
            E2EDsl.Keywords.NAVIGATE -> parseNavigateAction(iterator)
            E2EDsl.Keywords.GO_BACK, "goback" -> TestAction.GoBack
            E2EDsl.Keywords.GO_FORWARD, "goforward" -> TestAction.GoForward
            E2EDsl.Keywords.REFRESH -> TestAction.Refresh
            E2EDsl.Keywords.ASSERT -> parseAssertAction(iterator)
            E2EDsl.Keywords.SELECT -> parseSelectAction(iterator)
            E2EDsl.Keywords.UPLOAD_FILE, "uploadfile" -> parseUploadFileAction(iterator)
            E2EDsl.Keywords.SCREENSHOT -> parseScreenshotAction(iterator)
            else -> null
        }
    }

    /**
     * Parse action from a list of tokens (first token is the action keyword)
     */
    private fun parseActionFromTokens(tokens: List<Token>): TestAction? {
        if (tokens.isEmpty()) return null
        val actionToken = tokens[0]
        val remaining = tokens.drop(1)

        return when (actionToken.value.lowercase()) {
            E2EDsl.Keywords.CLICK -> parseClickFromList(remaining)
            E2EDsl.Keywords.TYPE -> parseTypeFromList(remaining)
            E2EDsl.Keywords.HOVER -> parseHoverFromList(remaining)
            E2EDsl.Keywords.SCROLL -> parseScrollFromList(remaining)
            E2EDsl.Keywords.WAIT -> parseWaitFromList(remaining)
            E2EDsl.Keywords.PRESS_KEY, "presskey" -> parsePressKeyFromList(remaining)
            E2EDsl.Keywords.NAVIGATE -> parseNavigateFromList(remaining)
            E2EDsl.Keywords.GO_BACK, "goback" -> TestAction.GoBack
            E2EDsl.Keywords.GO_FORWARD, "goforward" -> TestAction.GoForward
            E2EDsl.Keywords.REFRESH -> TestAction.Refresh
            E2EDsl.Keywords.ASSERT -> parseAssertFromList(remaining)
            E2EDsl.Keywords.SELECT -> parseSelectFromList(remaining)
            E2EDsl.Keywords.UPLOAD_FILE, "uploadfile" -> parseUploadFileFromList(remaining)
            E2EDsl.Keywords.SCREENSHOT -> parseScreenshotFromList(remaining)
            else -> null
        }
    }

    private fun parseClickFromList(tokens: List<Token>): TestAction.Click? {
        var targetId: Int? = null
        var button = MouseButton.LEFT
        var clickCount = 1

        for (token in tokens) {
            when {
                token.type == TokenType.TARGET_ID -> targetId = token.value.toIntOrNull()
                token.type == TokenType.KEYWORD -> when (token.value.lowercase()) {
                    E2EDsl.Keywords.LEFT -> button = MouseButton.LEFT
                    E2EDsl.Keywords.RIGHT -> button = MouseButton.RIGHT
                    E2EDsl.Keywords.MIDDLE -> button = MouseButton.MIDDLE
                    E2EDsl.Keywords.DOUBLE -> clickCount = 2
                }
            }
        }

        return targetId?.let { TestAction.Click(it, button, clickCount) }
    }

    private fun parseTypeFromList(tokens: List<Token>): TestAction.Type? {
        var targetId: Int? = null
        var text = ""
        var clearFirst = false
        var pressEnter = false

        for (token in tokens) {
            when {
                token.type == TokenType.TARGET_ID -> targetId = token.value.toIntOrNull()
                token.type == TokenType.STRING -> text = token.value
                token.type == TokenType.KEYWORD -> when (token.value.lowercase()) {
                    E2EDsl.Keywords.CLEAR_FIRST, "clearfirst" -> clearFirst = true
                    E2EDsl.Keywords.PRESS_ENTER, "pressenter" -> pressEnter = true
                }
            }
        }

        return targetId?.let { TestAction.Type(it, text, clearFirst, pressEnter) }
    }

    private fun parseHoverFromList(tokens: List<Token>): TestAction.Hover? {
        val targetId = tokens.firstOrNull { it.type == TokenType.TARGET_ID }?.value?.toIntOrNull()
        return targetId?.let { TestAction.Hover(it) }
    }

    private fun parseScrollFromList(tokens: List<Token>): TestAction.Scroll {
        var direction = ScrollDirection.DOWN
        var amount = 300
        var targetId: Int? = null

        for (token in tokens) {
            when {
                token.type == TokenType.TARGET_ID -> targetId = token.value.toIntOrNull()
                token.type == TokenType.NUMBER -> amount = token.value.toIntOrNull() ?: 300
                token.type == TokenType.KEYWORD -> when (token.value.lowercase()) {
                    E2EDsl.Keywords.UP -> direction = ScrollDirection.UP
                    E2EDsl.Keywords.DOWN -> direction = ScrollDirection.DOWN
                    E2EDsl.Keywords.LEFT -> direction = ScrollDirection.LEFT
                    E2EDsl.Keywords.RIGHT -> direction = ScrollDirection.RIGHT
                }
            }
        }

        return TestAction.Scroll(direction, amount, targetId)
    }

    private fun parseWaitFromList(tokens: List<Token>): TestAction.Wait {
        var condition: WaitCondition = WaitCondition.Duration(1000)
        var timeoutMs = 5000L

        var i = 0
        while (i < tokens.size) {
            val token = tokens[i]
            when {
                token.type == TokenType.NUMBER -> {
                    condition = WaitCondition.Duration(token.value.toLongOrNull() ?: 1000)
                }
                token.type == TokenType.KEYWORD -> when (token.value.lowercase()) {
                    E2EDsl.Keywords.DURATION -> {
                        if (i + 1 < tokens.size && tokens[i + 1].type == TokenType.NUMBER) {
                            condition = WaitCondition.Duration(tokens[i + 1].value.toLongOrNull() ?: 1000)
                            i++
                        }
                    }
                    E2EDsl.Keywords.VISIBLE -> {
                        if (i + 1 < tokens.size && tokens[i + 1].type == TokenType.TARGET_ID) {
                            condition = WaitCondition.ElementVisible(tokens[i + 1].value.toInt())
                            i++
                        }
                    }
                    E2EDsl.Keywords.HIDDEN -> {
                        if (i + 1 < tokens.size && tokens[i + 1].type == TokenType.TARGET_ID) {
                            condition = WaitCondition.ElementHidden(tokens[i + 1].value.toInt())
                            i++
                        }
                    }
                    E2EDsl.Keywords.ENABLED -> {
                        if (i + 1 < tokens.size && tokens[i + 1].type == TokenType.TARGET_ID) {
                            condition = WaitCondition.ElementEnabled(tokens[i + 1].value.toInt())
                            i++
                        }
                    }
                    E2EDsl.Keywords.TEXT_PRESENT, "textpresent" -> {
                        if (i + 1 < tokens.size && tokens[i + 1].type == TokenType.STRING) {
                            condition = WaitCondition.TextPresent(tokens[i + 1].value)
                            i++
                        }
                    }
                    E2EDsl.Keywords.URL_CONTAINS, "urlcontains" -> {
                        if (i + 1 < tokens.size && tokens[i + 1].type == TokenType.STRING) {
                            condition = WaitCondition.UrlContains(tokens[i + 1].value)
                            i++
                        }
                    }
                    E2EDsl.Keywords.PAGE_LOADED, "pageloaded" -> {
                        condition = WaitCondition.PageLoaded(timeoutMs)
                    }
                    E2EDsl.Keywords.NETWORK_IDLE, "networkidle" -> {
                        condition = WaitCondition.NetworkIdle(timeoutMs)
                    }
                    E2EDsl.Keywords.TIMEOUT -> {
                        if (i + 1 < tokens.size && tokens[i + 1].type == TokenType.NUMBER) {
                            timeoutMs = tokens[i + 1].value.toLongOrNull() ?: 5000
                            i++
                        }
                    }
                }
            }
            i++
        }

        return TestAction.Wait(condition, timeoutMs)
    }

    private fun parsePressKeyFromList(tokens: List<Token>): TestAction.PressKey? {
        var key = ""
        val modifiers = mutableListOf<KeyModifier>()

        for (token in tokens) {
            when {
                token.type == TokenType.STRING -> key = token.value
                token.type == TokenType.KEYWORD -> when (token.value.lowercase()) {
                    E2EDsl.Keywords.CTRL -> modifiers.add(KeyModifier.CTRL)
                    E2EDsl.Keywords.ALT -> modifiers.add(KeyModifier.ALT)
                    E2EDsl.Keywords.SHIFT -> modifiers.add(KeyModifier.SHIFT)
                    E2EDsl.Keywords.META -> modifiers.add(KeyModifier.META)
                }
            }
        }

        return if (key.isNotEmpty()) TestAction.PressKey(key, modifiers) else null
    }

    private fun parseNavigateFromList(tokens: List<Token>): TestAction.Navigate? {
        val url = tokens.firstOrNull { it.type == TokenType.STRING }?.value
        return url?.let { TestAction.Navigate(it) }
    }

    private fun parseAssertFromList(tokens: List<Token>): TestAction.Assert? {
        var targetId: Int? = null
        var assertion: AssertionType = AssertionType.Visible

        var i = 0
        while (i < tokens.size) {
            val token = tokens[i]
            when {
                token.type == TokenType.TARGET_ID -> targetId = token.value.toIntOrNull()
                token.type == TokenType.KEYWORD -> when (token.value.lowercase()) {
                    E2EDsl.Keywords.VISIBLE -> assertion = AssertionType.Visible
                    E2EDsl.Keywords.HIDDEN -> assertion = AssertionType.Hidden
                    E2EDsl.Keywords.ENABLED -> assertion = AssertionType.Enabled
                    E2EDsl.Keywords.DISABLED -> assertion = AssertionType.Disabled
                    E2EDsl.Keywords.CHECKED -> assertion = AssertionType.Checked
                    E2EDsl.Keywords.UNCHECKED -> assertion = AssertionType.Unchecked
                    E2EDsl.Keywords.TEXT_EQUALS, "textequals" -> {
                        if (i + 1 < tokens.size && tokens[i + 1].type == TokenType.STRING) {
                            assertion = AssertionType.TextEquals(tokens[i + 1].value)
                            i++
                        }
                    }
                    E2EDsl.Keywords.TEXT_CONTAINS, "textcontains" -> {
                        if (i + 1 < tokens.size && tokens[i + 1].type == TokenType.STRING) {
                            assertion = AssertionType.TextContains(tokens[i + 1].value)
                            i++
                        }
                    }
                    E2EDsl.Keywords.ATTRIBUTE_EQUALS, "attributeequals" -> {
                        if (i + 2 < tokens.size &&
                            tokens[i + 1].type == TokenType.STRING &&
                            tokens[i + 2].type == TokenType.STRING) {
                            assertion = AssertionType.AttributeEquals(tokens[i + 1].value, tokens[i + 2].value)
                            i += 2
                        }
                    }
                    E2EDsl.Keywords.HAS_CLASS, "hasclass" -> {
                        if (i + 1 < tokens.size && tokens[i + 1].type == TokenType.STRING) {
                            assertion = AssertionType.HasClass(tokens[i + 1].value)
                            i++
                        }
                    }
                }
            }
            i++
        }

        return targetId?.let { TestAction.Assert(it, assertion) }
    }

    private fun parseSelectFromList(tokens: List<Token>): TestAction.Select? {
        var targetId: Int? = null
        var value: String? = null
        var label: String? = null
        var index: Int? = null

        var i = 0
        while (i < tokens.size) {
            val token = tokens[i]
            when {
                token.type == TokenType.TARGET_ID -> targetId = token.value.toIntOrNull()
                token.type == TokenType.KEYWORD -> when (token.value.lowercase()) {
                    E2EDsl.Keywords.VALUE -> {
                        if (i + 1 < tokens.size && tokens[i + 1].type == TokenType.STRING) {
                            value = tokens[i + 1].value
                            i++
                        }
                    }
                    E2EDsl.Keywords.LABEL -> {
                        if (i + 1 < tokens.size && tokens[i + 1].type == TokenType.STRING) {
                            label = tokens[i + 1].value
                            i++
                        }
                    }
                    E2EDsl.Keywords.INDEX -> {
                        if (i + 1 < tokens.size && tokens[i + 1].type == TokenType.NUMBER) {
                            index = tokens[i + 1].value.toIntOrNull()
                            i++
                        }
                    }
                }
            }
            i++
        }

        return targetId?.let { TestAction.Select(it, value, label, index) }
    }

    private fun parseUploadFileFromList(tokens: List<Token>): TestAction.UploadFile? {
        var targetId: Int? = null
        var filePath = ""

        for (token in tokens) {
            when (token.type) {
                TokenType.TARGET_ID -> targetId = token.value.toIntOrNull()
                TokenType.STRING -> filePath = token.value
                else -> {}
            }
        }

        return if (targetId != null && filePath.isNotEmpty()) {
            TestAction.UploadFile(targetId, filePath)
        } else null
    }

    private fun parseScreenshotFromList(tokens: List<Token>): TestAction.Screenshot {
        var name = "screenshot"
        var fullPage = false

        for (token in tokens) {
            when {
                token.type == TokenType.STRING -> name = token.value
                token.type == TokenType.KEYWORD && token.value.lowercase() in listOf(E2EDsl.Keywords.FULL_PAGE, "fullpage") -> {
                    fullPage = true
                }
            }
        }

        return TestAction.Screenshot(name, fullPage)
    }

    private fun parseClickAction(iterator: Iterator<Token>): TestAction.Click? {
        var targetId: Int? = null
        var button = MouseButton.LEFT
        var clickCount = 1

        val remaining = collectRemainingTokens(iterator)
        for (token in remaining) {
            when {
                token.type == TokenType.TARGET_ID -> targetId = token.value.toIntOrNull()
                token.type == TokenType.KEYWORD -> when (token.value.lowercase()) {
                    E2EDsl.Keywords.LEFT -> button = MouseButton.LEFT
                    E2EDsl.Keywords.RIGHT -> button = MouseButton.RIGHT
                    E2EDsl.Keywords.MIDDLE -> button = MouseButton.MIDDLE
                    E2EDsl.Keywords.DOUBLE -> clickCount = 2
                }
            }
        }

        return targetId?.let { TestAction.Click(it, button, clickCount) }
    }

    private fun parseTypeAction(iterator: Iterator<Token>): TestAction.Type? {
        var targetId: Int? = null
        var text = ""
        var clearFirst = false
        var pressEnter = false

        val remaining = collectRemainingTokens(iterator)
        for (token in remaining) {
            when {
                token.type == TokenType.TARGET_ID -> targetId = token.value.toIntOrNull()
                token.type == TokenType.STRING -> text = token.value
                token.type == TokenType.KEYWORD -> when (token.value.lowercase()) {
                    E2EDsl.Keywords.CLEAR_FIRST, "clearfirst" -> clearFirst = true
                    E2EDsl.Keywords.PRESS_ENTER, "pressenter" -> pressEnter = true
                }
            }
        }

        return targetId?.let { TestAction.Type(it, text, clearFirst, pressEnter) }
    }

    private fun parseHoverAction(iterator: Iterator<Token>): TestAction.Hover? {
        val remaining = collectRemainingTokens(iterator)
        val targetId = remaining.firstOrNull { it.type == TokenType.TARGET_ID }?.value?.toIntOrNull()
        return targetId?.let { TestAction.Hover(it) }
    }

    private fun parseScrollAction(iterator: Iterator<Token>): TestAction.Scroll {
        var direction = ScrollDirection.DOWN
        var amount = 300
        var targetId: Int? = null

        val remaining = collectRemainingTokens(iterator)
        for (token in remaining) {
            when {
                token.type == TokenType.TARGET_ID -> targetId = token.value.toIntOrNull()
                token.type == TokenType.NUMBER -> amount = token.value.toIntOrNull() ?: 300
                token.type == TokenType.KEYWORD -> when (token.value.lowercase()) {
                    E2EDsl.Keywords.UP -> direction = ScrollDirection.UP
                    E2EDsl.Keywords.DOWN -> direction = ScrollDirection.DOWN
                    E2EDsl.Keywords.LEFT -> direction = ScrollDirection.LEFT
                    E2EDsl.Keywords.RIGHT -> direction = ScrollDirection.RIGHT
                }
            }
        }

        return TestAction.Scroll(direction, amount, targetId)
    }

    private fun parseWaitAction(iterator: Iterator<Token>): TestAction.Wait {
        var condition: WaitCondition = WaitCondition.Duration(1000)
        var timeoutMs = 5000L

        val remaining = collectRemainingTokens(iterator)
        var i = 0
        while (i < remaining.size) {
            val token = remaining[i]
            when {
                token.type == TokenType.NUMBER -> {
                    condition = WaitCondition.Duration(token.value.toLongOrNull() ?: 1000)
                }
                token.type == TokenType.KEYWORD -> when (token.value.lowercase()) {
                    E2EDsl.Keywords.DURATION -> {
                        if (i + 1 < remaining.size && remaining[i + 1].type == TokenType.NUMBER) {
                            condition = WaitCondition.Duration(remaining[i + 1].value.toLongOrNull() ?: 1000)
                            i++
                        }
                    }
                    E2EDsl.Keywords.VISIBLE -> {
                        if (i + 1 < remaining.size && remaining[i + 1].type == TokenType.TARGET_ID) {
                            condition = WaitCondition.ElementVisible(remaining[i + 1].value.toInt())
                            i++
                        }
                    }
                    E2EDsl.Keywords.HIDDEN -> {
                        if (i + 1 < remaining.size && remaining[i + 1].type == TokenType.TARGET_ID) {
                            condition = WaitCondition.ElementHidden(remaining[i + 1].value.toInt())
                            i++
                        }
                    }
                    E2EDsl.Keywords.ENABLED -> {
                        if (i + 1 < remaining.size && remaining[i + 1].type == TokenType.TARGET_ID) {
                            condition = WaitCondition.ElementEnabled(remaining[i + 1].value.toInt())
                            i++
                        }
                    }
                    E2EDsl.Keywords.TEXT_PRESENT, "textpresent" -> {
                        if (i + 1 < remaining.size && remaining[i + 1].type == TokenType.STRING) {
                            condition = WaitCondition.TextPresent(remaining[i + 1].value)
                            i++
                        }
                    }
                    E2EDsl.Keywords.URL_CONTAINS, "urlcontains" -> {
                        if (i + 1 < remaining.size && remaining[i + 1].type == TokenType.STRING) {
                            condition = WaitCondition.UrlContains(remaining[i + 1].value)
                            i++
                        }
                    }
                    E2EDsl.Keywords.PAGE_LOADED, "pageloaded" -> {
                        condition = WaitCondition.PageLoaded(timeoutMs)
                    }
                    E2EDsl.Keywords.NETWORK_IDLE, "networkidle" -> {
                        condition = WaitCondition.NetworkIdle(timeoutMs)
                    }
                    E2EDsl.Keywords.TIMEOUT -> {
                        if (i + 1 < remaining.size && remaining[i + 1].type == TokenType.NUMBER) {
                            timeoutMs = remaining[i + 1].value.toLongOrNull() ?: 5000
                            i++
                        }
                    }
                }
            }
            i++
        }

        return TestAction.Wait(condition, timeoutMs)
    }

    private fun parsePressKeyAction(iterator: Iterator<Token>): TestAction.PressKey? {
        var key = ""
        val modifiers = mutableListOf<KeyModifier>()

        val remaining = collectRemainingTokens(iterator)
        for (token in remaining) {
            when {
                token.type == TokenType.STRING -> key = token.value
                token.type == TokenType.KEYWORD -> when (token.value.lowercase()) {
                    E2EDsl.Keywords.CTRL -> modifiers.add(KeyModifier.CTRL)
                    E2EDsl.Keywords.ALT -> modifiers.add(KeyModifier.ALT)
                    E2EDsl.Keywords.SHIFT -> modifiers.add(KeyModifier.SHIFT)
                    E2EDsl.Keywords.META -> modifiers.add(KeyModifier.META)
                }
            }
        }

        return if (key.isNotEmpty()) TestAction.PressKey(key, modifiers) else null
    }

    private fun parseNavigateAction(iterator: Iterator<Token>): TestAction.Navigate? {
        val remaining = collectRemainingTokens(iterator)
        val url = remaining.firstOrNull { it.type == TokenType.STRING }?.value
        return url?.let { TestAction.Navigate(it) }
    }

    private fun parseAssertAction(iterator: Iterator<Token>): TestAction.Assert? {
        var targetId: Int? = null
        var assertion: AssertionType = AssertionType.Visible

        val remaining = collectRemainingTokens(iterator)
        var i = 0
        while (i < remaining.size) {
            val token = remaining[i]
            when {
                token.type == TokenType.TARGET_ID -> targetId = token.value.toIntOrNull()
                token.type == TokenType.KEYWORD -> when (token.value.lowercase()) {
                    E2EDsl.Keywords.VISIBLE -> assertion = AssertionType.Visible
                    E2EDsl.Keywords.HIDDEN -> assertion = AssertionType.Hidden
                    E2EDsl.Keywords.ENABLED -> assertion = AssertionType.Enabled
                    E2EDsl.Keywords.DISABLED -> assertion = AssertionType.Disabled
                    E2EDsl.Keywords.CHECKED -> assertion = AssertionType.Checked
                    E2EDsl.Keywords.UNCHECKED -> assertion = AssertionType.Unchecked
                    E2EDsl.Keywords.TEXT_EQUALS, "textequals" -> {
                        if (i + 1 < remaining.size && remaining[i + 1].type == TokenType.STRING) {
                            assertion = AssertionType.TextEquals(remaining[i + 1].value)
                            i++
                        }
                    }
                    E2EDsl.Keywords.TEXT_CONTAINS, "textcontains" -> {
                        if (i + 1 < remaining.size && remaining[i + 1].type == TokenType.STRING) {
                            assertion = AssertionType.TextContains(remaining[i + 1].value)
                            i++
                        }
                    }
                    E2EDsl.Keywords.ATTRIBUTE_EQUALS, "attributeequals" -> {
                        if (i + 2 < remaining.size &&
                            remaining[i + 1].type == TokenType.STRING &&
                            remaining[i + 2].type == TokenType.STRING) {
                            assertion = AssertionType.AttributeEquals(remaining[i + 1].value, remaining[i + 2].value)
                            i += 2
                        }
                    }
                    E2EDsl.Keywords.HAS_CLASS, "hasclass" -> {
                        if (i + 1 < remaining.size && remaining[i + 1].type == TokenType.STRING) {
                            assertion = AssertionType.HasClass(remaining[i + 1].value)
                            i++
                        }
                    }
                }
            }
            i++
        }

        return targetId?.let { TestAction.Assert(it, assertion) }
    }

    private fun parseSelectAction(iterator: Iterator<Token>): TestAction.Select? {
        var targetId: Int? = null
        var value: String? = null
        var label: String? = null
        var index: Int? = null

        val remaining = collectRemainingTokens(iterator)
        var i = 0
        while (i < remaining.size) {
            val token = remaining[i]
            when {
                token.type == TokenType.TARGET_ID -> targetId = token.value.toIntOrNull()
                token.type == TokenType.KEYWORD -> when (token.value.lowercase()) {
                    E2EDsl.Keywords.VALUE -> {
                        if (i + 1 < remaining.size && remaining[i + 1].type == TokenType.STRING) {
                            value = remaining[i + 1].value
                            i++
                        }
                    }
                    E2EDsl.Keywords.LABEL -> {
                        if (i + 1 < remaining.size && remaining[i + 1].type == TokenType.STRING) {
                            label = remaining[i + 1].value
                            i++
                        }
                    }
                    E2EDsl.Keywords.INDEX -> {
                        if (i + 1 < remaining.size && remaining[i + 1].type == TokenType.NUMBER) {
                            index = remaining[i + 1].value.toIntOrNull()
                            i++
                        }
                    }
                }
            }
            i++
        }

        return targetId?.let { TestAction.Select(it, value, label, index) }
    }

    private fun parseUploadFileAction(iterator: Iterator<Token>): TestAction.UploadFile? {
        var targetId: Int? = null
        var filePath = ""

        val remaining = collectRemainingTokens(iterator)
        for (token in remaining) {
            when (token.type) {
                TokenType.TARGET_ID -> targetId = token.value.toIntOrNull()
                TokenType.STRING -> filePath = token.value
                else -> {}
            }
        }

        return if (targetId != null && filePath.isNotEmpty()) {
            TestAction.UploadFile(targetId, filePath)
        } else null
    }

    private fun parseScreenshotAction(iterator: Iterator<Token>): TestAction.Screenshot {
        var name = "screenshot"
        var fullPage = false

        val remaining = collectRemainingTokens(iterator)
        for (token in remaining) {
            when {
                token.type == TokenType.STRING -> name = token.value
                token.type == TokenType.KEYWORD && token.value.lowercase() in listOf(E2EDsl.Keywords.FULL_PAGE, "fullpage") -> {
                    fullPage = true
                }
            }
        }

        return TestAction.Screenshot(name, fullPage)
    }

    private fun collectRemainingTokens(iterator: Iterator<Token>): List<Token> {
        val tokens = mutableListOf<Token>()
        while (iterator.hasNext()) {
            val token = iterator.next()
            if (token.type == TokenType.BRACE_CLOSE || token.type == TokenType.BRACE_OPEN) break
            if (token.type == TokenType.KEYWORD && token.value.lowercase() in listOf(
                    E2EDsl.Keywords.STEP, E2EDsl.Keywords.EXPECT, E2EDsl.Keywords.TIMEOUT,
                    E2EDsl.Keywords.RETRY, E2EDsl.Keywords.CONTINUE_ON_FAILURE
                )) break
            tokens.add(token)
        }
        return tokens
    }

    private fun parseTagsArray(arrayStr: String): List<String> {
        return arrayStr
            .removeSurrounding("[", "]")
            .split(",")
            .map { it.trim().removeSurrounding("\"") }
            .filter { it.isNotEmpty() }
    }

    private fun parsePriority(value: String): TestPriority {
        return when (value.lowercase()) {
            E2EDsl.Keywords.CRITICAL -> TestPriority.CRITICAL
            E2EDsl.Keywords.HIGH -> TestPriority.HIGH
            E2EDsl.Keywords.MEDIUM -> TestPriority.MEDIUM
            E2EDsl.Keywords.LOW -> TestPriority.LOW
            else -> TestPriority.MEDIUM
        }
    }
}

/**
 * Token types for DSL parsing
 */
enum class TokenType {
    KEYWORD,
    STRING,
    NUMBER,
    TARGET_ID,
    ARRAY,
    BRACE_OPEN,
    BRACE_CLOSE
}

/**
 * Token representation
 */
data class Token(
    val type: TokenType,
    val value: String,
    val line: Int,
    val column: Int
)

