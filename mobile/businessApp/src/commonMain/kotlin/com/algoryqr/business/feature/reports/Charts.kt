package com.algoryqr.business.feature.reports

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.algoryqr.shared.ui.AppColors
import kotlin.math.roundToInt

private val PillShape = RoundedCornerShape(50)

/** Draw-in animation shared by every chart: restarts whenever [key] (the data) changes. */
@Composable
private fun rememberReveal(key: Any?): Float {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(key) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durationMillis = 650, easing = FastOutSlowInEasing))
    }
    return progress.value
}

/**
 * Smooth area chart with a soft gradient under the line. [previous] is drawn as a dashed grey line behind it for
 * comparison. Tapping or dragging selects the nearest point; the caller shows its value, so nothing floats over the chart.
 */
@Composable
fun AreaChart(
    values: List<Double>,
    previous: List<Double>?,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    description: String,
    modifier: Modifier = Modifier,
) {
    val accent = AppColors.Accent
    val grid = AppColors.Line
    val muted = AppColors.Muted
    val surface = AppColors.Surface
    val reveal = rememberReveal(values)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentSelected by rememberUpdatedState(selected)
    val max = (values + previous.orEmpty()).maxOrNull()?.takeIf { it > 0 } ?: 1.0

    Canvas(
        modifier
            .semantics { contentDescription = description }
            .pointerInput(values.size) {
                fun indexAt(x: Float) = if (values.size < 2) 0 else (x / size.width * (values.size - 1)).roundToInt().coerceIn(0, values.lastIndex)
                detectTapGestures { offset ->
                    val index = indexAt(offset.x)
                    currentOnSelect(if (index == currentSelected) null else index)
                }
            }
            .pointerInput(values.size) {
                fun indexAt(x: Float) = if (values.size < 2) 0 else (x / size.width * (values.size - 1)).roundToInt().coerceIn(0, values.lastIndex)
                detectHorizontalDragGestures(onDragEnd = { currentOnSelect(null) }) { change, _ -> currentOnSelect(indexAt(change.position.x)) }
            },
    ) {
        if (values.isEmpty()) return@Canvas
        val top = 10.dp.toPx()
        val chartHeight = size.height - top
        val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
        fun points(series: List<Double>) = series.mapIndexed { i, value ->
            val x = if (series.size < 2) size.width / 2 else size.width * i / series.lastIndex
            Offset(x, top + chartHeight * (1f - (value / max).toFloat()))
        }

        for (k in 0..2) {
            val y = top + chartHeight * k / 3f
            drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx(), pathEffect = dash)
        }
        drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())

        previous?.takeIf { it.size > 1 }?.let { series ->
            drawPath(smoothPath(points(series)), muted.copy(alpha = 0.55f), style = Stroke(1.5.dp.toPx(), pathEffect = dash))
        }

        val current = points(values)
        val line = smoothPath(current)
        clipRect(right = size.width * reveal) {
            val area = Path().apply {
                addPath(line)
                lineTo(current.last().x, size.height)
                lineTo(current.first().x, size.height)
                close()
            }
            drawPath(area, Brush.verticalGradient(listOf(accent.copy(alpha = 0.26f), accent.copy(alpha = 0f)), startY = top, endY = size.height))
            drawPath(line, accent, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }

        selected?.let(current::getOrNull)?.let { point ->
            drawLine(accent.copy(alpha = 0.35f), Offset(point.x, top), Offset(point.x, size.height), strokeWidth = 1.dp.toPx())
            drawCircle(surface, radius = 7.dp.toPx(), center = point)
            drawCircle(accent, radius = 4.5.dp.toPx(), center = point)
        }
    }
}

/** Curve through every point; control points sit halfway between neighbours so the line never overshoots below zero. */
private fun smoothPath(points: List<Offset>): Path = Path().apply {
    if (points.isEmpty()) return@apply
    moveTo(points.first().x, points.first().y)
    for (i in 1 until points.size) {
        val from = points[i - 1]
        val to = points[i]
        val midX = (from.x + to.x) / 2
        cubicTo(midX, from.y, midX, to.y, to.x, to.y)
    }
}

/** Pill-shaped bars; the [highlight] bar (the busiest) and the selected bar are in full brand colour. */
@Composable
fun ColumnChart(
    values: List<Double>,
    highlight: Int?,
    selected: Int?,
    onSelect: (Int?) -> Unit,
    description: String,
    modifier: Modifier = Modifier,
) {
    val accent = AppColors.Accent
    val reveal = rememberReveal(values)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentSelected by rememberUpdatedState(selected)
    val max = values.maxOrNull()?.takeIf { it > 0 } ?: 1.0

    Canvas(
        modifier
            .semantics { contentDescription = description }
            .pointerInput(values.size) {
                detectTapGestures { offset ->
                    if (values.isEmpty()) return@detectTapGestures
                    val index = (offset.x / (size.width.toFloat() / values.size)).toInt().coerceIn(0, values.lastIndex)
                    currentOnSelect(if (index == currentSelected) null else index)
                }
            },
    ) {
        if (values.isEmpty()) return@Canvas
        val slot = size.width / values.size
        val barWidth = slot * 0.62f
        val minHeight = 4.dp.toPx()
        values.forEachIndexed { i, value ->
            val height = (size.height * (value / max).toFloat() * reveal).coerceAtLeast(minHeight)
            val strong = i == selected || (selected == null && i == highlight)
            drawRoundRect(
                color = if (strong) accent else accent.copy(alpha = 0.2f),
                topLeft = Offset(slot * i + (slot - barWidth) / 2, size.height - height),
                size = Size(barWidth, height),
                cornerRadius = CornerRadius(barWidth / 2, barWidth / 2),
            )
        }
    }
}

