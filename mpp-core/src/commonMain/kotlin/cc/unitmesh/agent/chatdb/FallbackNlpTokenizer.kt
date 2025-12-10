package cc.unitmesh.agent.chatdb

/**
 * Enhanced Fallback NLP Tokenizer for Kotlin Multiplatform.
 *
 * Capabilities:
 * 1. English: Porter Stemmer for morphological normalization.
 * 2. Chinese: Bi-directional Maximum Matching (BiMM) for segmentation.
 * 3. Code: SemVer and CamelCase handling.
 * 4. Keywords: RAKE (Rapid Automatic Keyword Extraction) algorithm.
 *
 * References:
 * - Porter Stemmer: https://tartarus.org/martin/PorterStemmer/
 * - BiMM: Bi-directional Maximum Matching for Chinese word segmentation
 * - RAKE: Rapid Automatic Keyword Extraction
 */
object FallbackNlpTokenizer {

    /**
     * Main entry point: Extract weighted keywords from a query.
     * This is the primary method for keyword extraction with intelligent weighting.
     */
    fun extractKeywords(query: String, maxKeywords: Int = 10): List<String> {
        // 1. Pre-process and Tokenize
        val tokens = tokenize(query)

        // 2. RAKE Algorithm implementation
        // Filter out stop words and delimiters to get content words
        val stopWords = StopWords.ENGLISH + StopWords.CHINESE
        val contentTokens = tokens.filter { it.text !in stopWords && it.text.length > 1 }

        if (contentTokens.isEmpty()) return emptyList()

        // Build Co-occurrence Graph
        val frequency = mutableMapOf<String, Int>()
        val degree = mutableMapOf<String, Int>()

        // RAKE window size (words appear together within this distance)
        val windowSize = 3

        for (i in contentTokens.indices) {
            val token = contentTokens[i].text
            frequency[token] = (frequency[token] ?: 0) + 1

            val windowStart = maxOf(0, i - windowSize)
            val windowEnd = minOf(contentTokens.size - 1, i + windowSize)

            for (j in windowStart..windowEnd) {
                if (i == j) continue
                degree[token] = (degree[token] ?: 0) + 1
            }
            // Degree includes self-occurrence in RAKE definitions
            degree[token] = (degree[token] ?: 0) + 1
        }

        // Calculate Scores: Score(w) = deg(w) / freq(w)
        val scores = contentTokens.map { it.text }.distinct().associateWith { word ->
            val deg = degree[word]?.toDouble() ?: 0.0
            val freq = frequency[word]?.toDouble() ?: 1.0
            deg / freq
        }

        return scores.entries
            .sortedByDescending { it.value }
            .take(maxKeywords)
            .map { it.key }
    }

    /**
     * Legacy method for backward compatibility.
     * Extract keywords from natural language query using simple tokenization.
     * Supports both English and Chinese text.
     */
    fun extractKeywords(query: String, stopWords: Set<String>): List<String> {
        val tokens = tokenize(query)

        return tokens
            .filter { it.text !in stopWords && it.text.length > 1 }
            .map { it.text }
            .distinct()
    }

    /**
     * Unified tokenization pipeline.
     */
    fun tokenize(text: String): List<Token> {
        val tokens = mutableListOf<Token>()

        // Step 1: Extract Semantic Versions (v1.2.3, 1.0.0-alpha, 2.1.0+build.123) to prevent splitting
        val versionPattern = Regex("""v?(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(?:-((?:0|[1-9]\d*|\d*[a-zA-Z-][0-9a-zA-Z-]*)(?:\.(?:0|[1-9]\d*|\d*[a-zA-Z-][0-9a-zA-Z-]*))*))?(?:\+([0-9a-zA-Z-]+(?:\.[0-9a-zA-Z-]+)*))?""")
        
        // Use placeholder strategy to preserve SemVer tokens
        var processedText = text
        val versionTokens = mutableListOf<String>()
        versionPattern.findAll(text).forEach { match ->
            versionTokens.add(match.value)
            processedText = processedText.replace(match.value, " __SEMVER_${versionTokens.size - 1}__ ")
        }

        // Step 2: Split by non-alphanumeric (but keep Chinese chars together for now)
        val rawSegments = processedText.split(Regex("[^a-zA-Z0-9\\u4e00-\\u9fa5._-]+"))

        for (segment in rawSegments) {
            if (segment.isBlank()) continue
            
            // Check for SemVer placeholder
            val semverMatch = Regex("__SEMVER_(\\d+)__").matchEntire(segment)
            if (semverMatch != null) {
                val index = semverMatch.groupValues[1].toInt()
                tokens.add(Token(versionTokens[index], TokenType.CODE))
                continue
            }

            if (segment.matches(Regex("[\\u4e00-\\u9fa5]+"))) {
                // Pure Chinese Segment
                tokens.addAll(ChineseSegmenter.segment(segment))
            } else if (segment.matches(Regex("[a-zA-Z0-9._-]+"))) {
                // Pure English/Code segment
                val splitCamel = splitCamelCase(segment)
                splitCamel.forEach { word ->
                    val stem = PorterStemmer.stem(word.lowercase())
                    tokens.add(Token(stem, TokenType.ENGLISH))
                }
            } else {
                // Mixed English and Chinese - split at character type boundaries
                tokens.addAll(splitMixedScript(segment))
            }
        }

        return tokens
    }
    
