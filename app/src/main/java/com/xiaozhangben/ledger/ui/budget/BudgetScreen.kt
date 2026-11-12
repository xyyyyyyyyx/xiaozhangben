// 预算页界面：月度切换、总预算、分类预算与超支提醒。
package com.xiaozhangben.ledger.ui.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xiaozhangben.ledger.domain.DateUtils
import com.xiaozhangben.ledger.domain.Money
import com.xiaozhangben.ledger.domain.StatsCalculator
import com.xiaozhangben.ledger.domain.model.BudgetProgress
import com.xiaozhangben.ledger.domain.model.Category
import com.xiaozhangben.ledger.domain.model.TxnType
import com.xiaozhangben.ledger.ui.AppViewModelFactory
import com.xiaozhangben.ledger.ui.components.CategoryAvatar
import com.xiaozhangben.ledger.ui.components.ConfirmDialog
import com.xiaozhangben.ledger.ui.components.EmptyState
import com.xiaozhangben.ledger.ui.components.LedgerScaffold
import com.xiaozhangben.ledger.ui.components.RatioBar
import com.xiaozhangben.ledger.ui.components.SectionCard
import com.xiaozhangben.ledger.ui.components.StatTile
import com.xiaozhangben.ledger.ui.theme.LedgerColors
import java.time.YearMonth

/** 预算编辑对话框的目标。 */
private sealed interface BudgetEditTarget {
    val current: Long

    /** 总预算（categoryId = null）。 */
    data class Total(override val current: Long) : BudgetEditTarget

    /** 已存在的分类预算：分类固定，只改金额。 */
    data class SingleCategory(
        val categoryId: Long,
        val name: String,
        override val current: Long,
    ) : BudgetEditTarget

    /** 新增分类预算：需要在对话框里选分类。 */
    data class New(override val current: Long = 0L) : BudgetEditTarget
}

