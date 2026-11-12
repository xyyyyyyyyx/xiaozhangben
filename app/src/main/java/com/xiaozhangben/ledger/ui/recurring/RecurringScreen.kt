// 周期账单页面：规则列表、到期提示、新建/编辑对话框（含 material3 日期选择器）。
package com.xiaozhangben.ledger.ui.recurring

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xiaozhangben.ledger.domain.DateUtils
import com.xiaozhangben.ledger.domain.Money
import com.xiaozhangben.ledger.domain.model.Account
import com.xiaozhangben.ledger.domain.model.Category
import com.xiaozhangben.ledger.domain.model.RecurrenceFrequency
import com.xiaozhangben.ledger.domain.model.RecurringRule
import com.xiaozhangben.ledger.domain.model.TxnType
import com.xiaozhangben.ledger.ui.AppViewModelFactory
import com.xiaozhangben.ledger.ui.components.AmountText
import com.xiaozhangben.ledger.ui.components.CategoryAvatar
import com.xiaozhangben.ledger.ui.components.ConfirmDialog
import com.xiaozhangben.ledger.ui.components.EmptyState
import com.xiaozhangben.ledger.ui.components.LedgerScaffold
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** 卡片/提示里用的规则名称：优先备注，其次分类名。 */
private fun ruleTitle(rule: RecurringRule): String =
    rule.note.ifBlank { rule.categoryName.ifBlank { "未分类" } }

@Composable
fun RecurringScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RecurringViewModel = viewModel(factory = AppViewModelFactory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var editorVisible by remember { mutableStateOf(false) }
    var editorTarget by remember { mutableStateOf<RecurringRule?>(null) }
    var deleteTarget by remember { mutableStateOf<RecurringRule?>(null) }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeMessage()
    }

    val dueCount = state.rules.count { it.enabled && it.nextRunAt <= System.currentTimeMillis() }

    LedgerScaffold(
        title = "周期账单",
        modifier = modifier,
        onBack = onBack,
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editorTarget = null
                    editorVisible = true
                },
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "新建周期账单")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (dueCount > 0) {
                item { DueBanner(count = dueCount) }
            }
            if (state.rules.isEmpty()) {
                item {
                    EmptyState(
                        icon = "🔁",
                        title = "还没有周期账单",
                        subtitle = "点右下角的加号，把房租、会员订阅这类固定收支存成规则",
                    )
                }
            }
            items(items = state.rules, key = { it.id }) { rule ->
                RecurringCard(
                    rule = rule,
                    categoryColor = state.categoryColorOf(rule.categoryId),
                    onClick = {
                        editorTarget = rule
                        editorVisible = true
                    },
                    onToggle = { checked -> viewModel.setEnabled(rule, checked) },
                    onPostNow = { viewModel.postNow(rule) },
                    onDelete = { deleteTarget = rule },
                )
            }
        }
    }

    if (editorVisible) {
        RecurringEditorDialog(
            initial = editorTarget,
            categories = state.categories,
            accounts = state.accounts,
            onDismiss = { editorVisible = false },
            onSave = { rule ->
                editorVisible = false
                viewModel.save(rule)
            },
        )
    }

    val pendingDelete = deleteTarget
    if (pendingDelete != null) {
        ConfirmDialog(
            title = "删除周期账单",
            message = "确定删除「${ruleTitle(pendingDelete)}」吗？已经生成的流水不会被删除。",
            onConfirm = {
                deleteTarget = null
                viewModel.delete(pendingDelete)
            },
            onDismiss = { deleteTarget = null },
            confirmText = "删除",
            destructive = true,
        )
    }
}

