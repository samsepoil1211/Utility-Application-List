package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.model.RecoveryContent
import com.example.model.VaultPayload
import com.example.ui.components.HairlineDivider
import com.example.ui.icons.InstrumentIcons
import com.example.ui.theme.LocalInstrumentColors
import kotlin.math.roundToInt

@Composable
fun BodyScreen(payload: VaultPayload) {
    val colors = LocalInstrumentColors.current
    val context = LocalContext.current

    val start = payload.currentStreakStartMs.takeIf { it > 0L } ?: payload.quitPlan.quitEpochMs
    val now = System.currentTimeMillis()
    val elapsedHours = ((now - start).coerceAtLeast(0L)).toDouble() / 3_600_000.0

    val milestones = RecoveryContent.MILESTONES
    val nextIdx = milestones.indexOfFirst { elapsedHours < it.hoursRequired }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Column {
                Text(
                    text = "Body",
                    style = MaterialTheme.typography.headlineLarge,
                    color = colors.text
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = RecoveryContent.VARIATION_DISCLAIMER,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted
                )
            }

            // Recovery Timeline (24h .. 1y)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.panel, RoundedCornerShape(16.dp))
                    .border(1.dp, colors.line, RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                milestones.forEachIndexed { idx, milestone ->
                    val isDone = elapsedHours >= milestone.hoursRequired
                    val isNext = idx == nextIdx
                    val prevHours = if (idx == 0) 0.0 else milestones[idx - 1].hoursRequired.toDouble()
                    val progress = if (isNext) {
                        val span = (milestone.hoursRequired.toDouble() - prevHours).coerceAtLeast(1.0)
                        ((elapsedHours - prevHours) / span).toFloat().coerceIn(0f, 1f)
                    } else if (isDone) 1f else 0f

                    val statusText = when {
                        isDone -> "Done"
                        isNext -> "Next · ${(progress * 100f).roundToInt()}%"
                        else -> "Upcoming"
                    }
                    val accent = when {
                        isDone -> colors.mint
                        isNext -> colors.amber
                        else -> colors.muted
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        // Status dot + short label
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.width(44.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(
                                        if (isDone || isNext) accent else colors.line,
                                        CircleShape
                                    )
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = milestone.shortLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = accent
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = milestone.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (isDone || isNext) colors.text else colors.muted
                                )
                                Text(
                                    text = statusText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = accent
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = milestone.summary,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isDone || isNext) colors.text.copy(alpha = 0.88f) else colors.muted
                            )
                            if (isNext) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(3.5.dp)
                                        .background(colors.line, RoundedCornerShape(2.dp))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(progress)
                                            .height(3.5.dp)
                                            .background(colors.amber, RoundedCornerShape(2.dp))
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = milestone.detail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.muted
                                )
                            }
                        }
                    }

                    if (idx < milestones.lastIndex) {
                        HairlineDivider()
                    }
                }
            }

            // Practical withdrawal tips
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Withdrawal guidance",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.text
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.panel, RoundedCornerShape(16.dp))
                        .border(1.dp, colors.line, RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    RecoveryContent.WITHDRAWAL_TIPS.forEachIndexed { idx, tip ->
                        Column(modifier = Modifier.padding(vertical = 8.dp)) {
                            Text(
                                text = tip.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = colors.lilac
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = tip.guidance,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.muted
                            )
                        }
                        if (idx < RecoveryContent.WITHDRAWAL_TIPS.lastIndex) {
                            HairlineDivider()
                        }
                    }
                }
            }

            // Clinical safety note & crisis helplines
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.panel, RoundedCornerShape(16.dp))
                    .border(1.dp, colors.coral.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = InstrumentIcons.Alert,
                        contentDescription = null,
                        tint = colors.coral,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "When to seek medical care",
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.coral
                    )
                }
                Text(
                    text = RecoveryContent.CLINICAL_SAFETY_NOTE,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.text
                )
                HairlineDivider()
                RecoveryContent.HELPLINES.forEach { helpline ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val dial = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${helpline.number}"))
                                context.startActivity(dial)
                            }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                            Text(
                                text = "${helpline.region} · ${helpline.name}",
                                style = MaterialTheme.typography.titleSmall,
                                color = colors.text
                            )
                            Text(
                                text = helpline.note,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.muted
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .background(colors.panelElevated, RoundedCornerShape(10.dp))
                                .border(1.dp, colors.line, RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = InstrumentIcons.Phone,
                                contentDescription = "Call ${helpline.number}",
                                tint = colors.amber,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = helpline.number,
                                style = MaterialTheme.typography.labelLarge,
                                color = colors.amber
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
