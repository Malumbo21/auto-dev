package cc.unitmesh.devins.ui.compose.agent.webedit

import cc.unitmesh.devins.ui.compose.agent.webedit.automation.VisionFallbackProvider
import cc.unitmesh.viewer.web.webedit.AccessibilityNode
import cc.unitmesh.viewer.web.webedit.WebEditAction
import cc.unitmesh.viewer.web.webedit.WebEditBridge
import kotlinx.coroutines.flow.Flow

/**
 * Platform-agnostic interface for vision-based WebEdit helper.
 *
 * JVM implementation uses MultimodalLLMService + Tencent COS.
 * Other platforms return null (vision fallback disabled).
 */
expect class WebEditVisionHelper(
    bridge: WebEditBridge
) : VisionFallbackProvider {
    /**
     * Analyze a screenshot with vision LLM.
     */
    fun analyzeScreenshot(
        userIntent: String,
        actionableContext: String? = null
    ): Flow<String>

    /**
     * Close resources.
     */
    fun close()

    /**
     * Check if vision fallback is available.
     */
    override fun isAvailable(): Boolean

    /**
     * Use vision to suggest browser actions when DOM-based approach fails.
     */
    override suspend fun suggestActionsWithVision(
        userIntent: String,
        failedAction: WebEditAction?,
        actionableElements: List<AccessibilityNode>
    ): List<WebEditAction>
}

/**
 * Factory function for creating WebEditVisionHelper from config.
 * Platform-specific implementations.
 */
expect suspend fun createWebEditVisionHelper(bridge: WebEditBridge): WebEditVisionHelper?

