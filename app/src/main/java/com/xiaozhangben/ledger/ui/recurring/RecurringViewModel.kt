// 周期账单页面的状态与操作：读取规则/分类/账户，处理新建、编辑、启停、立即记账与删除。
package com.xiaozhangben.ledger.ui.recurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xiaozhangben.ledger.data.repo.LedgerRepository
import com.xiaozhangben.ledger.domain.model.Account
import com.xiaozhangben.ledger.domain.model.Category
import com.xiaozhangben.ledger.domain.model.RecurringRule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 周期账单页面的 UI 状态。 */
data class RecurringUiState(
    val rules: List<RecurringRule> = emptyList(),
    val categories: List<Category> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val message: String? = null,
) {
    /** 按 categoryId 去分类表里取颜色，找不到时用兜底色。 */
    fun categoryColorOf(categoryId: Long): Long =
        categories.firstOrNull { it.id == categoryId }?.color ?: FALLBACK_COLOR

    companion object {
        /** 分类缺失时的兜底颜色（蓝灰）。 */
        const val FALLBACK_COLOR: Long = 0xFF78909C
    }
}

/** 周期账单页面的 ViewModel。 */
class RecurringViewModel(private val repository: LedgerRepository) : ViewModel() {

    private val _state = MutableStateFlow(RecurringUiState())
    val state: StateFlow<RecurringUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.recurringRules.collect { list ->
                _state.update { it.copy(rules = list) }
            }
        }
        viewModelScope.launch {
            repository.categories.collect { list ->
                _state.update { it.copy(categories = list) }
            }
        }
        viewModelScope.launch {
            repository.accounts.collect { list ->
                _state.update { it.copy(accounts = list) }
            }
        }
    }

    /** 新建（id == 0）或更新一条周期规则。 */
    fun save(rule: RecurringRule) {
        val isNew = rule.id == 0L
        viewModelScope.launch {
            val result = runCatching {
                if (isNew) repository.addRecurring(rule) else repository.updateRecurring(rule)
            }
            _state.update { current ->
                current.copy(
                    message = result.fold(
                        onSuccess = { if (isNew) "已新建周期账单" else "已保存修改" },
                        onFailure = { error -> "保存失败：${error.message ?: "未知错误"}" },
                    ),
                )
            }
        }
    }

    /** 启用或停用一条规则。 */
    fun setEnabled(rule: RecurringRule, enabled: Boolean) {
        viewModelScope.launch {
            val result = runCatching { repository.updateRecurring(rule.copy(enabled = enabled)) }
            _state.update { current ->
                current.copy(
                    message = result.fold(
                        onSuccess = { if (enabled) "已开启该规则" else "已停用该规则" },
                        onFailure = { error -> "操作失败：${error.message ?: "未知错误"}" },
                    ),
                )
            }
        }
    }

    /** 立即按规则记一笔，下次执行时间会往后推一期。 */
    fun postNow(rule: RecurringRule) {
        viewModelScope.launch {
            val result = runCatching { repository.postRecurringNow(rule) }
            _state.update { current ->
                current.copy(
                    message = result.fold(
                        onSuccess = { "已立即记一笔，下次执行时间已后移" },
                        onFailure = { error -> "记账失败：${error.message ?: "未知错误"}" },
                    ),
                )
            }
        }
    }

    /** 删除一条规则（已有的流水不受影响）。 */
    fun delete(rule: RecurringRule) {
        viewModelScope.launch {
            val result = runCatching { repository.deleteRecurring(rule.id) }
            _state.update { current ->
                current.copy(
                    message = result.fold(
                        onSuccess = { "已删除该规则" },
                        onFailure = { error -> "删除失败：${error.message ?: "未知错误"}" },
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
