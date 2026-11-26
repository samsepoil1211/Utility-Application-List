package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.example.ui.theme.LocalInstrumentColors
import kotlin.math.abs

data class ChartPoint(
    val dayLabel: String,
    val value: Float?,
    val valueText: String
)

/**
 * Smooth cubic-Bezier line/area chart drawn natively on Canvas (no external chart libraries).
 * Highlights the latest logged value with a crisp dot and reveals a value tooltip on tap.
 */
@Composable
fun SmoothAreaChart(
    title: String,
    subtitle: String,
    points: List<ChartPoint>,
    minY: Float,
    maxY: Float,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val colors = LocalInstrumentColors.current
    var selectedIdx by remember(points) { mutableStateOf<Int?>(null) }

    val nonNullIndices = remember(points) {
        points.indices.filter { points[it].value != null }
    }

    Column(
        modifier = modifier
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
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.text
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted
                )
            }
            val activePoint = selectedIdx?.let { points.getOrNull(it) }
                ?: nonNullIndices.lastOrNull()?.let { points[it] }
            if (activePoint != null && activePoint.value != null) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = activePoint.valueText,
                        style = MaterialTheme.typography.titleMedium,
                        color = accentColor
                    )
                    Text(
                        text = if (selectedIdx != null) activePoint.dayLabel else "Latest (${activePoint.dayLabel})",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.muted
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (nonNullIndices.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No entries in the last 14 days",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted
                )
            }
        } else {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(132.dp)
                    .pointerInput(points) {
                        detectTapGestures { offset ->
                            if (points.isEmpty()) return@detectTapGestures
                            val padH = 10.dp.toPx()
                            val usableW = (size.width - padH * 2).coerceAtLeast(1f)
                            val stepX = if (points.size > 1) usableW / (points.size - 1) else 0f
                            var nearest: Int? = null
                            var bestDist = Float.MAX_VALUE
                            for (i in nonNullIndices) {
                                val x = padH + i * stepX
                                val d = abs(offset.x - x)
                                if (d < bestDist) {
                                    bestDist = d
                                    nearest = i
                                }
                            }
                            selectedIdx = if (selectedIdx == nearest) null else nearest
                        }
                    }
            ) {
                val padH = 10.dp.toPx()
                val padTop = 10.dp.toPx()
                val padBottom = 12.dp.toPx()
                val chartW = (size.width - padH * 2).coerceAtLeast(1f)
                val chartH = (size.height - padTop - padBottom).coerceAtLeast(1f)
                val range = (maxY - minY).coerceAtLeast(1f)
                val stepX = if (points.size > 1) chartW / (points.size - 1) else 0f

                // 3 subtle horizontal hairlines
                for (g in 0..2) {
                    val y = padTop + chartH * (g / 2f)
                    drawLine(
                        color = colors.line.copy(alpha = 0.55f),
                        start = Offset(padH, y),
                        end = Offset(size.width - padH, y),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                val coords = nonNullIndices.map { i ->
                    val v = (points[i].value ?: minY).coerceIn(minY, maxY)
                    val x = padH + i * stepX
                    val y = padTop + chartH * (1f - ((v - minY) / range))
                    i to Offset(x, y)
                }

                if (coords.size >= 2) {
                    val linePath = Path().apply {
                        moveTo(coords.first().second.x, coords.first().second.y)
                        for (k in 0 until coords.size - 1) {
                            val p0 = coords[k].second
                            val p1 = coords[k + 1].second
                            val cx = (p0.x + p1.x) / 2f
                            cubicTo(cx, p0.y, cx, p1.y, p1.x, p1.y)
                        }
                    }

                    val fillPath = Path().apply {
                        addPath(linePath)
                        lineTo(coords.last().second.x, padTop + chartH)
                        lineTo(coords.first().second.x, padTop + chartH)
                        close()
                    }

                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                accentColor.copy(alpha = 0.22f),
                                accentColor.copy(alpha = 0.0f)
                            ),
                            startY = padTop,
                            endY = padTop + chartH
                        )
                    )

                    drawPath(
                        path = linePath,
                        color = accentColor,
                        style = Stroke(
                            width = 2.2.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }

                // Selected point vertical guide + dot, otherwise latest point dot
                val focusIdx = selectedIdx ?: coords.last().first
                val focusOffset = coords.firstOrNull { it.first == focusIdx }?.second ?: coords.last().second

                if (selectedIdx != null) {
                    drawLine(
                        color = accentColor.copy(alpha = 0.4f),
                        start = Offset(focusOffset.x, padTop),
                        end = Offset(focusOffset.x, padTop + chartH),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                // Latest point dot (or focused dot)
                drawCircle(
                    color = colors.panel,
                    radius = 5.5.dp.toPx(),
                    center = focusOffset
                )
                drawCircle(
                    color = accentColor,
                    radius = 4.dp.toPx(),
                    center = focusOffset
                )
            }

            // Minimal date axis endpoints
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = points.firstOrNull()?.dayLabel.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.muted
                )
                Text(
                    text = "Tap graph to inspect",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.muted.copy(alpha = 0.7f)
                )
                Text(
                    text = points.lastOrNull()?.dayLabel.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.muted
                )
            }
        }
    }
}
