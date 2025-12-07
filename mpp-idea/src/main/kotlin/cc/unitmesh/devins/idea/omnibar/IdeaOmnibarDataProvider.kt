package cc.unitmesh.devins.idea.omnibar

import cc.unitmesh.devins.ui.compose.omnibar.OmnibarActionResult
import cc.unitmesh.devins.ui.compose.omnibar.OmnibarDataProvider
import cc.unitmesh.devins.ui.compose.omnibar.OmnibarItem
import cc.unitmesh.devins.ui.compose.omnibar.OmnibarItemType
import cc.unitmesh.devti.command.dataprovider.BuiltinCommand
import cc.unitmesh.devti.command.dataprovider.CustomCommand
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * IDEA-specific implementation of OmnibarDataProvider.
 * Provides commands, files, and symbols from the IntelliJ IDEA project.
 */
@Service(Service.Level.PROJECT)
class IdeaOmnibarDataProvider(private val project: Project) : OmnibarDataProvider {
    
    private val recentlyUsedItems = mutableListOf<OmnibarItem>()
    private val maxRecentItems = 10
    
    override suspend fun getItems(): List<OmnibarItem> = withContext(Dispatchers.Default) {
        val items = mutableListOf<OmnibarItem>()
        
        // Add built-in commands
        items.addAll(getBuiltinCommands())
        
        // Add custom commands from project
        items.addAll(getCustomCommands())
        
        items
    }
    
    override suspend fun getRecentItems(): List<OmnibarItem> {
        return recentlyUsedItems.toList()
    }
    
    override suspend fun executeAction(item: OmnibarItem): OmnibarActionResult {
        return when (item.type) {
            OmnibarItemType.COMMAND, OmnibarItemType.CUSTOM_COMMAND -> {
                val commandText = "/${item.metadata["commandName"] ?: item.title}"
                OmnibarActionResult.InsertText(commandText)
            }
            OmnibarItemType.FILE -> {
                val path = item.metadata["path"] as? String ?: return OmnibarActionResult.Error("No file path")
                OmnibarActionResult.Navigate(path)
            }
            OmnibarItemType.SYMBOL -> {
                val path = item.metadata["path"] as? String ?: return OmnibarActionResult.Error("No symbol path")
                val line = item.metadata["line"] as? Int ?: 0
                OmnibarActionResult.Navigate(path, line)
            }
            else -> OmnibarActionResult.Success("Action executed: ${item.title}")
        }
    }
    
    override suspend fun recordUsage(item: OmnibarItem) {
        val updatedItem = item.copy(
            lastUsedTimestamp = System.currentTimeMillis(),
            type = OmnibarItemType.RECENT
        )
        recentlyUsedItems.removeAll { it.id == item.id }
        recentlyUsedItems.add(0, updatedItem)
        if (recentlyUsedItems.size > maxRecentItems) {
            recentlyUsedItems.removeAt(recentlyUsedItems.lastIndex)
        }
    }
    
    private fun getBuiltinCommands(): List<OmnibarItem> {
        return BuiltinCommand.all().map { command ->
            OmnibarItem(
                id = "builtin_${command.commandName}",
                title = "/${command.commandName}",
                type = OmnibarItemType.COMMAND,
                description = command.description,
                category = "Commands",
                weight = if (command.enableInSketch) 100 else 50,
                metadata = mapOf(
                    "commandName" to command.commandName,
                    "hasCompletion" to command.hasCompletion,
                    "requireProps" to command.requireProps
                )
            )
        }
    }
    
    private fun getCustomCommands(): List<OmnibarItem> {
        return try {
            CustomCommand.all(project).map { command ->
                OmnibarItem(
                    id = "custom_${command.commandName}",
                    title = "/${command.commandName}",
                    type = OmnibarItemType.CUSTOM_COMMAND,
                    description = command.content.take(100),
                    category = "Custom Commands",
                    weight = 80,
                    metadata = mapOf("commandName" to command.commandName)
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Search files in the project by query.
     */
    suspend fun searchFiles(query: String): List<OmnibarItem> = withContext(Dispatchers.Default) {
        if (query.length < 2) return@withContext emptyList()
        
        val scope = GlobalSearchScope.projectScope(project)
        val files = mutableListOf<OmnibarItem>()
        
        try {
            FilenameIndex.processAllFileNames({ fileName ->
                if (fileName.lowercase().contains(query.lowercase())) {
                    FilenameIndex.getVirtualFilesByName(fileName, scope).take(5).forEach { file ->
                        files.add(fileToOmnibarItem(file))
                    }
                }
                files.size < 50 // Continue while we have less than 50 results
            }, scope, null)
        } catch (e: Exception) {
            // Ignore indexing errors
        }
        
        files.take(20)
    }
    
    private fun fileToOmnibarItem(file: VirtualFile): OmnibarItem {
        return OmnibarItem(
            id = "file_${file.path}",
            title = file.name,
            type = OmnibarItemType.FILE,
            description = file.path,
            category = "Files",
            weight = 60,
            metadata = mapOf("path" to file.path)
        )
    }
    
    companion object {
        fun getInstance(project: Project): IdeaOmnibarDataProvider = project.service()
    }
}