    /**
     * Split mixed script text (English/Chinese) at character type boundaries.
     * E.g., "Hello世界Test" -> ["hello", "世", "界", "test"] with appropriate types
     */
    private fun splitMixedScript(text: String): List<Token> {
        val tokens = mutableListOf<Token>()
        val currentSegment = StringBuilder()
        var currentType: Char? = null // 'E' for English/Code, 'C' for Chinese
        
        for (char in text) {
            val charType = when {
                char in '\u4e00'..'\u9fa5' -> 'C'
                char.isLetterOrDigit() || char in "._-" -> 'E'
                else -> null
            }
            
            if (charType == null) {
                // Delimiter - flush current segment
                if (currentSegment.isNotEmpty()) {
                    tokens.addAll(processSegment(currentSegment.toString(), currentType))
                    currentSegment.clear()
                }
                currentType = null
                continue
            }
            
            if (currentType != null && currentType != charType) {
                // Type changed - flush current segment
                tokens.addAll(processSegment(currentSegment.toString(), currentType))
                currentSegment.clear()
            }
            
            currentSegment.append(char)
            currentType = charType
        }
        
        // Flush remaining
        if (currentSegment.isNotEmpty()) {
            tokens.addAll(processSegment(currentSegment.toString(), currentType))
        }
        
        return tokens
    }
    
    private fun processSegment(segment: String, type: Char?): List<Token> {
        return when (type) {
            'C' -> ChineseSegmenter.segment(segment)
            'E' -> {
                splitCamelCase(segment).map { word ->
                    Token(PorterStemmer.stem(word.lowercase()), TokenType.ENGLISH)
                }
            }
            else -> emptyList()
        }
    }

    private fun splitCamelCase(s: String): List<String> {
        // Regex to look for switch from lower to upper, or number boundaries
        return s.replace(Regex("([a-z])([A-Z])"), "$1 $2")
            .replace(Regex("([A-Z])([A-Z][a-z])"), "$1 $2") // Handle HTMLParser -> HTML Parser
            .replace(Regex("([a-zA-Z])([0-9])"), "$1 $2")
            .replace(Regex("([0-9])([a-zA-Z])"), "$1 $2")
            .replace('_', ' ')
            .replace('.', ' ')
            .replace('-', ' ')
            .split(' ')
            .filter { it.isNotBlank() }
    }

    data class Token(val text: String, val type: TokenType)
    enum class TokenType { ENGLISH, CHINESE, CODE }

    // --- INTERNAL HELPER CLASSES ---

    /**
     * Pure Kotlin implementation of the Porter Stemming Algorithm.
     * Reference: https://tartarus.org/martin/PorterStemmer/
     */
    internal object PorterStemmer {
        fun stem(word: String): String {
            if (word.length < 3) return word
            val stemmer = StemmerState(word.toCharArray())
            stemmer.step1()
            stemmer.step2()
            stemmer.step3()
            stemmer.step4()
            stemmer.step5()
            return stemmer.toString()
        }

        private class StemmerState(var b: CharArray) {
            var k: Int = b.size - 1 // offset to end of stemmed part
            var j: Int = 0 // general offset into string

            override fun toString() = b.concatToString(0, 0 + (k + 1))

            private fun cons(i: Int): Boolean {
                return when (b[i]) {
                    'a', 'e', 'i', 'o', 'u' -> false
                    'y' -> if (i == 0) true else !cons(i - 1)
                    else -> true
                }
            }

            // m() measures the number of consonant sequences between 0 and j.
            private fun m(): Int {
                var n = 0
                var i = 0
                while (true) {
                    if (i > j) return n
                    if (!cons(i)) break
                    i++
                }
                i++
                while (true) {
                    while (true) {
                        if (i > j) return n
                        if (cons(i)) break
                        i++
                    }
                    i++
                    n++
                    while (true) {
                        if (i > j) return n
                        if (!cons(i)) break
                        i++
                    }
                    i++
                }
            }

