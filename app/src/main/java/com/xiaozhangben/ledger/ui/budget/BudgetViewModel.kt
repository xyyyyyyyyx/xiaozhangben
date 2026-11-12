// 预算页 ViewModel：预算、分类与流水的读取，以及预算的保存/删除。
package com.xiaozhangben.ledger.ui.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xiaozhangben.ledger.data.repo.LedgerRepository
import com.xiaozhangben.ledger.domain.DateUtils
import com.xiaozhangben.ledger.domain.model.Budget
import com.xiaozhangben.ledger.domain.model.Category
import com.xiaozhangben.ledger.domain.model.Transaction
import java.time.YearMonth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 预算页界面状态：month 为当前查看的月份，预算金额按月复用。 */
data class BudgetUiState(
    val budgets: List<Budget> = emptyList(),
    val categories: List<Category> = emptyList(),
    val transactions: List<Transaction> = emptyList(),
    val month: YearMonth = DateUtils.currentMonth(),
    val error: String? = null,
)

class BudgetViewModel(private val repository: LedgerRepository) : ViewModel() {

    private val _state = MutableStateFlow(BudgetUiState())
    val state: StateFlow<BudgetUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.budgets.collect { list ->
                _state.update { it.copy(budgets = list) }
            }
        }
        viewModelScope.launch {
            repository.categories.collect { list ->
                _state.update { it.copy(categories = list) }
            }
        }
        viewModelScope.launch {
            repository.transactions.collect { list ->
                _state.update { it.copy(transactions = list) }
            }
        }
    }

    fun showPreviousMonth() {
        _state.update { it.copy(month = it.month.minusMonths(1)) }
    }

    fun showNextMonth() {
        _state.update { it.copy(month = it.month.plusMonths(1)) }
    }

    /** 总预算：categoryId = null。amount <= 0 时仓库会自动删除该预算。 */
    fun saveTotalBudget(amount: Long) {
        saveBudget(null, amount)
    }

    fun saveCategoryBudget(categoryId: Long, amount: Long) {
        saveBudget(categoryId, amount)
    }

    fun deleteBudget(id: Long) {
        viewModelScope.launch {
            runCatching { repository.deleteBudget(id) }
                .onFailure { throwable ->
                    _state.update { it.copy(error = throwable.message ?: "删除预算失败") }
                }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    private fun saveBudget(categoryId: Long?, amount: Long) {
        viewModelScope.launch {
            runCatching { repository.saveBudget(categoryId, amount) }
                .onFailure { throwable ->
                    _state.update { it.copy(error = throwable.message ?: "保存预算失败") }
                }
        }
    }
}
