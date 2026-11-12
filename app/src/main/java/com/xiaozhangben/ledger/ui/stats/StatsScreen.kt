// 统计页界面：时间范围趋势、分类占比排行与每日支出。
package com.xiaozhangben.ledger.ui.stats

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xiaozhangben.ledger.domain.DateUtils
import com.xiaozhangben.ledger.domain.Money
import com.xiaozhangben.ledger.domain.StatsCalculator
import com.xiaozhangben.ledger.domain.model.CategorySlice
import com.xiaozhangben.ledger.domain.model.MonthlyPoint
import com.xiaozhangben.ledger.domain.model.TxnType
import com.xiaozhangben.ledger.ui.AppViewModelFactory
import com.xiaozhangben.ledger.ui.components.CategoryAvatar
import com.xiaozhangben.ledger.ui.components.CategoryDonutChart
import com.xiaozhangben.ledger.ui.components.DailyBarChart
import com.xiaozhangben.ledger.ui.components.EmptyState
import com.xiaozhangben.ledger.ui.components.LedgerScaffold
import com.xiaozhangben.ledger.ui.components.MonthlyTrendChart
import com.xiaozhangben.ledger.ui.components.RatioBar
import com.xiaozhangben.ledger.ui.components.SectionCard
import com.xiaozhangben.ledger.ui.components.StatTile
import com.xiaozhangben.ledger.ui.theme.LedgerColors
import com.xiaozhangben.ledger.ui.toComposeColor
import java.time.YearMonth
import kotlin.math.roundToInt

@Composable
fun StatsScreen(
    modifier: Modifier = Modifier,
    viewModel: StatsViewModel = viewModel(factory = AppViewModelFactory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentMonth = remember { DateUtils.currentMonth() }

    // 趋势：以当前月为终点，向前取 range.months 个月。
    val points = remember(state.transactions, state.range, currentMonth) {
        StatsCalculator.monthlyTrend(state.transactions, currentMonth, state.range.months)
    }
    // 区间内流水：从区间首月 1 号到当前月月底。
    val rangeTransactions = remember(state.transactions, state.range, currentMonth) {
        val startMonth = currentMonth.minusMonths((state.range.months - 1).toLong())
        StatsCalculator.inRange(
            state.transactions,
            DateUtils.startOfMonth(startMonth),
            DateUtils.endOfMonthExclusive(currentMonth),
        )
    }
    val slices = remember(rangeTransactions, state.categoryType) {
        StatsCalculator.byCategory(rangeTransactions, state.categoryType)
    }
    // 每日支出画区间最后一个月（即当前月）。
    val anchorMonth = points.lastOrNull()?.let { YearMonth.of(it.year, it.month) } ?: currentMonth
    val dailyExpense = remember(state.transactions, anchorMonth) {
        StatsCalculator.dailyExpense(state.transactions, anchorMonth)
    }

    LedgerScaffold(title = "统计", modifier = modifier) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                RangeChips(selected = state.range, onSelect = viewModel::setRange)
            }
            item {
                TrendSection(points = points)
            }
            item {
                CategorySection(
                    type = state.categoryType,
                    onTypeChange = viewModel::setCategoryType,
                    slices = slices,
                )
            }
            item {
                SectionCard(title = "每日支出（${DateUtils.monthLabel(anchorMonth)}）") {
                    DailyBarChart(values = dailyExpense)
                }
            }
        }
    }
}

/** 时间范围切换：近 6 个月 / 近 12 个月 / 本月。 */
@Composable
private fun RangeChips(selected: StatsRange, onSelect: (StatsRange) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatsRange.entries.forEach { range ->
            FilterChip(
                selected = range == selected,
                onClick = { onSelect(range) },
                label = { Text(range.label) },
            )
        }
    }
}

/** 月度趋势图 + 逐月收支结余列表（只有有数据的月份，倒序）。 */
@Composable
private fun TrendSection(points: List<MonthlyPoint>) {
    val expenseColor = LedgerColors.expense
    val incomeColor = LedgerColors.income
    val primaryColor = MaterialTheme.colorScheme.primary
    val withData = points.filter { it.expense > 0L || it.income > 0L }.reversed()

    SectionCard(title = "趋势") {
        MonthlyTrendChart(points = points)
        if (withData.isEmpty()) {
            EmptyState(icon = "📈", title = "这段时间还没有收支记录")
        } else {
            withData.forEach { point ->
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                Text(
                    text = "${point.year}年${point.month}月",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StatTile(
                        label = "支出",
                        value = Money.format(point.expense),
                        modifier = Modifier.weight(1f),
                        valueColor = expenseColor,
                    )
                    StatTile(
                        label = "收入",
                        value = Money.format(point.income),
                        modifier = Modifier.weight(1f),
                        valueColor = incomeColor,
                    )
                    StatTile(
                        label = "结余",
                        value = Money.format(point.balance),
                        modifier = Modifier.weight(1f),
                        valueColor = if (point.balance >= 0L) primaryColor else expenseColor,
                    )
                }
            }
        }
    }
}

/** 分类占比：类型切换 + 环形图 + 分类排行。 */
@Composable
private fun CategorySection(
    type: TxnType,
    onTypeChange: (TxnType) -> Unit,
    slices: List<CategorySlice>,
) {
    val total = slices.sumOf { it.total }
    SectionCard(title = "分类占比") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TxnType.entries.forEach { option ->
                FilterChip(
                    selected = option == type,
                    onClick = { onTypeChange(option) },
                    label = { Text(option.label) },
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        if (slices.isEmpty() || total <= 0L) {
            EmptyState(icon = "📊", title = "这段时间还没有数据")
        } else {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CategoryDonutChart(slices = slices)
            }
            Spacer(Modifier.height(12.dp))
            slices.forEach { slice ->
                CategorySliceRow(slice = slice, total = total)
            }
        }
    }
}

/** 单个分类的占比行：图标、名称、金额、百分比与占比条。 */
@Composable
private fun CategorySliceRow(slice: CategorySlice, total: Long) {
    val ratio = if (total <= 0L) 0f else slice.total.toFloat() / total.toFloat()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CategoryAvatar(icon = slice.icon, color = slice.color, size = 34.dp)
            Text(
                text = slice.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = Money.format(slice.total),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "${(ratio * 100f).roundToInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        RatioBar(ratio = ratio, color = slice.color.toComposeColor())
    }
}
