package com.comtudia.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.min

/**
 * Circular progress ring: a full track plus a phase-colored arc that starts at
 * twelve o'clock and sweeps clockwise. [content] is centered inside the ring.
 */
@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    strokeWidth: Dp = 12.dp,
    content: @Composable BoxScope.() -> Unit = {},
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val stroke = strokeWidth.toPx()
            val diameter = min(size.width, size.height)
            val ringSide = (diameter - stroke).coerceAtLeast(0f)
            val ringSize = Size(ringSide, ringSide)
            val topLeft = Offset(
                (size.width - ringSize.width) / 2f,
                (size.height - ringSize.height) / 2f,
            )
            val arcStyle = Stroke(width = stroke, cap = StrokeCap.Round)

            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = ringSize,
                style = arcStyle,
            )

            val fraction = progress.coerceIn(0f, 1f)
            if (fraction > 0f) {
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = 360f * fraction,
                    useCenter = false,
                    topLeft = topLeft,
                    size = ringSize,
                    style = arcStyle,
                )
            }
        }
        content()
    }
}
