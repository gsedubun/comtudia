package com.comtudia.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.comtudia.app.core.Plan
import com.comtudia.app.core.Schedule
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * One contiguous stretch of the 24-hour bar, in minutes from the start of the
 * displayed day. Eating segments never overlap; fasting segments fill the rest
 * of the day, so the returned list always covers the whole day in order.
 */
data class TimelineSegment(
    val startMinutes: Int,
    val endMinutes: Int,
    val kind: Kind,
) {
    enum class Kind { EATING, FASTING }

    val durationMinutes: Int
        get() = endMinutes - startMinutes
}

/**
 * Length of the displayed day in minutes. Usually 1440, but 1380/1500 on a DST
 * transition day so positions stay proportional to real elapsed time.
 */
fun timelineDayMinutes(dayStart: LocalDate, zone: ZoneId): Int {
    val open = dayStart.atStartOfDay(zone)
    val close = dayStart.plusDays(1).atStartOfDay(zone)
    return Duration.between(open, close).toMinutes().toInt()
}

/**
 * Port of `internal/ui/timeline.go`'s `timelineSegments`.
 *
 * Meals from the previous cycle day and from the displayed day are clipped to
 * the day window, so a window that wraps past midnight shows up on both ends of
 * the bar (once as the tail of yesterday's cycle and once as the head of
 * today's). Fasting segments are derived from the gaps between the eating
 * segments; a plan with no meals produces no segments at all, matching the
 * desktop behaviour for an invalid plan.
 */
fun timelineSegments(plan: Plan, dayStart: LocalDate, zone: ZoneId): List<TimelineSegment> {
    val dayOpen = dayStart.atStartOfDay(zone)
    val dayClose = dayStart.plusDays(1).atStartOfDay(zone)
    val dayMinutes = Duration.between(dayOpen, dayClose).toMinutes().toInt()
    if (dayMinutes <= 0) {
        return emptyList()
    }

    val eating = mutableListOf<TimelineSegment>()
    for (day in listOf(dayStart.minusDays(1), dayStart)) {
        val slots = Schedule.slotsForDate(plan, day, zone)
        for (slot in slots) {
            if (!slot.end.isAfter(dayOpen) || !slot.start.isBefore(dayClose)) {
                continue
            }
            val start = maxOf(Duration.between(dayOpen, slot.start).toMinutes().toInt(), 0)
            val end = minOf(Duration.between(dayOpen, slot.end).toMinutes().toInt(), dayMinutes)
            if (end > start) {
                eating += TimelineSegment(start, end, TimelineSegment.Kind.EATING)
            }
        }
    }

    if (eating.isEmpty()) {
        return emptyList()
    }
    eating.sortBy { it.startMinutes }

    val segments = mutableListOf<TimelineSegment>()
    var cursor = 0
    for (segment in eating) {
        if (segment.startMinutes > cursor) {
            segments += TimelineSegment(cursor, segment.startMinutes, TimelineSegment.Kind.FASTING)
        }
        segments += segment
        if (segment.endMinutes > cursor) {
            cursor = segment.endMinutes
        }
    }
    if (cursor < dayMinutes) {
        segments += TimelineSegment(cursor, dayMinutes, TimelineSegment.Kind.FASTING)
    }
    return segments
}

/**
 * Horizontal 24-hour bar: eating blocks in the eating accent, fasting as the
 * track, a thin "now" marker, and 00/06/12/18/24 labels underneath.
 */
@Composable
fun Timeline(plan: Plan, now: ZonedDateTime, modifier: Modifier = Modifier) {
    val zone = now.zone
    val day = now.toLocalDate()
    val dayMinutes = remember(day, zone) { timelineDayMinutes(day, zone) }
    val segments = remember(plan, day, zone) { timelineSegments(plan, day, zone) }
    if (dayMinutes <= 0) {
        return
    }

    val eatingColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val markerColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f)
    val nowMinute = Duration.between(day.atStartOfDay(zone), now).toMinutes().toFloat()
    val nowFraction = (nowMinute / dayMinutes).coerceIn(0f, 1f)

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(modifier = Modifier.fillMaxWidth().height(36.dp)) {
            val barRadius = 10.dp.toPx()
            drawRoundRect(
                color = trackColor,
                cornerRadius = CornerRadius(barRadius, barRadius),
            )

            val inset = 3.dp.toPx()
            val blockRadius = 6.dp.toPx()
            for (segment in segments) {
                if (segment.kind != TimelineSegment.Kind.EATING) {
                    continue
                }
                val x0 = size.width * segment.startMinutes / dayMinutes
                val x1 = size.width * segment.endMinutes / dayMinutes
                val rawWidth = x1 - x0
                val left = x0 + if (rawWidth > inset * 3f) inset else 0f
                val right = x1 - if (rawWidth > inset * 3f) inset else 0f
                val blockWidth = (right - left).coerceAtLeast(2.dp.toPx())
                drawRoundRect(
                    color = eatingColor,
                    topLeft = Offset(left, inset),
                    size = Size(
                        blockWidth.coerceAtMost(size.width - left),
                        size.height - inset * 2f,
                    ),
                    cornerRadius = CornerRadius(blockRadius, blockRadius),
                )
            }

            val markerX = size.width * nowFraction
            drawRoundRect(
                color = markerColor,
                topLeft = Offset(markerX - 1.dp.toPx(), 0f),
                size = Size(2.dp.toPx(), size.height),
                cornerRadius = CornerRadius(1.dp.toPx(), 1.dp.toPx()),
            )
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
        ) {
            val labels = listOf("00", "06", "12", "18", "24")
            val labelWidth = 34.dp
            labels.forEachIndexed { index, label ->
                val x = when (index) {
                    0 -> 0.dp
                    labels.lastIndex -> maxWidth - labelWidth
                    else -> maxWidth * (index / labels.lastIndex.toFloat()) - labelWidth / 2f
                }
                Text(
                    text = label,
                    modifier = Modifier.offset(x = x).width(labelWidth),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = when (index) {
                        0 -> TextAlign.Start
                        labels.lastIndex -> TextAlign.End
                        else -> TextAlign.Center
                    },
                    maxLines = 1,
                )
            }
        }
    }
}