            // vowelInStem() is true if 0,...,j contains a vowel
            private fun vowelInStem(): Boolean {
                for (i in 0..j) if (!cons(i)) return true
                return false
            }

            // doublec(j) is true if j,(j-1) contain a double consonant
            private fun doublec(j: Int): Boolean {
                if (j < 1) return false
                if (b[j] != b[j - 1]) return false
                return cons(j)
            }

            // cvc(i) is true if i-2,i-1,i has the form consonant - vowel - consonant
            // and also if the second c is not w, x or y.
            private fun cvc(i: Int): Boolean {
                if (i < 2 || !cons(i) || cons(i - 1) || !cons(i - 2)) return false
                val ch = b[i]
                return ch != 'w' && ch != 'x' && ch != 'y'
            }

            private fun ends(s: String): Boolean {
                val l = s.length
                val o = k - l + 1
                if (o < 0) return false
                for (i in 0 until l) if (b[o + i] != s[i]) return false
                j = k - l
                return true
            }

            private fun setto(s: String) {
                val l = s.length
                val o = j + 1
                val newB = if (o + l > b.size) b.copyOf(o + l) else b
                for (i in 0 until l) newB[o + i] = s[i]
                b = newB
                k = j + l
            }

            private fun r(s: String) {
                if (m() > 0) setto(s)
            }

            fun step1() {
                if (b[k] == 's') {
                    if (ends("sses")) k -= 2
                    else if (ends("ies")) setto("i")
                    else if (b[k - 1] != 's') k--
                }
                if (ends("eed")) {
                    if (m() > 0) k--
                } else if ((ends("ed") || ends("ing")) && vowelInStem()) {
                    k = j
                    if (ends("at")) setto("ate")
                    else if (ends("bl")) setto("ble")
                    else if (ends("iz")) setto("ize")
                    else if (doublec(k)) {
                        k--
                        val ch = b[k]
                        if (ch == 'l' || ch == 's' || ch == 'z') k++
                    } else if (m() == 1 && cvc(k)) setto("e")
                }
            }

            fun step2() {
                if (ends("y") && vowelInStem()) b[k] = 'i'
                if (k == 0) return
                // Optimization: switch on penultimate char
                when (b[k - 1]) {
                    'a' -> {
                        if (ends("ational")) r("ate")
                        else if (ends("tional")) r("tion")
                    }
                    'c' -> {
                        if (ends("enci")) r("ence")
                        else if (ends("anci")) r("ance")
                    }
                    'e' -> if (ends("izer")) r("ize")
                    'l' -> {
                        if (ends("bli")) r("ble")
                        else if (ends("alli")) r("al")
                        else if (ends("entli")) r("ent")
                        else if (ends("eli")) r("e")
                        else if (ends("ousli")) r("ous")
                    }
                    'o' -> {
                        if (ends("ization")) r("ize")
                        else if (ends("ation")) r("ate")
                        else if (ends("ator")) r("ate")
                    }
                    's' -> {
                        if (ends("alism")) r("al")
                        else if (ends("iveness")) r("ive")
                        else if (ends("fulness")) r("ful")
                        else if (ends("ousness")) r("ous")
                    }
                    't' -> {
                        if (ends("aliti")) r("al")
                        else if (ends("iviti")) r("ive")
                        else if (ends("biliti")) r("ble")
                    }
                }
            }

            fun step3() {
                when (b[k]) {
                    'e' -> {
                        if (ends("icate")) r("ic")
                        else if (ends("ative")) r("")
                        else if (ends("alize")) r("al")
                    }
                    'i' -> if (ends("iciti")) r("ic")
                    'l' -> {
                        if (ends("ical")) r("ic")
                        else if (ends("ful")) r("")
                    }
                    's' -> if (ends("ness")) r("")
                }
            }

            fun step4() {
                if (k < 2) return
                when (b[k - 1]) {
                    'a' -> if (ends("al")) { if (m() > 1) k = j }
                    'c' -> {
                        if (ends("ance") || ends("ence")) {
                            if (m() > 1) k = j
                        }
                    }
                    'e' -> if (ends("er")) { if (m() > 1) k = j }
                    'i' -> if (ends("ic")) { if (m() > 1) k = j }
                    'l' -> {
                        if (ends("able") || ends("ible")) {
                            if (m() > 1) k = j
                        }
                    }
                    'n' -> {
                        if (ends("ant") || ends("ement") || ends("ment") || ends("ent")) {
                            if (m() > 1) k = j
                        }
                    }
                    'o' -> {
                        if (ends("ion") && j >= 0 && (b[j] == 's' || b[j] == 't')) {
                            if (m() > 1) k = j
                        } else if (ends("ou")) {
                            if (m() > 1) k = j
                        }
                    }
                    's' -> if (ends("ism")) { if (m() > 1) k = j }
                    't' -> {
                        if (ends("ate") || ends("iti")) {
                            if (m() > 1) k = j
                        }
                    }
                    'u' -> if (ends("ous")) { if (m() > 1) k = j }
                    'v' -> if (ends("ive")) { if (m() > 1) k = j }
                    'z' -> if (ends("ize")) { if (m() > 1) k = j }
                }
            }

