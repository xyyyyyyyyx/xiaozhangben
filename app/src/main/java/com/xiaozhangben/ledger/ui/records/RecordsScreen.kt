// 账单列表：搜索、类型/月份/分类/账户筛选、结果概览与按日分组的流水列表。
package com.xiaozhangben.ledger.ui.records

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xiaozhangben.ledger.domain.Money
import com.xiaozhangben.ledger.domain.model.Category
import com.xiaozhangben.ledger.domain.model.Transaction
import com.xiaozhangben.ledger.domain.model.TxnType
import com.xiaozhangben.ledger.ui.AppViewModelFactory
import com.xiaozhangben.ledger.ui.components.AmountText
import com.xiaozhangben.ledger.ui.components.CategoryAvatar
import com.xiaozhangben.ledger.ui.components.EmptyState
import com.xiaozhangben.ledger.ui.components.LedgerScaffold
import com.xiaozhangben.ledger.ui.theme.LedgerColors

@Composable
fun RecordsScreen(
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecordsViewModel = viewModel(factory = AppViewModelFactory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // 分类 / 账户选择弹窗的开关
    var showCategoryDialog by remember { mutableStateOf(false) }
    var showAccountDialog by remember { mutableStateOf(false) }

    LedgerScaffold(
        title = "账单",
        modifier = modifier,
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "记一笔")
            }
        },
        content = { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                item {
                    SearchField(
                        keyword = state.filter.keyword,
                        onKeywordChange = viewModel::onKeywordChange,
                        onClear = viewModel::clearKeyword,
                    )
                }
                item {
                    FilterRow(
                        state = state,
                        onTypeChange = viewModel::onTypeChange,
                        onMonthFilterChange = viewModel::onMonthFilterChange,
                        onOpenCategoryDialog = { showCategoryDialog = true },
                        onOpenAccountDialog = { showAccountDialog = true },
                    )
                }
                item {
                    ResultSummaryRow(state = state)
                }

                if (state.filtered.isEmpty()) {
                    item {
                        if (state.isFilterActive) {
                            EmptyState(
                                icon = "🔍",
                                title = "没有符合条件的记录",
                                subtitle = "换个关键字或筛选条件试试",
                            )
                        } else {
                            EmptyState(
                                icon = "📭",
                                title = "还没有记录",
                                subtitle = "点击右下角按钮记一笔",
                            )
                        }
                    }
                } else {
                    state.dayGroups.forEach { group ->
                        item(key = "header-${group.dayStart}") {
                            DayGroupHeader(group = group)
                        }
                        items(
                            count = group.transactions.size,
                            key = { index -> group.transactions[index].id },
                        ) { index ->
                            val transaction = group.transactions[index]
                            TransactionRow(
                                transaction = transaction,
                                onClick = { onEdit(transaction.id) },
                            )
                        }
                    }
                }
            }
        },
    )

    if (showCategoryDialog) {
        CategoryPickerDialog(
            categories = state.categories,
            selectedId = state.filter.categoryId,
            onDismiss = { showCategoryDialog = false },
            onPick = { id ->
                viewModel.onCategoryChange(id)
                showCategoryDialog = false
            },
        )
    }

    if (showAccountDialog) {
        AccountPickerDialog(
            state = state,
            onDismiss = { showAccountDialog = false },
            onPick = { id ->
                viewModel.onAccountChange(id)
                showAccountDialog = false
            },
        )
    }
}

/** 搜索框：前导搜索图标，有内容时出现清空按钮。 */
@Composable
private fun SearchField(
    keyword: String,
    onKeywordChange: (String) -> Unit,
    onClear: () -> Unit,
) {
    OutlinedTextField(
        value = keyword,
        onValueChange = onKeywordChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        singleLine = true,
        placeholder = { Text(text = "搜索备注、分类、账户或金额") },
        leadingIcon = {
            Icon(imageVector = Icons.Default.Search, contentDescription = "搜索")
        },
        trailingIcon = {
            if (keyword.isNotEmpty()) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "清空",
                    modifier = Modifier.clickable(onClick = onClear),
                )
            }
        },
    )
}

