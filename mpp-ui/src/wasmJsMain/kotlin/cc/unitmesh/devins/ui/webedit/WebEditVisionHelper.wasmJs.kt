package cc.unitmesh.devins.ui.compose.agent.webedit

import cc.unitmesh.devins.ui.compose.agent.webedit.automation.VisionFallbackProvider
import cc.unitmesh.viewer.web.webedit.AccessibilityNode
import cc.unitmesh.viewer.web.webedit.WebEditAction
import cc.unitmesh.viewer.web.webedit.WebEditBridge
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * WASM implementation of WebEditVisionHelper.
 * 
 * Vision-based analysis is not supported on WASM platform.
 * This is a stub implementation that returns empty results.
 */
actual class WebEditVisionHelper actual constructor(
    private val bridge: WebEditBridge
) : VisionFallbackProvider {
    
    /**
     * Analyze screenshot with vision LLM.
     * Not supported on WASM - returns empty flow.
     */
    actual fun analyzeScreenshot(
        userIntent: String,
        actionableContext: String?
    ): Flow<String> {
        println("Warning: Vision analysis not supported on WASM platform")
        return flowOf("Vision analysis is not available on this platform.")
    }
    
    /**
     * Close resources. No-op on WASM.
     */
    actual fun close() {
        // No resources to clean up on WASM
    }
    
    /**
     * Check if vision fallback is available - always false on WASM.
     */
    override fun isAvailable(): Boolean = false
    
    /**
     * Suggest actions with vision - not supported on WASM.
     */
    override suspend fun suggestActionsWithVision(
        userIntent: String,
        failedAction: WebEditAction?,
        actionableElements: List<AccessibilityNode>
    ): List<WebEditAction> {
        // Vision fallback not available on WASM
        return emptyList()
    }
}

/**
 * Factory function for creating WebEditVisionHelper on WASM.
 * Returns null as vision features are not supported on this platform.
 */
actual suspend fun createWebEditVisionHelper(bridge: WebEditBridge): WebEditVisionHelper? {
    // Vision features require server-side processing and cloud storage
    // which are not available in WASM environment
    return null
}