/** 顶部「有 N 条规则已到期」提示条。 */
@Composable
private fun DueBanner(count: Int) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(imageVector = Icons.Default.Notifications, contentDescription = null)
            Text(
                text = "有 $count 条规则已到期，返回首页时自动记账",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** 单条周期规则卡片。 */
@Composable
private fun RecurringCard(
    rule: RecurringRule,
    categoryColor: Long,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onPostNow: () -> Unit,
    onDelete: () -> Unit,
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CategoryAvatar(icon = rule.categoryIcon, color = categoryColor)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = ruleTitle(rule),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "${rule.frequency.label} · ${rule.accountName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "下次：${DateUtils.formatDate(rule.nextRunAt)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    AmountText(amount = rule.amount, type = rule.type)
                    Spacer(Modifier.height(2.dp))
                    Switch(checked = rule.enabled, onCheckedChange = onToggle)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onPostNow) { Text("立即记一笔") }
                TextButton(onClick = onDelete) {
                    Text(text = "删除", color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.weight(1f))
                if (!rule.enabled) {
                    Text(
                        text = "已停用",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** 新建 / 编辑周期规则的对话框。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecurringEditorDialog(
    initial: RecurringRule?,
    categories: List<Category>,
    accounts: List<Account>,
    onDismiss: () -> Unit,
    onSave: (RecurringRule) -> Unit,
) {
    // 用规则 id 做 key：切换编辑对象时对话框里的输入会重新初始化。
    val editorKey = initial?.id ?: 0L

    var type by remember(editorKey) { mutableStateOf(initial?.type ?: TxnType.EXPENSE) }
    var amountText by remember(editorKey) {
        mutableStateOf(initial?.let { Money.formatPlain(it.amount) } ?: "")
    }
    var categoryId by remember(editorKey) { mutableStateOf(initial?.categoryId ?: 0L) }
    var accountId by remember(editorKey) { mutableStateOf(initial?.accountId ?: 0L) }
    var note by remember(editorKey) { mutableStateOf(initial?.note ?: "") }
    var frequency by remember(editorKey) {
        mutableStateOf(initial?.frequency ?: RecurrenceFrequency.MONTHLY)
    }
    var nextRunAt by remember(editorKey) {
        mutableStateOf(initial?.nextRunAt ?: System.currentTimeMillis())
    }
    var autoPost by remember(editorKey) { mutableStateOf(initial?.autoPost ?: true) }
    var datePickerVisible by remember(editorKey) { mutableStateOf(false) }

    // 当前类型下未归档的分类；账户只列未归档的。
    val typeCategories = categories.filter { it.type == type && !it.archived }
    val usableAccounts = accounts.filter { !it.archived }
    val selectedCategoryId = typeCategories.firstOrNull { it.id == categoryId }?.id
        ?: typeCategories.firstOrNull()?.id
        ?: 0L
    val selectedAccountId = usableAccounts.firstOrNull { it.id == accountId }?.id
        ?: usableAccounts.firstOrNull()?.id
        ?: 0L

    val parsedAmount = Money.parse(amountText)
    val amountError = when {
        amountText.isBlank() -> "请输入金额"
        parsedAmount == null -> "金额格式不正确"
        parsedAmount <= 0L -> "金额必须大于 0"
        else -> null
    }
    val canSave = amountError == null && selectedCategoryId != 0L && selectedAccountId != 0L

    if (datePickerVisible) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = nextRunAt)
        DatePickerDialog(
            onDismissRequest = { datePickerVisible = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val picked = pickerState.selectedDateMillis
                        if (picked != null) {
                            // 只取日期，时间统一用当前时分。
                            val date = Instant.ofEpochMilli(picked)
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate()
                            nextRunAt = DateUtils.toMillis(date, LocalTime.now())
                        }
                        datePickerVisible = false
                    },
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { datePickerVisible = false }) { Text("取消") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "新建周期账单" else "编辑周期账单") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                EditorLabel("类型")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TxnType.entries.forEach { option ->
                        FilterChip(
                            selected = type == option,
                            onClick = { type = option },
                            label = { Text(option.label) },
                        )
                    }
                }

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("金额（元）") },
                    singleLine = true,
                    isError = amountError != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                if (amountError != null) {
                    Text(
                        text = amountError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                EditorLabel("分类")
                if (typeCategories.isEmpty()) {
                    Text(
                        text = "该类型下没有可用分类，请先到分类管理里添加",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        typeCategories.forEach { category ->
                            FilterChip(
                                selected = category.id == selectedCategoryId,
                                onClick = { categoryId = category.id },
                                label = { Text("${category.icon} ${category.name}") },
                            )
                        }
                    }
                }

                EditorLabel("账户")
                if (usableAccounts.isEmpty()) {
                    Text(
                        text = "没有可用账户，请先到账户管理里添加",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        usableAccounts.forEach { account ->
                            FilterChip(
                                selected = account.id == selectedAccountId,
                                onClick = { accountId = account.id },
                                label = { Text(account.name) },
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("备注") },
                    singleLine = true,
                )

                EditorLabel("频率")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RecurrenceFrequency.entries.forEach { option ->
                        FilterChip(
                            selected = frequency == option,
                            onClick = { frequency = option },
                            label = { Text(option.label) },
                        )
                    }
                }

                EditorLabel("下次执行日期")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = DateUtils.formatDate(nextRunAt),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { datePickerVisible = true }) { Text("选择日期") }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "自动记账", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = "到时间自动补记，关闭后只在提醒里出现",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = autoPost, onCheckedChange = { autoPost = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = {
                    onSave(
                        RecurringRule(
                            id = initial?.id ?: 0L,
                            type = type,
                            amount = parsedAmount ?: 0L,
                            categoryId = selectedCategoryId,
                            accountId = selectedAccountId,
                            note = note.trim(),
                            frequency = frequency,
                            nextRunAt = nextRunAt,
                            autoPost = autoPost,
                            enabled = initial?.enabled ?: true,
                        ),
                    )
                },
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

/** 对话框里的小节标题。 */
@Composable
private fun EditorLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
