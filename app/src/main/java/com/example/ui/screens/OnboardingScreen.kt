package com.example.ui.screens

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.config.AppConfig
import com.example.model.Currencies
import com.example.model.QuitPlan
import com.example.ui.components.AmberPrimaryButton
import com.example.ui.components.InstrumentTextField
import com.example.ui.theme.LocalInstrumentColors
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Onboarding (and reusable Quit Plan editor) using neutral Daily Log branding.
 * Supports setting a current, past, or future quit date and time.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun OnboardingScreen(
    initialPlan: QuitPlan,
    isEditingExisting: Boolean = false,
    onCancelEdit: (() -> Unit)? = null,
    onSavePlan: (QuitPlan) -> Unit
) {
    val colors = LocalInstrumentColors.current
    val initialEpoch = remember(initialPlan) {
        if (initialPlan.quitEpochMs > 0L) initialPlan.quitEpochMs else System.currentTimeMillis()
    }
    var quitEpochMs by remember { mutableLongStateOf(initialEpoch) }
    var weeklySpendText by remember {
        mutableStateOf(if (initialPlan.weeklySpend > 0.0) initialPlan.weeklySpend.toString() else "")
    }
    var gramsPerDayText by remember {
        mutableStateOf(if (initialPlan.gramsPerDay > 0.0) initialPlan.gramsPerDay.toString() else "")
    }
    var currencyCode by remember { mutableStateOf(initialPlan.currencyCode.ifBlank { "USD" }) }
    var reason by remember { mutableStateOf(initialPlan.reason) }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val dateFormatter = remember { SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault()) }
    val timeFormatter = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

    val cal = remember(quitEpochMs) {
        Calendar.getInstance().apply { timeInMillis = quitEpochMs }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = AppConfig.APP_NAME_PUBLIC,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.muted
                )
                if (isEditingExisting && onCancelEdit != null) {
                    TextButton(onClick = onCancelEdit) {
                        Text("Cancel", color = colors.muted)
                    }
                }
            }

            Column {
                Text(
                    text = if (isEditingExisting) "Edit your plan" else "Set your starting point",
                    style = MaterialTheme.typography.headlineLarge,
                    color = colors.text
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "These numbers power your savings and recovery dial. You can pick a future date or adjust them anytime.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.muted
                )
            }

            // Date & Time row
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Start date and time",
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.text
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1.4f)
                            .background(colors.panel, RoundedCornerShape(12.dp))
                            .border(1.dp, colors.line, RoundedCornerShape(12.dp))
                            .clickable { showDatePicker = true }
                            .padding(horizontal = 14.dp, vertical = 14.dp)
                            .testTag("onboarding_date_picker")
                    ) {
                        Column {
                            Text("Date", style = MaterialTheme.typography.labelSmall, color = colors.muted)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = dateFormatter.format(Date(quitEpochMs)),
                                style = MaterialTheme.typography.bodyLarge,
                                color = colors.text
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(colors.panel, RoundedCornerShape(12.dp))
                            .border(1.dp, colors.line, RoundedCornerShape(12.dp))
                            .clickable { showTimePicker = true }
                            .padding(horizontal = 14.dp, vertical = 14.dp)
                            .testTag("onboarding_time_picker")
                    ) {
                        Column {
                            Text("Time", style = MaterialTheme.typography.labelSmall, color = colors.muted)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = timeFormatter.format(Date(quitEpochMs)),
                                style = MaterialTheme.typography.bodyLarge,
                                color = colors.text
                            )
                        }
                    }
                }
                if (quitEpochMs > System.currentTimeMillis() + 60_000L) {
                    Text(
                        text = "Future date selected. Your dial will count down until this time.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.lilac
                    )
                }
            }

            // Currency selector
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Currency",
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.text
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Currencies.ALL.forEach { option ->
                        val active = currencyCode == option.code
                        Box(
                            modifier = Modifier
                                .background(
                                    if (active) colors.amber.copy(alpha = 0.16f) else colors.panel,
                                    RoundedCornerShape(10.dp)
                                )
                                .border(
                                    1.dp,
                                    if (active) colors.amber else colors.line,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { currencyCode = option.code }
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = option.label,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (active) colors.amber else colors.muted
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                InstrumentTextField(
                    value = weeklySpendText,
                    onValueChange = { s ->
                        if (s.all { it.isDigit() || it == '.' }) weeklySpendText = s
                    },
                    label = "Weekly spend (${Currencies.symbolFor(currencyCode)})",
                    placeholder = "0",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f).testTag("onboarding_weekly_spend_input")
                )
                InstrumentTextField(
                    value = gramsPerDayText,
                    onValueChange = { s ->
                        if (s.all { it.isDigit() || it == '.' }) gramsPerDayText = s
                    },
                    label = "Grams per day",
                    placeholder = "0.0",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f).testTag("onboarding_grams_input")
                )
            }

            InstrumentTextField(
                value = reason,
                onValueChange = { reason = it },
                label = "My reason for quitting",
                placeholder = "Clearer mornings, better sleep, being present...",
                singleLine = false,
                minLines = 3,
                modifier = Modifier.testTag("onboarding_reason_input")
            )

            Spacer(modifier = Modifier.height(6.dp))

            AmberPrimaryButton(
                text = if (isEditingExisting) "Save changes" else "Start tracking",
                onClick = {
                    val weekly = weeklySpendText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
                    val grams = gramsPerDayText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
                    onSavePlan(
                        QuitPlan(
                            quitEpochMs = quitEpochMs,
                            weeklySpend = weekly,
                            gramsPerDay = grams,
                            currencyCode = currencyCode,
                            reason = reason.trim()
                        )
                    )
                },
                modifier = Modifier.testTag("onboarding_save_button")
            )
            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = quitEpochMs)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { pickedUtc ->
                            val utcCal = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
                                timeInMillis = pickedUtc
                            }
                            val updated = Calendar.getInstance().apply {
                                timeInMillis = quitEpochMs
                                set(Calendar.YEAR, utcCal.get(Calendar.YEAR))
                                set(Calendar.MONTH, utcCal.get(Calendar.MONTH))
                                set(Calendar.DAY_OF_MONTH, utcCal.get(Calendar.DAY_OF_MONTH))
                            }
                            quitEpochMs = updated.timeInMillis
                        }
                        showDatePicker = false
                    }
                ) { Text("Set date", color = colors.amber) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel", color = colors.muted) }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val timeState = rememberTimePickerState(
            initialHour = cal.get(Calendar.HOUR_OF_DAY),
            initialMinute = cal.get(Calendar.MINUTE),
            is24Hour = false
        )
        Dialog(onDismissRequest = { showTimePicker = false }) {
            Column(
                modifier = Modifier
                    .background(colors.panel, RoundedCornerShape(18.dp))
                    .border(1.dp, colors.line, RoundedCornerShape(18.dp))
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Select time", style = MaterialTheme.typography.titleMedium, color = colors.text)
                Spacer(modifier = Modifier.height(14.dp))
                TimePicker(state = timeState)
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { showTimePicker = false }) { Text("Cancel", color = colors.muted) }
                    TextButton(
                        onClick = {
                            val updated = Calendar.getInstance().apply {
                                timeInMillis = quitEpochMs
                                set(Calendar.HOUR_OF_DAY, timeState.hour)
                                set(Calendar.MINUTE, timeState.minute)
                                set(Calendar.SECOND, 0)
                            }
                            quitEpochMs = updated.timeInMillis
                            showTimePicker = false
                        }
                    ) { Text("Set time", color = colors.amber) }
                }
            }
        }
    }
}
