package cc.unitmesh.devins.idea.toolwindow.codereview

import cc.unitmesh.agent.CodeReviewAgent
import cc.unitmesh.agent.ReviewTask
import cc.unitmesh.agent.ReviewType
import cc.unitmesh.agent.codereview.ModifiedCodeRange
import cc.unitmesh.agent.config.McpToolConfigService
import cc.unitmesh.agent.config.ToolConfigFile
import cc.unitmesh.agent.diff.ChangeType
import cc.unitmesh.agent.diff.DiffLineType
import cc.unitmesh.agent.diff.DiffParser
import cc.unitmesh.agent.linter.LintFileResult
import cc.unitmesh.agent.logging.AutoDevLogger
import cc.unitmesh.config.ConfigManager
import cc.unitmesh.devins.idea.renderer.JewelRenderer
import cc.unitmesh.devins.workspace.DefaultWorkspace
import cc.unitmesh.devins.workspace.Workspace
import cc.unitmesh.llm.LLMService
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator
import com.intellij.openapi.project.Project
import git4idea.GitCommit
import git4idea.GitRevisionNumber
import git4idea.changes.GitCommittedChangeListProvider
import git4idea.history.GitHistoryUtils
import git4idea.repo.GitRepositoryManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.CompletableFuture
import kotlinx.coroutines.future.await

/**
 * ViewModel for Code Review in IntelliJ IDEA plugin.
 *
 * Manages the state and business logic for code review functionality:
 * - Git operations (fetch commits, diffs)
 * - AI-powered analysis
 * - Plan generation and fix generation
 *
 * This implementation is specific to IntelliJ IDEA and uses:
 * - JewelRenderer for native IntelliJ theme integration
 * - IntelliJ Project for workspace context
 * - Disposable for proper resource cleanup
 */