/** One slice of a [DonutChart] and its legend row. */
data class Slice(val label: String, val value: Double, val color: Color)

/** Ring chart with a thin gap between slices and free space in the middle for the total. */
@Composable
fun DonutChart(
    slices: List<Slice>,
    description: String,
    modifier: Modifier = Modifier,
    thickness: Dp = 16.dp,
    center: @Composable BoxScope.() -> Unit = {},
) {
    val track = AppColors.Line
    val reveal = rememberReveal(slices)
    val total = slices.sumOf { it.value }
    Box(modifier.semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val stroke = thickness.toPx()
            val diameter = size.minDimension - stroke
            val topLeft = Offset((size.width - diameter) / 2, (size.height - diameter) / 2)
            val arcSize = Size(diameter, diameter)
            drawArc(track, 0f, 360f, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(stroke))
            if (total <= 0) return@Canvas
            val visible = slices.filter { it.value > 0 }
            val gap = if (visible.size > 1) 2.5f else 0f
            var start = -90f
            visible.forEach { slice ->
                val sweep = (slice.value / total * 360).toFloat() * reveal
                drawArc(
                    slice.color,
                    startAngle = start + gap / 2,
                    sweepAngle = (sweep - gap).coerceAtLeast(0.5f),
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(stroke),
                )
                start += sweep
            }
        }
        center()
    }
}

/** Rounded track with an animated fill, used for shares and rankings. */
@Composable
fun ShareBar(fraction: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 8.dp) {
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(650, easing = FastOutSlowInEasing))
    Box(modifier.fillMaxWidth().height(height).clip(PillShape).background(color.copy(alpha = 0.14f))) {
        Box(Modifier.fillMaxWidth(animated).fillMaxHeight().clip(PillShape).background(color))
    }
}

/** Compact range switch for a card header: "1G · 7G · 30G" in a soft track, the chosen one raised on a white chip. */
@Composable
fun RangePills(ranges: List<ReportRange>, selected: ReportRange, onSelect: (ReportRange) -> Unit) {
    OptionPills(ranges, selected, onSelect, label = { it.label }, accessibilityLabel = { it.description })
}

/** Same look as [RangePills] for any small set of options, like "Ciro / Adet". */
@Composable
fun <T> OptionPills(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    accessibilityLabel: (T) -> String = label,
) {
    Row(Modifier.clip(PillShape).background(AppColors.Subtle).padding(3.dp)) {
        options.forEach { option ->
            val isSelected = option == selected
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .clip(PillShape)
                    .then(if (isSelected) Modifier.background(AppColors.Surface).border(1.dp, AppColors.Line, PillShape) else Modifier)
                    .clickable(role = Role.Tab) { onSelect(option) }
                    .semantics {
                        contentDescription = accessibilityLabel(option)
                        this.selected = isSelected
                    }
                    .padding(horizontal = 11.dp, vertical = 7.dp),
            ) {
                Text(
                    label(option),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (isSelected) AppColors.Ink else AppColors.Muted,
                )
            }
        }
    }
}

/** Tiny area line for previews: no grid, no touch, so the card around it stays one tap target. */
@Composable
fun Sparkline(values: List<Double>, modifier: Modifier = Modifier, color: Color = AppColors.Accent) {
    val reveal = rememberReveal(values)
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val max = values.maxOrNull()?.takeIf { it > 0 } ?: 1.0
        val top = 3.dp.toPx()
        val points = values.mapIndexed { i, value ->
            Offset(size.width * i / values.lastIndex, top + (size.height - top) * (1f - (value / max).toFloat()))
        }
        val line = smoothPath(points)
        clipRect(right = size.width * reveal) {
            val area = Path().apply {
                addPath(line)
                lineTo(points.last().x, size.height)
                lineTo(points.first().x, size.height)
                close()
            }
            drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.22f), color.copy(alpha = 0f))))
            drawPath(line, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/** Tiny bar strip for previews, the tallest bar in full colour. */
@Composable
fun MiniBars(values: List<Double>, modifier: Modifier = Modifier, color: Color = AppColors.Accent) {
    val reveal = rememberReveal(values)
    Canvas(modifier) {
        if (values.isEmpty()) return@Canvas
        val max = values.maxOrNull()?.takeIf { it > 0 } ?: 1.0
        val peak = values.indexOf(values.max())
        val slot = size.width / values.size
        val barWidth = slot * 0.6f
        values.forEachIndexed { i, value ->
            val height = (size.height * (value / max).toFloat() * reveal).coerceAtLeast(2.dp.toPx())
            drawRoundRect(
                color = if (i == peak) color else color.copy(alpha = 0.22f),
                topLeft = Offset(slot * i + (slot - barWidth) / 2, size.height - height),
                size = Size(barWidth, height),
                cornerRadius = CornerRadius(barWidth / 2, barWidth / 2),
            )
        }
    }
}

/** One bar split into coloured parts, e.g. each person's share of the team's revenue. */
@Composable
fun StackedShareBar(parts: List<Pair<Double, Color>>, modifier: Modifier = Modifier, height: Dp = 10.dp) {
    val total = parts.sumOf { it.first }.takeIf { it > 0 } ?: return
    val reveal = rememberReveal(parts)
    Canvas(modifier.fillMaxWidth().height(height).clip(PillShape)) {
        var x = 0f
        val gap = 2.dp.toPx()
        parts.forEach { (value, color) ->
            val width = (size.width * (value / total).toFloat() * reveal)
            drawRect(color, topLeft = Offset(x, 0f), size = Size((width - gap).coerceAtLeast(0f), size.height))
            x += width
        }
    }
}
