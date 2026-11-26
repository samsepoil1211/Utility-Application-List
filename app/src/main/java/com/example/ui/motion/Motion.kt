package com.example.ui.motion

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.example.ui.theme.LocalInstrumentColors
import com.example.ui.theme.LocalReduceMotion

object InstrumentMotion {
    /**
     * Checks Android's system-wide reduced motion / animator scale setting.
     */
    fun isSystemReducedMotion(context: Context): Boolean {
        return try {
            val scale = Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            )
            scale == 0f
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Light 10ms haptic pulse on key interactions (equivalent to navigator.vibrate(10)).
     */
    @Suppress("DEPRECATION")
    fun lightHaptic(context: Context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(10L, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(
                        VibrationEffect.createOneShot(10L, VibrationEffect.DEFAULT_AMPLITUDE)
                    )
                } else {
                    vibrator?.vibrate(10L)
                }
            }
        } catch (_: Exception) {
            // Gracefully ignore if device has no vibrator
        }
    }
}

/**
 * GPU-friendly transform-only press spring (scale down to 0.97 on press, release with spring).
 * Respects [LocalReduceMotion] by keeping scale at 1.0 when reduced motion is enabled.
 */
fun Modifier.pressSpring(interactionSource: MutableInteractionSource): Modifier = composed {
    val reduceMotion = LocalReduceMotion.current
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (!reduceMotion && pressed) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = 700f
        ),
        label = "pressSpringScale"
    )
    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

@Composable
fun InstrumentToast(
    message: String?,
    isError: Boolean = false,
    modifier: Modifier = Modifier
) {
    val colors = LocalInstrumentColors.current
    val reduceMotion = LocalReduceMotion.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = !message.isNullOrBlank(),
            enter = if (reduceMotion) fadeIn() else fadeIn() + slideInVertically { it / 2 },
            exit = if (reduceMotion) fadeOut() else fadeOut() + slideOutVertically { it / 2 }
        ) {
            val borderColor = if (isError) colors.coral else colors.line
            Text(
                text = message.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                color = if (isError) colors.coral else colors.text,
                modifier = Modifier
                    .background(colors.panelElevated, RoundedCornerShape(14.dp))
                    .border(1.dp, borderColor, RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }
    }
}
