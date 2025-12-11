package cc.unitmesh.devins.idea.editor

import cc.unitmesh.devins.idea.editor.multimodal.*
import cc.unitmesh.devti.language.DevInLanguage
import cc.unitmesh.devti.util.InsertUtil
import com.intellij.codeInsight.lookup.LookupManagerListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.CustomShortcutSet
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorCopyPasteHelper
import com.intellij.openapi.editor.EditorModificationUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.actions.IncrementalFindAction
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileTypes.FileTypes
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFileFactory
import com.intellij.ui.EditorTextField
import com.intellij.util.EventDispatcher
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.launch
import java.awt.Color
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.event.KeyEvent
import java.awt.image.BufferedImage
import java.util.*
import javax.swing.KeyStroke

/**
 * DevIn language input component for mpp-idea module.
 *
 * Features:
 * - DevIn language support with syntax highlighting and completion
 * - Enter to submit, Shift/Ctrl/Cmd+Enter for newline
 * - Integration with IntelliJ's completion system (lookup listener)
 * - Auto-completion for @, /, $, : characters
 * - Placeholder text support
 * - Multimodal support: image paste (Ctrl/Cmd+V), file selection, upload tracking
 *
 * Based on AutoDevInput from core module but adapted for standalone mpp-idea usage.
 */
