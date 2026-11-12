// 账户与余额页面：净资产汇总、账户列表、增删改与归档管理。
package com.xiaozhangben.ledger.ui.accounts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xiaozhangben.ledger.data.db.DefaultData
import com.xiaozhangben.ledger.domain.Money
import com.xiaozhangben.ledger.domain.StatsCalculator
import com.xiaozhangben.ledger.domain.model.Account
import com.xiaozhangben.ledger.domain.model.AccountType
import com.xiaozhangben.ledger.ui.AppViewModelFactory
import com.xiaozhangben.ledger.ui.components.LedgerScaffold
import com.xiaozhangben.ledger.ui.components.SectionCard
import com.xiaozhangben.ledger.ui.components.StatTile
import com.xiaozhangben.ledger.ui.theme.LedgerColors
import com.xiaozhangben.ledger.ui.toComposeColor
import kotlinx.coroutines.launch

@Composable
fun AccountsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AccountsViewModel = viewModel(factory = AppViewModelFactory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var dialogAccount by remember { mutableStateOf<Account?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    val balances = remember(state.accounts, state.transactions) {
        StatsCalculator.accountBalances(state.accounts, state.transactions)
    }
    val activeBalances = balances.filter { !it.account.archived }
    val archivedBalances = balances.filter { it.account.archived }
    val netAssets = activeBalances.sumOf { it.balance }
    val totalIncome = activeBalances.sumOf { it.income }
    val totalExpense = activeBalances.sumOf { it.expense }

    LedgerScaffold(
        title = "账户",
        modifier = modifier,
        onBack = onBack,
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    dialogAccount = null
                    showDialog = true
                },
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "新建账户")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "summary") {
                NetAssetsCard(
                    total = netAssets,
                    income = totalIncome,
                    expense = totalExpense,
                )
            }

            item(key = "title") {
                Text(
                    text = "账户（${activeBalances.size}）",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, top = 6.dp),
                )
            }

            if (activeBalances.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = "还没有账户，点右下角「+」新建一个吧。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }

            items(items = activeBalances, key = { it.account.id }) { item ->
                AccountRow(
                    account = item.account,
                    balance = item.balance,
                    onClick = {
                        dialogAccount = item.account
                        showDialog = true
                    },
                )
            }

            if (archivedBalances.isNotEmpty()) {
                item(key = "archivedHeader") {
                    ArchivedHeader(
                        count = archivedBalances.size,
                        expanded = state.archivedExpanded,
                        onClick = viewModel::toggleArchived,
                    )
                }
                if (state.archivedExpanded) {
                    items(items = archivedBalances, key = { "archived-${it.account.id}" }) { item ->
                        AccountRow(
                            account = item.account,
                            balance = item.balance,
                            subtitle = "已归档",
                            onClick = { scope.launch { viewModel.setArchived(item.account.id, false) } },
                        )
                    }
                }
            }

            val message = state.errorMessage
            if (message != null) {
                item(key = "error") {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.consumeError() }
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                    )
                }
            }

            item(key = "bottomSpace") { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (showDialog) {
        AccountEditDialog(
            account = dialogAccount,
            existingNames = state.accounts.map { it.name },
            onDismiss = { showDialog = false },
            onSave = { name, type, initialBalance, color ->
                scope.launch {
                    val editing = dialogAccount
                    val ok = if (editing == null) {
                        viewModel.addAccount(name, type, initialBalance, color)
                    } else {
                        viewModel.updateAccount(editing, name, type, initialBalance, color)
                    }
                    if (ok) showDialog = false
                }
            },
            onDelete = {
                val editing = dialogAccount
                if (editing != null) {
                    showDialog = false
                    scope.launch { viewModel.deleteAccount(editing) }
                }
            },
        )
    }

    val pending = state.pendingArchive
    if (pending != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissPendingArchive,
            title = { Text("无法删除账户") },
            text = {
                Text(
                    text = buildString {
                        append("该账户下已有 ")
                        append(state.pendingArchiveUsage.ifEmpty { "若干" })
                        append("，删除会丢失关联记录。建议改为归档，归档后不再出现在记账选择里，历史数据仍保留。")
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch { viewModel.setArchived(pending.id, true) }
                    },
                ) {
                    Text("归档")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissPendingArchive) { Text("取消") }
            },
        )
    }
}

