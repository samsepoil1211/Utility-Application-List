package com.example.model

import com.example.ui.components.ChartPoint
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

data class TriggerCount(
    val trigger: String,
    val count: Int,
    val fraction: Float
)

data class ComputedStats(
    val hasAnyData: Boolean,
    val cravingSeries14d: List<ChartPoint>,
    val moodSeries14d: List<ChartPoint>,
    val sleepSeries14d: List<ChartPoint>,
    val cravingTrendText: String,
    val moodTrendText: String,
    val sleepTrendText: String,
    val cravingsResisted: Int,
    val slipsCount: Int,
    val topTriggers: List<TriggerCount>,
    val peakCravingHour: Int?, // 0..23
    val peakCravingLabel: String?
)

object StatsAnalytics {
    private val keyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val labelFormat = SimpleDateFormat("MMM d", Locale.US)

    fun todayDateKey(nowMs: Long = System.currentTimeMillis()): String =
        synchronized(keyFormat) { keyFormat.format(Date(nowMs)) }

    fun compute(payload: VaultPayload, nowMs: Long = System.currentTimeMillis()): ComputedStats {
        val hasAny = payload.checkIns.isNotEmpty() || payload.cravings.isNotEmpty() || payload.slips.isNotEmpty()

        // Build 14-day window ending today
        val days = (13 downTo 0).map { offset ->
            val cal = Calendar.getInstance().apply {
                timeInMillis = nowMs
                add(Calendar.DAY_OF_YEAR, -offset)
            }
            val date = cal.time
            val key = synchronized(keyFormat) { keyFormat.format(date) }
            val label = synchronized(labelFormat) { labelFormat.format(date) }
            key to label
        }

        val checkInsByDay = payload.checkIns.groupBy { it.dateKey }
        val cravingsByDay = payload.cravings.groupBy {
            synchronized(keyFormat) { keyFormat.format(Date(it.timestampMs)) }
        }

        val cravingPoints = mutableListOf<ChartPoint>()
        val moodPoints = mutableListOf<ChartPoint>()
        val sleepPoints = mutableListOf<ChartPoint>()

        val prevWeekCraving = mutableListOf<Double>()
        val currWeekCraving = mutableListOf<Double>()
        val prevWeekMood = mutableListOf<Double>()
        val currWeekMood = mutableListOf<Double>()
        val prevWeekSleep = mutableListOf<Double>()
        val currWeekSleep = mutableListOf<Double>()

        days.forEachIndexed { idx, (key, label) ->
            val dayCheckIns = checkInsByDay[key].orEmpty()
            val dayCravings = cravingsByDay[key].orEmpty()

            // Combine check-in craving level and SOS craving intensities for the day's craving index
            val cravingVals = dayCheckIns.map { it.cravingLevel.toDouble() } +
                dayCravings.map { it.intensity.toDouble() }
            val avgCraving = if (cravingVals.isNotEmpty()) cravingVals.average() else null
            val avgMood = if (dayCheckIns.isNotEmpty()) dayCheckIns.map { it.mood.toDouble() }.average() else null
            val avgSleep = if (dayCheckIns.isNotEmpty()) dayCheckIns.map { it.sleepHours }.average() else null

            cravingPoints.add(
                ChartPoint(
                    dayLabel = label,
                    value = avgCraving?.toFloat(),
                    valueText = avgCraving?.let { String.format(Locale.US, "%.1f / 10", it) } ?: "—"
                )
            )
            moodPoints.add(
                ChartPoint(
                    dayLabel = label,
                    value = avgMood?.toFloat(),
                    valueText = avgMood?.let { String.format(Locale.US, "%.1f / 5", it) } ?: "—"
                )
            )
            sleepPoints.add(
                ChartPoint(
                    dayLabel = label,
                    value = avgSleep?.toFloat(),
                    valueText = avgSleep?.let { String.format(Locale.US, "%.1fh", it) } ?: "—"
                )
            )

            if (idx < 7) {
                avgCraving?.let { prevWeekCraving.add(it) }
                avgMood?.let { prevWeekMood.add(it) }
                avgSleep?.let { prevWeekSleep.add(it) }
            } else {
                avgCraving?.let { currWeekCraving.add(it) }
                avgMood?.let { currWeekMood.add(it) }
                avgSleep?.let { currWeekSleep.add(it) }
            }
        }

        val cravingTrend = buildTrendText(
            prev = prevWeekCraving,
            curr = currWeekCraving,
            unit = "pts",
            lowerIsBetter = true,
            emptyHint = "Log check-ins or cravings across two weeks to compare."
        )
        val moodTrend = buildTrendText(
            prev = prevWeekMood,
            curr = currWeekMood,
            unit = "pts",
            lowerIsBetter = false,
            emptyHint = "Check in over two weeks to see mood direction."
        )
        val sleepTrend = buildTrendText(
            prev = prevWeekSleep,
            curr = currWeekSleep,
            unit = "h",
            lowerIsBetter = false,
            emptyHint = "Track sleep over two weeks to compare averages."
        )

        // Triggers across cravings + slips
        val triggerTallies = mutableMapOf<String, Int>()
        payload.cravings.forEach { triggerTallies[it.trigger] = (triggerTallies[it.trigger] ?: 0) + 1 }
        payload.slips.forEach { triggerTallies[it.trigger] = (triggerTallies[it.trigger] ?: 0) + 1 }
        val maxTriggerCount = triggerTallies.values.maxOrNull()?.coerceAtLeast(1) ?: 1
        val topTriggers = triggerTallies.entries
            .sortedByDescending { it.value }
            .take(5)
            .map { TriggerCount(it.key, it.value, it.value.toFloat() / maxTriggerCount.toFloat()) }

        // Peak craving hour computed strictly from user's own craving & slip timestamps
        val hourCounts = IntArray(24)
        val cal = Calendar.getInstance()
        payload.cravings.forEach {
            cal.timeInMillis = it.timestampMs
            hourCounts[cal.get(Calendar.HOUR_OF_DAY)]++
        }
        payload.slips.forEach {
            cal.timeInMillis = it.timestampMs
            hourCounts[cal.get(Calendar.HOUR_OF_DAY)]++
        }
        val maxHourCount = hourCounts.maxOrNull() ?: 0
        val peakHour = if (maxHourCount > 0) hourCounts.indices.maxByOrNull { hourCounts[it] } else null
        val peakLabel = peakHour?.let { formatHourRange(it) }

        val resisted = payload.cravings.count { it.resisted }

        return ComputedStats(
            hasAnyData = hasAny,
            cravingSeries14d = cravingPoints,
            moodSeries14d = moodPoints,
            sleepSeries14d = sleepPoints,
            cravingTrendText = cravingTrend,
            moodTrendText = moodTrend,
            sleepTrendText = sleepTrend,
            cravingsResisted = resisted,
            slipsCount = payload.slips.size,
            topTriggers = topTriggers,
            peakCravingHour = peakHour,
            peakCravingLabel = peakLabel
        )
    }

