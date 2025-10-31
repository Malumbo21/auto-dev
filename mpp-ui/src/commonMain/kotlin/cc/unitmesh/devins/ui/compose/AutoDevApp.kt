package cc.unitmesh.devins.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cc.unitmesh.agent.Platform
import cc.unitmesh.devins.db.ModelConfigRepository
import cc.unitmesh.devins.ui.compose.editor.DevInEditorInput
import cc.unitmesh.devins.workspace.WorkspaceManager
import cc.unitmesh.devins.ui.compose.chat.*
import cc.unitmesh.llm.KoogLLMService
import cc.unitmesh.llm.ModelConfig
import cc.unitmesh.devins.llm.ChatHistoryManager
import cc.unitmesh.devins.llm.Message
import kotlinx.coroutines.launch
import cc.unitmesh.devins.ui.platform.createFileChooser
import cc.unitmesh.devins.filesystem.DefaultFileSystem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoDevApp() {
    val scope = rememberCoroutineScope()
    var compilerOutput by remember { mutableStateOf("") }

    var messages by remember { mutableStateOf<List<Message>>(emptyList()) }
    var currentStreamingOutput by remember { mutableStateOf("") }
    var isLLMProcessing by remember { mutableStateOf(false) }
    
    val chatHistoryManager = remember { ChatHistoryManager.getInstance() }
    
    LaunchedEffect(Unit) {
        messages = chatHistoryManager.getMessages()
    }
    
    var currentModelConfig by remember { mutableStateOf<ModelConfig?>(null) }
    var allModelConfigs by remember { mutableStateOf<List<ModelConfig>>(emptyList()) }
    var llmService by remember { mutableStateOf<KoogLLMService?>(null) }
    var showConfigWarning by remember { mutableStateOf(false) }
    var showDebugDialog by remember { mutableStateOf(false) }
    var showErrorDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var showModelConfigDialog by remember { mutableStateOf(false) }
    var selectedAgent by remember { mutableStateOf("Default") }
    
    val availableAgents = listOf("Default", "clarify", "code-review", "test-gen", "refactor")
    
    var currentWorkspace by remember { mutableStateOf(WorkspaceManager.getCurrentOrEmpty()) }

    val workspaceState by WorkspaceManager.workspaceFlow.collectAsState()

    LaunchedEffect(workspaceState) {
        workspaceState?.let { workspace ->
            currentWorkspace = workspace
        }
    }

    LaunchedEffect(Unit) {
        if (!WorkspaceManager.hasActiveWorkspace()) {
            val defaultPath = "/Users/phodal/IdeaProjects/untitled"
            val fileSystem = DefaultFileSystem(defaultPath)
            if (fileSystem.exists(defaultPath)) {
                WorkspaceManager.openWorkspace("Default Project", defaultPath)
            } else {
                WorkspaceManager.openEmptyWorkspace("Empty Workspace")
            }
        }
    }
    
    val repository = remember {
        ModelConfigRepository.getInstance()
    }
    
    LaunchedEffect(Unit) {
        try {
            val savedConfigs = repository.getAllConfigs()

            // 保存所有配置到状态
            allModelConfigs = savedConfigs

            if (savedConfigs.isNotEmpty()) {
                val defaultConfig = repository.getDefaultConfig()
                val configToUse = defaultConfig ?: savedConfigs.first()

                currentModelConfig = configToUse
                if (configToUse.isValid()) {
                    llmService = KoogLLMService.create(configToUse)
                }
            }
        } catch (e: Exception) {
            println("⚠️ 加载配置失败: ${e.message}")
        }
    }
    
    val callbacks = createChatCallbacks(
        fileSystem = currentWorkspace.fileSystem,
        llmService = llmService,
        chatHistoryManager = chatHistoryManager,
        scope = scope,
        onCompilerOutput = { compilerOutput = it },
        onUserMessage = { userMsg ->
            // 添加用户消息到本地状态
            messages = messages + userMsg
        },
        onStreamingOutput = { output ->
            // 更新流式输出
            currentStreamingOutput = output
        },
        onAssistantMessage = { assistantMsg ->
            // AI 响应完成，添加到本地状态
            messages = messages + assistantMsg
            currentStreamingOutput = ""  // 清空流式输出
        },
        onProcessingChange = { isLLMProcessing = it },
        onError = { 
            errorMessage = it
            showErrorDialog = true
        },
        onConfigWarning = { showConfigWarning = true }
    )
    
    // 打开目录选择器
    fun openDirectoryChooser() {
        scope.launch {
            val fileChooser = createFileChooser()
            val selectedPath = fileChooser.chooseDirectory(
                title = "Select Project Directory",
                initialDirectory = currentWorkspace.rootPath
            )

            selectedPath?.let { path ->
                val projectName = path.substringAfterLast('/')
                try {
                    WorkspaceManager.openWorkspace(projectName, path)
                    println("📁 已切换项目路径: $path")
                } catch (e: Exception) {
                    errorMessage = "切换工作空间失败: ${e.message}"
                    showErrorDialog = true
                }
            }
        }
    }
    
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        // 让 Scaffold 自动处理系统栏和键盘，但我们会在组件级别微调
        contentWindowInsets = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 顶部工具栏 - 添加状态栏边距
            TopBarMenu(
                hasHistory = messages.isNotEmpty(),
                hasDebugInfo = compilerOutput.isNotEmpty(),
                currentModelConfig = currentModelConfig,
                availableConfigs = allModelConfigs,
                selectedAgent = selectedAgent,
                availableAgents = availableAgents,
                onOpenDirectory = { openDirectoryChooser() },
                onClearHistory = { 
                    chatHistoryManager.clearCurrentSession()
                    messages = emptyList()
                    currentStreamingOutput = ""
                    println("🗑️ [SimpleAIChat] 聊天历史已清空")
                },
                onShowDebug = { showDebugDialog = true },
                onModelConfigChange = { config ->
                    currentModelConfig = config
                    if (config.isValid()) {
                        try {
                            llmService = KoogLLMService.create(config)
                            println("✅ 切换模型: ${config.provider.displayName} / ${config.modelName}")
                        } catch (e: Exception) {
                            println("❌ 切换模型失败: ${e.message}")
                        }
                    }
                },
                onAgentChange = { agent ->
                    selectedAgent = agent
                    println("🤖 切换 Agent: $agent")
                },
                onShowModelConfig = { showModelConfigDialog = true },
                modifier = Modifier
                    .statusBarsPadding() // 添加状态栏边距
            )
            
            // 判断是否应该显示紧凑布局（有消息历史或正在处理）
            val isCompactMode = messages.isNotEmpty() || isLLMProcessing
            
            if (isCompactMode) {
                // 紧凑模式：显示消息列表，输入框在底部
                MessageList(
                    messages = messages,
                    isLLMProcessing = isLLMProcessing,
                    currentOutput = currentStreamingOutput,
                    projectPath = currentWorkspace.rootPath,
                    fileSystem = currentWorkspace.fileSystem,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
                
                // 底部输入框 - 紧凑模式（一行）
                // 使用 Column 包装以正确处理键盘遮挡
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding() // 键盘弹出时，整个区域向上推
                        .navigationBarsPadding() // 添加导航栏边距
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shadowElevation = 8.dp,
                        tonalElevation = 2.dp
                    ) {
                    DevInEditorInput(
                        initialText = "",
                        placeholder = "Type your message...",
                        callbacks = callbacks,
                        completionManager = currentWorkspace.completionManager,
                        isCompactMode = true,
                        initialModelConfig = currentModelConfig,
                        availableConfigs = allModelConfigs,
                        onModelConfigChange = { config ->
                            currentModelConfig = config
                            if (config.isValid()) {
                                try {
                                    llmService = KoogLLMService.create(config)
                                    println("✅ 切换模型: ${config.provider.displayName} / ${config.modelName}")
                                } catch (e: Exception) {
                                    println("❌ 切换模型失败: ${e.message}")
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    )
                    }
                }
            } else {
                // 默认模式：输入框居中显示
                // Android: 使用更紧凑的布局和更小的 padding
                val isAndroid = Platform.isAndroid

                // 使用 Box 支持键盘避让
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .imePadding() // 键盘弹出时自动调整
                        .padding(if (isAndroid) 16.dp else 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // 完整的输入组件（包含底部工具栏）
                    DevInEditorInput(
                        initialText = "",
                        placeholder = "Type your message...",
                        callbacks = callbacks,
                        completionManager = currentWorkspace.completionManager,
                        initialModelConfig = currentModelConfig,
                        availableConfigs = allModelConfigs,
                        onModelConfigChange = { config ->
                            currentModelConfig = config
                            if (config.isValid()) {
                                try {
                                    llmService = KoogLLMService.create(config)
                                    scope.launch {
                                        try {
                                            repository.saveConfig(config, setAsDefault = true)
                                            allModelConfigs = repository.getAllConfigs()
                                            println("✅ 模型配置已保存")
                                        } catch (e: Exception) {
                                            println("⚠️ 保存配置失败: ${e.message}")
                                        }
                                    }
                                } catch (e: Exception) {
                                    println("❌ 配置 LLM 服务失败: ${e.message}")
                                    llmService = null
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(if (isAndroid) 1f else 0.9f)
                    )
                }
            }
        }
    }
    
        // Model Config Dialog
        if (showModelConfigDialog) {
            cc.unitmesh.devins.ui.compose.editor.ModelConfigDialog(
                currentConfig = currentModelConfig ?: ModelConfig(),
                onDismiss = { showModelConfigDialog = false },
                onSave = { newConfig ->
                    currentModelConfig = newConfig
                    if (newConfig.isValid()) {
                        try {
                            llmService = KoogLLMService.create(newConfig)
                            scope.launch {
                                try {
                                    repository.saveConfig(newConfig, setAsDefault = true)
                                    allModelConfigs = repository.getAllConfigs()
                                    println("✅ 模型配置已保存")
                                } catch (e: Exception) {
                                    println("⚠️ 保存配置失败: ${e.message}")
                                }
                            }
                        } catch (e: Exception) {
                            println("❌ 配置 LLM 服务失败: ${e.message}")
                            llmService = null
                        }
                    }
                    showModelConfigDialog = false
                }
            )
        }
        
        // Debug Dialog
        if (showDebugDialog) {
            DebugDialog(
                compilerOutput = compilerOutput,
                onDismiss = { showDebugDialog = false }
            )
        }
        
        // 配置警告弹窗
        if (showConfigWarning) {
            AlertDialog(
                onDismissRequest = { showConfigWarning = false },
                title = {
                    Text("⚠️ 未配置 LLM 模型")
                },
                text = {
                    Column {
                        Text("请先配置 LLM 模型才能使用 AI 功能。")
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "点击右下角的模型选择器进行配置。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showConfigWarning = false }) {
                        Text("知道了")
                    }
                }
            )
        }
        
        // 错误提示弹窗
        if (showErrorDialog) {
            AlertDialog(
                onDismissRequest = { showErrorDialog = false },
                title = {
                    Text("❌ LLM API 错误")
                },
                text = {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        Text(
                            "调用 LLM API 时发生错误：",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // 错误信息卡片
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            )
                        ) {
                            SelectionContainer {
                                Text(
                                    text = errorMessage,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                    ),
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // 常见问题提示
                        Text(
                            "常见解决方法：",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "• 检查 API Key 是否正确\n" +
                            "• 确认账户余额充足\n" +
                            "• 检查网络连接\n" +
                            "• 验证模型名称是否正确",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showErrorDialog = false }) {
                        Text("关闭")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showErrorDialog = false
                            // 打开模型配置
                        }
                    ) {
                        Text("重新配置")
                    }
                }
            )
        }
}

