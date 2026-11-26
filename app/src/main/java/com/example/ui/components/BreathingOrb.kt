package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.example.ui.theme.BricolageGrotesqueFamily
import com.example.ui.theme.LocalInstrumentColors
import com.example.ui.theme.LocalReduceMotion
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.sin

enum class BreathPhase(val label: String, val durationMs: Long) {
    Inhale("Inhale", 4_000L),
    Hold("Hold", 7_000L),
    Exhale("Exhale", 8_000L)
}

/**
 * 4-7-8 Breathing Orb for Craving SOS urge-surfing.
 * - Inhale grows smoothly over 4s
 * - Hold stays expanded for 7s with a gentle ring pulse
 * - Exhale shrinks smoothly over 8s
 * - Radiates a subtle ring ripple on every phase change
 * - Plays a clean Mint success pulse on completion when [completedSuccess] turns true (no confetti).
 * - Automatically pauses when app is backgrounded.
 */
@Composable
fun BreathingOrb(
    isRunning: Boolean,
    completedSuccess: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = LocalInstrumentColors.current
    val reduceMotion = LocalReduceMotion.current
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val isVisible = lifecycleState.isAtLeast(Lifecycle.State.STARTED)

    var phase by remember { mutableStateOf(BreathPhase.Inhale) }
    var phaseFraction by remember { mutableFloatStateOf(0f) }
    var phaseSecondsLeft by remember { mutableIntStateOf(4) }
    var phaseChangeCount by remember { mutableIntStateOf(0) }

    val rippleProgress = remember { Animatable(1f) }
    val successPulse = remember { Animatable(0f) }

    LaunchedEffect(phaseChangeCount, reduceMotion) {
        if (phaseChangeCount > 0 && !reduceMotion) {
            rippleProgress.snapTo(0f)
            rippleProgress.animateTo(1f, animationSpec = tween(durationMillis = 850, easing = FastOutSlowInEasing))
        }
    }

    LaunchedEffect(completedSuccess, reduceMotion) {
        if (completedSuccess) {
            if (!reduceMotion) {
                successPulse.snapTo(0f)
                successPulse.animateTo(1f, animationSpec = tween(durationMillis = 750, easing = FastOutSlowInEasing))
            } else {
                successPulse.snapTo(1f)
            }
        }
    }

    // 4-7-8 phase clock — runs ONLY while visible & active
    LaunchedEffect(isRunning, isVisible, completedSuccess) {
        if (!isRunning || !isVisible || completedSuccess) return@LaunchedEffect
        var elapsedInPhase = 0L
        var lastTick = System.currentTimeMillis()
        while (true) {
            delay(32L)
            val now = System.currentTimeMillis()
            val dt = (now - lastTick).coerceIn(0L, 250L)
            lastTick = now
            elapsedInPhase += dt

            if (elapsedInPhase >= phase.durationMs) {
                elapsedInPhase -= phase.durationMs
                phase = when (phase) {
                    BreathPhase.Inhale -> BreathPhase.Hold
                    BreathPhase.Hold -> BreathPhase.Exhale
                    BreathPhase.Exhale -> BreathPhase.Inhale
                }
                phaseChangeCount++
            }
            val frac = (elapsedInPhase.toFloat() / phase.durationMs.toFloat()).coerceIn(0f, 1f)
            phaseFraction = frac
            val remMs = (phase.durationMs - elapsedInPhase).coerceAtLeast(0L)
            phaseSecondsLeft = ceil(remMs / 1000.0).toInt().coerceAtLeast(1)
        }
    }

    // Smoothly map phase -> orb radius factor
    val orbScale = when {
        completedSuccess -> 0.78f
        reduceMotion -> 0.72f
        phase == BreathPhase.Inhale -> 0.46f + (0.42f * easeInOut(phaseFraction))
        phase == BreathPhase.Hold -> 0.88f + (0.03f * sin(phaseFraction * PI * 4.0).toFloat())
        else -> 0.88f - (0.42f * easeInOut(phaseFraction))
    }

    val ringColor = if (completedSuccess) colors.mint else colors.amber

    Box(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 264.dp)
            .aspectRatio(1f)
            .semantics {
                contentDescription = if (completedSuccess) {
                    "Craving resisted"
                } else {
                    "${phase.label}, $phaseSecondsLeft seconds remaining"
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val minDim = size.minDimension
            val ringRadius = minDim * 0.44f
            val ringStroke = 3.dp.toPx()

            // Radiating ripple on phase change
            if (!reduceMotion && rippleProgress.value in 0.01f..0.99f && !completedSuccess) {
                val r = ringRadius * (0.82f + 0.24f * rippleProgress.value)
                drawCircle(
                    color = colors.amber.copy(alpha = (1f - rippleProgress.value) * 0.35f),
                    radius = r,
                    center = center,
                    style = Stroke(width = 1.5.dp.toPx())
                )
            }

            // Success pulse ring (mint, once, no confetti)
            if (completedSuccess && successPulse.value in 0.01f..0.99f) {
                val r = ringRadius * (0.95f + 0.18f * successPulse.value)
                drawCircle(
                    color = colors.mint.copy(alpha = (1f - successPulse.value) * 0.8f),
                    radius = r,
                    center = center,
                    style = Stroke(width = 2.5.dp.toPx())
                )
            }

            // Soft breathing inner orb
            val maxOrbR = ringRadius * 0.84f
            val currentOrbR = maxOrbR * orbScale
            drawCircle(
                color = ringColor.copy(alpha = if (completedSuccess) 0.20f else 0.14f),
                radius = currentOrbR,
                center = center
            )
            drawCircle(
                color = ringColor.copy(alpha = 0.42f),
                radius = currentOrbR,
                center = center,
                style = Stroke(width = 1.5.dp.toPx())
            )

            // Outer track ring
            drawCircle(
                color = colors.line,
                radius = ringRadius,
                center = center,
                style = Stroke(width = ringStroke)
            )

            // Phase progress arc
            val sweep = if (completedSuccess) 360f else (360f * phaseFraction)
            val arcTopLeft = Offset(center.x - ringRadius, center.y - ringRadius)
            drawArc(
                color = ringColor,
                startAngle = -90f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = arcTopLeft,
                size = Size(ringRadius * 2f, ringRadius * 2f),
                style = Stroke(width = ringStroke, cap = StrokeCap.Round)
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (completedSuccess) {
                Text(
                    text = "Done",
                    fontFamily = BricolageGrotesqueFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 30.sp,
                    color = colors.mint
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "You rode it out",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted
                )
            } else {
                Text(
                    text = "$phaseSecondsLeft",
                    fontFamily = BricolageGrotesqueFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 40.sp,
                    color = colors.text,
                    style = MaterialTheme.typography.displayLarge.copy(fontFeatureSettings = "tnum")
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = phase.label,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.amber
                )
            }
        }
    }
}

private fun easeInOut(t: Float): Float =
    if (t < 0.5f) 2f * t * t else 1f - ((-2f * t + 2f) * (-2f * t + 2f)) / 2f