@Composable
fun BudgetScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BudgetViewModel = viewModel(factory = AppViewModelFactory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val month = state.month

    val monthTransactions = remember(state.transactions, month) {
        StatsCalculator.inMonth(state.transactions, month)
    }
    val progressList = remember(state.budgets, monthTransactions, state.categories) {
        StatsCalculator.budgetProgress(state.budgets, monthTransactions, state.categories)
    }
    val totalProgress = progressList.firstOrNull { it.budget.isTotal }
    val categoryProgress = progressList.filter { !it.budget.isTotal }
    val overCount = progressList.count { it.over }
    val monthExpense = remember(monthTransactions) {
        StatsCalculator.summarize(monthTransactions).expense
    }
    val expenseCategories = remember(state.categories) {
        state.categories.filter { it.type == TxnType.EXPENSE && !it.archived }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.error) {
        val message = state.error
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.clearError()
        }
    }

    var editTarget by remember { mutableStateOf<BudgetEditTarget?>(null) }
    var pendingDelete by remember { mutableStateOf<BudgetProgress?>(null) }

    LedgerScaffold(
        title = "预算",
        modifier = modifier,
        onBack = onBack,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (overCount > 0) {
                item { OverBudgetBanner(count = overCount) }
            }
            item {
                MonthSwitcher(
                    month = month,
                    onPrevious = viewModel::showPreviousMonth,
                    onNext = viewModel::showNextMonth,
                )
            }
            item {
                TotalBudgetCard(
                    progress = totalProgress,
                    monthExpense = monthExpense,
                    onEdit = { editTarget = BudgetEditTarget.Total(totalProgress?.limit ?: 0L) },
                )
            }
            item {
                SectionCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "分类预算",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { editTarget = BudgetEditTarget.New() }) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("添加")
                        }
                    }
                    if (categoryProgress.isEmpty()) {
                        EmptyState(
                            icon = "💸",
                            title = "还没有分类预算",
                            subtitle = "点右上角「添加」为常用分类设置月度预算",
                        )
                    } else {
                        categoryProgress.forEach { progress ->
                            CategoryBudgetRow(
                                progress = progress,
                                onEdit = {
                                    editTarget = BudgetEditTarget.SingleCategory(
                                        categoryId = progress.budget.categoryId ?: 0L,
                                        name = progress.category?.name ?: "未分类",
                                        current = progress.limit,
                                    )
                                },
                                onDelete = { pendingDelete = progress },
                            )
                        }
                    }
                }
            }
        }
    }

    val target = editTarget
    if (target != null) {
        BudgetEditDialog(
            target = target,
            categories = expenseCategories,
            onDismiss = { editTarget = null },
            onSaveTotal = { amount ->
                viewModel.saveTotalBudget(amount)
                editTarget = null
            },
            onSaveCategory = { categoryId, amount ->
                viewModel.saveCategoryBudget(categoryId, amount)
                editTarget = null
            },
        )
    }

    val deleteTarget = pendingDelete
    if (deleteTarget != null) {
        val name = deleteTarget.category?.name
            ?: if (deleteTarget.budget.isTotal) "总预算" else "未分类"
        ConfirmDialog(
            title = "删除预算",
            message = "确定删除「$name」的这笔预算吗？",
            onConfirm = {
                viewModel.deleteBudget(deleteTarget.budget.id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
            confirmText = "删除",
            destructive = true,
        )
    }
}

/** 超支提醒条：红色底 + 警告图标。 */
@Composable
private fun OverBudgetBanner(count: Int) {
    val errorColor = MaterialTheme.colorScheme.error
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(errorColor.copy(alpha = 0.12f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = errorColor,
        )
        Text(
            text = "本月有 $count 项预算已超支",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = errorColor,
        )
    }
}

/** 月份切换：允许查看未来月份。 */
@Composable
private fun MonthSwitcher(month: YearMonth, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = onPrevious) {
            Text(text = "‹", fontSize = 24.sp)
        }
        Text(
            text = DateUtils.monthLabel(month),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        IconButton(onClick = onNext) {
            Text(text = "›", fontSize = 24.sp)
        }
    }
}

/** 总预算卡片：本月预算 / 已用 / 剩余（超支时改显示超支金额）。 */
@Composable
private fun TotalBudgetCard(
    progress: BudgetProgress?,
    monthExpense: Long,
    onEdit: () -> Unit,
) {
    val expenseColor = LedgerColors.expense
    val warningColor = LedgerColors.warning
    val primaryColor = MaterialTheme.colorScheme.primary
    val errorColor = MaterialTheme.colorScheme.error
    val hintColor = MaterialTheme.colorScheme.onSurfaceVariant

    val ratio = progress?.ratio ?: 0f
    val barColor = when {
        ratio >= 1f -> expenseColor
        ratio >= 0.8f -> warningColor
        else -> primaryColor
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onEdit),
    ) {
        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "总预算",
                    style = MaterialTheme.typography.titleSmall,
                    color = hintColor,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onEdit) {
                    Text(text = if (progress == null) "设置预算" else "修改预算")
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatTile(
                    label = "本月预算",
                    value = progress?.let { Money.format(it.limit) } ?: "未设置",
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    label = "已用",
                    value = Money.format(progress?.spent ?: monthExpense),
                    modifier = Modifier.weight(1f),
                    valueColor = expenseColor,
                )
                if (progress != null && progress.over) {
                    StatTile(
                        label = "超支",
                        value = Money.format(progress.spent - progress.limit),
                        modifier = Modifier.weight(1f),
                        valueColor = errorColor,
                    )
                } else {
                    StatTile(
                        label = "剩余",
                        value = progress?.let { Money.format(it.remaining) } ?: "—",
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            RatioBar(ratio = ratio, color = barColor)
        }
    }
}

