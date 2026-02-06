package cc.unitmesh.devins.idea.toolwindow.acp

import com.agentclientprotocol.model.PermissionOption
import com.agentclientprotocol.model.SessionUpdate
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.*

/**
 * Dialog for requesting user permission for ACP tool calls.
 * 
 * Displays the tool call details and presents permission options (allow/reject)
 * for the user to choose from.
 */
class IdeaAcpPermissionDialog(
    private val project: Project?,
    private val toolCall: SessionUpdate.ToolCallUpdate,
    private val options: List<PermissionOption>
) : DialogWrapper(project) {

    private var selectedOption: PermissionOption? = null
    private val buttonGroup = ButtonGroup()

    init {
        title = "Agent Permission Request"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints().apply {
            gridx = 0
            gridy = 0
            weightx = 1.0
            fill = GridBagConstraints.HORIZONTAL
            insets = JBUI.insets(8)
        }

        // Tool call title
        val titleLabel = JBLabel("<html><b>${toolCall.title ?: "Tool Call"}</b></html>").apply {
            font = font.deriveFont(font.size + 2f)
        }
        panel.add(titleLabel, gbc)

        // Tool call details
        gbc.gridy++
        val detailsText = buildString {
            append("Tool ID: ${toolCall.toolCallId?.value ?: "N/A"}\n")
            append("Kind: ${toolCall.kind ?: "other"}\n")
            append("Status: ${toolCall.status ?: "pending"}\n")
            
            toolCall.rawInput?.let { input ->
                append("\nInput:\n${input.toString().take(500)}")
            }
        }
        
        val detailsArea = JBTextArea(detailsText).apply {
            isEditable = false
            lineWrap = true
            wrapStyleWord = true
            rows = 6
            background = panel.background
        }
        val scrollPane = JBScrollPane(detailsArea).apply {
            preferredSize = Dimension(500, 120)
        }
        panel.add(scrollPane, gbc)

        // Permission options
        gbc.gridy++
        val optionsLabel = JBLabel("Please choose an action:")
        panel.add(optionsLabel, gbc)

        gbc.gridy++
        gbc.insets = JBUI.insets(4, 16, 4, 8)
        
        options.forEachIndexed { index, option ->
            val radioButton = JRadioButton().apply {
                text = option.name
                toolTipText = "Option ID: ${option.optionId.value}, Kind: ${option.kind}"
                addActionListener {
                    selectedOption = option
                }
            }
            buttonGroup.add(radioButton)
            panel.add(radioButton, gbc)
            gbc.gridy++
        }

        // Select first option by default
        if (options.isNotEmpty()) {
            val firstButton = buttonGroup.elements.toList()[0] as JRadioButton
            firstButton.isSelected = true
            selectedOption = options[0]
        }

        val wrapper = JPanel(BorderLayout()).apply {
            add(panel, BorderLayout.NORTH)
            preferredSize = Dimension(550, 300)
        }

        return wrapper
    }

    fun getSelectedOption(): PermissionOption? = selectedOption

    companion object {
        /**
         * Show permission dialog and return the selected permission option.
         * Returns null if cancelled.
         */
        fun show(
            project: Project?,
            toolCall: SessionUpdate.ToolCallUpdate,
            options: List<PermissionOption>
        ): PermissionOption? {
            val dialog = IdeaAcpPermissionDialog(project, toolCall, options)
            return if (dialog.showAndGet()) {
                dialog.getSelectedOption()
            } else {
                null // User cancelled
            }
        }
    }
}
