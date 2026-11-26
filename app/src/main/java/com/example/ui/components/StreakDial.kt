package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.example.model.RecoveryMilestone
import com.example.ui.theme.BricolageGrotesqueFamily
import com.example.ui.theme.LocalInstrumentColors
import com.example.ui.theme.LocalReduceMotion
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private const val TICK_COUNT = 72

/**
 * Hero 72-tick Dusk Instrument Streak Dial.
 *
 * Motion spec:
 * - Plays one orchestrated entrance per session (8ms per tick sequential sweep + count-up numerals).
 * - Ambient 6s breathing glow behind dial, paused when lifecycle is backgrounded or reduced motion is on.
 * - Subtle tick shimmer when [minutePulseKey] increments.
 * - Completed milestones render as small Mint markers around the ring; [pulsingMilestoneIndex] pulses once.
 */
@Composable
fun StreakDial(
    elapsedMillis: Long,
    isFutureQuit: Boolean,
    futureRemainingMillis: Long,
    progressToNextMilestone: Float,
    completedMilestones: List<RecoveryMilestone>,
    totalMilestones: Int,
    shouldPlayEntrance: Boolean,
    minutePulseKey: Long,
    pulsingMilestoneIndex: Int?,
    modifier: Modifier = Modifier
) {
    val colors = LocalInstrumentColors.current
    val reduceMotion = LocalReduceMotion.current
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val isVisible = lifecycleState.isAtLeast(Lifecycle.State.STARTED)

    // 1. Orchestrated entrance progress (0f -> 1f over 72 * 8ms = 576ms)
    val entranceProgress = remember {
        Animatable(if (shouldPlayEntrance && !reduceMotion) 0f else 1f)
    }
    LaunchedEffect(shouldPlayEntrance, reduceMotion) {
        if (shouldPlayEntrance && !reduceMotion) {
            entranceProgress.snapTo(0f)
            entranceProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = TICK_COUNT * 8, easing = FastOutSlowInEasing)
            )
        } else {
            entranceProgress.snapTo(1f)
        }
    }

    // 2. Numeral count-up progress
    val numeralProgress = remember {
        Animatable(if (shouldPlayEntrance && !reduceMotion) 0f else 1f)
    }
    LaunchedEffect(shouldPlayEntrance, reduceMotion) {
        if (shouldPlayEntrance && !reduceMotion) {
            numeralProgress.snapTo(0f)
            numeralProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 680, delayMillis = 120, easing = FastOutSlowInEasing)
            )
        } else {
            numeralProgress.snapTo(1f)
        }
    }

    // 3. Subtle minute-change tick shimmer
    val shimmerAnim = remember { Animatable(0f) }
    LaunchedEffect(minutePulseKey) {
        if (minutePulseKey > 0L && !reduceMotion && isVisible) {
            shimmerAnim.snapTo(0f)
            shimmerAnim.animateTo(1f, animationSpec = tween(durationMillis = 750, easing = FastOutSlowInEasing))
            shimmerAnim.snapTo(0f)
        }
    }

    // 4. Newly reached milestone marker pulse (once)
    val milestonePulse = remember { Animatable(0f) }
    LaunchedEffect(pulsingMilestoneIndex) {
        if (pulsingMilestoneIndex != null && !reduceMotion) {
            milestonePulse.snapTo(0f)
            milestonePulse.animateTo(1f, animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing))
        }
    }

    // 5. Ambient 6s breathing glow (active ONLY when visible and motion allowed)
    val glowAlpha: Float = if (!reduceMotion && isVisible) {
        val infinite = rememberInfiniteTransition(label = "dialBreathingGlow")
        val anim by infinite.animateFloat(
            initialValue = 0.08f,
            targetValue = 0.20f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 6000, easing = EaseInOutSine),
                repeatMode = RepeatMode.Reverse
            ),
            label = "glowAlpha"
        )
        anim
    } else {
        0.10f
    }

    val safeElapsed = elapsedMillis.coerceAtLeast(0L)
    val displayMillis = if (isFutureQuit) futureRemainingMillis.coerceAtLeast(0L) else safeElapsed
    val totalHours = displayMillis / 3_600_000L
    val days = (totalHours / 24L).toInt()
    val hours = (totalHours % 24L).toInt()
    val minutes = ((displayMillis % 3_600_000L) / 60_000L).toInt()

    val shownDays = (days * numeralProgress.value).roundToInt()
    val shownHours = if (days == 0) (hours * numeralProgress.value).roundToInt() else hours
    val shownMinutes = if (days == 0 && hours == 0) (minutes * numeralProgress.value).roundToInt() else minutes

    val primaryFigure = when {
        days > 0 -> "${shownDays}d ${shownHours}h"
        hours > 0 -> "${shownHours}h ${shownMinutes}m"
        else -> "${shownDays}d ${shownHours}h"
    }
    val subFigure = when {
        isFutureQuit -> "until quit date"
        days == 0 && hours == 0 && minutes > 0 -> "${shownMinutes}m without weed"
        else -> "without weed"
    }

    val a11yDescription = if (isFutureQuit) {
        "$days days and $hours hours until your quit date"
    } else {
        "$days days and $hours hours without weed"
    }

    val filledTicks = (progressToNextMilestone.coerceIn(0f, 1f) * TICK_COUNT).roundToInt()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 296.dp)
            .aspectRatio(1f)
            .semantics { contentDescription = a11yDescription },
        contentAlignment = Alignment.Center
    ) {
        // Ambient breathing glow layer (transform/opacity only)
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = glowAlpha }
        ) {
            val radius = size.minDimension * 0.48f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        colors.dialAmber.copy(alpha = 0.7f),
                        colors.dialAmber.copy(alpha = 0.0f)
                    ),
                    center = center,
                    radius = radius
                ),
                radius = radius,
                center = center
            )
        }

        // Precision 72-tick ring + Mint milestone markers
        Canvas(modifier = Modifier.fillMaxSize()) {
            val outerRadius = size.minDimension * 0.455f
            val majorLength = size.minDimension * 0.068f
            val minorLength = size.minDimension * 0.042f
            val revealLimit = (entranceProgress.value * TICK_COUNT).roundToInt()
            val shimmerTick = if (shimmerAnim.value > 0f) (shimmerAnim.value * TICK_COUNT).toInt() else -10

            for (i in 0 until TICK_COUNT) {
                val angleRad = ((i.toDouble() / TICK_COUNT) * 2.0 * PI) - (PI / 2.0)
                val isMajor = i % 6 == 0
                val tickLen = if (isMajor) majorLength else minorLength
                val innerR = outerRadius - tickLen

                val start = Offset(
                    x = center.x + (innerR * cos(angleRad)).toFloat(),
                    y = center.y + (innerR * sin(angleRad)).toFloat()
                )
                val end = Offset(
                    x = center.x + (outerRadius * cos(angleRad)).toFloat(),
                    y = center.y + (outerRadius * sin(angleRad)).toFloat()
                )

                val revealed = i <= revealLimit
                val isFilled = !isFutureQuit && i < filledTicks
                val nearShimmer = kotlin.math.abs(i - shimmerTick) <= 2

                val tickColor = when {
                    !revealed -> colors.line.copy(alpha = 0.2f)
                    isFilled && nearShimmer -> colors.text
                    isFilled -> colors.dialAmber
                    nearShimmer -> colors.muted.copy(alpha = 0.7f)
                    else -> colors.line
                }

                val strokeW = when {
                    isMajor && isFilled -> 2.7.dp.toPx()
                    isFilled -> 2.1.dp.toPx()
                    isMajor -> 2.0.dp.toPx()
                    else -> 1.5.dp.toPx()
                }

                drawLine(
                    color = tickColor,
                    start = start,
                    end = end,
                    strokeWidth = strokeW,
                    cap = StrokeCap.Round
                )
            }

            // Inner delicate instrument hairline circle
            drawCircle(
                color = colors.line.copy(alpha = 0.65f),
                radius = outerRadius - majorLength - 10.dp.toPx(),
                center = center,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
            )

            // Past completed milestones shown as small Mint markers around the outer bezel
            if (!isFutureQuit && totalMilestones > 0) {
                val markerRadius = outerRadius + 8.dp.toPx()
                completedMilestones.forEachIndexed { idx, _ ->
                    val fraction = (idx + 1).toFloat() / totalMilestones.toFloat()
                    val angleRad = (fraction * 2.0 * PI) - (PI / 2.0)
                    val pos = Offset(
                        x = center.x + (markerRadius * cos(angleRad)).toFloat(),
                        y = center.y + (markerRadius * sin(angleRad)).toFloat()
                    )
                    if (pulsingMilestoneIndex == idx && milestonePulse.value in 0.01f..0.99f) {
                        val rippleR = 3.5.dp.toPx() + (12.dp.toPx() * milestonePulse.value)
                        drawCircle(
                            color = colors.mint.copy(alpha = (1f - milestonePulse.value) * 0.7f),
                            radius = rippleR,
                            center = pos,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx())
                        )
                    }
                    drawCircle(
                        color = colors.mint,
                        radius = 3.2.dp.toPx(),
                        center = pos
                    )
                }
            }
        }

        // Center streak numerals
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.graphicsLayer { alpha = numeralProgress.value.coerceIn(0.2f, 1f) }
        ) {
            Text(
                text = primaryFigure,
                fontFamily = BricolageGrotesqueFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 42.sp,
                lineHeight = 46.sp,
                letterSpacing = (-0.8).sp,
                color = colors.text,
                style = MaterialTheme.typography.displayLarge.copy(fontFeatureSettings = "tnum")
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subFigure,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.muted
            )
            if (!isFutureQuit && completedMilestones.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "${completedMilestones.size} of $totalMilestones milestones",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.mint
                )
            }
        }
    }
}
