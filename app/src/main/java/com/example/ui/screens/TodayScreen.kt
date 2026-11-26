package com.example.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.example.config.AppConfig
import com.example.model.CravingTriggers
import com.example.model.Currencies
import com.example.model.RecoveryContent
import com.example.model.RecoveryMilestone
import com.example.model.StatsAnalytics
import com.example.model.VaultPayload
import com.example.ui.components.AmberPrimaryButton
import com.example.ui.components.HairlineDivider
import com.example.ui.components.InstrumentTextField
import com.example.ui.components.InstrumentToggleRow
import com.example.ui.components.QuietOutlineButton
import com.example.ui.components.StreakDial
import com.example.ui.icons.InstrumentIcons
import com.example.ui.motion.InstrumentMotion
import com.example.ui.theme.LocalInstrumentColors
import com.example.ui.theme.LocalReduceMotion
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Main Today Dashboard — the ONLY screen displaying the header "QuitWeed".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    payload: VaultPayload,
    hasPlayedEntrance: Boolean,
    pulsingMilestoneIndex: Int?,
    newlyReachedMilestone: RecoveryMilestone?,
    onMarkEntrancePlayed: () -> Unit,
    onOpenCravingSos: () -> Unit,
    onLockNow: () -> Unit,
    onLogCheckIn: (mood: Int, sleepHours: Double, cravingLevel: Int, usedToday: Boolean, note: String) -> Unit,
    onLogSlip: (trigger: String, note: String) -> Unit,
    onCheckMilestoneTick: () -> Unit,
    onDismissMilestone: () -> Unit
) {
    val colors = LocalInstrumentColors.current
    val reduceMotion = LocalReduceMotion.current
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val isVisible = lifecycleState.isAtLeast(Lifecycle.State.STARTED)

    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var minutePulseKey by remember { mutableLongStateOf(0L) }

    var showCheckInSheet by remember { mutableStateOf(false) }
    var showSlipSheet by remember { mutableStateOf(false) }

    // Minute-aligned clock — runs ONLY while visible (zero background battery drain)
    LaunchedEffect(isVisible) {
        if (!isVisible) return@LaunchedEffect
        nowMs = System.currentTimeMillis()
        onCheckMilestoneTick()
        while (true) {
            val msToNextMinute = 60_000L - (System.currentTimeMillis() % 60_000L)
            delay(msToNextMinute.coerceAtLeast(1_000L))
            nowMs = System.currentTimeMillis()
            minutePulseKey++
            onCheckMilestoneTick()
        }
    }

    // Orchestrated stagger entrance once per session (buttons + figures rise in with 40ms stagger)
    val shouldPlayEntrance = !hasPlayedEntrance
    val actionsRise = remember { Animatable(if (shouldPlayEntrance && !reduceMotion) 0f else 1f) }
    val figuresRise = remember { Animatable(if (shouldPlayEntrance && !reduceMotion) 0f else 1f) }
    val milestoneRise = remember { Animatable(if (shouldPlayEntrance && !reduceMotion) 0f else 1f) }

    LaunchedEffect(shouldPlayEntrance, reduceMotion) {
        if (shouldPlayEntrance && !reduceMotion) {
            delay(280L)
            actionsRise.animateTo(1f, tween(340, easing = FastOutSlowInEasing))
            delay(40L)
            figuresRise.animateTo(1f, tween(320, easing = FastOutSlowInEasing))
            delay(40L)
            milestoneRise.animateTo(1f, tween(320, easing = FastOutSlowInEasing))
        } else {
            actionsRise.snapTo(1f)
            figuresRise.snapTo(1f)
            milestoneRise.snapTo(1f)
        }
        onMarkEntrancePlayed()
    }

    val streakStart = payload.currentStreakStartMs.takeIf { it > 0L } ?: payload.quitPlan.quitEpochMs
    val isFutureQuit = streakStart > nowMs
    val elapsedMs = if (isFutureQuit) 0L else (nowMs - streakStart).coerceAtLeast(0L)
    val futureRemMs = if (isFutureQuit) (streakStart - nowMs).coerceAtLeast(0L) else 0L
    val elapsedHours = elapsedMs.toDouble() / 3_600_000.0

    val milestones = RecoveryContent.MILESTONES
    val completedMilestones = remember(elapsedHours) {
        milestones.filter { elapsedHours >= it.hoursRequired }
    }
    val nextMilestone = remember(elapsedHours) {
        milestones.firstOrNull { elapsedHours < it.hoursRequired }
    }
    val prevHours = completedMilestones.lastOrNull()?.hoursRequired?.toDouble() ?: 0.0
    val progressToNext = if (nextMilestone == null) {
        1f
    } else {
        val span = (nextMilestone.hoursRequired.toDouble() - prevHours).coerceAtLeast(1.0)
        ((elapsedHours - prevHours) / span).toFloat().coerceIn(0f, 1f)
    }

    val moneySaved = StatsAnalytics.computeMoneySaved(elapsedMs, payload.quitPlan.weeklySpend)
    val gramsNotSmoked = StatsAnalytics.computeGramsAvoided(elapsedMs, payload.quitPlan.gramsPerDay)
    val cravingsResisted = payload.cravings.count { it.resisted }
    val longestStreakMs = maxOf(payload.longestStreakMs, elapsedMs)
    val symbol = Currencies.symbolFor(payload.quitPlan.currencyCode)

    val todayKey = StatsAnalytics.todayDateKey(nowMs)
    val checkedInToday = payload.checkIns.any { it.dateKey == todayKey }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top bar: "QuitWeed" ONLY here + quick lock icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = AppConfig.DASHBOARD_TITLE,
                    style = MaterialTheme.typography.headlineLarge,
                    color = colors.text,
                    modifier = Modifier.testTag("dashboard_header_title")
                )
                IconButton(
                    onClick = onLockNow,
                    modifier = Modifier.size(48.dp).testTag("today_lock_button")
                ) {
                    Icon(
                        imageVector = InstrumentIcons.Lock,
                        contentDescription = "Lock vault",
                        tint = colors.muted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Hero 72-tick Streak Dial
            StreakDial(
                elapsedMillis = elapsedMs,
                isFutureQuit = isFutureQuit,
                futureRemainingMillis = futureRemMs,
                progressToNextMilestone = progressToNext,
                completedMilestones = completedMilestones,
                totalMilestones = milestones.size,
                shouldPlayEntrance = shouldPlayEntrance,
                minutePulseKey = minutePulseKey,
                pulsingMilestoneIndex = pulsingMilestoneIndex,
                modifier = Modifier.testTag("streak_dial")
            )

            Spacer(modifier = Modifier.height(22.dp))

            // Primary action: Dominant Amber "I have a craving" + quieter "Daily check-in"
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = actionsRise.value
                        translationY = (1f - actionsRise.value) * 18f
                    },
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AmberPrimaryButton(
                    text = "I have a craving",
                    icon = InstrumentIcons.Wind,
                    onClick = onOpenCravingSos,
                    modifier = Modifier.testTag("today_craving_button")
                )
                QuietOutlineButton(
                    text = if (checkedInToday) "Update daily check-in" else "Daily check-in",
                    icon = InstrumentIcons.CheckIn,
                    onClick = { showCheckInSheet = true },
                    modifier = Modifier.testTag("today_checkin_button")
                )
            }

            Spacer(modifier = Modifier.height(22.dp))

            // Compact single row of 3 figures separated by hairlines (NOT boxed cards)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = figuresRise.value
                        translationY = (1f - figuresRise.value) * 18f
                    }
            ) {
                HairlineDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                        .padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MetricColumn(
                        value = formatMoney(symbol, moneySaved),
                        label = "Saved",
                        modifier = Modifier.weight(1f)
                    )
                    VerticalDivider(
                        thickness = 1.dp,
                        color = colors.line,
                        modifier = Modifier.fillMaxHeight().padding(vertical = 4.dp)
                    )
                    MetricColumn(
                        value = String.format(Locale.US, "%.1fg", gramsNotSmoked),
                        label = "Not smoked",
                        modifier = Modifier.weight(1f)
                    )
                    VerticalDivider(
                        thickness = 1.dp,
                        color = colors.line,
                        modifier = Modifier.fillMaxHeight().padding(vertical = 4.dp)
                    )
                    MetricColumn(
                        value = "$cravingsResisted",
                        label = "Got through",
                        modifier = Modifier.weight(1f)
                    )
                }
                HairlineDivider()
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Slim next-milestone line with progress track + longest streak + reason
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = milestoneRise.value
                        translationY = (1f - milestoneRise.value) * 16f
                    },
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (nextMilestone != null) {
                                "Next milestone: ${nextMilestone.title}"
                            } else {
                                "All 1-year milestones reached"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.text
                        )
                        Text(
                            text = "${(progressToNext * 100f).roundToInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (nextMilestone == null) colors.mint else colors.amber
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .background(colors.line, RoundedCornerShape(2.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progressToNext.coerceIn(0f, 1f))
                                .height(4.dp)
                                .background(
                                    if (nextMilestone == null) colors.mint else colors.amber,
                                    RoundedCornerShape(2.dp)
                                )
                        )
                    }
                    if (nextMilestone != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = nextMilestone.summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.muted
                        )
                    }
                }

                // Longest streak & personal reason
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Longest streak",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.muted
                    )
                    Text(
                        text = StatsAnalytics.formatDurationDaysHours(longestStreakMs),
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.lilac
                    )
                }

                if (payload.quitPlan.reason.isNotBlank()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.panel, RoundedCornerShape(14.dp))
                            .border(1.dp, colors.line, RoundedCornerShape(14.dp))
                            .padding(14.dp)
                    ) {
                        Text(
                            text = "Your reason",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.muted
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = payload.quitPlan.reason,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.text
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            // Quiet "I slipped" text button at the bottom
            TextButton(
                onClick = { showSlipSheet = true },
                modifier = Modifier.testTag("today_slipped_button")
            ) {
                Text(
                    text = "I slipped",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.muted
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }

    if (showCheckInSheet) {
        val existingToday = payload.checkIns.firstOrNull { it.dateKey == todayKey }
        DailyCheckInSheet(
            initialMood = existingToday?.mood ?: 3,
            initialSleep = existingToday?.sleepHours ?: 7.0,
            initialCraving = existingToday?.cravingLevel ?: 2,
            initialUsedToday = existingToday?.usedToday ?: false,
            initialNote = existingToday?.note.orEmpty(),
            onDismiss = { showCheckInSheet = false },
            onSave = { mood, sleep, craving, used, note ->
                showCheckInSheet = false
                onLogCheckIn(mood, sleep, craving, used, note)
                if (used) {
                    showSlipSheet = true
                }
            }
        )
    }

    if (showSlipSheet) {
        SlipHandlingSheet(
            onDismiss = { showSlipSheet = false },
            onConfirmSlip = { trigger, note ->
                showSlipSheet = false
                onLogSlip(trigger, note)
            }
        )
    }

    if (newlyReachedMilestone != null) {
        ModalBottomSheet(
            onDismissRequest = onDismissMilestone,
            containerColor = colors.panel,
            contentColor = colors.text
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Milestone reached · ${newlyReachedMilestone.shortLabel}",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.mint
                )
                Text(
                    text = newlyReachedMilestone.title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = colors.text
                )
                Text(
                    text = newlyReachedMilestone.summary,
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.text
                )
                Text(
                    text = newlyReachedMilestone.detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.muted
                )
                Spacer(modifier = Modifier.height(6.dp))
                AmberPrimaryButton(
                    text = "Keep going",
                    onClick = onDismissMilestone
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun MetricColumn(
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    val colors = LocalInstrumentColors.current
    Column(
        modifier = modifier.padding(horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = colors.text,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.muted,
            textAlign = TextAlign.Center
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DailyCheckInSheet(
    initialMood: Int,
    initialSleep: Double,
    initialCraving: Int,
    initialUsedToday: Boolean,
    initialNote: String,
    onDismiss: () -> Unit,
    onSave: (Int, Double, Int, Boolean, String) -> Unit
) {
    val colors = LocalInstrumentColors.current
    val context = LocalContext.current
    var mood by remember { mutableIntStateOf(initialMood) }
    var sleepHours by remember { mutableDoubleStateOf(initialSleep) }
    var craving by remember { mutableFloatStateOf(initialCraving.toFloat()) }
    var usedToday by remember { mutableStateOf(initialUsedToday) }
    var note by remember { mutableStateOf(initialNote) }

    val moodLabels = listOf("Low", "Rough", "Steady", "Good", "Clear")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.panel,
        contentColor = colors.text
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Daily check-in",
                style = MaterialTheme.typography.headlineMedium,
                color = colors.text
            )

            // Mood 1..5
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Mood", style = MaterialTheme.typography.titleSmall, color = colors.text)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    (1..5).forEach { level ->
                        val active = mood == level
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .background(
                                    if (active) colors.mint.copy(alpha = 0.16f) else colors.panelElevated,
                                    RoundedCornerShape(10.dp)
                                )
                                .border(
                                    1.dp,
                                    if (active) colors.mint else colors.line,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    InstrumentMotion.lightHaptic(context)
                                    mood = level
                                }
                                .padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "$level",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (active) colors.mint else colors.text
                            )
                            Text(
                                text = moodLabels[level - 1],
                                style = MaterialTheme.typography.labelSmall,
                                color = if (active) colors.mint else colors.muted
                            )
                        }
                    }
                }
            }

            // Sleep hours
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Sleep", style = MaterialTheme.typography.titleSmall, color = colors.text)
                    Text(
                        text = String.format(Locale.US, "%.1f hours", sleepHours),
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.lilac
                    )
                }
                Slider(
                    value = sleepHours.toFloat(),
                    onValueChange = { sleepHours = (it * 2f).roundToInt() / 2.0 },
                    valueRange = 0f..14f,
                    steps = 27,
                    colors = SliderDefaults.colors(
                        thumbColor = colors.lilac,
                        activeTrackColor = colors.lilac,
                        inactiveTrackColor = colors.line
                    )
                )
            }

            // Craving level 0..10
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Craving level", style = MaterialTheme.typography.titleSmall, color = colors.text)
                    Text(
                        text = "${craving.roundToInt()} / 10",
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.amber
                    )
                }
                Slider(
                    value = craving,
                    onValueChange = { craving = it },
                    valueRange = 0f..10f,
                    steps = 9,
                    colors = SliderDefaults.colors(
                        thumbColor = colors.amber,
                        activeTrackColor = colors.amber,
                        inactiveTrackColor = colors.line
                    )
                )
            }

            InstrumentToggleRow(
                title = "I used today",
                subtitle = "Logs honestly and lets you reset your current streak if needed",
                checked = usedToday,
                onCheckedChange = { usedToday = it }
            )

            InstrumentTextField(
                value = note,
                onValueChange = { note = it },
                label = "Encrypted note (optional)",
                placeholder = "How did today feel?",
                singleLine = false,
                minLines = 2
            )

            AmberPrimaryButton(
                text = "Save check-in",
                onClick = { onSave(mood, sleepHours, craving.roundToInt(), usedToday, note) },
                modifier = Modifier.testTag("checkin_save_button")
            )
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SlipHandlingSheet(
    initialTrigger: String = CravingTriggers.ALL.first(),
    onDismiss: () -> Unit,
    onConfirmSlip: (trigger: String, note: String) -> Unit
) {
    val colors = LocalInstrumentColors.current
    var trigger by remember { mutableStateOf(initialTrigger) }
    var note by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.panel,
        contentColor = colors.text
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "A slip is data, not defeat.",
                style = MaterialTheme.typography.headlineMedium,
                color = colors.text
            )
            Text(
                text = "Your current streak will restart from now. Your history, cravings resisted, and longest streak stay intact.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.muted
            )

            Text("What led up to it?", style = MaterialTheme.typography.titleSmall, color = colors.text)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CravingTriggers.ALL.forEach { t ->
                    val active = trigger == t
                    Box(
                        modifier = Modifier
                            .background(
                                if (active) colors.coral.copy(alpha = 0.16f) else colors.panelElevated,
                                RoundedCornerShape(10.dp)
                            )
                            .border(
                                1.dp,
                                if (active) colors.coral else colors.line,
                                RoundedCornerShape(10.dp)
                            )
                            .clickable { trigger = t }
                            .padding(horizontal = 12.dp, vertical = 9.dp)
                    ) {
                        Text(
                            text = t,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (active) colors.coral else colors.text
                        )
                    }
                }
            }

            InstrumentTextField(
                value = note,
                onValueChange = { note = it },
                label = "What can help next time? (encrypted)",
                singleLine = false,
                minLines = 2
            )

            QuietOutlineButton(
                text = "Log slip and restart streak",
                danger = true,
                onClick = { onConfirmSlip(trigger, note) },
                modifier = Modifier.testTag("slip_confirm_button")
            )
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

private fun formatMoney(symbol: String, amount: Double): String {
    return if (amount >= 100.0) {
        "$symbol${amount.roundToInt()}"
    } else {
        String.format(Locale.US, "%s%.2f", symbol, amount)
    }
}