            fun step5() {
                j = k
                if (b[k] == 'e') {
                    val a = m()
                    if (a > 1 || (a == 1 && !cvc(k - 1))) k--
                }
                if (b[k] == 'l' && doublec(k) && m() > 1) k--
            }
        }
    }

    /**
     * Bi-directional Maximum Matching for Chinese Segmentation.
     * Uses a built-in dictionary for common words.
     */
    internal object ChineseSegmenter {
        // Common Chinese words dictionary for segmentation
        // In a real app, this can be loaded from external resources
        val dictionary = setOf(
            // Common verbs
            "我们", "他们", "你们", "这个", "那个", "什么", "怎么", "为什么", "因为", "所以",
            "可以", "能够", "应该", "必须", "需要", "希望", "想要", "知道", "认为", "觉得",
            // Tech terms
            "超市", "自然", "语言", "处理", "测试", "数据", "开发", "工程师", "程序员",
            "人工智能", "机器学习", "深度学习", "神经网络", "模型", "代码", "逻辑",
            "用户", "界面", "设计", "系统", "分析", "数据库", "服务器", "客户端",
            "软件", "硬件", "算法", "函数", "变量", "对象", "类型", "接口", "实现",
            // Place names
            "北京", "上海", "广州", "深圳", "杭州", "南京", "天津", "重庆", "成都", "武汉",
            "南京市", "长江", "大桥", "黄河", "长城", "故宫",
            // Common nouns
            "公司", "学校", "医院", "银行", "商店", "餐厅", "酒店", "机场", "火车站",
            "电脑", "手机", "网络", "互联网", "电子", "科技", "技术", "产品", "项目",
            // Time words
            "今天", "明天", "昨天", "现在", "以后", "以前", "将来", "过去", "年月", "日期"
        )
        private const val MAX_LEN = 5

        fun segment(text: String): List<Token> {
            val fmm = forwardMaxMatch(text)
            val rmm = reverseMaxMatch(text)

            // Heuristic: Prefer fewer tokens (implies longer words matched)
            return if (fmm.size <= rmm.size) fmm else rmm
        }

        private fun forwardMaxMatch(text: String): List<Token> {
            val tokens = mutableListOf<Token>()
            var i = 0
            while (i < text.length) {
                var matched = false
                // Try window sizes from MAX_LEN down to 1
                for (len in minOf(MAX_LEN, text.length - i) downTo 1) {
                    val sub = text.substring(i, i + len)
                    if (len == 1 || dictionary.contains(sub)) {
                        tokens.add(Token(sub, TokenType.CHINESE))
                        i += len
                        matched = true
                        break
                    }
                }
                if (!matched) i++ // Should not happen due to len=1 fallback
            }
            return tokens
        }

        private fun reverseMaxMatch(text: String): List<Token> {
            val tokens = mutableListOf<Token>()
            var i = text.length
            while (i > 0) {
                var matched = false
                for (len in minOf(MAX_LEN, i) downTo 1) {
                    val sub = text.substring(i - len, i)
                    if (len == 1 || dictionary.contains(sub)) {
                        tokens.add(0, Token(sub, TokenType.CHINESE))
                        i -= len
                        matched = true
                        break
                    }
                }
                if (!matched) i--
            }
            return tokens
        }
    }

    internal object StopWords {
        val ENGLISH = setOf(
            "the", "is", "at", "which", "on", "and", "a", "an", "in", "to", "of", "for",
            "it", "that", "this", "by", "from", "be", "or", "as", "with", "are", "was",
            "were", "been", "being", "have", "has", "had", "do", "does", "did", "will",
            "would", "could", "should", "may", "might", "must", "shall", "can", "need",
            "get", "all", "me", "my", "show", "most", "top", "up", "down", "out"
        )
        // Minimal set for Chinese
        val CHINESE = setOf(
            "的", "了", "和", "是", "就", "都", "而", "及", "与", "着", "或", "一个", "没有", "我们",
            "不", "也", "很", "在", "有", "这", "那", "他", "她", "它", "们", "吗", "呢", "吧"
        )
    }
}
