package cc.unitmesh.devins.ui.compose.agent.webedit

import cc.unitmesh.devins.ui.compose.agent.webedit.automation.VisionFallbackProvider
import cc.unitmesh.viewer.web.webedit.AccessibilityNode
import cc.unitmesh.viewer.web.webedit.WebEditAction
import cc.unitmesh.viewer.web.webedit.WebEditBridge
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * iOS implementation of WebEditVisionHelper.
 * Vision fallback is not available on iOS (returns null).
 */
actual class WebEditVisionHelper actual constructor(
    private val bridge: WebEditBridge
) : VisionFallbackProvider {

    actual override fun isAvailable(): Boolean = false

    actual fun analyzeScreenshot(
        userIntent: String,
        actionableContext: String?
    ): Flow<String> = flowOf("[Vision fallback not available on iOS]")

    actual fun close() {
        // No resources to clean up
    }

    actual override suspend fun suggestActionsWithVision(
        userIntent: String,
        failedAction: WebEditAction?,
        actionableElements: List<AccessibilityNode>
    ): List<WebEditAction> {
        // Vision fallback not available on iOS
        return emptyList()
    }
}

/**
 * Factory function for iOS - returns null as vision is not supported.
 */
actual suspend fun createWebEditVisionHelper(bridge: WebEditBridge): WebEditVisionHelper? = null
