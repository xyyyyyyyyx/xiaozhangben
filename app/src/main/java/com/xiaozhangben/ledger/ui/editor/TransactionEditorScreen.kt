package com.xiaozhangben.ledger.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xiaozhangben.ledger.domain.DateUtils
import com.xiaozhangben.ledger.domain.model.TxnType
import com.xiaozhangben.ledger.ui.AppViewModelFactory
import com.xiaozhangben.ledger.ui.components.CategoryAvatar
import com.xiaozhangben.ledger.ui.components.ConfirmDialog
import com.xiaozhangben.ledger.ui.components.LedgerScaffold
import com.xiaozhangben.ledger.ui.components.SectionCard
import com.xiaozhangben.ledger.ui.theme.LedgerColors
import java.time.Instant
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionEditorScreen(
    txnId: Long?,
    initialType: TxnType?,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EditorViewModel = viewModel(factory = AppViewModelFactory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showDatePicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(txnId, initialType) { viewModel.prepare(txnId, initialType) }
    LaunchedEffect(state.finished) { if (state.finished) onSaved() }

    LedgerScaffold(
        title = if (state.isEditing) "编辑记录" else "记一笔",
        modifier = modifier,
        onBack = onBack,
        actions = {
            if (state.isEditing) {
                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(imageVector = Icons.Default.Delete, contentDescription = "删除")
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TypeSelector(
                type = state.type,
                onTypeChange = viewModel::setType,
            )

            AmountField(
                type = state.type,
                amountText = state.amountText,
                onAmountChange = viewModel::setAmount,
            )

            SectionCard(title = "分类") {
                if (state.categories.isEmpty()) {
                    Text(
                        text = "还没有该类型的分类，请到「分类管理」里添加。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    CategoryGrid(
                        state = state,
                        onSelect = viewModel::setCategory,
                    )
                }
            }

            SectionCard(title = "账户") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.accounts.forEach { account ->
                        FilterChip(
                            selected = account.id == state.accountId,
                            onClick = { viewModel.setAccount(account.id) },
                            label = { Text("${account.type.defaultIcon} ${account.name}") },
                        )
                    }
                }
            }

            SectionCard(title = "日期") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = DateUtils.formatDate(state.occurredAt),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = "${DateUtils.friendlyDay(state.occurredAt)} ${DateUtils.formatTime(state.occurredAt)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { showDatePicker = true }) { Text("选择日期") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val today = DateUtils.toMillis(java.time.LocalDate.now())
                    FilterChip(
                        selected = DateUtils.toLocalDate(state.occurredAt) == DateUtils.toLocalDate(today),
                        onClick = { viewModel.setOccurredAt(System.currentTimeMillis()) },
                        label = { Text("今天") },
                    )
                    FilterChip(
                        selected = false,
                        onClick = { viewModel.shiftDay(-1) },
                        label = { Text("前一天") },
                    )
                    FilterChip(
                        selected = false,
                        onClick = { viewModel.shiftDay(1) },
                        label = { Text("后一天") },
                    )
                }
            }

            SectionCard(title = "备注") {
                OutlinedTextField(
                    value = state.note,
                    onValueChange = viewModel::setNote,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("可选，例如「和同事聚餐」") },
                    singleLine = false,
                    maxLines = 3,
                )
            }

            if (state.message != null) {
                Text(
                    text = state.message.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Button(
                onClick = viewModel::save,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = state.canSave,
            ) {
                Text(
                    text = if (state.isEditing) "保存修改" else "保存",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(12.dp))
        }
    }

    if (showDatePicker) {
        val initialUtcMillis = DateUtils.toLocalDate(state.occurredAt).toEpochDay() * 86_400_000L
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialUtcMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val selected = datePickerState.selectedDateMillis
                        if (selected != null) {
                            val date = Instant.ofEpochMilli(selected).atZone(ZoneOffset.UTC).toLocalDate()
                            val time = DateUtils.toLocalDateTime(state.occurredAt).toLocalTime()
                            viewModel.setOccurredAt(DateUtils.toMillis(date, time))
                        }
                        showDatePicker = false
                    },
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showDeleteConfirm) {
        ConfirmDialog(
            title = "删除这条记录？",
            message = "删除后无法恢复。",
            confirmText = "删除",
            destructive = true,
            onConfirm = {
                showDeleteConfirm = false
                viewModel.delete()
            },
            onDismiss = { showDeleteConfirm = false },
        )
    }
}

@Composable
private fun TypeSelector(
    type: TxnType,
    onTypeChange: (TxnType) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FilterChip(
            selected = type == TxnType.EXPENSE,
            onClick = { onTypeChange(TxnType.EXPENSE) },
            label = { Text("支出") },
            modifier = Modifier.weight(1f),
        )
        FilterChip(
            selected = type == TxnType.INCOME,
            onClick = { onTypeChange(TxnType.INCOME) },
            label = { Text("收入") },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun AmountField(
    type: TxnType,
    amountText: String,
    onAmountChange: (String) -> Unit,
) {
    // 用语义色而不是主题色：换主题时"收入绿/支出红"不能跟着变粉
    val tint = if (type == TxnType.INCOME) LedgerColors.income else LedgerColors.expense
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(tint.copy(alpha = 0.08f))
            .padding(16.dp),
    ) {
        Text(
            text = if (type == TxnType.INCOME) "收入金额" else "支出金额",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "¥",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = tint,
            )
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value = amountText,
                onValueChange = onAmountChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("0.00") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        }
    }
}

@Composable
private fun CategoryGrid(
    state: EditorUiState,
    onSelect: (Long) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        state.categories.chunked(4).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                rowItems.forEach { category ->
                    val selected = category.id == state.categoryId
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .then(
                                if (selected) {
                                    Modifier.border(
                                        width = 2.dp,
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = RoundedCornerShape(12.dp),
                                    )
                                } else {
                                    Modifier
                                },
                            )
                            .clickable { onSelect(category.id) }
                            .padding(vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CategoryAvatar(
                            icon = category.icon,
                            color = category.color,
                            size = 38.dp,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = category.name,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                repeat(4 - rowItems.size) {
                    Box(modifier = Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}
