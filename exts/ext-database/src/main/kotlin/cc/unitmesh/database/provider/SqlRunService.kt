package cc.unitmesh.database.provider

import cc.unitmesh.database.util.DatabaseSchemaAssistant
import cc.unitmesh.devti.provider.RunService
import com.intellij.database.console.runConfiguration.DatabaseScriptRunConfiguration
import com.intellij.database.console.runConfiguration.DatabaseScriptRunConfigurationOptions
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunProfile
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.sql.SqlFileType

class SqlRunService : RunService {
    override fun isApplicable(project: Project, file: VirtualFile): Boolean {
        return file.extension == "sql"
    }

    override fun runConfigurationClass(project: Project): Class<out RunProfile>? =
        DatabaseScriptRunConfiguration::class.java

    override fun createConfiguration(project: Project, virtualFile: VirtualFile): RunConfiguration? {
        return createDatabaseScriptConfiguration(project, virtualFile)?.configuration
    }

    override fun createRunSettings(
        project: Project,
        virtualFile: VirtualFile,
        testElement: PsiElement?
    ): RunnerAndConfigurationSettings? {
        return createDatabaseScriptConfiguration(project, virtualFile)
    }

    private fun createDatabaseScriptConfiguration(project: Project, file: VirtualFile): RunnerAndConfigurationSettings? {
        if (file.fileType != SqlFileType.INSTANCE) return null
        val psiFile = PsiManager.getInstance(project).findFile(file) ?: return null
        val dataSource = DatabaseSchemaAssistant.getDataSources(project).firstOrNull() ?: return null
        val configurationsFromContext = ConfigurationContext(psiFile).configurationsFromContext.orEmpty()
        // @formatter:off
        val configurationSettings = configurationsFromContext
            .firstOrNull { it.configuration is DatabaseScriptRunConfiguration }
            ?.configurationSettings
            ?: return null
        // @formatter:on

        val target = DatabaseScriptRunConfigurationOptions.Target(dataSource.uniqueId, null)
        // Safe cast because configuration was checked before
        (configurationSettings.configuration as DatabaseScriptRunConfiguration).options.targets.add(target)
        configurationSettings.isActivateToolWindowBeforeRun = false

        return configurationSettings
    }
}