/** 单条分类预算：点击改金额，长按或右侧删除图标删除。 */
@Composable
private fun CategoryBudgetRow(
    progress: BudgetProgress,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val expenseColor = LedgerColors.expense
    val warningColor = LedgerColors.warning
    val primaryColor = MaterialTheme.colorScheme.primary
    val errorColor = MaterialTheme.colorScheme.error
    val hintColor = MaterialTheme.colorScheme.onSurfaceVariant
    val category = progress.category
    val ratio = progress.ratio
    val barColor = when {
        ratio >= 1f -> expenseColor
        ratio >= 0.8f -> warningColor
        else -> primaryColor
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .pointerInput(progress.budget.id) {
                detectTapGestures(
                    onTap = { onEdit() },
                    onLongPress = { onDelete() },
                )
            }
            .padding(vertical = 10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CategoryAvatar(
                icon = category?.icon ?: "📦",
                color = category?.color ?: 0xFF607D8B,
                size = 34.dp,
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = category?.name ?: "未分类",
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (progress.over) {
                        Text(
                            text = "已超支",
                            style = MaterialTheme.typography.labelSmall,
                            color = errorColor,
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "已用 ${Money.format(progress.spent)} / 预算 ${Money.format(progress.limit)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = hintColor,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "删除预算",
                    tint = hintColor,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        RatioBar(ratio = ratio, color = barColor)
    }
}

/** 预算金额编辑/新增对话框。 */
@Composable
private fun BudgetEditDialog(
    target: BudgetEditTarget,
    categories: List<Category>,
    onDismiss: () -> Unit,
    onSaveTotal: (Long) -> Unit,
    onSaveCategory: (Long, Long) -> Unit,
) {
    val errorColor = MaterialTheme.colorScheme.error
    val hintColor = MaterialTheme.colorScheme.onSurfaceVariant
    var amountText by remember(target) {
        mutableStateOf(if (target.current > 0L) Money.formatPlain(target.current) else "")
    }
    var selectedCategoryId by remember(target) {
        mutableStateOf(categories.firstOrNull()?.id)
    }
    var errorText by remember(target) { mutableStateOf<String?>(null) }

    val isNew = target is BudgetEditTarget.New
    val title = when (target) {
        is BudgetEditTarget.Total -> "设置总预算"
        is BudgetEditTarget.SingleCategory -> "修改「${target.name}」预算"
        is BudgetEditTarget.New -> "添加分类预算"
    }

    val confirm: () -> Unit = {
        val cents = Money.parse(amountText)
        if (cents == null) {
            errorText = "请输入有效金额，例如 2000 或 199.9"
        } else {
            when (target) {
                is BudgetEditTarget.Total -> onSaveTotal(cents)
                is BudgetEditTarget.SingleCategory -> onSaveCategory(target.categoryId, cents)
                is BudgetEditTarget.New -> {
                    val categoryId = selectedCategoryId
                    if (categoryId == null) {
                        errorText = "请先选择分类"
                    } else {
                        onSaveCategory(categoryId, cents)
                    }
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = {
            Column {
                if (isNew) {
                    if (categories.isEmpty()) {
                        Text(
                            text = "没有可用的支出分类，请先到分类页新建。",
                            style = MaterialTheme.typography.bodySmall,
                            color = errorColor,
                        )
                    } else {
                        Text(
                            text = "选择分类",
                            style = MaterialTheme.typography.labelMedium,
                            color = hintColor,
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            categories.forEach { category ->
                                FilterChip(
                                    selected = category.id == selectedCategoryId,
                                    onClick = { selectedCategoryId = category.id },
                                    label = { Text(category.name) },
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
                OutlinedTextField(
                    value = amountText,
                    onValueChange = {
                        amountText = it
                        errorText = null
                    },
                    label = { Text("金额（元）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "输入 0 会删除该预算",
                    style = MaterialTheme.typography.labelSmall,
                    color = hintColor,
                )
                val message = errorText
                if (message != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = errorColor,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = confirm,
                enabled = !(isNew && categories.isEmpty()),
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
