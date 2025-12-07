package cc.unitmesh.devins.ui.compose.omnibar

import cc.unitmesh.agent.Platform

/**
 * Compose Desktop implementation of OmnibarDataProvider.
 * Provides built-in commands and settings for the Compose desktop application.
 */
class ComposeOmnibarDataProvider(
    private val onInsertText: (String) -> Unit = {},
    private val onOpenModelConfig: () -> Unit = {},
    private val onOpenToolConfig: () -> Unit = {},
    private val onOpenDirectory: () -> Unit = {},
    private val onClearHistory: () -> Unit = {},
    private val onToggleSidebar: () -> Unit = {}
) : OmnibarDataProvider {

    private val recentlyUsedItems = mutableListOf<OmnibarItem>()
    private val maxRecentItems = 10

    override suspend fun getItems(): List<OmnibarItem> {
        val items = mutableListOf<OmnibarItem>()
        
        // Add built-in commands
        items.addAll(getBuiltinCommands())
        
        // Add settings/actions
        items.addAll(getSettingsItems())
        
        return items
    }

    override suspend fun getRecentItems(): List<OmnibarItem> {
        return recentlyUsedItems.toList()
    }

    override suspend fun executeAction(item: OmnibarItem): OmnibarActionResult {
        return when (item.type) {
            OmnibarItemType.COMMAND, OmnibarItemType.CUSTOM_COMMAND -> {
                val commandText = "/${item.metadata["commandName"] ?: item.title.removePrefix("/")}"
                onInsertText(commandText)
                OmnibarActionResult.InsertText(commandText)
            }
            OmnibarItemType.SETTING -> {
                when (item.id) {
                    "setting_model_config" -> onOpenModelConfig()
                    "setting_tool_config" -> onOpenToolConfig()
                    "setting_open_directory" -> onOpenDirectory()
                    "setting_clear_history" -> onClearHistory()
                    "setting_toggle_sidebar" -> onToggleSidebar()
                }
                OmnibarActionResult.Success("Setting action executed: ${item.title}")
            }
            else -> OmnibarActionResult.Success("Action executed: ${item.title}")
        }
    }

    override suspend fun recordUsage(item: OmnibarItem) {
        recentlyUsedItems.removeAll { it.id == item.id }
        val updatedItem = item.copy(
            lastUsedTimestamp = Platform.getCurrentTimestamp(),
            type = OmnibarItemType.RECENT
        )
        recentlyUsedItems.add(0, updatedItem)
        if (recentlyUsedItems.size > maxRecentItems) {
            recentlyUsedItems.removeAt(recentlyUsedItems.lastIndex)
        }
    }

    private fun getBuiltinCommands(): List<OmnibarItem> {
        return listOf(
            OmnibarItem(
                id = "cmd_file",
                title = "/file",
                type = OmnibarItemType.COMMAND,
                description = "Read file content from project",
                category = "Commands",
                weight = 100,
                metadata = mapOf("commandName" to "file")
            ),
            OmnibarItem(
                id = "cmd_write",
                title = "/write",
                type = OmnibarItemType.COMMAND,
                description = "Write content to a file",
                category = "Commands",
                weight = 95,
                metadata = mapOf("commandName" to "write")
            ),
            OmnibarItem(
                id = "cmd_shell",
                title = "/shell",
                type = OmnibarItemType.COMMAND,
                description = "Execute shell commands",
                category = "Commands",
                weight = 90,
                metadata = mapOf("commandName" to "shell")
            ),
            OmnibarItem(
                id = "cmd_search",
                title = "/search",
                type = OmnibarItemType.COMMAND,
                description = "Search for files or content",
                category = "Commands",
                weight = 85,
                metadata = mapOf("commandName" to "search")
            ),
            OmnibarItem(
                id = "cmd_patch",
                title = "/patch",
                type = OmnibarItemType.COMMAND,
                description = "Apply code patches",
                category = "Commands",
                weight = 80,
                metadata = mapOf("commandName" to "patch")
            ),
            OmnibarItem(
                id = "cmd_browse",
                title = "/browse",
                type = OmnibarItemType.COMMAND,
                description = "Browse web pages",
                category = "Commands",
                weight = 75,
                metadata = mapOf("commandName" to "browse")
            ),
            OmnibarItem(
                id = "cmd_commit",
                title = "/commit",
                type = OmnibarItemType.COMMAND,
                description = "Git commit changes",
                category = "Commands",
                weight = 70,
                metadata = mapOf("commandName" to "commit")
            ),
            OmnibarItem(
                id = "cmd_help",
                title = "/help",
                type = OmnibarItemType.COMMAND,
                description = "Show available commands",
                category = "Commands",
                weight = 50,
                metadata = mapOf("commandName" to "help")
            )
        )
    }

    private fun getSettingsItems(): List<OmnibarItem> {
        return listOf(
            OmnibarItem(
                id = "setting_model_config",
                title = "Configure Model",
                type = OmnibarItemType.SETTING,
                description = "Open model configuration dialog",
                category = "Settings",
                weight = 60,
                shortcutHint = "⌘,"
            ),
            OmnibarItem(
                id = "setting_tool_config",
                title = "Configure Tools",
                type = OmnibarItemType.SETTING,
                description = "Open tool configuration dialog",
                category = "Settings",
                weight = 55
            ),
            OmnibarItem(
                id = "setting_open_directory",
                title = "Open Directory",
                type = OmnibarItemType.SETTING,
                description = "Open a project directory",
                category = "Settings",
                weight = 50,
                shortcutHint = "⌘O"
            ),
            OmnibarItem(
                id = "setting_clear_history",
                title = "Clear History",
                type = OmnibarItemType.SETTING,
                description = "Clear chat history",
                category = "Settings",
                weight = 40
            ),
            OmnibarItem(
                id = "setting_toggle_sidebar",
                title = "Toggle Sidebar",
                type = OmnibarItemType.SETTING,
                description = "Show/hide session sidebar",
                category = "Settings",
                weight = 35,
                shortcutHint = "⌘B"
            )
        )
    }
}

