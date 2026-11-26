package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.model.ComputedStats
import com.example.ui.components.HairlineDivider
import com.example.ui.components.SmoothAreaChart
import com.example.ui.icons.InstrumentIcons
import com.example.ui.theme.LocalInstrumentColors

@Composable
fun StatsScreen(stats: ComputedStats) {
    val colors = LocalInstrumentColors.current

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
                    text = "Stats",
                    style = MaterialTheme.typography.headlineLarge,
                    color = colors.text
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "14-day patterns computed privately from your encrypted entries.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted
                )
            }

            if (!stats.hasAnyData) {
                // Truly empty first-launch state — no fake charts or seeded trends
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 32.dp)
                        .background(colors.panel, RoundedCornerShape(18.dp))
                        .border(1.dp, colors.line, RoundedCornerShape(18.dp))
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = InstrumentIcons.Stats,
                        contentDescription = null,
                        tint = colors.muted,
                        modifier = Modifier.size(34.dp)
                    )
                    Text(
                        text = "Your charts start with your first log",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.text,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Complete a daily check-in or log a craving session from Today. Your 14-day trends, top triggers, and peak craving hour will appear here automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.muted,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                // Cravings resisted vs slips + Peak craving hour summary strip
                Column {
                    HairlineDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
                            .padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "${stats.cravingsResisted}",
                                style = MaterialTheme.typography.headlineMedium,
                                color = colors.mint
                            )
                            Text(
                                text = "Resisted",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.muted
                            )
                        }
                        VerticalDivider(
                            thickness = 1.dp,
                            color = colors.line,
                            modifier = Modifier.fillMaxHeight().padding(vertical = 4.dp)
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "${stats.slipsCount}",
                                style = MaterialTheme.typography.headlineMedium,
                                color = if (stats.slipsCount > 0) colors.coral else colors.text
                            )
                            Text(
                                text = "Slips",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.muted
                            )
                        }
                        VerticalDivider(
                            thickness = 1.dp,
                            color = colors.line,
                            modifier = Modifier.fillMaxHeight().padding(vertical = 4.dp)
                        )
                        Column(
                            modifier = Modifier.weight(1.3f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = stats.peakCravingLabel ?: "—",
                                style = MaterialTheme.typography.titleSmall,
                                color = colors.amber,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Peak craving hour",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.muted
                            )
                        }
                    }
                    HairlineDivider()
                }

                // 14-day smooth charts
                SmoothAreaChart(
                    title = "Craving level",
                    subtitle = stats.cravingTrendText,
                    points = stats.cravingSeries14d,
                    minY = 0f,
                    maxY = 10f,
                    accentColor = colors.amber
                )

                SmoothAreaChart(
                    title = "Mood",
                    subtitle = stats.moodTrendText,
                    points = stats.moodSeries14d,
                    minY = 1f,
                    maxY = 5f,
                    accentColor = colors.mint
                )

                SmoothAreaChart(
                    title = "Sleep hours",
                    subtitle = stats.sleepTrendText,
                    points = stats.sleepSeries14d,
                    minY = 0f,
                    maxY = 12f,
                    accentColor = colors.lilac
                )

                // Top triggers breakdown
                if (stats.topTriggers.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.panel, RoundedCornerShape(16.dp))
                            .border(1.dp, colors.line, RoundedCornerShape(16.dp))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Top triggers",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.text
                        )
                        stats.topTriggers.forEach { item ->
                            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = item.trigger,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = colors.text
                                    )
                                    Text(
                                        text = "${item.count}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = colors.muted
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .background(colors.line, RoundedCornerShape(2.dp))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(item.fraction.coerceIn(0.08f, 1f))
                                            .height(4.dp)
                                            .background(colors.lilac, RoundedCornerShape(2.dp))
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
