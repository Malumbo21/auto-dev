package cc.unitmesh.devins.idea.toolwindow.acp

import cc.unitmesh.config.AcpAgentConfig
import cc.unitmesh.config.AutoDevConfigWrapper
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.awt.*
import javax.swing.*
import javax.swing.border.CompoundBorder
import javax.swing.border.LineBorder

/**
 * IntelliJ DialogWrapper for configuring ACP (Agent Client Protocol) agents.
 *
 * Features:
 * - List configured agents with selection
 * - Add/Edit/Delete agents
 * - Preset detection (Codex, Kimi, Gemini, Claude, Copilot)
 * - Persist to ~/.autodev/config.yaml
 */
class IdeaAcpConfigDialogWrapper(
    private val project: Project?,
    private var agents: MutableMap<String, AcpAgentConfig>,
    private var activeKey: String?,
    private val onSave: (agents: Map<String, AcpAgentConfig>, activeKey: String?) -> Unit,
) : DialogWrapper(project) {

    private val listModel = DefaultListModel<String>()
    private val agentList = JBList(listModel)

    // Edit form fields
    private val nameField = JBTextField()
    private val commandField = JBTextField()
    private val argsField = JBTextField()
    private val envArea = JTextArea(3, 40)

    // Detected presets
    private val installedPresets = IdeaAcpAgentPreset.detectInstalled()

    init {
        title = "Configure ACP Agents"
        setOKButtonText("Save")
        init()
        refreshList()
    }

    override fun createCenterPanel(): JComponent {
        val mainPanel = JPanel(BorderLayout(12, 0))
        mainPanel.preferredSize = Dimension(700, 450)

        // Left: Agent list
        val listPanel = createListPanel()
        mainPanel.add(listPanel, BorderLayout.WEST)

        // Right: Edit form
        val editPanel = createEditPanel()
        mainPanel.add(editPanel, BorderLayout.CENTER)

        return mainPanel
    }

    private fun createListPanel(): JPanel {
        val panel = JPanel(BorderLayout(0, 8))
        panel.preferredSize = Dimension(220, 0)

        val titleLabel = JBLabel("Agents")
        titleLabel.font = titleLabel.font.deriveFont(Font.BOLD)
        panel.add(titleLabel, BorderLayout.NORTH)

        agentList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        agentList.addListSelectionListener {
            if (!it.valueIsAdjusting) {
                val selectedKey = getSelectedKey()
                if (selectedKey != null) {
                    loadAgentToForm(selectedKey)
                }
            }
        }

        val scrollPane = JBScrollPane(agentList)
        panel.add(scrollPane, BorderLayout.CENTER)

        // Buttons
        val buttonPanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0))

        val addButton = JButton("Add")
        addButton.addActionListener { addNewAgent() }
        buttonPanel.add(addButton)

        val deleteButton = JButton("Delete")
        deleteButton.addActionListener { deleteSelectedAgent() }
        buttonPanel.add(deleteButton)

        // Add from preset
        if (installedPresets.isNotEmpty()) {
            val presetButton = JButton("+ Preset")
            presetButton.toolTipText = "Add from detected preset"
            presetButton.addActionListener { showPresetMenu(presetButton) }
            buttonPanel.add(presetButton)
        }

        panel.add(buttonPanel, BorderLayout.SOUTH)

        return panel
    }

    private fun createEditPanel(): JPanel {
        val panel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints().apply {
            insets = JBUI.insets(4)
            anchor = GridBagConstraints.WEST
            fill = GridBagConstraints.HORIZONTAL
        }

        val titleLabel = JBLabel("Agent Configuration")
        titleLabel.font = titleLabel.font.deriveFont(Font.BOLD)
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2; gbc.weightx = 1.0
        panel.add(titleLabel, gbc)

        gbc.gridwidth = 1; gbc.weightx = 0.0

        // Name
        gbc.gridx = 0; gbc.gridy = 1
        panel.add(JBLabel("Display Name:"), gbc)
        gbc.gridx = 1; gbc.weightx = 1.0
        nameField.toolTipText = "Display name for this agent (e.g., Codex CLI)"
        panel.add(nameField, gbc)

        // Command
        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0.0
        panel.add(JBLabel("Command *:"), gbc)
        gbc.gridx = 1; gbc.weightx = 1.0
        commandField.toolTipText = "Executable to spawn (must be in PATH or absolute path)"
        panel.add(commandField, gbc)

        // Args
        gbc.gridx = 0; gbc.gridy = 3; gbc.weightx = 0.0
        panel.add(JBLabel("Arguments:"), gbc)
        gbc.gridx = 1; gbc.weightx = 1.0
        argsField.toolTipText = "Space-separated arguments (e.g., --acp --verbose)"
        panel.add(argsField, gbc)

        // Env
        gbc.gridx = 0; gbc.gridy = 4; gbc.weightx = 0.0; gbc.anchor = GridBagConstraints.NORTHWEST
        panel.add(JBLabel("Env Vars:"), gbc)
        gbc.gridx = 1; gbc.weightx = 1.0; gbc.weighty = 1.0; gbc.fill = GridBagConstraints.BOTH
        envArea.toolTipText = "KEY=VALUE (one per line)"
        envArea.border = CompoundBorder(
            LineBorder(JBColor.border(), 1),
            JBUI.Borders.empty(4)
        )
        panel.add(JBScrollPane(envArea), gbc)

        // Save agent button
        gbc.gridx = 1; gbc.gridy = 5; gbc.weighty = 0.0; gbc.fill = GridBagConstraints.NONE
        gbc.anchor = GridBagConstraints.EAST
        val saveAgentBtn = JButton("Apply Changes")
        saveAgentBtn.addActionListener { saveCurrentForm() }
        panel.add(saveAgentBtn, gbc)

        // Info text
        gbc.gridx = 0; gbc.gridy = 6; gbc.gridwidth = 2; gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.anchor = GridBagConstraints.WEST
        val infoLabel = JBLabel(
            "<html><small>ACP agents are external CLIs (Codex, Kimi, Gemini, Claude) " +
                "that communicate via Agent Client Protocol over stdio.</small></html>"
        )
        infoLabel.foreground = JBColor.GRAY
        panel.add(infoLabel, gbc)

        return panel
    }

    private fun refreshList() {
        listModel.clear()
        agents.forEach { (key, config) ->
            val displayName = config.name.ifBlank { key }
            val marker = if (key == activeKey) " *" else ""
            listModel.addElement("$displayName$marker")
        }
    }

    private fun getSelectedKey(): String? {
        val index = agentList.selectedIndex
        if (index < 0) return null
        return agents.keys.toList().getOrNull(index)
    }

    private fun loadAgentToForm(key: String) {
        val config = agents[key] ?: return
        nameField.text = config.name
        commandField.text = config.command
        argsField.text = config.args
        envArea.text = config.env
    }

    private fun saveCurrentForm() {
        val key = getSelectedKey() ?: return
        val command = commandField.text.trim()
        if (command.isBlank()) {
            Messages.showWarningDialog(contentPanel, "Command is required", "Validation Error")
            return
        }

        val config = AcpAgentConfig(
            name = nameField.text.trim().ifBlank { command },
            command = command,
            args = argsField.text.trim(),
            env = envArea.text.trim()
        )

        agents[key] = config
        activeKey = key
        refreshList()
        // Re-select
        val index = agents.keys.toList().indexOf(key)
        if (index >= 0) agentList.selectedIndex = index
    }

    private fun addNewAgent() {
        val key = "custom-${System.currentTimeMillis() % 10000}"
        agents[key] = AcpAgentConfig(name = "", command = "", args = "", env = "")
        refreshList()
        val index = agents.keys.toList().indexOf(key)
        if (index >= 0) {
            agentList.selectedIndex = index
            nameField.requestFocus()
        }
    }

    private fun deleteSelectedAgent() {
        val key = getSelectedKey() ?: return
        agents.remove(key)
        if (activeKey == key) {
            activeKey = agents.keys.firstOrNull()
        }
        refreshList()
        if (listModel.size() > 0) {
            agentList.selectedIndex = 0
        }
    }

    private fun showPresetMenu(anchor: JButton) {
        val popup = JPopupMenu()
        for (preset in installedPresets) {
            if (agents.containsKey(preset.id)) continue
            val item = JMenuItem("${preset.name} (${preset.command} ${preset.args})")
            item.addActionListener {
                agents[preset.id] = preset.toConfig()
                activeKey = preset.id
                refreshList()
                val index = agents.keys.toList().indexOf(preset.id)
                if (index >= 0) agentList.selectedIndex = index
            }
            popup.add(item)
        }

        if (popup.componentCount == 0) {
            val noItem = JMenuItem("All presets already added")
            noItem.isEnabled = false
            popup.add(noItem)
        }

        popup.show(anchor, 0, anchor.height)
    }

    override fun doOKAction() {
        // Remove agents with empty commands
        val validAgents = agents.filter { it.value.command.isNotBlank() }.toMutableMap()
        if (activeKey != null && activeKey !in validAgents) {
            activeKey = validAgents.keys.firstOrNull()
        }

        // Save to config.yaml
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            try {
                AutoDevConfigWrapper.saveAcpAgents(validAgents, activeKey)
            } catch (e: Exception) {
                // Log but don't block
            }
        }

        onSave(validAgents, activeKey)
        super.doOKAction()
    }

    companion object {
        /**
         * Show the ACP config dialog.
         */
        fun show(
            project: Project?,
            agents: Map<String, AcpAgentConfig>,
            activeKey: String?,
            onSave: (agents: Map<String, AcpAgentConfig>, activeKey: String?) -> Unit,
        ) {
            val dialog = IdeaAcpConfigDialogWrapper(
                project = project,
                agents = agents.toMutableMap(),
                activeKey = activeKey,
                onSave = onSave
            )
            dialog.show()
        }
    }
}
