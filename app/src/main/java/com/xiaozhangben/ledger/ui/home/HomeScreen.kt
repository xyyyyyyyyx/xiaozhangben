// 首页：月份切换、本月结余与收支、每日支出图、总预算提醒、账户概览与最近记录。
package com.xiaozhangben.ledger.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xiaozhangben.ledger.R
import com.xiaozhangben.ledger.domain.DateUtils
import com.xiaozhangben.ledger.domain.Money
import com.xiaozhangben.ledger.domain.model.AccountBalance
import com.xiaozhangben.ledger.domain.model.BudgetProgress
import com.xiaozhangben.ledger.domain.model.Transaction
import com.xiaozhangben.ledger.ui.AppViewModelFactory
import com.xiaozhangben.ledger.ui.components.AmountText
import com.xiaozhangben.ledger.ui.components.CategoryAvatar
import com.xiaozhangben.ledger.ui.components.DailyBarChart
import com.xiaozhangben.ledger.ui.components.EmptyState
import com.xiaozhangben.ledger.ui.components.LedgerScaffold
import com.xiaozhangben.ledger.ui.components.RatioBar
import com.xiaozhangben.ledger.ui.components.SectionCard
import com.xiaozhangben.ledger.ui.components.StatTile
import com.xiaozhangben.ledger.ui.theme.LedgerColors

@Composable
fun HomeScreen(
    onAddExpense: () -> Unit,
    onAddIncome: () -> Unit,
    onEditTransaction: (Long) -> Unit,
    onOpenRecords: () -> Unit,
    onOpenBudget: () -> Unit,
    onOpenAccounts: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel(factory = AppViewModelFactory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LedgerScaffold(
        // 读 strings.xml 里的 app_name，改名字只需改一处
        title = stringResource(R.string.app_name),
        modifier = modifier,
        actions = {
            IconButton(onClick = onOpenSettings) {
                Icon(imageVector = Icons.Default.Settings, contentDescription = "设置")
            }
        },
        content = { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    MonthSelectorRow(
                        label = DateUtils.monthLabel(state.month),
                        canGoNext = state.canGoNextMonth,
                        onPrevious = viewModel::previousMonth,
                        onNext = viewModel::nextMonth,
                    )
                }
                item {
                    SummaryCard(
                        balanceText = Money.format(state.summary.balance),
                        balanceColor = when {
                            state.summary.balance > 0L -> LedgerColors.income
                            state.summary.balance < 0L -> LedgerColors.expense
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                        incomeText = Money.format(state.summary.income),
                        expenseText = Money.format(state.summary.expense),
                    )
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Button(
                            onClick = onAddExpense,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(text = "记支出")
                        }
                        OutlinedButton(
                            onClick = onAddIncome,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(text = "记收入")
                        }
                    }
                }
                item {
                    SectionCard(
                        modifier = Modifier.clickable(onClick = onOpenStats),
                        title = "每日支出",
                    ) {
                        DailyBarChart(values = state.dailyExpense)
                    }
                }
                item {
                    BudgetCard(
                        progress = state.totalBudgetProgress,
                        onClick = onOpenBudget,
                    )
                }
                item {
                    SectionCard(title = "账户概览") {
                        if (state.topAccountBalances.isEmpty()) {
                            Text(
                                text = "还没有账户",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            state.topAccountBalances.forEachIndexed { index, balance ->
                                if (index > 0) Spacer(Modifier.height(12.dp))
                                AccountOverviewRow(balance = balance, onClick = onOpenAccounts)
                            }
                        }
                    }
                }
                item {
                    SectionHeaderRow(
                        title = "最近记录",
                        actionText = "全部 ›",
                        onActionClick = onOpenRecords,
                    )
                }
                if (state.recentTransactions.isEmpty()) {
                    item {
                        SectionCard {
                            EmptyState(
                                icon = "🧾",
                                title = "本月还没有记录",
                                subtitle = "点上面的按钮记第一笔",
                            )
                        }
                    }
                } else {
                    items(items = state.recentTransactions, key = { it.id }) { transaction ->
                        SectionCard(contentPadding = PaddingValues(12.dp)) {
                            TransactionRow(
                                transaction = transaction,
                                onClick = { onEditTransaction(transaction.id) },
                            )
                        }
                    }
                }
            }
        },
    )
}

