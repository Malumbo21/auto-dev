package cc.unitmesh.devins.idea.compiler

import cc.unitmesh.devins.compiler.result.DevInsCompiledResult
import cc.unitmesh.devins.compiler.service.DevInsCompilerService
import cc.unitmesh.devins.filesystem.ProjectFileSystem
import cc.unitmesh.devti.language.compiler.DevInsCompiler
import cc.unitmesh.devti.language.psi.DevInFile
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.PsiUtilBase

/**
 * IDEA 专用的 DevIns 编译器服务
 * 
 * 使用 devins-lang 模块的 DevInsCompiler，基于 IntelliJ PSI 解析。
 * 支持完整的 IDE 功能：
 * - Symbol 解析 (/symbol 命令)
 * - 代码重构 (/refactor 命令)
 * - 数据库操作 (/database 命令)
 * - 代码结构分析 (/structure 命令)
 * - 符号使用查找 (/usage 命令)
 * - 文件操作 (/file, /write, /edit_file 命令)
 * - 进程管理 (/launch_process, /kill_process 等)
 * 
 * @param project IntelliJ Project 实例
 * @param editor 可选的编辑器实例，用于获取当前光标位置
 */
class IdeaDevInsCompilerService(
    private val project: Project,
    private val editor: Editor? = null
) : DevInsCompilerService {
    
    override suspend fun compile(source: String, fileSystem: ProjectFileSystem): DevInsCompiledResult {
        return compileInternal(source)
    }
    
    override suspend fun compile(
        source: String,
        fileSystem: ProjectFileSystem,
        variables: Map<String, Any>
    ): DevInsCompiledResult {
        // TODO: 支持自定义变量注入到 VariableTable
        return compileInternal(source)
    }
    
    override fun supportsIdeFeatures(): Boolean = true
    
    override fun getName(): String = "IdeaDevInsCompilerService (devins-lang PSI)"
    
    private suspend fun compileInternal(source: String): DevInsCompiledResult {
        // Create DevInFile from string (internally uses runReadAction)
        val devInFile = DevInFile.fromString(project, source)
        
        // Get current editor and element at caret position
        // FileEditorManager.selectedTextEditor should be called on EDT or in runReadAction
        val (currentEditor, element) = runReadAction {
            val ed = editor ?: FileEditorManager.getInstance(project).selectedTextEditor
            val elem = ed?.let { getElementAtCaretInternal(it) }
            Pair(ed, elem)
        }
        
        // Create and execute compiler (internally uses withContext(Dispatchers.IO) and runReadAction)
        val compiler = DevInsCompiler(project, devInFile, currentEditor, element)
        val ideaResult = compiler.compile()
        
        // Convert to mpp-core's DevInsCompiledResult
        return convertToMppResult(ideaResult)
    }
    
    /**
     * Internal version of getElementAtCaret that assumes caller is already in ReadAction.
     */
    private fun getElementAtCaretInternal(editor: Editor): PsiElement? {
        val offset = editor.caretModel.currentCaret.offset
        val psiFile = PsiUtilBase.getPsiFileInEditor(editor, project) ?: return null
        
        var element = psiFile.findElementAt(offset) ?: return null
        if (element is PsiWhiteSpace) {
            element = element.parent
        }
        return element
    }
    
    private fun getElementAtCaret(editor: Editor): PsiElement? {
        return runReadAction {
            val offset = editor.caretModel.currentCaret.offset
            val psiFile = PsiUtilBase.getPsiFileInEditor(editor, project) ?: return@runReadAction null
            
            var element = psiFile.findElementAt(offset) ?: return@runReadAction null
            if (element is PsiWhiteSpace) {
                element = element.parent
            }
            element
        }
    }
    
    /**
     * 将 devins-lang 的编译结果转换为 mpp-core 的格式
     */
    private fun convertToMppResult(
        ideaResult: cc.unitmesh.devti.language.compiler.DevInsCompiledResult
    ): DevInsCompiledResult {
        return DevInsCompiledResult(
            input = ideaResult.input,
            output = ideaResult.output,
            isLocalCommand = ideaResult.isLocalCommand,
            hasError = ideaResult.hasError,
            errorMessage = null, // IDEA 版本没有 errorMessage 字段
            executeAgent = ideaResult.executeAgent?.let { agent ->
                cc.unitmesh.devins.compiler.result.CustomAgentConfig(
                    name = agent.name,
                    type = agent.state.name,
                    parameters = emptyMap()
                )
            },
            nextJob = null, // DevInFile 不能直接转换，需要时再处理
            config = ideaResult.config?.let { hobbitHole ->
                cc.unitmesh.devins.compiler.result.FrontMatterConfig(
                    name = hobbitHole.name,
                    description = hobbitHole.description,
                    variables = emptyMap(),
                    lifecycle = emptyMap(),
                    functions = emptyList(),
                    agents = emptyList()
                )
            }
        )
    }
    
    companion object {
        /**
         * 创建 IDEA 编译器服务实例
         */
        fun create(project: Project, editor: Editor? = null): IdeaDevInsCompilerService {
            return IdeaDevInsCompilerService(project, editor)
        }
        
        /**
         * 注册为全局编译器服务
         * 应在 IDEA 插件启动时调用
         */
        fun registerAsGlobal(project: Project, editor: Editor? = null) {
            DevInsCompilerService.setInstance(IdeaDevInsCompilerService(project, editor))
        }
    }
}

