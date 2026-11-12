// 「我的」设置页面：数据备份、管理入口、安全、外观与关于。
package com.xiaozhangben.ledger.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xiaozhangben.ledger.data.prefs.ThemeMode
import com.xiaozhangben.ledger.domain.DateUtils
import com.xiaozhangben.ledger.ui.AppViewModelFactory
import com.xiaozhangben.ledger.ui.components.ConfirmDialog
import com.xiaozhangben.ledger.ui.components.LedgerScaffold
import com.xiaozhangben.ledger.ui.components.SectionCard
import com.xiaozhangben.ledger.ui.components.SettingRow

/** 时间戳格式化：0 表示从未发生过。 */
private fun formatStamp(millis: Long): String =
    if (millis <= 0L) "从未" else DateUtils.formatDateTime(millis)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenAccounts: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenBudget: () -> Unit,
    onOpenRecurring: () -> Unit,
    onOpenLock: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(factory = AppViewModelFactory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var confirmClear by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        if (uri != null) viewModel.exportTo(uri)
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) viewModel.importFrom(uri)
    }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeMessage()
    }

    LedgerScaffold(
        title = "我的",
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ---------------- 数据 ----------------
            item {
                SectionCard(title = "数据") {
                    SettingRow(
                        title = "导出 CSV",
                        subtitle = "导出全部流水到文件",
                        onClick = { exportLauncher.launch(viewModel.exportFileName) },
                    )
                    SettingRow(
                        title = "导入 CSV",
                        subtitle = "从 CSV 文件追加记录",
                        onClick = {
                            // 部分手机的 CSV 类型不标准，直接放开全部类型最稳。
                            importLauncher.launch(arrayOf("*/*"))
                        },
                    )
                    SettingRow(
                        title = "清空所有流水",
                        subtitle = "保留分类与账户设置",
                        onClick = { confirmClear = true },
                    )
                    SettingRow(
                        title = "恢复出厂设置",
                        subtitle = "清空全部数据并重建默认分类",
                        onClick = { confirmReset = true },
                    )
                }
            }

            // ---------------- 管理 ----------------
            item {
                SectionCard(title = "管理") {
                    SettingRow(title = "账户管理", subtitle = "现金、银行卡等账户", onClick = onOpenAccounts)
                    SettingRow(title = "分类管理", subtitle = "自定义收支分类与排序", onClick = onOpenCategories)
                    SettingRow(title = "预算", subtitle = "总预算与分类预算", onClick = onOpenBudget)
                    SettingRow(title = "周期账单", subtitle = "房租、订阅等固定收支", onClick = onOpenRecurring)
                }
            }

            // ---------------- 安全 ----------------
            item {
                SectionCard(title = "安全") {
                    SettingRow(
                        title = "密码与指纹锁",
                        subtitle = if (state.settings.lockEnabled) "已开启" else "未开启",
                        onClick = onOpenLock,
                    )
                }
            }

            // ---------------- 外观 ----------------
            item {
                SectionCard(title = "外观") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = state.settings.themeMode == mode,
                                onClick = { viewModel.setThemeMode(mode) },
                                label = { Text(mode.label) },
                            )
                        }
                    }
                }
            }

            // ---------------- 关于 ----------------
            item {
                SectionCard(title = "关于") {
                    SettingRow(title = "版本", trailingText = "1.0.0", showArrow = false)
                    SettingRow(
                        title = "记录条数",
                        trailingText = "${state.transactionCount} 笔",
                        showArrow = false,
                    )
                    SettingRow(
                        title = "上次导出时间",
                        trailingText = formatStamp(state.settings.lastExportAt),
                        showArrow = false,
                    )
                    SettingRow(
                        title = "上次导入时间",
                        trailingText = formatStamp(state.settings.lastImportAt),
                        showArrow = false,
                    )
                }
            }
        }
    }

    if (confirmClear) {
        ConfirmDialog(
            title = "清空所有流水",
            message = "将删除全部流水记录，分类与账户设置会保留。此操作无法撤销。",
            onConfirm = {
                confirmClear = false
                viewModel.clearTransactions()
            },
            onDismiss = { confirmClear = false },
            confirmText = "清空",
            destructive = true,
        )
    }

    if (confirmReset) {
        ConfirmDialog(
            title = "恢复出厂设置",
            message = "将清空全部流水、分类、账户、预算与周期账单，并重建默认分类。此操作无法撤销。",
            onConfirm = {
                confirmReset = false
                viewModel.resetEverything()
            },
            onDismiss = { confirmReset = false },
            confirmText = "恢复出厂",
            destructive = true,
        )
    }
}
