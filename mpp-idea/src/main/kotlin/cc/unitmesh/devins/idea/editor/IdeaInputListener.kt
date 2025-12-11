package cc.unitmesh.devins.idea.editor

import cc.unitmesh.devins.idea.editor.multimodal.IdeaMultimodalState
import com.intellij.openapi.editor.ex.EditorEx
import java.util.EventListener

/**
 * Trigger type for input submission.
 */
enum class IdeaInputTrigger {
    Button,
    Key
}

/**
 * Listener interface for input events from IdeaDevInInput.
 * Modeled after AutoDevInputListener from core module.
 */
interface IdeaInputListener : EventListener {
    /**
     * Called when the editor is added to the component.
     */
    fun editorAdded(editor: EditorEx) {}

    /**
     * Called when user submits input (via Enter key or Send button).
     */
    fun onSubmit(text: String, trigger: IdeaInputTrigger) {}
    
    /**
     * Called when user submits input with multimodal content (images).
     * @param text The text input
     * @param trigger How the submission was triggered
     * @param multimodalState State containing attached images and analysis info
     * @param analysisResult Result from vision model analysis (if performed)
     */
    fun onSubmitWithMultimodal(
        text: String, 
        trigger: IdeaInputTrigger,
        multimodalState: IdeaMultimodalState,
        analysisResult: String?
    ) {
        // Default implementation: delegate to standard onSubmit
        // Subclasses can override for multimodal handling
        val fullText = if (analysisResult != null) {
            "$text\n\n[Image Analysis]\n$analysisResult"
        } else {
            text
        }
        onSubmit(fullText, trigger)
    }

    /**
     * Called when user requests to stop current execution.
     */
    fun onStop() {}

    /**
     * Called when text content changes.
     */
    fun onTextChanged(text: String) {}
    
    /**
     * Called when multimodal state changes (images added/removed/uploaded).
     */
    fun onMultimodalStateChanged(state: IdeaMultimodalState) {}
}