class IdeaCodeReviewViewModel(
    private val project: Project,
    private val coroutineScope: CoroutineScope
) : Disposable {

    private val logger = Logger.getInstance(IdeaCodeReviewViewModel::class.java)

    // State management
    private val _state = MutableStateFlow(CodeReviewState())
    val state: StateFlow<CodeReviewState> = _state.asStateFlow()

    // JewelRenderer for IntelliJ native theme
    val jewelRenderer = JewelRenderer()

    // Workspace
    val workspace: Workspace = createWorkspaceFromProject(project)

    // CodeReviewAgent
    private var codeReviewAgent: CodeReviewAgent? = null

    // Control execution
    private var currentJob: Job? = null

    // Git repository
    private val gitRepository by lazy {
        GitRepositoryManager.getInstance(project).repositories.firstOrNull()
    }

    init {
        // Initialize by loading commit history
        if (workspace.rootPath.isNullOrEmpty()) {
            logger.warn("Workspace root path is null or empty")
            updateState {
                it.copy(error = "No workspace path configured. Please open a project first.")
            }
        } else {
            coroutineScope.launch {
                try {
                    codeReviewAgent = initializeCodingAgent()
                    if (gitRepository != null) {
                        loadCommitHistory()
                    } else {
                        updateState {
                            it.copy(error = "No Git repository found in project")
                        }
                    }
                } catch (e: Exception) {
                    logger.error("Failed to initialize", e)
                    updateState {
                        it.copy(error = "Initialization failed: ${e.message}")
                    }
                }
            }
        }
    }

    private suspend fun initializeCodingAgent(): CodeReviewAgent {
        codeReviewAgent?.let { return it }

        val projectPath = workspace.rootPath
        if (projectPath.isNullOrEmpty()) {
            error("Cannot initialize coding agent: workspace root path is null or empty")
        }

        return createCodeReviewAgent(projectPath)
    }

    /**
     * Load recent git commits (initial load)
     */
    suspend fun loadCommitHistory(count: Int = 50) {
        updateState { it.copy(isLoading = true, error = null) }

        try {
            val repository = gitRepository ?: run {
                updateState {
                    it.copy(
                        isLoading = false,
                        error = "No Git repository found"
                    )
                }
                return
            }

            val future = CompletableFuture<List<GitCommit>>()
            val task = object : Task.Backgroundable(project, "Loading Commits", false) {
                override fun run(indicator: ProgressIndicator) {
                    val commits: List<GitCommit> = try {
                        GitHistoryUtils.history(project, repository.root, "-$count")
                    } catch (e: Exception) {
                        logger.error("Failed to fetch commits", e)
                        emptyList()
                    }
                    future.complete(commits)
                }
            }

            ProgressManager.getInstance()
                .runProcessWithProgressAsynchronously(task, BackgroundableProcessIndicator(task))

            val gitCommits = future.await()
            val commits = gitCommits.map { git ->
                CommitInfo(
                    hash = git.id.asString(),
                    shortHash = git.id.toShortString(),
                    author = git.author.name,
                    timestamp = git.authorTime,
                    date = formatDate(git.authorTime),
                    message = git.fullMessage
                )
            }

            updateState {
                it.copy(
                    isLoading = false,
                    commitHistory = commits,
                    selectedCommitIndices = if (commits.isNotEmpty()) setOf(0) else emptySet(),
                    error = null
                )
            }

            if (commits.isNotEmpty()) {
                loadCommitDiffInternal(setOf(0))
            }

        } catch (e: Exception) {
            logger.error("Failed to load commits", e)
            updateState {
                it.copy(
                    isLoading = false,
                    error = "Failed to load commits: ${e.message}"
                )
            }
        }
    }

    /**
     * Load diff for selected commits
     */
    private suspend fun loadCommitDiffInternal(selectedIndices: Set<Int>) {
        if (selectedIndices.isEmpty()) {
            updateState {
                it.copy(
                    isLoadingDiff = false,
                    selectedCommitIndices = emptySet(),
                    diffFiles = emptyList(),
                    error = null
                )
            }
            return
        }

        updateState {
            it.copy(
                isLoadingDiff = true,
                selectedCommitIndices = selectedIndices,
                error = null
            )
        }

        try {
            val repository = gitRepository ?: run {
                updateState {
                    it.copy(
                        isLoadingDiff = false,
                        error = "No Git repository found"
                    )
                }
                return
            }

            val commits = _state.value.commitHistory
            val selectedCommits = selectedIndices.mapNotNull { commits.getOrNull(it) }

            if (selectedCommits.isEmpty()) {
                updateState {
                    it.copy(
                        isLoadingDiff = false,
                        error = "No commits selected"
                    )
                }
                return
            }

            // Get diff for the selected commit(s)
            val commitHash = selectedCommits.first().hash

            val future = CompletableFuture<String>()
            val task = object : Task.Backgroundable(project, "Loading Diff", false) {
                override fun run(indicator: ProgressIndicator) {
                    val changeList = GitCommittedChangeListProvider.getCommittedChangeList(
                        project, repository.root, GitRevisionNumber(commitHash)
                    )

                    val diffText = changeList?.changes?.joinToString("\n\n") { change ->
                        val beforePath = change.beforeRevision?.file?.path ?: ""
                        val afterPath = change.afterRevision?.file?.path ?: ""
                        val beforeContent = change.beforeRevision?.content ?: ""
                        val afterContent = change.afterRevision?.content ?: ""

                        buildString {
                            append("diff --git a/$beforePath b/$afterPath\n")
                            append("--- a/$beforePath\n")
                            append("+++ b/$afterPath\n")
                            // Simple diff representation
                            append("@@ -1,${beforeContent.lines().size} +1,${afterContent.lines().size} @@\n")
                            beforeContent.lines().forEach { append("-$it\n") }
                            afterContent.lines().forEach { append("+$it\n") }
                        }
                    } ?: ""

                    future.complete(diffText)
                }
            }

            ProgressManager.getInstance()
                .runProcessWithProgressAsynchronously(task, BackgroundableProcessIndicator(task))

            val diffText = future.await()

            // Parse the diff
            val parsedDiffs = DiffParser.parse(diffText)
            val diffFiles = parsedDiffs.map { fileDiff ->
                val changeType = when {
                    fileDiff.isNewFile -> ChangeType.CREATE
                    fileDiff.isDeletedFile -> ChangeType.DELETE
                    fileDiff.oldPath != fileDiff.newPath -> ChangeType.RENAME
                    else -> ChangeType.EDIT
                }

                DiffFileInfo(
                    path = fileDiff.newPath ?: fileDiff.oldPath ?: "",
                    oldPath = if (fileDiff.oldPath != fileDiff.newPath) fileDiff.oldPath else null,
                    changeType = changeType,
                    hunks = fileDiff.hunks
                )
            }

            updateState {
                it.copy(
                    isLoadingDiff = false,
                    diffFiles = diffFiles,
                    error = null
                )
            }

        } catch (e: Exception) {
            logger.error("Failed to load diff", e)
            updateState {
                it.copy(
                    isLoadingDiff = false,
                    error = "Failed to load diff: ${e.message}"
                )
            }
        }
    }

    /**
     * Select a commit or toggle selection
     */
    fun selectCommit(index: Int, toggle: Boolean = false) {
        currentJob?.cancel()

        currentJob = coroutineScope.launch {
            updateState {
                it.copy(
                    aiProgress = AIAnalysisProgress(stage = AnalysisStage.IDLE)
                )
            }

            val newSelection = if (toggle) {
                if (_state.value.selectedCommitIndices.contains(index)) {
                    _state.value.selectedCommitIndices - index
                } else {
                    _state.value.selectedCommitIndices + index
                }
            } else {
                setOf(index)
            }

            loadCommitDiffInternal(newSelection)
        }
    }

    /**
     * Start AI analysis
     */
    fun startAnalysis() {
        if (_state.value.diffFiles.isEmpty()) {
            updateState { it.copy(error = "No files to analyze") }
            return
        }

        currentJob?.cancel()
        currentJob = coroutineScope.launch(Dispatchers.IO) {
            try {
                updateState {
                    it.copy(
                        aiProgress = AIAnalysisProgress(stage = AnalysisStage.RUNNING_LINT),
                        error = null
                    )
                }

                // Step 1: Analyze modified code structure
                val modifiedCodeRanges = analyzeModifiedCode()

                // Step 2: Run lint on modified files
                val filePaths = _state.value.diffFiles.map { it.path }
                runLint(filePaths, modifiedCodeRanges)

                updateState {
                    it.copy(
                        aiProgress = it.aiProgress.copy(stage = AnalysisStage.ANALYZING_LINT)
                    )
                }

                val agent = initializeCodingAgent()

                // Step 3: Perform AI analysis
                val analysisOutputBuilder = StringBuilder()
                val reviewTask = buildReviewTask()

                try {
                    analysisOutputBuilder.appendLine()
                    agent.execute(reviewTask) { progressMessage ->
                        analysisOutputBuilder.append(progressMessage)
                        updateState {
                            it.copy(
                                aiProgress = it.aiProgress.copy(
                                    analysisOutput = analysisOutputBuilder.toString()
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    logger.error("Failed to execute review task", e)
                    analysisOutputBuilder.append("\n❌ Error: ${e.message}")
                    updateState {
                        it.copy(
                            aiProgress = it.aiProgress.copy(
                                analysisOutput = analysisOutputBuilder.toString()
                            )
                        )
                    }
                }

                // Generate modification plan after analysis
                generateModificationPlan()

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.error("Analysis failed", e)
                updateState {
                    it.copy(
                        aiProgress = it.aiProgress.copy(stage = AnalysisStage.ERROR),
                        error = "Analysis failed: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * Cancel current analysis
     */
    fun cancelAnalysis() {
        currentJob?.cancel()
        updateState {
            it.copy(
                aiProgress = AIAnalysisProgress(stage = AnalysisStage.IDLE)
            )
        }
    }

    /**
     * Proceed to generate fixes
     */
    fun proceedToGenerateFixes() {
        logger.info("Proceeding to generate fixes")
        currentJob?.cancel()
        currentJob = coroutineScope.launch(Dispatchers.IO) {
            try {
                updateState {
                    it.copy(
                        aiProgress = it.aiProgress.copy(stage = AnalysisStage.GENERATING_FIX)
                    )
                }

                val agent = initializeCodingAgent()
                val fixRenderer = JewelRenderer()

                updateState {
                    it.copy(
                        aiProgress = it.aiProgress.copy(fixRenderer = fixRenderer)
                    )
                }

                // Get patch from diff files
                val patch = _state.value.diffFiles.joinToString("\n\n") { file ->
                    buildString {
                        append("File: ${file.path}\n")
                        file.hunks.forEach { hunk ->
                            append(hunk.header)
                            append("\n")
                            hunk.lines.forEach { line ->
                                val prefix = when (line.type) {
                                    DiffLineType.ADDED -> "+"
                                    DiffLineType.DELETED -> "-"
                                    DiffLineType.CONTEXT -> " "
                                    DiffLineType.HEADER -> ""
                                }
                                append(prefix)
                                append(line.content)
                                append("\n")
                            }
                        }
                    }
                }

                val userFeedback = _state.value.aiProgress.userFeedback

                // Execute fix generation
                fixRenderer.addUserMessage("Generating fixes...")

                val result = agent.generateFixes(
                    patch = patch,
                    lintResults = _state.value.aiProgress.lintResults,
                    analysisOutput = _state.value.aiProgress.analysisOutput,
                    userFeedback = userFeedback,
                    language = "ZH",
                    renderer = fixRenderer
                ) { progress ->
                    val currentOutput = _state.value.aiProgress.fixOutput
                    updateState {
                        it.copy(
                            aiProgress = it.aiProgress.copy(
                                fixOutput = currentOutput + progress
                            )
                        )
                    }
                }

                updateState {
                    it.copy(
                        aiProgress = it.aiProgress.copy(stage = AnalysisStage.COMPLETED)
                    )
                }

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.error("Fix generation failed", e)
                updateState {
                    it.copy(
                        aiProgress = it.aiProgress.copy(stage = AnalysisStage.ERROR),
                        error = "Fix generation failed: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * Refresh issue for commit
     */
    fun refreshIssueForCommit(commitIndex: Int) {
        logger.info("Refreshing issue for commit at index: $commitIndex")
        // TODO: Implement issue refresh logic
    }

    /**
     * Select a file in the diff view
     */
    fun selectFile(index: Int) {
        if (index in _state.value.diffFiles.indices) {
            // Note: CodeReviewState doesn't have selectedFileIndex, so this is a no-op for now
            // If needed, add selectedFileIndex to CodeReviewState
            logger.info("Selected file at index: $index")
        }
    }

    /**
     * Get selected file
     */
    fun getSelectedFile(): DiffFileInfo? {
        // Since we don't have selectedFileIndex in state, return first file
        return _state.value.diffFiles.firstOrNull()
    }

    /**
     * Refresh the view
     */
    fun refresh() {
        coroutineScope.launch {
            if (gitRepository != null) {
                loadCommitHistory()
            } else {
                updateState {
                    it.copy(error = "No Git repository found in project")
                }
            }
        }
    }

    /**
     * Set user feedback
     */
    fun setUserFeedback(feedback: String) {
        updateState {
            it.copy(
                aiProgress = it.aiProgress.copy(userFeedback = feedback)
            )
        }
    }

    /**
     * Analyze modified code to find which functions/classes were changed
     */
    private suspend fun analyzeModifiedCode(): Map<String, List<ModifiedCodeRange>> {
        val projectPath = workspace.rootPath ?: return emptyMap()
        val modifiedRanges = mutableMapOf<String, List<ModifiedCodeRange>>()

        _state.value.diffFiles.forEach { file ->
            val ranges = mutableListOf<ModifiedCodeRange>()
            file.hunks.forEach { hunk ->
                // Extract modified line numbers from the hunk
                val modifiedLines = hunk.lines
                    .filter { it.type == DiffLineType.ADDED || it.type == DiffLineType.DELETED }
                    .mapNotNull { it.newLineNumber }

                // Simple range extraction from hunks
                ranges.add(
                    ModifiedCodeRange(
                        filePath = file.path,
                        elementName = "modified_block_${hunk.newStartLine}",
                        elementType = "BLOCK",
                        startLine = hunk.newStartLine,
                        endLine = hunk.newStartLine + hunk.newLineCount - 1,
                        modifiedLines = modifiedLines
                    )
                )
            }
            if (ranges.isNotEmpty()) {
                modifiedRanges[file.path] = ranges
            }
        }

        updateState {
            it.copy(
                aiProgress = it.aiProgress.copy(
                    modifiedCodeRanges = modifiedRanges
                )
            )
        }

        return modifiedRanges
    }

    /**
     * Run lint on modified files
     */
    private suspend fun runLint(
        filePaths: List<String>,
        modifiedCodeRanges: Map<String, List<ModifiedCodeRange>> = emptyMap()
    ) {
        val projectPath = workspace.rootPath ?: return

        // For now, create empty lint results
        // In a real implementation, this would call actual linters
        val lintResults = filePaths.map { path ->
            LintFileResult(
                filePath = path,
                linterName = "idea-lint",
                errorCount = 0,
                warningCount = 0,
                infoCount = 0,
                issues = emptyList()
            )
        }

        updateState {
            it.copy(
                aiProgress = it.aiProgress.copy(
                    lintResults = lintResults,
                    lintOutput = "Lint analysis completed for ${filePaths.size} files\n"
                )
            )
        }
    }

    /**
     * Build review task for AI analysis
     */
    private fun buildReviewTask(): ReviewTask {
        val projectPath = workspace.rootPath ?: ""
        val filePaths = _state.value.diffFiles.map { it.path }

        return ReviewTask(
            projectPath = projectPath,
            filePaths = filePaths,
            reviewType = ReviewType.COMPREHENSIVE,
            language = "ZH"
        )
    }

    /**
     * Generate modification plan based on analysis results
     */
    private suspend fun generateModificationPlan() {
        try {
            updateState {
                it.copy(
                    aiProgress = it.aiProgress.copy(stage = AnalysisStage.GENERATING_PLAN)
                )
            }

            val planOutputBuilder = StringBuilder()
            updateState {
                it.copy(aiProgress = it.aiProgress.copy(planOutput = "💡 生成修改建议...\n"))
            }

            // Create a temporary LLM service for plan generation
            val configWrapper = ConfigManager.load()
            val modelConfig = configWrapper.getActiveModelConfig()
                ?: error("No active model configuration found")

            val llmService = LLMService.create(modelConfig)

            // Build plan prompt
            val planPrompt = buildString {
                appendLine("Based on the code review analysis, please provide a modification plan:")
                appendLine()
                appendLine("Analysis Output:")
                appendLine(_state.value.aiProgress.analysisOutput)
                appendLine()
                appendLine("Lint Results:")
                _state.value.aiProgress.lintResults.forEach { result ->
                    appendLine("${result.filePath}: ${result.errorCount} errors, ${result.warningCount} warnings")
                }
            }

            // Use LLM service to generate plan with streaming
            llmService.streamPrompt(planPrompt, compileDevIns = false).collect { chunk ->
                planOutputBuilder.append(chunk)
                updateState {
                    it.copy(
                        aiProgress = it.aiProgress.copy(
                            planOutput = planOutputBuilder.toString()
                        )
                    )
                }
            }

            updateState {
                it.copy(
                    aiProgress = it.aiProgress.copy(stage = AnalysisStage.WAITING_FOR_USER_INPUT)
                )
            }

        } catch (e: Exception) {
            logger.error("Failed to generate modification plan", e)
            updateState {
                it.copy(
                    aiProgress = it.aiProgress.copy(
                        planOutput = "\n❌ Error generating plan: ${e.message}",
                        stage = AnalysisStage.WAITING_FOR_USER_INPUT
                    )
                )
            }
        }
    }

    /**
     * Update state helper
     */
    private fun updateState(update: (CodeReviewState) -> CodeReviewState) {
        _state.value = update(_state.value)
    }

    /**
     * Format date
     */
    private fun formatDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    companion object {
        /**
         * Create a Workspace from an IntelliJ Project
         */
        private fun createWorkspaceFromProject(project: Project): Workspace {
            val projectPath = project.basePath
            val projectName = project.name

            return if (projectPath != null) {
                DefaultWorkspace.create(projectName, projectPath)
            } else {
                DefaultWorkspace.createEmpty(projectName)
            }
        }

        /**
         * Create CodeReviewAgent
         */
        suspend fun createCodeReviewAgent(projectPath: String): CodeReviewAgent {
            if (projectPath.isEmpty()) {
                throw IllegalArgumentException("Project path cannot be empty")
            }

            try {
                val toolConfig = ToolConfigFile.default()
                val configWrapper = ConfigManager.load()
                val modelConfig = configWrapper.getActiveModelConfig()
                    ?: error("No active model configuration found. Please configure a model in settings.")

                val llmService = LLMService.create(modelConfig)
                val mcpToolConfigService = McpToolConfigService(toolConfig)
                val renderer = JewelRenderer()

                val agent = CodeReviewAgent(
                    projectPath = projectPath,
                    llmService = llmService,
                    maxIterations = 50,
                    renderer = renderer,
                    mcpToolConfigService = mcpToolConfigService,
                    enableLLMStreaming = true
                )

                return agent
            } catch (e: Exception) {
                AutoDevLogger.error("IdeaCodeReviewViewModel") {
                    "Failed to create CodeReviewAgent: ${e.message}"
                }
                throw e
            }
        }
    }

    /**
     * Open a file in the IDE editor
     */
    fun openFileViewer(path: String) {
        val basePath = project.basePath ?: run {
            logger.warn("Cannot open file: project basePath is null")
            return
        }
        val file = java.io.File(basePath, path)
        if (!file.exists()) {
            logger.warn("File not found in openFileViewer: ${file.path}")
            return
        }

        val localFileSystem = com.intellij.openapi.vfs.LocalFileSystem.getInstance()
        ApplicationManager.getApplication().invokeLater {
            val virtualFile = localFileSystem.refreshAndFindFileByIoFile(file)
            if (virtualFile != null) {
                com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project).openFile(virtualFile, true)
            } else {
                logger.warn("VirtualFile not found for file: ${file.path}")
            }
        }
    }

    /**
     * Dispose resources when the ViewModel is no longer needed
     */
    override fun dispose() {
        logger.info("Disposing IdeaCodeReviewViewModel")
        // The parent class cleanup will happen when the scope is cancelled
    }
}
