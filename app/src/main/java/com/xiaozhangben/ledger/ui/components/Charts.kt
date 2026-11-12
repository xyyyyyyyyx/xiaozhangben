package com.xiaozhangben.ledger.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xiaozhangben.ledger.domain.Money
import com.xiaozhangben.ledger.domain.model.CategorySlice
import com.xiaozhangben.ledger.domain.model.MonthlyPoint
import com.xiaozhangben.ledger.ui.theme.LedgerColors
import com.xiaozhangben.ledger.ui.toComposeColor

@Composable
fun LegendDot(color: Color, label: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(color))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 月度收支双柱图。 */
@Composable
fun MonthlyTrendChart(
    points: List<MonthlyPoint>,
    modifier: Modifier = Modifier,
    chartHeight: Dp = 156.dp,
) {
    if (points.isEmpty()) return
    val expenseColor = LedgerColors.expense
    val incomeColor = LedgerColors.income
    val maximum = maxOf(points.maxOf { it.expense }, points.maxOf { it.income }, 1L)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            LegendDot(expenseColor, "支出")
            LegendDot(incomeColor, "收入")
            Spacer(Modifier.weight(1f))
            Text(
                text = "最高 ${Money.formatCompact(maximum)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(10.dp))
        Canvas(modifier = Modifier.fillMaxWidth().height(chartHeight)) {
            val count = points.size
            val slot = size.width / count
            val barWidth = (slot * 0.26f).coerceAtLeast(2f)
            val gap = (barWidth * 0.28f).coerceAtLeast(1f)
            points.forEachIndexed { index, point ->
                val centerX = slot * index + slot / 2f
                val expenseHeight = size.height * (point.expense.toFloat() / maximum.toFloat())
                val incomeHeight = size.height * (point.income.toFloat() / maximum.toFloat())
                drawRoundRect(
                    color = expenseColor,
                    topLeft = Offset(centerX - barWidth - gap / 2f, size.height - expenseHeight),
                    size = Size(barWidth, expenseHeight),
                    cornerRadius = CornerRadius(barWidth / 3f, barWidth / 3f),
                )
                drawRoundRect(
                    color = incomeColor,
                    topLeft = Offset(centerX + gap / 2f, size.height - incomeHeight),
                    size = Size(barWidth, incomeHeight),
                    cornerRadius = CornerRadius(barWidth / 3f, barWidth / 3f),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            points.forEach { point ->
                Text(
                    text = point.label,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 分类占比环形图，中间显示合计。 */
@Composable
fun CategoryDonutChart(
    slices: List<CategorySlice>,
    modifier: Modifier = Modifier,
    size: Dp = 200.dp,
) {
    val total = slices.sumOf { it.total }
    if (total <= 0L) return
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize().padding(6.dp)) {
            val strokeWidth = this.size.minDimension * 0.17f
            val diameter = this.size.minDimension - strokeWidth
            val topLeft = Offset(
                (this.size.width - diameter) / 2f,
                (this.size.height - diameter) / 2f,
            )
            var startAngle = -90f
            slices.forEach { slice ->
                val sweep = 360f * (slice.total.toFloat() / total.toFloat())
                drawArc(
                    color = slice.color.toComposeColor(),
                    startAngle = startAngle,
                    sweepAngle = (sweep - 1.5f).coerceAtLeast(0.6f),
                    useCenter = false,
                    topLeft = topLeft,
                    size = Size(diameter, diameter),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
                )
                startAngle += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "合计",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = Money.format(total),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** 每日支出柱状图，用于首页与统计页。 */
@Composable
fun DailyBarChart(
    values: List<Long>,
    modifier: Modifier = Modifier,
    chartHeight: Dp = 90.dp,
    barColor: Color = LedgerColors.expense,
    showAxis: Boolean = true,
) {
    if (values.isEmpty()) return
    val maximum = maxOf(values.maxOrNull() ?: 0L, 1L)
    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(modifier = Modifier.fillMaxWidth().height(chartHeight)) {
            val count = values.size
            val slot = size.width / count
            val barWidth = (slot * 0.6f).coerceAtLeast(1.5f)
            values.forEachIndexed { index, value ->
                val height = size.height * (value.toFloat() / maximum.toFloat())
                drawRoundRect(
                    color = if (value == 0L) barColor.copy(alpha = 0.15f) else barColor,
                    topLeft = Offset(slot * index + (slot - barWidth) / 2f, size.height - height),
                    size = Size(barWidth, height.coerceAtLeast(1.5f)),
                    cornerRadius = CornerRadius(barWidth / 2.6f, barWidth / 2.6f),
                )
            }
        }
        if (showAxis) {
            Spacer(Modifier.height(4.dp))
            // 只标 1 / 10 / 20 和最后一天：31 个格子太窄，标密了会折行
            val labelDays = setOf(1, 10, 20, values.size)
            Row(modifier = Modifier.fillMaxWidth()) {
                values.indices.forEach { index ->
                    val day = index + 1
                    Text(
                        text = if (labelDays.contains(day)) day.toString() else "",
                        // unbounded 让两位数字可以溢出这个 1/31 宽的格子，而不是被裁掉
                        modifier = Modifier.weight(1f).wrapContentWidth(unbounded = true),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    }
}

/** 横向占比条，用于分类排行。 */
@Composable
fun RatioBar(
    ratio: Float,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(trackColor),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width * ratio.coerceIn(0f, 1f)
            if (width <= 0f) return@Canvas
            drawRoundRect(
                color = color,
                topLeft = Offset.Zero,
                size = Size(width, size.height),
                cornerRadius = CornerRadius(size.height / 2f, size.height / 2f),
            )
        }
    }
}
