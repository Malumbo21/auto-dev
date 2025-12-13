package cc.unitmesh.devins.ui.compose.launch

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cc.unitmesh.devins.ui.compose.theme.AutoDevAnimation
import cc.unitmesh.devins.ui.compose.theme.AutoDevColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Xuiper launch screen animation - Cool & Dynamic version.
 *
 * Intent:
 * - X draws with energy burst effect
 * - Arrow shoots forward with speed trails and glow
 * - Pulsing energy waves
 * - Use Xuiper palette via tokens (no hardcoded UI colors).
 * - Respect reduced motion.
 */
@Composable
fun XiuperLaunchScreen(
    onFinished: () -> Unit,
    reducedMotion: Boolean = false
) {
    // X stroke draw progress (each stroke)
    val xStroke1 = remember { Animatable(0f) }
    val xStroke2 = remember { Animatable(0f) }
    // Energy burst when X completes
    val energyBurst = remember { Animatable(0f) }
    // Arrow shoots forward
    val arrowShoot = remember { Animatable(0f) }
    // Speed trails intensity
    val trailIntensity = remember { Animatable(0f) }
    // Glow pulse
    val glowPulse = remember { Animatable(0f) }
    // Final fade out
    val fade = remember { Animatable(1f) }

    LaunchedEffect(reducedMotion) {
        xStroke1.snapTo(0f)
        xStroke2.snapTo(0f)
        energyBurst.snapTo(0f)
        arrowShoot.snapTo(0f)
        trailIntensity.snapTo(0f)
        glowPulse.snapTo(0f)
        fade.snapTo(1f)

        if (reducedMotion) {
            xStroke1.snapTo(1f)
            xStroke2.snapTo(1f)
            arrowShoot.snapTo(1f)
            delay(400)
            fade.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 300, easing = AutoDevAnimation.EaseXiu)
            )
            onFinished()
            return@LaunchedEffect
        }

        // Start glow pulse loop
        launch {
            while (true) {
                glowPulse.animateTo(1f, tween(800, easing = FastOutSlowInEasing))
                glowPulse.animateTo(0f, tween(800, easing = FastOutSlowInEasing))
            }
        }

        delay(150)

        // X stroke 1 - fast slash
        xStroke1.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 280, easing = AutoDevAnimation.EaseXiu)
        )

        // X stroke 2 - fast slash with slight overlap
        delay(80)
        xStroke2.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 280, easing = AutoDevAnimation.EaseXiu)
        )

        // Energy burst when X completes
        launch {
            energyBurst.animateTo(1f, tween(400, easing = FastOutSlowInEasing))
            energyBurst.animateTo(0f, tween(300, easing = LinearEasing))
        }

        delay(150)

        // Trail intensity ramps up
        launch {
            trailIntensity.animateTo(1f, tween(200))
            delay(600)
            trailIntensity.animateTo(0f, tween(400))
        }

        // Arrow shoots forward - fast acceleration
        arrowShoot.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 700, easing = AutoDevAnimation.EaseXiu)
        )

        delay(500)

        // Fade out
        fade.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = 350, easing = AutoDevAnimation.EaseXiu)
        )
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        AutoDevColors.Xuiper.bg2,
                        AutoDevColors.Xuiper.bg
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Canvas(modifier = Modifier.size(240.dp)) {
                val s1 = xStroke1.value
                val s2 = xStroke2.value
                val burst = energyBurst.value
                val arrow = arrowShoot.value
                val trails = trailIntensity.value
                val glow = glowPulse.value
                val a = fade.value

                val center = Offset(size.width / 2f, size.height / 2f)
                val markWidth = size.minDimension * 0.65f
                val half = markWidth / 2f
                val stroke = max(5f, size.minDimension * 0.04f)

                fun lerp(from: Float, to: Float, t: Float): Float = from + (to - from) * t
                fun lerpOffset(from: Offset, to: Offset, t: Float): Offset =
                    Offset(lerp(from.x, to.x, t), lerp(from.y, to.y, t))

                // Eased progress for smoother feel
                fun easeOut(t: Float): Float = 1f - (1f - t).pow(3)

                val xVisible = min(1f, s1 + s2)
                val hot = AutoDevColors.Xuiper.markHot.copy(alpha = a)
                val cool = AutoDevColors.Xuiper.markCool.copy(alpha = a)

                // Pulsing background glow
                val pulseRadius = size.minDimension * (0.4f + glow * 0.15f)
                val pulseAlpha = a * (0.2f + glow * 0.15f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            AutoDevColors.Xuiper.markHot.copy(alpha = pulseAlpha * 0.6f),
                            AutoDevColors.Xuiper.markCool.copy(alpha = pulseAlpha * 0.4f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = pulseRadius
                    ),
                    radius = pulseRadius,
                    center = center
                )

                // Energy burst ring when X completes
                if (burst > 0f) {
                    val burstRadius = size.minDimension * 0.15f + burst * size.minDimension * 0.4f
                    val burstAlpha = a * (1f - burst) * 0.8f
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.Transparent,
                                AutoDevColors.Xuiper.markHot.copy(alpha = burstAlpha),
                                AutoDevColors.Xuiper.markCool.copy(alpha = burstAlpha * 0.5f),
                                Color.Transparent
                            ),
                            center = center,
                            radius = burstRadius
                        ),
                        radius = burstRadius,
                        center = center
                    )
                }

                // X position
                val xCenterX = center.x - half * 0.25f
                val xHalfW = half * 0.45f
                val xHalfH = half * 0.55f

                // X stroke 1 (top-left to bottom-right) with glow
                val x1Start = Offset(xCenterX - xHalfW, center.y - xHalfH)
                val x1End = Offset(xCenterX + xHalfW, center.y + xHalfH)
                val x1Current = lerpOffset(x1Start, x1End, easeOut(s1))

                // Glow behind stroke 1
                if (s1 > 0.1f) {
                    drawLine(
                        color = AutoDevColors.Xuiper.markHot.copy(alpha = a * 0.4f),
                        start = x1Start,
                        end = x1Current,
                        strokeWidth = stroke * 3f,
                        cap = StrokeCap.Round
                    )
                }
                drawLine(
                    brush = Brush.linearGradient(listOf(cool, hot)),
                    start = x1Start,
                    end = x1Current,
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )

                // X stroke 2 (bottom-left to top-right) with glow
                val x2Start = Offset(xCenterX - xHalfW, center.y + xHalfH)
                val x2End = Offset(xCenterX + xHalfW, center.y - xHalfH)
                val x2Current = lerpOffset(x2Start, x2End, easeOut(s2))

                // Glow behind stroke 2
                if (s2 > 0.1f) {
                    drawLine(
                        color = AutoDevColors.Xuiper.markCool.copy(alpha = a * 0.4f),
                        start = x2Start,
                        end = x2Current,
                        strokeWidth = stroke * 3f,
                        cap = StrokeCap.Round
                    )
                }
                drawLine(
                    brush = Brush.linearGradient(listOf(hot, cool)),
                    start = x2Start,
                    end = x2Current,
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )

                // Arrow with speed effect
                if (arrow > 0f || xVisible > 0.8f) {
                    val arrowStartX = xCenterX + xHalfW + stroke * 3
                    val arrowY = center.y

                    // Speed trails (multiple lines behind arrow)
                    if (trails > 0f) {
                        val trailCount = 5
                        for (i in 0 until trailCount) {
                            val t = i.toFloat() / (trailCount - 1)
                            val offsetY = lerp(-half * 0.25f, half * 0.25f, t)
                            val trailLen = lerp(markWidth * 0.15f, markWidth * 0.35f, 1f - t)
                            val trailAlpha = a * trails * lerp(0.5f, 0.2f, t)

                            val trailStart = Offset(
                                arrowStartX - trailLen - arrow * markWidth * 0.1f,
                                arrowY + offsetY
                            )
                            val trailEnd = Offset(arrowStartX - stroke, arrowY + offsetY)

                            drawLine(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        AutoDevColors.Xuiper.markHot.copy(alpha = trailAlpha)
                                    ),
                                    startX = trailStart.x,
                                    endX = trailEnd.x
                                ),
                                start = trailStart,
                                end = trailEnd,
                                strokeWidth = max(2f, stroke * lerp(0.3f, 0.15f, t)),
                                cap = StrokeCap.Round
                            )
                        }
                    }

                    // Main arrow line - extends beyond canvas
                    val arrowEndX = lerp(arrowStartX, size.width * 1.2f, easeOut(arrow))
                    val arrowStart = Offset(arrowStartX, arrowY)
                    val arrowEnd = Offset(arrowEndX, arrowY)

                    // Arrow glow
                    drawLine(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                AutoDevColors.Xuiper.markHot.copy(alpha = a * 0.5f),
                                AutoDevColors.Xuiper.markCool.copy(alpha = a * 0.3f),
                                Color.Transparent
                            ),
                            startX = arrowStart.x,
                            endX = arrowEnd.x
                        ),
                        start = arrowStart,
                        end = arrowEnd,
                        strokeWidth = stroke * 2.5f,
                        cap = StrokeCap.Round
                    )

                    // Arrow main line
                    drawLine(
                        brush = Brush.horizontalGradient(
                            colors = listOf(hot, cool, cool.copy(alpha = 0f)),
                            startX = arrowStart.x,
                            endX = arrowEnd.x
                        ),
                        start = arrowStart,
                        end = arrowEnd,
                        strokeWidth = stroke,
                        cap = StrokeCap.Round
                    )

                    // Arrow head - only when visible on canvas
                    if (arrow > 0.2f && arrowEndX < size.width - stroke) {
                        val headProgress = min(1f, (arrow - 0.2f) / 0.3f)
                        val headLen = stroke * 3f
                        val tip = arrowEnd
                        val up = Offset(tip.x - headLen, tip.y - headLen * 0.7f)
                        val down = Offset(tip.x - headLen, tip.y + headLen * 0.7f)

                        // Head glow
                        drawLine(
                            color = AutoDevColors.Xuiper.markCool.copy(alpha = a * 0.4f * headProgress),
                            start = up,
                            end = tip,
                            strokeWidth = stroke * 2f,
                            cap = StrokeCap.Round
                        )
                        drawLine(
                            color = AutoDevColors.Xuiper.markCool.copy(alpha = a * 0.4f * headProgress),
                            start = down,
                            end = tip,
                            strokeWidth = stroke * 2f,
                            cap = StrokeCap.Round
                        )

                        // Head main lines
                        drawLine(
                            color = cool.copy(alpha = cool.alpha * headProgress),
                            start = up,
                            end = tip,
                            strokeWidth = stroke * 0.9f,
                            cap = StrokeCap.Round
                        )
                        drawLine(
                            color = cool.copy(alpha = cool.alpha * headProgress),
                            start = down,
                            end = tip,
                            strokeWidth = stroke * 0.9f,
                            cap = StrokeCap.Round
                        )
                    }
                }

                // Spark particles when burst happens
                if (burst > 0f) {
                    val sparkCount = 8
                    for (i in 0 until sparkCount) {
                        val angle = (i.toFloat() / sparkCount) * 2f * 3.14159f
                        val dist = burst * size.minDimension * 0.35f
                        val sparkX = center.x + kotlin.math.cos(angle).toFloat() * dist
                        val sparkY = center.y + sin(angle).toFloat() * dist
                        val sparkAlpha = a * (1f - burst) * 0.8f
                        val sparkRadius = stroke * 0.4f * (1f - burst * 0.5f)

                        drawCircle(
                            color = if (i % 2 == 0)
                                AutoDevColors.Xuiper.markHot.copy(alpha = sparkAlpha)
                            else
                                AutoDevColors.Xuiper.markCool.copy(alpha = sparkAlpha),
                            radius = sparkRadius,
                            center = Offset(sparkX, sparkY)
                        )
                    }
                }
            }

            val textAlpha = fade.value * min(1f, xStroke1.value + xStroke2.value)

            Text(
                text = "Xuiper",
                fontSize = 50.sp,
                fontWeight = FontWeight.Bold,
                color = AutoDevColors.Xuiper.text.copy(alpha = textAlpha)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "X => Xiuper open, super build.",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 3.sp,
                color = AutoDevColors.Xuiper.textSecondary.copy(alpha = textAlpha)
            )
        }
    }
}

