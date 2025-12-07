package cc.unitmesh.devins.ui.compose.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * AutoDev Animation System - Xiuper 动效系统
 *
 * 基于「唯快不破」的设计理念，定义三套专用动效曲线：
 * 1. EaseXiu - 极速响应，瞬间到达
 * 2. EaseStream - 线性流，AI 代码生成的稳定输出
 * 3. SpringTactile - 武侠弹簧，微交互的触觉反馈
 *
 * 设计参考：
 * - Linear 的极简主义交互
 * - Raycast 的键盘优先工作流
 * - 东方赛博朋克与武侠美学
 */
object AutoDevAnimation {
    // ========================================================================
    // 动效曲线 (Easing Curves)
    // ========================================================================

    /**
     * Xiu 曲线 - 极速响应
     *
     * cubic-bezier(0.16, 1, 0.3, 1)
     * 特性：极速启动，瞬间到达，几乎没有减速过程
     * 用途：模态框弹出、下拉菜单展开、Tab 切换
     * 隐喻：光剑出鞘、瞬间移动的物理质感
     */
    val EaseXiu = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

    /**
     * 线性流 - AI 代码生成
     *
     * 特性：匀速，无加速减速
     * 用途：AI 代码生成的打字机效果
     * 原理：变与不确定的生成速度会让人感到卡顿，
     *       强制的线性匀速反而让用户感到机器的稳定与高效
     */
    val EaseStream = LinearEasing

    /**
     * 标准缓动 - 通用过渡
     *
     * cubic-bezier(0.4, 0, 0.2, 1)
     * Material Design 标准缓动
     */
    val EaseStandard = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

    // ========================================================================
    // 弹簧动效 (Spring Animation)
    // ========================================================================

    /**
     * 武侠弹簧 - 微交互反馈
     *
     * 特性：轻微的过冲（Overshoot）后迅速回弹归位
     * 用途：微交互反馈，如复制成功、代码采纳、点赞
     * 隐喻：类似机械键盘敲击的触觉反馈（Haptic Feedback）
     */
    object SpringTactile {
        const val DAMPING_RATIO = 0.6f
        const val STIFFNESS = 180f

        fun <T> spec() = spring<T>(
            dampingRatio = DAMPING_RATIO,
            stiffness = STIFFNESS
        )
    }

    /**
     * 轻柔弹簧 - 平滑过渡
     */
    object SpringGentle {
        const val DAMPING_RATIO = Spring.DampingRatioMediumBouncy
        const val STIFFNESS = Spring.StiffnessLow

        fun <T> spec() = spring<T>(
            dampingRatio = DAMPING_RATIO,
            stiffness = STIFFNESS
        )
    }

    // ========================================================================
    // 时长常量 (Duration Constants)
    // ========================================================================

    object Duration {
        /** 瞬间反馈 - 用于微交互 */
        const val INSTANT = 100

        /** 快速过渡 - 用于大多数 UI 过渡 */
        const val FAST = 150

        /** 标准过渡 - 用于需要用户注意的变化 */
        const val NORMAL = 250

        /** 强调动画 - 用于重要的状态变化 */
        const val SLOW = 400

        /** Launch 动画专用 - 基于 Xiuper 设计系统 */
        object Launch {
            const val INITIAL_DELAY = 100   // 初始延迟，营造"蓄力"感
            const val GLOW_EXPAND = 400     // 光晕扩散（先于 Logo）
            const val LOGO_SCALE = 500      // Logo 放大
            const val TEXT_SLIDE = 300      // 文字滑入
            const val GLOW_PULSE = 600      // 光晕呼吸停留（更长的品牌展示）
            const val FADE_OUT = 300        // 淡出（更优雅的过渡）
            const val TOTAL = 2200          // 总时长（让用户看清品牌）
        }
    }

    // ========================================================================
    // 预设 AnimationSpec (Preset Animation Specs)
    // ========================================================================

    /** Xiu 快速动画 - 用于模态框、菜单等 */
    fun <T> tweenXiu(durationMillis: Int = Duration.FAST) = tween<T>(
        durationMillis = durationMillis,
        easing = EaseXiu
    )

    /** 线性流动画 - 用于流式输出 */
    fun <T> tweenStream(durationMillis: Int = Duration.NORMAL) = tween<T>(
        durationMillis = durationMillis,
        easing = EaseStream
    )

    /** 标准动画 */
    fun <T> tweenStandard(durationMillis: Int = Duration.NORMAL) = tween<T>(
        durationMillis = durationMillis,
        easing = EaseStandard
    )
}