    fun formatHourRange(hour24: Int): String {
        val next = (hour24 + 1) % 24
        return "${formatHour12(hour24)} – ${formatHour12(next)}"
    }

    fun formatHour12(hour24: Int): String {
        val h = hour24 % 12
        val display = if (h == 0) 12 else h
        val amPm = if (hour24 < 12) "AM" else "PM"
        return "$display $amPm"
    }

    private fun buildTrendText(
        prev: List<Double>,
        curr: List<Double>,
        unit: String,
        lowerIsBetter: Boolean,
        emptyHint: String
    ): String {
        if (curr.isEmpty()) return emptyHint
        if (prev.isEmpty()) {
            val avg = curr.average()
            return String.format(Locale.US, "7-day average: %.1f %s (collecting baseline)", avg, unit)
        }
        val prevAvg = prev.average()
        val currAvg = curr.average()
        val diff = currAvg - prevAvg
        if (kotlin.math.abs(diff) < 0.15) {
            return String.format(Locale.US, "Steady vs last week (%.1f %s avg)", currAvg, unit)
        }
        val direction = if (diff > 0) "up" else "down"
        val tone = when {
            lowerIsBetter && diff < 0 -> "easing"
            !lowerIsBetter && diff > 0 -> "improving"
            else -> "worth watching"
        }
        return String.format(
            Locale.US,
            "Week-over-week %s %.1f %s (%s)",
            direction,
            kotlin.math.abs(diff),
            unit,
            tone
        )
    }

    fun formatDurationDaysHours(ms: Long): String {
        val safe = ms.coerceAtLeast(0L)
        val totalHours = safe / 3_600_000L
        val d = totalHours / 24L
        val h = totalHours % 24L
        return if (d > 0) "${d}d ${h}h" else "${h}h"
    }

    fun computeMoneySaved(elapsedMs: Long, weeklySpend: Double): Double {
        if (elapsedMs <= 0L || weeklySpend <= 0.0) return 0.0
        val weeks = elapsedMs.toDouble() / (7.0 * 24.0 * 3_600_000.0)
        return (weeks * weeklySpend).coerceAtLeast(0.0)
    }

    fun computeGramsAvoided(elapsedMs: Long, gramsPerDay: Double): Double {
        if (elapsedMs <= 0L || gramsPerDay <= 0.0) return 0.0
        val days = elapsedMs.toDouble() / (24.0 * 3_600_000.0)
        return ((days * gramsPerDay) * 10.0).roundToInt() / 10.0
    }
}
