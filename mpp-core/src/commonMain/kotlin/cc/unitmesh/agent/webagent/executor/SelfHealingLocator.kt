package cc.unitmesh.agent.webagent.executor

import cc.unitmesh.agent.webagent.HealingLevel
import cc.unitmesh.agent.webagent.model.*
import cc.unitmesh.llm.LLMService
import kotlin.math.min

/**
 * Self-healing locator that can recover from element selector failures.
 * 
 * Implements a two-level healing strategy:
 * - L1: Algorithm-based healing (milliseconds, low cost)
 * - L2: LLM semantic healing (seconds, high cost)
 * 
 * Inspired by Mabl's weighted attribute scoring algorithm.
 * 
 * @see <a href="https://github.com/phodal/auto-dev/issues/532">Issue #532</a>
 */
class SelfHealingLocator(
    private val llmService: LLMService? = null,
    private val config: SelfHealingConfig = SelfHealingConfig()
) {
    /**
     * Attempt to heal a failed selector using L1 algorithm
     * 
     * @param failedSelector The selector that failed
     * @param fingerprint The element fingerprint from recording
     * @param currentElements Current actionable elements on page
     * @return Healed selector or null if healing failed
     */
    fun healWithAlgorithm(
        failedSelector: String,
        fingerprint: ElementFingerprint,
        currentElements: List<ActionableElement>
    ): HealingResult? {
        if (currentElements.isEmpty()) return null

        val candidates = currentElements.map { element ->
            element to calculateSimilarity(fingerprint, element.fingerprint)
        }.filter { it.second >= config.threshold }
            .sortedByDescending { it.second }

        val best = candidates.firstOrNull() ?: return null

        return HealingResult(
            originalSelector = failedSelector,
            healedSelector = best.first.selector,
            confidence = best.second,
            level = HealingLevel.L1_ALGORITHM,
            matchReasons = buildMatchReasons(fingerprint, best.first.fingerprint)
        )
    }

    /**
     * Attempt to heal using LLM semantic understanding (L2)
     * 
     * @param failedSelector The selector that failed
     * @param fingerprint The element fingerprint
     * @param screenshot Base64 encoded screenshot
     * @param pageContext Additional page context
     * @return Healed selector or null if healing failed
     */
    suspend fun healWithLLM(
        failedSelector: String,
        fingerprint: ElementFingerprint,
        screenshot: String?,
        pageContext: String
    ): HealingResult? {
        if (llmService == null) return null

        val prompt = buildLLMHealingPrompt(failedSelector, fingerprint, pageContext)
        
        // TODO: Call LLM service and parse response
        // For now, return null as LLM integration is pending
        return null
    }

    /**
     * Calculate similarity score between two element fingerprints
     * 
     * Uses weighted scoring based on attribute reliability:
     * - data-testid: 0.95 (highest confidence)
     * - id: 0.90
     * - aria-label: 0.85
     * - text content: 0.70
     * - class names: 0.50
     * - tag name: 0.30
     * - position: 0.20
     */
    private fun calculateSimilarity(
        original: ElementFingerprint,
        candidate: ElementFingerprint
    ): Double {
        var score = 0.0
        var totalWeight = 0.0

        // data-testid (highest weight)
        if (original.testId != null) {
            totalWeight += config.weights.testId
            if (original.testId == candidate.testId) {
                score += config.weights.testId
            }
        }

        // ID attribute
        if (original.id != null) {
            totalWeight += config.weights.id
            if (original.id == candidate.id) {
                score += config.weights.id
            }
        }

        // ARIA label
        if (original.ariaLabel != null) {
            totalWeight += config.weights.ariaLabel
            if (original.ariaLabel == candidate.ariaLabel) {
                score += config.weights.ariaLabel
            } else if (candidate.ariaLabel != null) {
                score += config.weights.ariaLabel * stringSimilarity(original.ariaLabel, candidate.ariaLabel)
            }
        }

        // Text content
        if (original.textContent != null) {
            totalWeight += config.weights.textContent
            if (original.textContent == candidate.textContent) {
                score += config.weights.textContent
            } else if (candidate.textContent != null) {
                score += config.weights.textContent * stringSimilarity(original.textContent, candidate.textContent)
            }
        }

        // Tag name
        totalWeight += config.weights.tagName
        if (original.tagName.equals(candidate.tagName, ignoreCase = true)) {
            score += config.weights.tagName
        }

        // Class names (Jaccard similarity)
        if (original.classNames.isNotEmpty()) {
            totalWeight += config.weights.classNames
            val intersection = original.classNames.intersect(candidate.classNames.toSet())
            val union = original.classNames.union(candidate.classNames.toSet())
            if (union.isNotEmpty()) {
                score += config.weights.classNames * (intersection.size.toDouble() / union.size)
            }
        }

        // Role
        if (original.role != null) {
            totalWeight += config.weights.role
            if (original.role == candidate.role) {
                score += config.weights.role
            }
        }

        // Position (bounding box proximity)
        if (original.boundingBox != null && candidate.boundingBox != null) {
            totalWeight += config.weights.position
            val positionScore = calculatePositionSimilarity(original.boundingBox, candidate.boundingBox)
            score += config.weights.position * positionScore
        }

        return if (totalWeight > 0) score / totalWeight else 0.0
    }

    /**
     * Calculate string similarity using Levenshtein distance
     */
    private fun stringSimilarity(s1: String, s2: String): Double {
        if (s1 == s2) return 1.0
        if (s1.isEmpty() || s2.isEmpty()) return 0.0

        val distance = levenshteinDistance(s1.lowercase(), s2.lowercase())
        val maxLen = maxOf(s1.length, s2.length)
        return 1.0 - (distance.toDouble() / maxLen)
    }

    /**
     * Levenshtein distance implementation
     */
    private fun levenshteinDistance(s1: String, s2: String): Int {
        val m = s1.length
        val n = s2.length
        val dp = Array(m + 1) { IntArray(n + 1) }

        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j

        for (i in 1..m) {
            for (j in 1..n) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        return dp[m][n]
    }

    /**
     * Calculate position similarity based on bounding box overlap
     */
    private fun calculatePositionSimilarity(box1: BoundingBox, box2: BoundingBox): Double {
        // Calculate center distance
        val cx1 = box1.x + box1.width / 2
        val cy1 = box1.y + box1.height / 2
        val cx2 = box2.x + box2.width / 2
        val cy2 = box2.y + box2.height / 2

        val distance = kotlin.math.sqrt(
            (cx1 - cx2) * (cx1 - cx2) + (cy1 - cy2) * (cy1 - cy2)
        )

        // Normalize by viewport diagonal (assume 1280x720)
        val maxDistance = kotlin.math.sqrt(1280.0 * 1280.0 + 720.0 * 720.0)
        return (1.0 - min(distance / maxDistance, 1.0)).coerceIn(0.0, 1.0)
    }

    /**
     * Build list of reasons why elements matched
     */
    private fun buildMatchReasons(
        original: ElementFingerprint,
        candidate: ElementFingerprint
    ): List<String> {
        val reasons = mutableListOf<String>()

        if (original.testId != null && original.testId == candidate.testId) {
            reasons.add("data-testid match: ${original.testId}")
        }
        if (original.id != null && original.id == candidate.id) {
            reasons.add("id match: ${original.id}")
        }
        if (original.ariaLabel != null && original.ariaLabel == candidate.ariaLabel) {
            reasons.add("aria-label match: ${original.ariaLabel}")
        }
        if (original.textContent != null && original.textContent == candidate.textContent) {
            reasons.add("text content match")
        }
        if (original.tagName.equals(candidate.tagName, ignoreCase = true)) {
            reasons.add("tag name match: ${original.tagName}")
        }
        if (original.role != null && original.role == candidate.role) {
            reasons.add("role match: ${original.role}")
        }

        return reasons
    }

    /**
     * Build prompt for LLM-based healing
     */
    private fun buildLLMHealingPrompt(
        failedSelector: String,
        fingerprint: ElementFingerprint,
        pageContext: String
    ): String {
        return buildString {
            appendLine("You are a browser automation assistant helping to fix a broken element selector.")
            appendLine()
            appendLine("## Failed Selector")
            appendLine(failedSelector)
            appendLine()
            appendLine("## Original Element Properties")
            appendLine("- Tag: ${fingerprint.tagName}")
            fingerprint.id?.let { appendLine("- ID: $it") }
            fingerprint.textContent?.let { appendLine("- Text: $it") }
            fingerprint.ariaLabel?.let { appendLine("- ARIA Label: $it") }
            fingerprint.role?.let { appendLine("- Role: $it") }
            if (fingerprint.classNames.isNotEmpty()) {
                appendLine("- Classes: ${fingerprint.classNames.joinToString(" ")}")
            }
            appendLine()
            appendLine("## Current Page Context")
            appendLine(pageContext)
            appendLine()
            appendLine("## Task")
            appendLine("Find the element that best matches the original element properties.")
            appendLine("Output ONLY a valid CSS selector, nothing else.")
        }
    }
}

/**
 * Result of a healing attempt
 */
data class HealingResult(
    val originalSelector: String,
    val healedSelector: String,
    val confidence: Double,
    val level: HealingLevel,
    val matchReasons: List<String> = emptyList()
)

/**
 * Configuration for self-healing
 */
data class SelfHealingConfig(
    /**
     * Minimum confidence threshold for healing
     */
    val threshold: Double = 0.8,

    /**
     * Attribute weights for similarity calculation
     */
    val weights: AttributeWeights = AttributeWeights()
)

/**
 * Weights for different attributes in similarity calculation
 */
data class AttributeWeights(
    val testId: Double = 0.95,
    val id: Double = 0.90,
    val ariaLabel: Double = 0.85,
    val textContent: Double = 0.70,
    val role: Double = 0.60,
    val classNames: Double = 0.50,
    val tagName: Double = 0.30,
    val position: Double = 0.20
)