/** 顶部净资产卡片。 */
@Composable
private fun NetAssetsCard(total: Long, income: Long, expense: Long) {
    SectionCard(title = "净资产合计") {
        Text(
            text = Money.format(total),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = if (total < 0) LedgerColors.expense else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatTile(
                label = "总收入",
                value = Money.format(income),
                modifier = Modifier.weight(1f),
                valueColor = LedgerColors.income,
            )
            StatTile(
                label = "总支出",
                value = Money.format(expense),
                modifier = Modifier.weight(1f),
                valueColor = LedgerColors.expense,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "净资产 = 账户初始余额 + 收入 - 支出（不含已归档账户）",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 单个账户一行。 */
@Composable
private fun AccountRow(
    account: Account,
    balance: Long,
    subtitle: String? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AccountBadge(account = account)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = account.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val label = subtitle ?: account.type.label
            Text(
                text = "$label · 初始 ${Money.format(account.initialBalance)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = Money.format(balance),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (balance < 0) LedgerColors.expense else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

/** 带账户颜色背景的 emoji 圆角方块。 */
@Composable
private fun AccountBadge(account: Account) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(account.color.toComposeColor().copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = account.type.defaultIcon, fontSize = 20.sp)
    }
}

/** 「已归档（N）」折叠标题。 */
@Composable
private fun ArchivedHeader(count: Int, expanded: Boolean, onClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "已归档（$count）",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = if (expanded) "收起" else "展开",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        HorizontalDivider()
    }
}

/** 新建 / 编辑账户对话框。 */
@Composable
private fun AccountEditDialog(
    account: Account?,
    existingNames: List<String>,
    onDismiss: () -> Unit,
    onSave: (String, AccountType, Long, Long) -> Unit,
    onDelete: () -> Unit,
) {
    var name by remember(account) { mutableStateOf(account?.name ?: "") }
    var type by remember(account) { mutableStateOf(account?.type ?: AccountType.CASH) }
    var balanceText by remember(account) {
        mutableStateOf(if (account == null) "" else Money.formatPlain(account.initialBalance))
    }
    var color by remember(account) { mutableLongStateOf(account?.color ?: DefaultData.categoryColors.first()) }
    var localError by remember(account) { mutableStateOf<String?>(null) }

    val editing = account != null

    fun submit() {
        val trimmed = name.trim()
        when {
            trimmed.isEmpty() -> localError = "请填写账户名称"
            existingNames.any { it == trimmed && it != account?.name } -> localError = "已有同名账户：$trimmed"
            else -> {
                val parsed = if (balanceText.isBlank()) 0L else Money.parse(balanceText)
                if (parsed == null) {
                    localError = "初始余额格式不正确，例如 1200.50 或 -300"
                } else {
                    localError = null
                    onSave(trimmed, type, parsed, color)
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editing) "编辑账户" else "新建账户") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        localError = null
                    },
                    label = { Text("账户名称") },
                    singleLine = true,
                    isError = localError != null,
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(
                    text = "账户类型",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AccountType.entries.forEach { entry ->
                        FilterChip(
                            selected = type == entry,
                            onClick = { type = entry },
                            label = { Text("${entry.defaultIcon} ${entry.label}") },
                        )
                    }
                }

                OutlinedTextField(
                    value = balanceText,
                    onValueChange = {
                        balanceText = it
                        localError = null
                    },
                    label = { Text("初始余额") },
                    placeholder = { Text("如 1200.50，信用卡欠款可填 -300") },
                    supportingText = { Text("单位：元，允许负数") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(
                    text = "账户颜色",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DefaultData.categoryColors.forEach { value ->
                        ColorDot(selected = value == color, color = value, onClick = { color = value })
                    }
                }

                if (localError != null) {
                    Text(
                        text = localError ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { submit() }) { Text("保存") }
        },
        dismissButton = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (editing) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "删除",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
}

/** 颜色选择小圆点。 */
@Composable
private fun ColorDot(selected: Boolean, color: Long, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(color.toComposeColor())
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "已选颜色",
                tint = MaterialTheme.colorScheme.surface,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
