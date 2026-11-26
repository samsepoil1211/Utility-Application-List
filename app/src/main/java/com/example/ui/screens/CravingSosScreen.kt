package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.example.model.CravingTriggers
import com.example.model.RecoveryContent
import com.example.ui.components.AmberPrimaryButton
import com.example.ui.components.BreathingOrb
import com.example.ui.components.QuietOutlineButton
import com.example.ui.icons.InstrumentIcons
import com.example.ui.motion.InstrumentMotion
import com.example.ui.theme.LocalInstrumentColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private const val URGE_SURF_TOTAL_SECONDS = 10 * 60 // 10-minute session

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CravingSosScreen(
    userReason: String,
    onClose: () -> Unit,
    onResisted: (intensity: Int, trigger: String, durationSeconds: Int) -> Unit,
    onUsed: (intensity: Int, trigger: String, durationSeconds: Int) -> Unit
) {
    BackHandler { onClose() }
    val colors = LocalInstrumentColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val isVisible = lifecycleState.isAtLeast(Lifecycle.State.STARTED)

    var step by remember { mutableIntStateOf(0) } // 0 = setup, 1 = breathing session
    var intensity by remember { mutableFloatStateOf(6f) }
    var selectedTrigger by remember { mutableStateOf(CravingTriggers.ALL.first()) }

    var secondsElapsed by remember { mutableIntStateOf(0) }
    var tipIndex by remember { mutableIntStateOf(0) }
    var completedSuccess by remember { mutableStateOf(false) }

    // 10-minute session countdown — runs ONLY while visible & in step 1
    LaunchedEffect(step, isVisible, completedSuccess) {
        if (step != 1 || !isVisible || completedSuccess) return@LaunchedEffect
        while (secondsElapsed < URGE_SURF_TOTAL_SECONDS) {
            delay(1000L)
            secondsElapsed++
            if (secondsElapsed % 20 == 0) {
                tipIndex = (tipIndex + 1) % RecoveryContent.COPING_TIPS.size
            }
        }
    }

    val remaining = (URGE_SURF_TOTAL_SECONDS - secondsElapsed).coerceAtLeast(0)
    val remMin = remaining / 60
    val remSec = remaining % 60
    val timerText = String.format("%02d:%02d", remMin, remSec)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (step == 0) "Craving check" else "Ride the wave",
                    style = MaterialTheme.typography.headlineMedium,
                    color = colors.text
                )
                IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
                    Icon(
                        imageVector = InstrumentIcons.Close,
                        contentDescription = "Close craving session",
                        tint = colors.muted
                    )
                }
            }

            if (step == 0) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Pause for ten minutes before acting. Notice the urge without judging it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.muted,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(22.dp))

                // Intensity slider 1-10
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.panel, RoundedCornerShape(16.dp))
                        .border(1.dp, colors.line, RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Intensity",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.text
                        )
                        Text(
                            text = "${intensity.roundToInt()} / 10",
                            style = MaterialTheme.typography.titleLarge,
                            color = colors.amber
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Slider(
                        value = intensity,
                        onValueChange = { intensity = it },
                        valueRange = 1f..10f,
                        steps = 8,
                        colors = SliderDefaults.colors(
                            thumbColor = colors.amber,
                            activeTrackColor = colors.amber,
                            inactiveTrackColor = colors.line
                        ),
                        modifier = Modifier.testTag("craving_intensity_slider")
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "What triggered it?",
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.text,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CravingTriggers.ALL.forEach { trigger ->
                        val active = selectedTrigger == trigger
                        Box(
                            modifier = Modifier
                                .background(
                                    if (active) colors.amber.copy(alpha = 0.16f) else colors.panel,
                                    RoundedCornerShape(12.dp)
                                )
                                .border(
                                    1.dp,
                                    if (active) colors.amber else colors.line,
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    InstrumentMotion.lightHaptic(context)
                                    selectedTrigger = trigger
                                }
                                .padding(horizontal = 14.dp, vertical = 11.dp)
                        ) {
                            Text(
                                text = trigger,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (active) colors.amber else colors.text
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                AmberPrimaryButton(
                    text = "Start 10-minute urge surfing",
                    icon = InstrumentIcons.Wind,
                    onClick = { step = 1 },
                    modifier = Modifier.testTag("craving_start_session_button")
                )
            } else {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "$timerText remaining · 4-7-8 breathing",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.muted
                )

                Spacer(modifier = Modifier.height(18.dp))

                BreathingOrb(
                    isRunning = true,
                    completedSuccess = completedSuccess
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Coping tip
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.panel, RoundedCornerShape(14.dp))
                        .border(1.dp, colors.line, RoundedCornerShape(14.dp))
                        .clickable {
                            tipIndex = (tipIndex + 1) % RecoveryContent.COPING_TIPS.size
                        }
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Coping focus",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.lilac
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = RecoveryContent.COPING_TIPS[tipIndex],
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.text
                    )
                }

                if (userReason.isNotBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.panelElevated, RoundedCornerShape(14.dp))
                            .border(1.dp, colors.line, RoundedCornerShape(14.dp))
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Your reason",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.amber
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = userReason,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.text
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                AmberPrimaryButton(
                    text = "I got through it",
                    enabled = !completedSuccess,
                    icon = InstrumentIcons.Check,
                    onClick = {
                        completedSuccess = true
                        scope.launch {
                            delay(820L) // Let the Mint ring success pulse play cleanly
                            onResisted(intensity.roundToInt(), selectedTrigger, secondsElapsed.coerceAtLeast(1))
                        }
                    },
                    modifier = Modifier.testTag("craving_resisted_button")
                )

                Spacer(modifier = Modifier.height(10.dp))

                TextButton(
                    enabled = !completedSuccess,
                    onClick = {
                        onUsed(intensity.roundToInt(), selectedTrigger, secondsElapsed.coerceAtLeast(1))
                    },
                    modifier = Modifier.testTag("craving_used_button")
                ) {
                    Text(
                        text = "I used",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.coral,
                        textAlign = TextAlign.Center
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