/** 横向滚动的筛选行：类型、月份、分类、账户。 */
@Composable
private fun FilterRow(
    state: RecordsUiState,
    onTypeChange: (TxnType?) -> Unit,
    onMonthFilterChange: (MonthFilter) -> Unit,
    onOpenCategoryDialog: () -> Unit,
    onOpenAccountDialog: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = state.filter.type == null,
            onClick = { onTypeChange(null) },
            label = { Text(text = "全部") },
        )
        FilterChip(
            selected = state.filter.type == TxnType.EXPENSE,
            onClick = { onTypeChange(TxnType.EXPENSE) },
            label = { Text(text = "支出") },
        )
        FilterChip(
            selected = state.filter.type == TxnType.INCOME,
            onClick = { onTypeChange(TxnType.INCOME) },
            label = { Text(text = "收入") },
        )
        MonthFilter.entries.forEach { option ->
            FilterChip(
                selected = state.monthFilter == option,
                onClick = { onMonthFilterChange(option) },
                label = { Text(text = option.label) },
            )
        }
        FilterChip(
            selected = state.filter.categoryId != null,
            onClick = onOpenCategoryDialog,
            label = { Text(text = "${state.categoryFilterLabel} ▾") },
        )
        FilterChip(
            selected = state.filter.accountId != null,
            onClick = onOpenAccountDialog,
            label = { Text(text = "${state.accountFilterLabel} ▾") },
        )
    }
}

/** 结果概览：支出 / 收入 / 笔数。 */
@Composable
private fun ResultSummaryRow(state: RecordsUiState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "支出 ${Money.format(state.resultSummary.expense)}",
            style = MaterialTheme.typography.bodySmall,
            color = LedgerColors.expense,
        )
        Text(
            text = "·",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "收入 ${Money.format(state.resultSummary.income)}",
            style = MaterialTheme.typography.bodySmall,
            color = LedgerColors.income,
        )
        Text(
            text = "·",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "共 ${state.filtered.size} 笔",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 分组标题行：友好日期 + 当日合计。 */
@Composable
private fun DayGroupHeader(group: TransactionDayGroup) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = group.header,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = summaryText(group),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 当日合计文字：支出与收入各占一段，金额为 0 时省略。 */
private fun summaryText(group: TransactionDayGroup): String {
    val parts = ArrayList<String>(2)
    if (group.summary.expense > 0L) parts.add("支出 ${Money.format(group.summary.expense)}")
    if (group.summary.income > 0L) parts.add("收入 ${Money.format(group.summary.income)}")
    return if (parts.isEmpty()) "共 ${group.transactions.size} 笔" else parts.joinToString(separator = " · ")
}

/** 一条流水行。 */
@Composable
private fun TransactionRow(
    transaction: Transaction,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
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
        AmountText(amount = transaction.amount, type = transaction.type)
    }
}

/** 分类选择弹窗，含「全部分类」。 */
@Composable
private fun CategoryPickerDialog(
    categories: List<Category>,
    selectedId: Long?,
    onDismiss: () -> Unit,
    onPick: (Long?) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "选择分类") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                PickerOptionRow(
                    label = "全部分类",
                    selected = selectedId == null,
                    icon = "📁",
                    onClick = { onPick(null) },
                )
                categories.forEach { category ->
                    PickerOptionRow(
                        label = category.name,
                        selected = selectedId == category.id,
                        icon = category.icon,
                        onClick = { onPick(category.id) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(text = "关闭") }
        },
    )
}

/** 账户选择弹窗，含「全部账户」。 */
@Composable
private fun AccountPickerDialog(
    state: RecordsUiState,
    onDismiss: () -> Unit,
    onPick: (Long?) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "选择账户") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 400.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                PickerOptionRow(
                    label = "全部账户",
                    selected = state.filter.accountId == null,
                    icon = "👛",
                    onClick = { onPick(null) },
                )
                state.accounts.forEach { account ->
                    PickerOptionRow(
                        label = account.name,
                        selected = state.filter.accountId == account.id,
                        icon = account.type.defaultIcon,
                        onClick = { onPick(account.id) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(text = "关闭") }
        },
    )
}

/** 弹窗里的一行候选项。 */
@Composable
private fun PickerOptionRow(
    label: String,
    selected: Boolean,
    icon: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(text = icon)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "已选中",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