class IdeaDevInInput(
    private val project: Project,
    private val listeners: List<DocumentListener> = emptyList(),
    val disposable: Disposable?,
    private val showAgent: Boolean = true,
    // Multimodal callbacks
    private val onImageUpload: ImageUploadCallback? = null,
    private val onImageUploadBytes: ImageUploadBytesCallback? = null,
    private val onMultimodalAnalysis: MultimodalAnalysisCallback? = null,
    private val onError: ((String) -> Unit)? = null
) : EditorTextField(project, FileTypes.PLAIN_TEXT), Disposable {

    private val editorListeners = EventDispatcher.create(IdeaInputListener::class.java)

    // Multimodal support
    private val imageUploadManager: IdeaImageUploadManager? = if (onImageUpload != null || onImageUploadBytes != null) {
        println("🖼️ IdeaDevInInput: Creating ImageUploadManager (upload=${onImageUpload != null}, bytes=${onImageUploadBytes != null}, analysis=${onMultimodalAnalysis != null})")
        IdeaImageUploadManager(
            project = project,
            uploadCallback = onImageUpload,
            uploadBytesCallback = onImageUploadBytes,
            onError = onError
        ).also { manager ->
            disposable?.let { Disposer.register(it, manager) }

            // Listen for state changes and notify listeners
            manager.addListener(object : IdeaMultimodalStateListener {
                override fun onStateChanged(state: IdeaMultimodalState) {
                    editorListeners.multicaster.onMultimodalStateChanged(state)
                }
            })
        }
    } else {
        println("🖼️ IdeaDevInInput: No multimodal callbacks provided")
        null
    }

    /** Current multimodal state */
    val multimodalState: IdeaMultimodalState get() = imageUploadManager?.state ?: IdeaMultimodalState()

    /** Check if multimodal support is enabled */
    val isMultimodalEnabled: Boolean get() = imageUploadManager != null

    private val internalDocumentListener = object : DocumentListener {
        override fun documentChanged(event: com.intellij.openapi.editor.event.DocumentEvent) {
            editorListeners.multicaster.onTextChanged(text)
        }
    }

    // Enter key handling - submit on Enter, newline on Shift/Ctrl/Cmd+Enter
    private val submitAction = DumbAwareAction.create {
        submitInput(IdeaInputTrigger.Key)
    }

    // Image paste action (Ctrl+V / Cmd+V for images)
    private val imagePasteAction = DumbAwareAction.create {
        if (!tryPasteImage()) {
            val editor = editor ?: return@create
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            val transferable = clipboard.getContents(null) ?: return@create
            EditorCopyPasteHelper.getInstance().pasteTransferable(editor, transferable)
        }
    }

    private val imagePasteShortcutSet = CustomShortcutSet(
        KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_V, KeyEvent.CTRL_DOWN_MASK), null),
        KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_V, KeyEvent.META_DOWN_MASK), null)
    )

    private val enterShortcutSet = CustomShortcutSet(
        KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), null)
    )

    private val newlineAction = DumbAwareAction.create {
        val editor = editor ?: return@create
        insertNewLine(editor)
    }

    private fun insertNewLine(editor: Editor) {
        CommandProcessor.getInstance().executeCommand(project, {
            val eol = "\n"
            val document = editor.document
            val caretOffset = editor.caretModel.offset
            val lineEndOffset = document.getLineEndOffset(document.getLineNumber(caretOffset))
            val textAfterCaret = document.getText(TextRange(caretOffset, lineEndOffset))

            WriteCommandAction.runWriteCommandAction(project) {
                if (textAfterCaret.isBlank()) {
                    document.insertString(caretOffset, eol)
                    EditorModificationUtil.moveCaretRelatively(editor, 1)
                } else {
                    document.insertString(caretOffset, eol)
                    editor.caretModel.moveToOffset(caretOffset + eol.length)
                }
            }
        }, "Insert New Line", null)
    }

    init {
        isOneLineMode = false
        setPlaceholder("Type your message or /help for commands...")
        setFontInheritedFromLAF(true)

        addSettingsProvider {
            it.putUserData(IncrementalFindAction.SEARCH_DISABLED, true)
            it.colorsScheme.lineSpacing = 1.2f
            it.settings.isUseSoftWraps = true
            it.isEmbeddedIntoDialogWrapper = true
            it.contentComponent.setOpaque(false)
        }

        background = EditorColorsManager.getInstance().globalScheme.defaultBackground

        registerEnterShortcut()

        // Register newline shortcuts: Ctrl+Enter, Cmd+Enter, Shift+Enter
        newlineAction.registerCustomShortcutSet(
            CustomShortcutSet(
                KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, KeyEvent.CTRL_DOWN_MASK), null),
                KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, KeyEvent.META_DOWN_MASK), null),
                KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, KeyEvent.SHIFT_DOWN_MASK), null),
            ), this
        )

        // Register image paste shortcut (Ctrl+V / Cmd+V) if multimodal is enabled
        if (imageUploadManager != null) {
            imagePasteAction.registerCustomShortcutSet(imagePasteShortcutSet, this)
        }

        listeners.forEach { listener ->
            document.addDocumentListener(listener)
        }

        runReadAction {
            document.addDocumentListener(internalDocumentListener)
        }

        project.messageBus.connect(disposable ?: this)
            .subscribe(LookupManagerListener.TOPIC, object : LookupManagerListener {
                override fun activeLookupChanged(
                    oldLookup: com.intellij.codeInsight.lookup.Lookup?,
                    newLookup: com.intellij.codeInsight.lookup.Lookup?
                ) {
                    if (newLookup != null) {
                        unregisterEnterShortcut()
                    } else {
                        registerEnterShortcut()
                    }
                }
            })
    }

    private fun registerEnterShortcut() {
        submitAction.registerCustomShortcutSet(enterShortcutSet, this)
    }

    private fun unregisterEnterShortcut() {
        submitAction.unregisterCustomShortcutSet(this)
    }

    override fun onEditorAdded(editor: Editor) {
        editorListeners.multicaster.editorAdded(editor as EditorEx)
    }

    public override fun createEditor(): EditorEx {
        val editor = super.createEditor()
        editor.setVerticalScrollbarVisible(true)
        setBorder(JBUI.Borders.empty())
        editor.setShowPlaceholderWhenFocused(true)
        runReadAction { editor.caretModel.moveToOffset(0) }
        editor.scrollPane.setBorder(border)
        editor.contentComponent.setOpaque(false)
        return editor
    }

    override fun getBackground(): Color {
        val editor = editor ?: return super.getBackground()
        return editor.colorsScheme.defaultBackground
    }

    override fun dispose() {
        editor?.document?.removeDocumentListener(internalDocumentListener)
        listeners.forEach {
            editor?.document?.removeDocumentListener(it)
        }
    }

    /**
     * Recreate the document with DevIn language support.
     * This enables syntax highlighting and completion for DevIn commands.
     */
    fun recreateDocument() {
        // Remove listeners from old document before replacing
        editor?.document?.let { oldDoc ->
            oldDoc.removeDocumentListener(internalDocumentListener)
            listeners.forEach { listener ->
                oldDoc.removeDocumentListener(listener)
            }
        }

        // Create new document with DevIn language support
        val id = UUID.randomUUID()
        val document = ReadAction.compute<Document, Throwable> {
            val psiFile = PsiFileFactory.getInstance(project)
                .createFileFromText("IdeaDevInInput-$id.devin", DevInLanguage, "")
            PsiDocumentManager.getInstance(project).getDocument(psiFile)
        }

        if (document != null) {
            initializeDocumentListeners(document)
            setDocument(document)
        }
    }

    private fun initializeDocumentListeners(inputDocument: Document) {
        listeners.forEach { listener ->
            inputDocument.addDocumentListener(listener)
        }
        // Re-add internal listener to new document
        inputDocument.addDocumentListener(internalDocumentListener)
    }

    /**
     * Add a listener for input events.
     */
    fun addInputListener(listener: IdeaInputListener) {
        editorListeners.addListener(listener)
    }

    /**
     * Remove a listener.
     */
    fun removeInputListener(listener: IdeaInputListener) {
        editorListeners.removeListener(listener)
    }

    /**
     * Append text at the end of the document.
     * Uses InsertUtil for proper text insertion with DevIn language support.
     */
    fun appendText(textToAppend: String) {
        WriteCommandAction.runWriteCommandAction(project, "Append text", "intentions.write.action", {
            val document = this.editor?.document ?: return@runWriteCommandAction
            InsertUtil.insertStringAndSaveChange(project, textToAppend, document, document.textLength, false)
        })
    }

    /**
     * Replace the text content of the input.
     * Clears existing content and sets new text.
     */
    fun replaceText(newText: String) {
        WriteCommandAction.runWriteCommandAction(project, "Replace text", "intentions.write.action", {
            val document = this.editor?.document ?: return@runWriteCommandAction
            document.setText(newText)
        })
    }

    /**
     * Clear the input and recreate document.
     */
    fun clearInput() {
        recreateDocument()
    }

    // ========== Multimodal Support ==========

    /**
     * Submit the input, handling multimodal content if present.
     */
    private fun submitInput(trigger: IdeaInputTrigger) {
        val inputText = text.trim()
        val state = multimodalState

        // Check if we can submit
        if (inputText.isEmpty() && !state.hasImages) {
            return
        }

        // Don't allow sending if images are still uploading
        if (state.isUploading) {
            onError?.invoke("Please wait for image upload to complete")
            return
        }

        // Don't allow sending if any upload failed
        if (state.hasUploadError) {
            onError?.invoke("Some images failed to upload. Please remove or retry them.")
            return
        }

        // If we have uploaded images and multimodal analysis is enabled, perform analysis first
        if (state.allImagesUploaded && state.hasImages && onMultimodalAnalysis != null) {
            val imageUrls = state.images.mapNotNull { it.uploadedUrl }

            imageUploadManager?.setAnalyzing(true, "Analyzing ${imageUrls.size} image(s)...")

            // Notify listeners that analysis is starting
            ApplicationManager.getApplication().invokeLater {
                editorListeners.multicaster.onMultimodalAnalysisStart(imageUrls.size, inputText)
            }

            // Use coroutine scope instead of runBlocking to avoid thread pool starvation
            // Create a dedicated scope for this analysis task
            val analysisScope = kotlinx.coroutines.CoroutineScope(
                kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO
            )
            analysisScope.launch {
                try {
                    val analysisResult = onMultimodalAnalysis.invoke(imageUrls, inputText) { chunk ->
                        // Update both the status panel and notify listeners for renderer
                        imageUploadManager?.updateAnalysisProgress(chunk)
                        ApplicationManager.getApplication().invokeLater {
                            editorListeners.multicaster.onMultimodalAnalysisChunk(chunk)
                        }
                    }

                    imageUploadManager?.setAnalysisResult(analysisResult)

                    // Notify listeners that analysis is complete
                    ApplicationManager.getApplication().invokeLater {
                        editorListeners.multicaster.onMultimodalAnalysisComplete(analysisResult, null)
                    }

                    // Submit with multimodal content on EDT
                    ApplicationManager.getApplication().invokeLater {
                        editorListeners.multicaster.onSubmitWithMultimodal(
                            inputText,
                            trigger,
                            state,
                            analysisResult
                        )

                        // Clear input and images
                        clearInput()
                        imageUploadManager?.clearImages()
                    }
                } catch (e: Exception) {
                    val errorMsg = e.message ?: "Analysis failed"
                    imageUploadManager?.setAnalysisResult(null, errorMsg)
                    // Notify listeners of error
                    ApplicationManager.getApplication().invokeLater {
                        editorListeners.multicaster.onMultimodalAnalysisComplete(null, errorMsg)
                    }
                    onError?.invoke("Multimodal analysis failed: $errorMsg")
                }
            }
        } else if (state.hasImages && state.allImagesUploaded) {
            editorListeners.multicaster.onSubmitWithMultimodal(
                inputText,
                trigger,
                state,
                null
            )
            clearInput()
            imageUploadManager?.clearImages()
        } else {
            // No images - standard submit
            println("   📝 Standard text submit (no images)")
            if (inputText.isNotEmpty()) {
                editorListeners.multicaster.onSubmit(inputText, trigger)
            }
        }
    }

    /**
     * Try to paste an image from clipboard.
     * @return true if an image was found and added, false otherwise
     */
    private fun tryPasteImage(): Boolean {
        if (imageUploadManager == null) return false

        try {
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard

            if (!clipboard.isDataFlavorAvailable(DataFlavor.imageFlavor)) {
                return false
            }

            val image = clipboard.getData(DataFlavor.imageFlavor) as? java.awt.Image ?: return false
            val bufferedImage = toBufferedImage(image)

            imageUploadManager.addImageFromBufferedImage(bufferedImage)
            return true
        } catch (e: Exception) {
            println("Error reading image from clipboard: ${e.message}")
            return false
        }
    }

    /**
     * Convert any Image to BufferedImage.
     */
    private fun toBufferedImage(image: java.awt.Image): BufferedImage {
        if (image is BufferedImage) {
            return image
        }

        val width = image.getWidth(null)
        val height = image.getHeight(null)

        if (width <= 0 || height <= 0) {
            throw IllegalArgumentException("Invalid image dimensions: ${width}x${height}")
        }

        val bufferedImage = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val graphics = bufferedImage.createGraphics()
        try {
            graphics.drawImage(image, 0, 0, null)
        } finally {
            graphics.dispose()
        }
        return bufferedImage
    }

    /**
     * Add an image from a file path.
     */
    fun addImageFromPath(path: String) {
        if (imageUploadManager == null) {
            onError?.invoke("Multimodal support is not configured")
            return
        }

        val image = IdeaAttachedImage.fromPath(path)
        imageUploadManager.addImageAndUpload(image)
    }

    /**
     * Add an image from bytes (e.g., programmatically).
     */
    fun addImageFromBytes(bytes: ByteArray, mimeType: String, name: String) {
        if (imageUploadManager == null) {
            onError?.invoke("Multimodal support is not configured")
            return
        }

        imageUploadManager.addImageFromBytes(bytes, mimeType, name)
    }

    /**
     * Remove an attached image.
     */
    fun removeImage(imageId: String) {
        imageUploadManager?.removeImage(imageId)
    }

    /**
     * Clear all attached images.
     */
    fun clearImages() {
        imageUploadManager?.clearImages()
    }

    /**
     * Get the upload manager for external UI components (e.g., IdeaImageAttachmentPanel).
     */
    fun getImageUploadManager(): IdeaImageUploadManager? = imageUploadManager

    /**
     * Trigger submit programmatically (e.g., from external button).
     */
    fun triggerSubmit(trigger: IdeaInputTrigger = IdeaInputTrigger.Button) {
        submitInput(trigger)
    }

    /**
     * Trigger stop event.
     */
    fun triggerStop() {
        editorListeners.multicaster.onStop()
    }
}

