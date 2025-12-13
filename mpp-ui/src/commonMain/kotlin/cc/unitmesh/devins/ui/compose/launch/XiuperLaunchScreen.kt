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
 * Xuiper launch screen animation - Rocket version.
 *
 * Intent:
 * - Rocket flies from left, passes through X, shoots to the right
 * - X lights up when rocket passes through
 * - Fire trail and energy effects
 * - Use Xuiper palette via tokens (no hardcoded UI colors).
 * - Respect reduced motion.
 */
@Composable
fun XiuperLaunchScreen(
    onFinished: () -> Unit,
    reducedMotion: Boolean = false
) {
    // Rocket position (0 = off-screen left, 0.5 = at X center, 1 = off-screen right)
    val rocketProgress = remember { Animatable(0f) }
    // X glow intensity (lights up when rocket passes)
    val xGlow = remember { Animatable(0.3f) }
    // Fire trail intensity
    val fireTrail = remember { Animatable(0f) }
    // Energy burst when rocket passes X
    val energyBurst = remember { Animatable(0f) }
    // Final fade out
    val fade = remember { Animatable(1f) }

    LaunchedEffect(reducedMotion) {
        rocketProgress.snapTo(0f)
        xGlow.snapTo(0.3f)
        fireTrail.snapTo(0f)
        energyBurst.snapTo(0f)
        fade.snapTo(1f)

        if (reducedMotion) {
            rocketProgress.snapTo(1f)
            xGlow.snapTo(1f)
            delay(400)
            fade.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 300, easing = AutoDevAnimation.EaseXiu)
            )
            onFinished()
            return@LaunchedEffect
        }

        delay(200)

        // Fire trail starts
        launch {
            fireTrail.animateTo(1f, tween(150))
        }

        // Rocket flies across - accelerates then maintains speed
        launch {
            rocketProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing)
            )
        }

        // X glows up as rocket approaches and passes
        launch {
            delay(300)
            xGlow.animateTo(1f, tween(400, easing = FastOutSlowInEasing))
        }

        // Energy burst when rocket passes through X (around 40-60% progress)
        launch {
            delay(450)
            energyBurst.animateTo(1f, tween(250, easing = FastOutSlowInEasing))
            energyBurst.animateTo(0f, tween(350, easing = LinearEasing))
        }

        // Wait for rocket to exit
        delay(1400)

        // Fire trail fades
        fireTrail.animateTo(0f, tween(200))

        delay(300)

        // Fade out
        fade.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = 400, easing = AutoDevAnimation.EaseXiu)
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
                val rocket = rocketProgress.value
                val xGlowVal = xGlow.value
                val fire = fireTrail.value
                val burst = energyBurst.value
                val a = fade.value

                val center = Offset(size.width / 2f, size.height / 2f)
                val markWidth = size.minDimension * 0.55f
                val half = markWidth / 2f
                val stroke = max(5f, size.minDimension * 0.04f)

                fun lerp(from: Float, to: Float, t: Float): Float = from + (to - from) * t

                val hot = AutoDevColors.Xuiper.markHot.copy(alpha = a)
                val cool = AutoDevColors.Xuiper.markCool.copy(alpha = a)

                // X position (centered)
                val xCenterX = center.x
                val xHalfW = half * 0.5f
                val xHalfH = half * 0.6f

                // X glow background (intensifies when rocket passes)
                val glowRadius = size.minDimension * (0.3f + xGlowVal * 0.2f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            AutoDevColors.Xuiper.markHot.copy(alpha = a * xGlowVal * 0.5f),
                            AutoDevColors.Xuiper.markCool.copy(alpha = a * xGlowVal * 0.3f),
                            Color.Transparent
                        ),
                        center = Offset(xCenterX, center.y),
                        radius = glowRadius
                    ),
                    radius = glowRadius,
                    center = Offset(xCenterX, center.y)
                )

                // Energy burst ring when rocket passes through X
                if (burst > 0f) {
                    val burstRadius = size.minDimension * 0.1f + burst * size.minDimension * 0.5f
                    val burstAlpha = a * (1f - burst) * 0.9f
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.Transparent,
                                AutoDevColors.Xuiper.markHot.copy(alpha = burstAlpha),
                                AutoDevColors.Xuiper.markCool.copy(alpha = burstAlpha * 0.6f),
                                Color.Transparent
                            ),
                            center = Offset(xCenterX, center.y),
                            radius = burstRadius
                        ),
                        radius = burstRadius,
                        center = Offset(xCenterX, center.y)
                    )
                }

                // X strokes with dynamic glow
                val x1Start = Offset(xCenterX - xHalfW, center.y - xHalfH)
                val x1End = Offset(xCenterX + xHalfW, center.y + xHalfH)
                val x2Start = Offset(xCenterX - xHalfW, center.y + xHalfH)
                val x2End = Offset(xCenterX + xHalfW, center.y - xHalfH)

                // X glow (behind strokes)
                drawLine(
                    color = AutoDevColors.Xuiper.markHot.copy(alpha = a * xGlowVal * 0.5f),
                    start = x1Start,
                    end = x1End,
                    strokeWidth = stroke * 4f,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = AutoDevColors.Xuiper.markCool.copy(alpha = a * xGlowVal * 0.5f),
                    start = x2Start,
                    end = x2End,
                    strokeWidth = stroke * 4f,
                    cap = StrokeCap.Round
                )

                // X main strokes
                drawLine(
                    brush = Brush.linearGradient(listOf(cool, hot)),
                    start = x1Start,
                    end = x1End,
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
                drawLine(
                    brush = Brush.linearGradient(listOf(hot, cool)),
                    start = x2Start,
                    end = x2End,
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )

                // Rocket position: from left edge (-20%) to right edge (120%)
                val rocketX = lerp(-size.width * 0.2f, size.width * 1.2f, rocket)
                val rocketY = center.y
                val rocketLen = stroke * 4f
                val rocketWidth = stroke * 1.5f

                // Only draw rocket and trail when visible
                if (rocket > 0f && rocket < 1f) {
                    // Fire trail behind rocket (multiple layers)
                    if (fire > 0f) {
                        val trailLen = size.width * 0.4f * fire

                        // Outer fire glow (wide, faint)
                        drawLine(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    AutoDevColors.Xuiper.markHot.copy(alpha = a * fire * 0.2f),
                                    AutoDevColors.Xuiper.markHot.copy(alpha = a * fire * 0.4f)
                                ),
                                startX = rocketX - trailLen,
                                endX = rocketX - rocketLen
                            ),
                            start = Offset(rocketX - trailLen, rocketY),
                            end = Offset(rocketX - rocketLen, rocketY),
                            strokeWidth = stroke * 5f,
                            cap = StrokeCap.Round
                        )

                        // Middle fire (medium)
                        drawLine(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    AutoDevColors.Xuiper.markHot.copy(alpha = a * fire * 0.5f),
                                    AutoDevColors.Xuiper.markCool.copy(alpha = a * fire * 0.7f)
                                ),
                                startX = rocketX - trailLen * 0.7f,
                                endX = rocketX - rocketLen
                            ),
                            start = Offset(rocketX - trailLen * 0.7f, rocketY),
                            end = Offset(rocketX - rocketLen, rocketY),
                            strokeWidth = stroke * 2.5f,
                            cap = StrokeCap.Round
                        )

                        // Core fire (bright, narrow)
                        drawLine(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.White.copy(alpha = a * fire * 0.6f),
                                    AutoDevColors.Xuiper.markCool.copy(alpha = a * fire * 0.9f)
                                ),
                                startX = rocketX - trailLen * 0.4f,
                                endX = rocketX - rocketLen * 0.5f
                            ),
                            start = Offset(rocketX - trailLen * 0.4f, rocketY),
                            end = Offset(rocketX - rocketLen * 0.5f, rocketY),
                            strokeWidth = stroke * 1.2f,
                            cap = StrokeCap.Round
                        )

                        // Fire particles
                        val particleCount = 6
                        for (i in 0 until particleCount) {
                            val t = i.toFloat() / (particleCount - 1)
                            val px = rocketX - rocketLen - trailLen * lerp(0.1f, 0.6f, t)
                            val py = rocketY + sin((rocket * 20f + i * 1.5f).toDouble()).toFloat() * stroke * lerp(0.5f, 2f, t)
                            val pAlpha = a * fire * lerp(0.7f, 0.2f, t)
                            val pRadius = stroke * lerp(0.4f, 0.15f, t)

                            drawCircle(
                                color = if (i % 2 == 0)
                                    AutoDevColors.Xuiper.markHot.copy(alpha = pAlpha)
                                else
                                    Color.White.copy(alpha = pAlpha * 0.8f),
                                radius = pRadius,
                                center = Offset(px, py)
                            )
                        }
                    }

                    // Rocket body glow
                    drawLine(
                        color = AutoDevColors.Xuiper.markCool.copy(alpha = a * 0.6f),
                        start = Offset(rocketX - rocketLen, rocketY),
                        end = Offset(rocketX + rocketLen, rocketY),
                        strokeWidth = rocketWidth * 3f,
                        cap = StrokeCap.Round
                    )

                    // Rocket body
                    drawLine(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                AutoDevColors.Xuiper.markHot,
                                AutoDevColors.Xuiper.markCool,
                                Color.White.copy(alpha = 0.9f)
                            ),
                            startX = rocketX - rocketLen,
                            endX = rocketX + rocketLen
                        ),
                        start = Offset(rocketX - rocketLen, rocketY),
                        end = Offset(rocketX + rocketLen, rocketY),
                        strokeWidth = rocketWidth,
                        cap = StrokeCap.Round
                    )

                    // Rocket head (arrow tip)
                    val headLen = stroke * 2.5f
                    val tipX = rocketX + rocketLen
                    drawLine(
                        color = Color.White.copy(alpha = a * 0.9f),
                        start = Offset(tipX - headLen, rocketY - headLen * 0.5f),
                        end = Offset(tipX, rocketY),
                        strokeWidth = stroke * 0.8f,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = Color.White.copy(alpha = a * 0.9f),
                        start = Offset(tipX - headLen, rocketY + headLen * 0.5f),
                        end = Offset(tipX, rocketY),
                        strokeWidth = stroke * 0.8f,
                        cap = StrokeCap.Round
                    )
                }

                // Spark particles when burst happens
                if (burst > 0f) {
                    val sparkCount = 12
                    for (i in 0 until sparkCount) {
                        val angle = (i.toFloat() / sparkCount) * 2f * 3.14159f
                        val dist = burst * size.minDimension * 0.4f
                        val sparkX = xCenterX + kotlin.math.cos(angle).toFloat() * dist
                        val sparkY = center.y + sin(angle).toFloat() * dist
                        val sparkAlpha = a * (1f - burst) * 0.9f
                        val sparkRadius = stroke * 0.5f * (1f - burst * 0.4f)

                        drawCircle(
                            color = if (i % 3 == 0)
                                Color.White.copy(alpha = sparkAlpha)
                            else if (i % 3 == 1)
                                AutoDevColors.Xuiper.markHot.copy(alpha = sparkAlpha)
                            else
                                AutoDevColors.Xuiper.markCool.copy(alpha = sparkAlpha),
                            radius = sparkRadius,
                            center = Offset(sparkX, sparkY)
                        )
                    }
                }
            }

            val textAlpha = fade.value * xGlow.value

            Text(
                text = "Xuiper",
                fontSize = 50.sp,
                fontWeight = FontWeight.Bold,
                color = AutoDevColors.Xuiper.text.copy(alpha = textAlpha)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "X => Super open, Xiuper build.",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 3.sp,
                color = AutoDevColors.Xuiper.textSecondary.copy(alpha = textAlpha)
            )
        }
    }
}

