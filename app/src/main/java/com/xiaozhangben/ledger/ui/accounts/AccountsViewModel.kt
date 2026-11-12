// 账户页的状态与业务逻辑：余额汇总、增删改、归档与恢复。
package com.xiaozhangben.ledger.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xiaozhangben.ledger.data.repo.DeleteResult
import com.xiaozhangben.ledger.data.repo.LedgerRepository
import com.xiaozhangben.ledger.domain.model.Account
import com.xiaozhangben.ledger.domain.model.AccountType
import com.xiaozhangben.ledger.domain.model.Transaction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 账户页界面状态。 */
data class AccountsUiState(
    val accounts: List<Account> = emptyList(),
    val transactions: List<Transaction> = emptyList(),
    val loading: Boolean = true,
    val errorMessage: String? = null,
    /** 已经展开「已归档」区域。 */
    val archivedExpanded: Boolean = false,
    /** 删除被流水占用时，待确认归档的账户。 */
    val pendingArchive: Account? = null,
    val pendingArchiveUsage: String = "",
)

class AccountsViewModel(private val repository: LedgerRepository) : ViewModel() {

    private val _state = MutableStateFlow(AccountsUiState())
    val state: StateFlow<AccountsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.accounts.collect { list ->
                _state.update { it.copy(accounts = list, loading = false) }
            }
        }
        viewModelScope.launch {
            repository.transactions.collect { list ->
                _state.update { it.copy(transactions = list) }
            }
        }
    }

    // ---------------- 列表交互 ----------------

    fun toggleArchived() {
        _state.update { it.copy(archivedExpanded = !it.archivedExpanded) }
    }

    fun consumeError() {
        _state.update { it.copy(errorMessage = null) }
    }

    fun dismissPendingArchive() {
        _state.update { it.copy(pendingArchive = null, pendingArchiveUsage = "") }
    }

    // ---------------- 写操作 ----------------

    /** 新建账户，成功返回 true 供界面关闭对话框。 */
    suspend fun addAccount(name: String, type: AccountType, initialBalance: Long, color: Long): Boolean {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            _state.update { it.copy(errorMessage = "请填写账户名称") }
            return false
        }
        val duplicated = _state.value.accounts.any { !it.archived && it.name == trimmed }
        if (duplicated) {
            _state.update { it.copy(errorMessage = "已有同名账户：$trimmed") }
            return false
        }
        val result = runCatching {
            repository.addAccount(
                Account(name = trimmed, type = type, initialBalance = initialBalance, color = color),
            )
        }
        return result.fold(
            onSuccess = { true },
            onFailure = { throwable ->
                _state.update { it.copy(errorMessage = "保存失败：${throwable.message ?: "未知错误"}") }
                false
            },
        )
    }

    /** 保存对已有账户的修改。 */
    suspend fun updateAccount(account: Account, name: String, type: AccountType, initialBalance: Long, color: Long): Boolean {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            _state.update { it.copy(errorMessage = "请填写账户名称") }
            return false
        }
        val duplicated = _state.value.accounts.any { it.id != account.id && it.name == trimmed }
        if (duplicated) {
            _state.update { it.copy(errorMessage = "已有同名账户：$trimmed") }
            return false
        }
        val result = runCatching {
            repository.updateAccount(
                account.copy(name = trimmed, type = type, initialBalance = initialBalance, color = color),
            )
        }
        return result.fold(
            onSuccess = { true },
            onFailure = { throwable ->
                _state.update { it.copy(errorMessage = "保存失败：${throwable.message ?: "未知错误"}") }
                false
            },
        )
    }

    /** 删除账户；被流水占用时不删除，只记录待归档提示。 */
    suspend fun deleteAccount(account: Account) {
        val result = runCatching { repository.deleteAccount(account.id) }
        result.fold(
            onSuccess = { outcome ->
                if (outcome is DeleteResult.InUse) {
                    _state.update {
                        it.copy(pendingArchive = account, pendingArchiveUsage = usageText(outcome))
                    }
                }
            },
            onFailure = { throwable ->
                _state.update { it.copy(errorMessage = "删除失败：${throwable.message ?: "未知错误"}") }
            },
        )
    }

    suspend fun setArchived(id: Long, archived: Boolean) {
        val result = runCatching { repository.setAccountArchived(id, archived) }
        result.onFailure { throwable ->
            _state.update { it.copy(errorMessage = "操作失败：${throwable.message ?: "未知错误"}") }
        }
        if (archived) {
            _state.update { it.copy(pendingArchive = null, pendingArchiveUsage = "") }
        }
    }

    private fun usageText(result: DeleteResult.InUse): String {
        val parts = mutableListOf<String>()
        if (result.transactionCount > 0) parts += "${result.transactionCount} 笔流水"
        if (result.recurringCount > 0) parts += "${result.recurringCount} 条周期账单"
        return parts.joinToString("、")
    }
}
