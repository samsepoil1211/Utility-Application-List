package com.example.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.ui.icons.InstrumentIcons
import com.example.ui.motion.InstrumentMotion
import com.example.ui.theme.LocalInstrumentColors
import com.example.ui.theme.LocalReduceMotion

enum class MainTab(val label: String, val icon: ImageVector, val tag: String) {
    Today("Today", InstrumentIcons.Dial, "tab_today"),
    Stats("Stats", InstrumentIcons.Stats, "tab_stats"),
    Body("Body", InstrumentIcons.Body, "tab_body"),
    Settings("Settings", InstrumentIcons.Settings, "tab_settings")
}

/**
 * Custom Dusk Instrument bottom navigation bar with a spring-animated sliding active indicator,
 * proper navigationBarsPadding(), and >=48dp touch targets.
 */
@Composable
fun InstrumentBottomBar(
    selectedTab: MainTab,
    onSelect: (MainTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalInstrumentColors.current
    val reduceMotion = LocalReduceMotion.current
    val context = LocalContext.current
    val tabs = MainTab.entries

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.panel)
            .navigationBarsPadding()
    ) {
        HorizontalDivider(thickness = 1.dp, color = colors.line)
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
        ) {
            val tabWidth = maxWidth / tabs.size
            val indicatorWidth = 36.dp
            val targetOffset = tabWidth * selectedTab.ordinal + (tabWidth - indicatorWidth) / 2
            val animatedOffset by animateDpAsState(
                targetValue = targetOffset,
                animationSpec = if (reduceMotion) {
                    spring(stiffness = Spring.StiffnessHigh)
                } else {
                    spring(dampingRatio = 0.8f, stiffness = 500f)
                },
                label = "tabIndicatorOffset"
            )

            // Sliding active indicator bar along top edge
            Box(
                modifier = Modifier
                    .offset(x = animatedOffset)
                    .width(indicatorWidth)
                    .height(2.5.dp)
                    .background(colors.amber, RoundedCornerShape(bottomStart = 3.dp, bottomEnd = 3.dp))
            )

            Row(modifier = Modifier.fillMaxWidth()) {
                tabs.forEach { tab ->
                    val isSelected = tab == selectedTab
                    val tint = if (isSelected) colors.amber else colors.muted
                    val interaction = remember { MutableInteractionSource() }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .height(64.dp)
                            .testTag(tab.tag)
                            .semantics { selected = isSelected }
                            .clickable(
                                interactionSource = interaction,
                                indication = ripple(bounded = true, color = colors.amber),
                                role = Role.Tab
                            ) {
                                if (!isSelected) {
                                    InstrumentMotion.lightHaptic(context)
                                    onSelect(tab)
                                }
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.label,
                            tint = tint,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = tint
                        )
                    }
                }
            }
        }
    }
}

/**
 * Adaptive Navigation Rail for Medium/Expanded tablet and foldable layouts.
 */
@Composable
fun InstrumentNavigationRail(
    selectedTab: MainTab,
    onSelect: (MainTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalInstrumentColors.current
    val context = LocalContext.current
    val tabs = MainTab.entries

    Row(modifier = modifier.fillMaxHeight().background(colors.panel)) {
        Column(
            modifier = Modifier
                .width(84.dp)
                .fillMaxHeight()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            tabs.forEach { tab ->
                val isSelected = tab == selectedTab
                val tint = if (isSelected) colors.amber else colors.muted
                val interaction = remember { MutableInteractionSource() }
                Column(
                    modifier = Modifier
                        .size(width = 64.dp, height = 56.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isSelected) colors.panelElevated else colors.panel)
                        .testTag(tab.tag)
                        .semantics { selected = isSelected }
                        .clickable(
                            interactionSource = interaction,
                            indication = ripple(bounded = true, color = colors.amber),
                            role = Role.Tab
                        ) {
                            if (!isSelected) {
                                InstrumentMotion.lightHaptic(context)
                                onSelect(tab)
                            }
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.label,
                        tint = tint,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = tint
                    )
                }
            }
        }
        VerticalDivider(thickness = 1.dp, color = colors.line)
    }
}
