package cc.unitmesh.devins.idea.editor.multimodal

import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.*
import java.awt.datatransfer.DataFlavor
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import javax.swing.*

/**
 * Swing panel for displaying attached images with thumbnails, upload status, and remove buttons.
 * Adapted from Compose ImageAttachmentBar for IntelliJ IDEA.
 */
class IdeaImageAttachmentPanel(
    private val project: Project,
    private val uploadManager: IdeaImageUploadManager,
    private val parentDisposable: Disposable
) : JPanel(BorderLayout()), IdeaMultimodalStateListener, Disposable {

    companion object {
        private const val THUMBNAIL_SIZE = 60
        private val BORDER_COLOR = JBColor(Color(200, 200, 200), Color(80, 80, 80))
        private val UPLOADING_COLOR = JBColor(Color(255, 193, 7), Color(255, 193, 7))
        private val COMPLETED_COLOR = JBColor(Color(76, 175, 80), Color(76, 175, 80))
        private val FAILED_COLOR = JBColor(Color(244, 67, 54), Color(244, 67, 54))
    }

    private val imagesPanel = JPanel(FlowLayout(FlowLayout.LEFT, 8, 4))
    private val statusLabel = JBLabel()
    private val modelLabel = JBLabel()

    init {
        Disposer.register(parentDisposable, this)
        
        border = JBUI.Borders.empty(4, 8)
        isOpaque = false
        isVisible = false // Hidden by default, shown when images are added

        // Top row: Vision model and upload status
        val headerPanel = JPanel(BorderLayout()).apply {
            isOpaque = false
            border = JBUI.Borders.emptyBottom(4)
        }
        
        modelLabel.apply {
            icon = AllIcons.General.InspectionsEye
            text = "Vision: ${uploadManager.state.visionModel}"
            font = UIUtil.getLabelFont().deriveFont(Font.PLAIN, 11f)
            foreground = UIUtil.getLabelInfoForeground()
        }
        headerPanel.add(modelLabel, BorderLayout.WEST)
        
        statusLabel.apply {
            font = UIUtil.getLabelFont().deriveFont(Font.PLAIN, 11f)
            foreground = UIUtil.getLabelInfoForeground()
        }
        headerPanel.add(statusLabel, BorderLayout.EAST)
        
        add(headerPanel, BorderLayout.NORTH)

        // Thumbnail scroll pane
        val scrollPane = JBScrollPane(imagesPanel).apply {
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
            verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER
            border = null
            isOpaque = false
            viewport.isOpaque = false
        }
        add(scrollPane, BorderLayout.CENTER)

        // Register listener
        uploadManager.addListener(this)
        
        // Initial state
        updateUI(uploadManager.state)
    }

    override fun onStateChanged(state: IdeaMultimodalState) {
        SwingUtilities.invokeLater {
            updateUI(state)
        }
    }

    private fun updateUI(state: IdeaMultimodalState) {
        isVisible = state.hasImages

        if (!state.hasImages) {
            imagesPanel.removeAll()
            return
        }

        // Update status label
        statusLabel.text = when {
            state.isAnalyzing -> "Analyzing..."
            state.isUploading -> "Uploading... (${state.uploadedCount}/${state.imageCount})"
            state.allImagesUploaded -> "Ready (${state.uploadedCount}/${state.imageCount})"
            state.hasUploadError -> "Upload failed"
            else -> "${state.uploadedCount}/${state.imageCount}"
        }
        
        statusLabel.foreground = when {
            state.hasUploadError -> FAILED_COLOR
            state.allImagesUploaded -> COMPLETED_COLOR
            state.isUploading -> UPLOADING_COLOR
            else -> UIUtil.getLabelInfoForeground()
        }
        
        // Update model label
        modelLabel.text = "Vision: ${state.visionModel}"

        // Rebuild thumbnails
        imagesPanel.removeAll()
        state.images.forEach { image ->
            imagesPanel.add(createThumbnailPanel(image))
        }
        
        imagesPanel.revalidate()
        imagesPanel.repaint()
        revalidate()
        repaint()
    }

    private fun createThumbnailPanel(image: IdeaAttachedImage): JPanel {
        val panel = object : JPanel(BorderLayout()) {
            override fun getPreferredSize(): Dimension = Dimension(THUMBNAIL_SIZE + 8, THUMBNAIL_SIZE + 20)
            override fun paintComponent(g: Graphics) {
                super.paintComponent(g)
                val g2 = g as Graphics2D
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                
                // Draw border based on status
                val borderColor = when {
                    image.isFailed -> FAILED_COLOR
                    image.isUploaded -> COMPLETED_COLOR
                    image.isUploading -> UPLOADING_COLOR
                    else -> BORDER_COLOR
                }
                g2.color = borderColor
                g2.drawRoundRect(0, 0, width - 1, height - 1, 8, 8)
            }
        }
        
        panel.isOpaque = false
        panel.border = JBUI.Borders.empty(2)

        // Thumbnail area
        val thumbnailPanel = JPanel(BorderLayout()).apply {
            preferredSize = Dimension(THUMBNAIL_SIZE, THUMBNAIL_SIZE)
            isOpaque = false
        }

        // Show progress or status icon
        val statusComponent: JComponent = when {
            image.isUploading -> {
                JProgressBar().apply {
                    isIndeterminate = image.uploadProgress == 0
                    if (image.uploadProgress > 0) {
                        value = image.uploadProgress
                    }
                    preferredSize = Dimension(THUMBNAIL_SIZE - 8, 16)
                }
            }
            image.isFailed -> {
                JBLabel(AllIcons.General.Error).apply {
                    toolTipText = image.uploadError ?: "Upload failed"
                }
            }
            image.isUploaded -> {
                JBLabel(AllIcons.Actions.Commit).apply {
                    toolTipText = "Uploaded: ${image.uploadedUrl}"
                }
            }
            else -> {
                JBLabel(AllIcons.FileTypes.Image).apply {
                    toolTipText = image.name
                }
            }
        }

        val iconPanel = JPanel(GridBagLayout()).apply {
            isOpaque = false
            add(statusComponent)
        }
        thumbnailPanel.add(iconPanel, BorderLayout.CENTER)

        panel.add(thumbnailPanel, BorderLayout.CENTER)

        // File name label
        val nameLabel = JBLabel(truncateName(image.name, 10)).apply {
            horizontalAlignment = SwingConstants.CENTER
            font = UIUtil.getLabelFont().deriveFont(Font.PLAIN, 9f)
            foreground = UIUtil.getLabelInfoForeground()
            toolTipText = "${image.name} (${image.displaySize})"
        }
        panel.add(nameLabel, BorderLayout.SOUTH)

        // Remove button (always visible as overlay)
        val removeButton = JButton(AllIcons.Actions.Close).apply {
            preferredSize = Dimension(16, 16)
            isBorderPainted = false
            isContentAreaFilled = false
            isFocusable = false
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            addActionListener {
                uploadManager.removeImage(image.id)
            }
        }

        // Overlay remove button at top-right
        val layeredPane = JLayeredPane().apply {
            layout = null
            preferredSize = panel.preferredSize
            
            panel.bounds = Rectangle(0, 0, preferredSize.width, preferredSize.height)
            add(panel, JLayeredPane.DEFAULT_LAYER)
            
            removeButton.bounds = Rectangle(preferredSize.width - 20, 2, 16, 16)
            add(removeButton, JLayeredPane.POPUP_LAYER)
        }

        // Add retry button for failed uploads
        if (image.isFailed) {
            val retryButton = JButton(AllIcons.Actions.Refresh).apply {
                preferredSize = Dimension(16, 16)
                isBorderPainted = false
                isContentAreaFilled = false
                isFocusable = false
                toolTipText = "Retry upload"
                cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                addActionListener {
                    uploadManager.retryUpload(image)
                }
            }
            retryButton.bounds = Rectangle(2, preferredSize.height - 20, 16, 16)
            layeredPane.add(retryButton, JLayeredPane.POPUP_LAYER)
        }

        // Click to preview (disabled during upload)
        if (!image.isUploading) {
            panel.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
            panel.addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (e.clickCount == 2) {
                        showImagePreview(image)
                    }
                }
            })
        }

        return JPanel(BorderLayout()).apply {
            isOpaque = false
            add(layeredPane)
        }
    }

    private fun truncateName(name: String, maxLength: Int): String {
        return if (name.length > maxLength) {
            name.take(maxLength - 1) + "…"
        } else {
            name
        }
    }

    private fun showImagePreview(image: IdeaAttachedImage) {
        val previewPanel = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(8)
        }

        val infoText = buildString {
            appendLine("Name: ${image.name}")
            appendLine("Size: ${image.displaySize}")
            if (image.compressionSavings != null) {
                appendLine("Compression: -${image.compressionSavings}")
            }
            if (image.isUploaded) {
                appendLine("URL: ${image.uploadedUrl}")
            }
            if (image.isFailed) {
                appendLine("Error: ${image.uploadError}")
            }
        }

        val textArea = JTextArea(infoText).apply {
            isEditable = false
            font = UIUtil.getLabelFont()
            background = UIUtil.getPanelBackground()
        }
        previewPanel.add(JBScrollPane(textArea), BorderLayout.CENTER)

        Messages.showInfoMessage(project, infoText, "Image: ${image.name}")
    }

    /**
     * Open file chooser to select images.
     */
    fun selectImageFile() {
        val descriptor = FileChooserDescriptor(
            true, false, false, false, false, true
        ).apply {
            title = "Select Image"
            description = "Select one or more image files to attach"
            withFileFilter { file ->
                val extension = file.extension?.lowercase() ?: ""
                extension in IdeaAttachedImage.SUPPORTED_EXTENSIONS
            }
        }

        FileChooser.chooseFiles(descriptor, project, null) { files ->
            files.forEach { file ->
                val image = IdeaAttachedImage.fromPath(file.path)
                uploadManager.addImageAndUpload(image)
            }
        }
    }

    /**
     * Try to read image from clipboard.
     * @return true if an image was found and added
     */
    fun pasteImageFromClipboard(): Boolean {
        try {
            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
            
            if (!clipboard.isDataFlavorAvailable(DataFlavor.imageFlavor)) {
                return false
            }
            
            val image = clipboard.getData(DataFlavor.imageFlavor) as? Image ?: return false
            val bufferedImage = toBufferedImage(image)
            
            uploadManager.addImageFromBufferedImage(bufferedImage)
            return true
        } catch (e: Exception) {
            println("Error reading image from clipboard: ${e.message}")
            return false
        }
    }

    private fun toBufferedImage(image: Image): BufferedImage {
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

    override fun dispose() {
        uploadManager.removeListener(this)
    }
}

