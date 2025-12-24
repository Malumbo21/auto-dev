package cc.unitmesh.devins.ui.state

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

/**
 * 全局 UI 状态管理器
 *
 * 管理应用级别的 UI 状态，避免状态在多个组件层级之间传递的复杂性。
 * 使用 StateFlow 确保状态变化能自动触发 UI 更新。
 */
object UIStateManager {
    /**
     * Font scale for main content (chat, markdown, etc.).
     * Applied via `AutoDevTheme` by overriding `LocalDensity.fontScale`.
     */
    const val CONTENT_FONT_SCALE_MIN: Float = 0.85f
    const val CONTENT_FONT_SCALE_MAX: Float = 1.25f
    const val CONTENT_FONT_SCALE_STEP: Float = 0.05f
    const val CONTENT_FONT_SCALE_DEFAULT: Float = 1.0f

    private val _contentFontScale = MutableStateFlow(CONTENT_FONT_SCALE_DEFAULT)
    val contentFontScale: StateFlow<Float> = _contentFontScale.asStateFlow()

    // TreeView 显示状态
    private val _isTreeViewVisible = MutableStateFlow(false)
    val isTreeViewVisible: StateFlow<Boolean> = _isTreeViewVisible.asStateFlow()

    // Session Sidebar 显示状态
    private val _isSessionSidebarVisible = MutableStateFlow(true)
    val isSessionSidebarVisible: StateFlow<Boolean> = _isSessionSidebarVisible.asStateFlow()

    // 当前工作空间路径
    private val _workspacePath = MutableStateFlow("")
    val workspacePath: StateFlow<String> = _workspacePath.asStateFlow()

    // 是否有历史记录
    private val _hasHistory = MutableStateFlow(false)
    val hasHistory: StateFlow<Boolean> = _hasHistory.asStateFlow()

    /**
     * 切换 TreeView 显示状态
     */
    fun toggleTreeView() {
        _isTreeViewVisible.value = !_isTreeViewVisible.value
        println("🔄 [UIStateManager] TreeView toggled to: ${_isTreeViewVisible.value}")
    }

    /**
     * 设置 TreeView 显示状态
     */
    fun setTreeViewVisible(visible: Boolean) {
        if (_isTreeViewVisible.value != visible) {
            _isTreeViewVisible.value = visible
            println("📝 [UIStateManager] TreeView set to: $visible")
        }
    }

    /**
     * 切换 Session Sidebar 显示状态
     */
    fun toggleSessionSidebar() {
        _isSessionSidebarVisible.value = !_isSessionSidebarVisible.value
        println("🔄 [UIStateManager] Session Sidebar toggled to: ${_isSessionSidebarVisible.value}")
    }

    /**
     * 设置 Session Sidebar 显示状态
     */
    fun setSessionSidebarVisible(visible: Boolean) {
        if (_isSessionSidebarVisible.value != visible) {
            _isSessionSidebarVisible.value = visible
            println("📝 [UIStateManager] Session Sidebar set to: $visible")
        }
    }

    /**
     * 设置工作空间路径
     */
    fun setWorkspacePath(path: String) {
        if (_workspacePath.value != path) {
            _workspacePath.value = path
            println("📁 [UIStateManager] Workspace path set to: $path")
        }
    }

    /**
     * 设置历史记录状态
     */
    fun setHasHistory(hasHistory: Boolean) {
        if (_hasHistory.value != hasHistory) {
            _hasHistory.value = hasHistory
        }
    }

    /**
     * Set content font scale (clamped).
     */
    fun setContentFontScale(scale: Float) {
        val clamped = scale.coerceIn(CONTENT_FONT_SCALE_MIN, CONTENT_FONT_SCALE_MAX)
        // Keep two decimals for stable UI display and predictable stepping
        val normalized = (clamped * 100f).roundToInt() / 100f
        if (_contentFontScale.value != normalized) {
            _contentFontScale.value = normalized
            println("📝 [UIStateManager] Content font scale set to: ${_contentFontScale.value}")
        }
    }

    fun increaseContentFontScale() = setContentFontScale(_contentFontScale.value + CONTENT_FONT_SCALE_STEP)

    fun decreaseContentFontScale() = setContentFontScale(_contentFontScale.value - CONTENT_FONT_SCALE_STEP)

    fun resetContentFontScale() = setContentFontScale(CONTENT_FONT_SCALE_DEFAULT)

    /**
     * 重置所有状态到默认值
     */
    fun reset() {
        _isTreeViewVisible.value = false
        _isSessionSidebarVisible.value = true
        _workspacePath.value = ""
        _hasHistory.value = false
        _contentFontScale.value = CONTENT_FONT_SCALE_DEFAULT
        println("🔄 [UIStateManager] All states reset to default")
    }
}