/** 月份选择行：‹ 上一月 / 2026年1月 / › 下一月（不能超过当前月）。 */
@Composable
private fun MonthSelectorRow(
    label: String,
    canGoNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "上一月",
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        IconButton(onClick = onNext, enabled = canGoNext) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "下一月",
            )
        }
    }
}

/** 主卡片：大号结余 + 收入/支出两个 StatTile。 */
@Composable
private fun SummaryCard(
    balanceText: String,
    balanceColor: Color,
    incomeText: String,
    expenseText: String,
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "本月结余",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = balanceText,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                color = balanceColor,
            )
            Spacer(Modifier.height(14.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                StatTile(
                    label = "收入",
                    value = incomeText,
                    modifier = Modifier.weight(1f),
                    valueColor = LedgerColors.income,
                )
                StatTile(
                    label = "支出",
                    value = expenseText,
                    modifier = Modifier.weight(1f),
                    valueColor = LedgerColors.expense,
                )
            }
        }
    }
}

/** 总预算提醒卡片：已用 / 预算 + 进度条；没有总预算时给一个设置入口。 */
@Composable
private fun BudgetCard(
    progress: BudgetProgress?,
    onClick: () -> Unit,
) {
    if (progress == null) {
        SectionCard(
            modifier = Modifier.clickable(onClick = onClick),
            title = "预算提醒",
        ) {
            Text(
                text = "设置本月预算",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
        return
    }

    val over = progress.ratio >= 1f
    val warning = progress.ratio >= 0.8f && !over
    val barColor = when {
        over -> LedgerColors.expense
        warning -> LedgerColors.warning
        else -> MaterialTheme.colorScheme.primary
    }

    SectionCard(
        modifier = Modifier.clickable(onClick = onClick),
        title = "预算提醒",
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "已用 ${Money.format(progress.spent)} / 预算 ${Money.format(progress.limit)}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${(progress.ratio * 100).toInt()}%",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = barColor,
            )
        }
        Spacer(Modifier.height(10.dp))
        RatioBar(ratio = progress.ratio, color = barColor)
        Spacer(Modifier.height(10.dp))
        Text(
            text = when {
                over -> "本月已超支 ${Money.format(progress.spent - progress.limit)}"
                warning -> "预算快用完了"
                else -> "剩余 ${Money.format(progress.remaining)}"
            },
            style = MaterialTheme.typography.bodySmall,
            color = barColor,
        )
    }
}

/** 账户概览的一行：类型图标 + 名称 + 余额。 */
@Composable
private fun AccountOverviewRow(
    balance: AccountBalance,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = balance.account.type.defaultIcon,
            fontSize = 20.sp,
        )
        Text(
            text = balance.account.name,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = Money.format(balance.balance),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (balance.balance < 0L) LedgerColors.expense else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** 区块标题 + 右侧文字按钮。 */
@Composable
private fun SectionHeaderRow(
    title: String,
    actionText: String,
    onActionClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onActionClick) {
            Text(text = actionText)
        }
    }
}

/** 一条流水：分类图标 + 分类名/备注 + 账户名 + 金额。 */
@Composable
private fun TransactionRow(
    transaction: Transaction,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CategoryAvatar(icon = transaction.categoryIcon, color = transaction.categoryColor)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = transaction.categoryName,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
            )
            Text(
                text = if (transaction.note.isBlank()) transaction.accountName else "${transaction.note} · ${transaction.accountName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(4.dp))
        AmountText(amount = transaction.amount, type = transaction.type)
    }
}
