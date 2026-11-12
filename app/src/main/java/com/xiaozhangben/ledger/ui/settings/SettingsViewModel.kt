// 设置页面的状态与操作：偏好设置、流水条数、CSV 导出/导入与两项危险操作。
package com.xiaozhangben.ledger.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xiaozhangben.ledger.data.backup.BackupManager
import com.xiaozhangben.ledger.data.prefs.AppSettings
import com.xiaozhangben.ledger.data.prefs.ThemeMode
import com.xiaozhangben.ledger.data.repo.LedgerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 设置页面的 UI 状态。 */
data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val transactionCount: Int = 0,
    val message: String? = null,
)

/** 设置页面的 ViewModel。 */
class SettingsViewModel(
    private val repository: LedgerRepository,
    private val backupManager: BackupManager,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    /** 导出文件名，形如「小账本_20260131_120000.csv」。 */
    val exportFileName: String get() = backupManager.suggestedFileName("小账本")

    init {
        viewModelScope.launch {
            repository.settings.collect { value ->
                _state.update { it.copy(settings = value) }
            }
        }
        viewModelScope.launch {
            repository.transactions.collect { list ->
                _state.update { it.copy(transactionCount = list.size) }
            }
        }
    }

    /** 主题模式三选一。 */
    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            val result = runCatching { repository.setThemeMode(mode) }
            _state.update { current ->
                current.copy(
                    message = result.fold(
                        onSuccess = { "主题已切换为${mode.label}" },
                        onFailure = { error -> "主题设置失败：${error.message ?: "未知错误"}" },
                    ),
                )
            }
        }
    }

    /** 把全部流水写成 CSV 并写入用户选择的文件。 */
    fun exportTo(uri: Uri) {
        viewModelScope.launch {
            val result = runCatching {
                val text = repository.exportCsv()
                backupManager.writeText(uri, text)
            }
            _state.update { current ->
                current.copy(
                    message = result.fold(
                        onSuccess = { "已导出全部流水" },
                        onFailure = { error -> "导出失败：${error.message ?: "未知错误"}" },
                    ),
                )
            }
        }
    }

    /** 读取用户选择的 CSV 并追加记录。 */
    fun importFrom(uri: Uri) {
        viewModelScope.launch {
            val result = runCatching {
                val text = backupManager.readText(uri)
                repository.importCsv(text)
            }
            _state.update { current ->
                current.copy(
                    message = result.fold(
                        onSuccess = { imported ->
                            "导入 ${imported.transactions} 笔，新建分类 ${imported.newCategories} 个，" +
                                "新建账户 ${imported.newAccounts} 个"
                        },
                        onFailure = { error -> "导入失败：${error.message ?: "未知错误"}" },
                    ),
                )
            }
        }
    }

    /** 清空全部流水，保留分类与账户设置。 */
    fun clearTransactions() {
        viewModelScope.launch {
            val result = runCatching { repository.clearTransactions() }
            _state.update { current ->
                current.copy(
                    message = result.fold(
                        onSuccess = { "已清空所有流水" },
                        onFailure = { error -> "清空失败：${error.message ?: "未知错误"}" },
                    ),
                )
            }
        }
    }

    /** 恢复出厂设置：清空全部数据并重建默认分类。 */
    fun resetEverything() {
        viewModelScope.launch {
            val result = runCatching { repository.resetEverything() }
            _state.update { current ->
                current.copy(
                    message = result.fold(
                        onSuccess = { "已恢复出厂设置" },
                        onFailure = { error -> "恢复失败：${error.message ?: "未知错误"}" },
                    ),
                )
            }
        }
    }

    /** 消息展示完清空，避免重复弹出。 */
    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }
}
