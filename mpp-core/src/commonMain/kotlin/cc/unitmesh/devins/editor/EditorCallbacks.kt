package cc.unitmesh.devins.editor

/**
 * Represents a file in the context.
 * Used for passing file context information with submissions.
 */
data class FileContext(
    val name: String,
    val path: String,
    val relativePath: String = name,
    val isDirectory: Boolean = false
)

/**
 * 编辑器回调接口
 *
 * 定义了编辑器的各种回调方法，用于响应编辑器事件
 * 所有方法都有默认空实现，子类只需要重写感兴趣的方法
 */
interface EditorCallbacks {
    /**
     * 当用户提交内容时调用（例如按下 Cmd+Enter）
     */
    fun onSubmit(text: String) {}

    /**
     * 当用户提交内容时调用，包含文件上下文
     * 默认实现调用不带文件上下文的 onSubmit
     */
    fun onSubmit(text: String, files: List<FileContext>) {
        onSubmit(text)
    }
    
    /**
     * 当用户提交包含多模态内容（如图片）时调用
     * @param text 用户输入的文本
     * @param files 关联的文件上下文
     * @param imageAnalysis 图片分析结果（来自视觉模型）
     */
    fun onSubmitWithMultimodal(text: String, files: List<FileContext>, imageAnalysis: String?) {
        // 默认将图片分析结果追加到文本后发送
        val fullText = if (imageAnalysis.isNullOrBlank()) {
            text
        } else {
            "$text\n\n[Image Analysis]\n$imageAnalysis"
        }
        onSubmit(fullText, files)
    }
    
    /**
     * 当文本内容变化时调用
     */
    fun onTextChanged(text: String) {}
    
    /**
     * 当光标位置移动时调用
     */
    fun onCursorMoved(position: Int) {}
    
    /**
     * 当选中区域变化时调用
     */
    fun onSelectionChanged(start: Int, end: Int) {}
    
    /**
     * 当编辑器获得焦点时调用
     */
    fun onFocusGained() {}
    
    /**
     * 当编辑器失去焦点时调用
     */
    fun onFocusLost() {}
}

