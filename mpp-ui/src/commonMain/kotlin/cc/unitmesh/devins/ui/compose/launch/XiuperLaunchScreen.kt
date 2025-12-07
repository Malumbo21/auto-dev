package cc.unitmesh.devins.ui.compose.launch

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.unitmesh.devins.ui.compose.theme.AutoDevAnimation
import cc.unitmesh.devins.ui.compose.theme.AutoDevColors
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI

/**
 * Xiuper Launch Screen - 启动动画
 *
 * 展示 Xiuper「咻」的极速美学品牌动画
 * 基于「霓虹暗夜」与「气韵流动」的设计理念
 *
 * 动画时序（优化后）：
 * [0-100ms]    蓄力延迟 - 静如处子
 * [100-500ms]  光晕扩散 - 电光青能量聚集
 * [500-1000ms] Logo 放大 - 动如脱兔 (ease-xiu)
 * [1000-1300ms] "Xiuper Fast" 文字滑入
 * [1300-1900ms] 品牌展示 + 光晕呼吸效果
 * [1900-2200ms] 整体淡出，进入主界面
 *
 * @param onFinished 动画完成后的回调
 * @param reducedMotion 是否启用简化动画模式
 */
@Composable
fun XiuperLaunchScreen(
    onFinished: () -> Unit,
    reducedMotion: Boolean = false
) {
    // 动画阶段：0=初始蓄力, 1=光晕扩散, 2=Logo放大, 3=文字滑入, 4=停留呼吸, 5=淡出
    var phase by remember { mutableStateOf(0) }

    // 如果启用简化动画，跳过动画直接完成
    LaunchedEffect(reducedMotion) {
        if (reducedMotion) {
            delay(300) // 短暂显示品牌
            onFinished()
            return@LaunchedEffect
        }

        // 正常动画序列 - 总时长约 2.2s
        delay(AutoDevAnimation.Duration.Launch.INITIAL_DELAY.toLong())
        phase = 1  // 光晕开始扩散
        delay(AutoDevAnimation.Duration.Launch.GLOW_EXPAND.toLong())
        phase = 2  // Logo 放大
        delay(AutoDevAnimation.Duration.Launch.LOGO_SCALE.toLong())
        phase = 3  // 文字滑入
        delay(AutoDevAnimation.Duration.Launch.TEXT_SLIDE.toLong())
        phase = 4  // 停留呼吸
        delay(AutoDevAnimation.Duration.Launch.GLOW_PULSE.toLong())
        phase = 5  // 淡出
        delay(AutoDevAnimation.Duration.Launch.FADE_OUT.toLong())
        onFinished()
    }

    // 光晕扩散动画（先于 Logo）
    val glowExpand by animateFloatAsState(
        targetValue = if (phase >= 1) 1f else 0f,
        animationSpec = tween(
            durationMillis = AutoDevAnimation.Duration.Launch.GLOW_EXPAND,
            easing = AutoDevAnimation.EaseXiu
        ),
        label = "glowExpand"
    )

    // Logo 缩放动画 - 从 0.6 开始，更优雅
    val logoScale by animateFloatAsState(
        targetValue = if (phase >= 2) 1f else 0.6f,
        animationSpec = tween(
            durationMillis = AutoDevAnimation.Duration.Launch.LOGO_SCALE,
            easing = AutoDevAnimation.EaseXiu
        ),
        label = "logoScale"
    )

    // Logo 透明度
    val logoAlpha by animateFloatAsState(
        targetValue = when (phase) {
            0, 1 -> 0f
            5 -> 0f
            else -> 1f
        },
        animationSpec = tween(
            durationMillis = if (phase == 5) AutoDevAnimation.Duration.Launch.FADE_OUT
            else AutoDevAnimation.Duration.Launch.LOGO_SCALE,
            easing = AutoDevAnimation.EaseXiu
        ),
        label = "logoAlpha"
    )

    // 文字偏移动画 - 从下方 30dp 滑入
    val textOffset by animateFloatAsState(
        targetValue = if (phase >= 3) 0f else 30f,
        animationSpec = tween(
            durationMillis = AutoDevAnimation.Duration.Launch.TEXT_SLIDE,
            easing = AutoDevAnimation.EaseXiu
        ),
        label = "textOffset"
    )

    // 文字透明度
    val textAlpha by animateFloatAsState(
        targetValue = when (phase) {
            in 0..2 -> 0f
            5 -> 0f
            else -> 1f
        },
        animationSpec = tween(
            durationMillis = if (phase == 5) AutoDevAnimation.Duration.Launch.FADE_OUT
            else AutoDevAnimation.Duration.Launch.TEXT_SLIDE,
            easing = AutoDevAnimation.EaseXiu
        ),
        label = "textAlpha"
    )

    // 光晕脉冲（呼吸效果）- 更缓慢的呼吸
    var glowPulse by remember { mutableStateOf(0.5f) }
    LaunchedEffect(phase) {
        if (phase >= 1 && phase < 5) {
            var time = 0f
            while (phase < 5) {
                time += 0.03f  // 更慢的呼吸节奏
                glowPulse = (sin(time * 2) * 0.25f + 0.75f)
                delay(16) // ~60fps
            }
        }
    }

    // AI 光环旋转角度
    var aiRingAngle by remember { mutableStateOf(0f) }
    LaunchedEffect(phase) {
        if (phase >= 1 && phase < 5) {
            while (phase < 5) {
                aiRingAngle += 1.5f
                if (aiRingAngle >= 360f) aiRingAngle = 0f
                delay(16)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AutoDevColors.Void.bg),
        contentAlignment = Alignment.Center
    ) {
        // 外层 AI 品红光环（旋转的能量环）
        Canvas(
            modifier = Modifier
                .size(350.dp)
                .alpha(glowExpand * 0.6f * glowPulse)
        ) {
            val center = Offset(size.width / 2, size.height / 2)
            val outerRadius = size.minDimension / 2

            // AI 品红色弧形光环（部分圆弧，产生旋转感）
            for (i in 0..2) {
                val startAngle = aiRingAngle + i * 120f
                drawArc(
                    color = AutoDevColors.Energy.ai.copy(alpha = 0.3f - i * 0.08f),
                    startAngle = startAngle,
                    sweepAngle = 60f,
                    useCenter = false,
                    topLeft = Offset(center.x - outerRadius, center.y - outerRadius),
                    size = androidx.compose.ui.geometry.Size(outerRadius * 2, outerRadius * 2),
                    style = Stroke(width = 2f + i)
                )
            }
        }

        // 中层电光青光晕（主光晕）
        Canvas(
            modifier = Modifier
                .size(280.dp)
                .alpha(glowExpand * glowPulse)
        ) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = size.minDimension / 2

            // 电光青径向渐变光晕
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        AutoDevColors.Energy.xiuDim.copy(alpha = 0.8f),
                        AutoDevColors.Energy.xiuDim.copy(alpha = 0.3f),
                        AutoDevColors.Energy.xiuDim.copy(alpha = 0.1f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = radius
                ),
                radius = radius,
                center = center
            )

            // 内层聚焦光环（xiu-focus-ring 效果）
            drawCircle(
                color = AutoDevColors.Energy.xiu.copy(alpha = 0.4f * glowPulse),
                radius = radius * 0.4f,
                center = center,
                style = Stroke(width = 2f)
            )
        }

        // 速度线效果（向右的动势暗示）
        if (phase >= 2 && phase < 5) {
            Canvas(
                modifier = Modifier
                    .size(400.dp)
                    .alpha(logoAlpha * 0.3f)
            ) {
                val center = Offset(size.width / 2, size.height / 2)

                // 绘制多条速度线
                for (i in 0..5) {
                    val angle = -15f + i * 6f  // 略微向右倾斜的角度
                    val lineLength = 60f + i * 20f
                    val startX = center.x + 80f
                    val startY = center.y - 30f + i * 12f

                    val radians = angle * PI / 180.0
                    val endX = startX + (lineLength * cos(radians)).toFloat()
                    val endY = startY + (lineLength * sin(radians)).toFloat()

                    drawLine(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                AutoDevColors.Energy.xiu.copy(alpha = 0.6f - i * 0.08f),
                                Color.Transparent
                            ),
                            startX = startX,
                            endX = endX
                        ),
                        start = Offset(startX, startY),
                        end = Offset(endX, endY),
                        strokeWidth = 1.5f - i * 0.2f
                    )
                }
            }
        }

        // Logo 和文字
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .scale(logoScale)
                .alpha(logoAlpha)
        ) {
            Text(
                text = "Xiuper",
                fontSize = 52.sp,
                fontWeight = FontWeight.Bold,
                color = AutoDevColors.Energy.xiu
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "AutoDev code, Xiuper Fast",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 4.sp,  // 增加字间距，更有科技感
                color = AutoDevColors.Text.secondary,
                modifier = Modifier
                    .offset(y = textOffset.dp)
                    .alpha(textAlpha)
            )
        }
    }
}

