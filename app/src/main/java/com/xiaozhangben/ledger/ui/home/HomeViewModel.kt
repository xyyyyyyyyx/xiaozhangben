// 首页状态与业务聚合：按月份统计结余、每日支出、总预算执行情况与账户概览。
package com.xiaozhangben.ledger.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xiaozhangben.ledger.data.repo.LedgerRepository
import com.xiaozhangben.ledger.domain.DateUtils
import com.xiaozhangben.ledger.domain.StatsCalculator
import com.xiaozhangben.ledger.domain.model.Account
import com.xiaozhangben.ledger.domain.model.AccountBalance
import com.xiaozhangben.ledger.domain.model.Budget
import com.xiaozhangben.ledger.domain.model.BudgetProgress
import com.xiaozhangben.ledger.domain.model.Category
import com.xiaozhangben.ledger.domain.model.PeriodSummary
import com.xiaozhangben.ledger.domain.model.Transaction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.YearMonth

/** 首页界面状态。loading 只用于首屏，避免后续数据刷新时闪白。 */
data class HomeUiState(
    val month: YearMonth = DateUtils.currentMonth(),
    val transactions: List<Transaction> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val budgets: List<Budget> = emptyList(),
    val loading: Boolean = true,
) {
    /** 选中月份的全部流水。 */
    val monthTransactions: List<Transaction>
        get() = StatsCalculator.inMonth(transactions, month)

    /** 选中月份的收支合计。 */
    val summary: PeriodSummary
        get() = StatsCalculator.summarize(monthTransactions)

    /** 选中月份每天的支出合计，索引 0 对应 1 号。 */
    val dailyExpense: List<Long>
        get() = StatsCalculator.dailyExpense(monthTransactions, month)

    /** 已启用的总预算（categoryId == null），没有则返回 null。 */
    val totalBudget: Budget?
        get() = budgets.firstOrNull { it.isTotal && it.enabled }

    /** 总预算执行进度，没有启用的总预算时为 null。 */
    val totalBudgetProgress: BudgetProgress?
        get() = totalBudget?.let { budget ->
            StatsCalculator.budgetProgress(listOf(budget), monthTransactions, categories).firstOrNull()
        }

    /** 未归档账户的余额，只取前 4 个用于首页概览。 */
    val accountBalances: List<AccountBalance>
        get() = StatsCalculator.accountBalances(accounts.filter { !it.archived }, transactions)

    val topAccountBalances: List<AccountBalance>
        get() = accountBalances.take(4)

    /** 选中月份最近 8 条流水，repository 已按时间倒序。 */
    val recentTransactions: List<Transaction>
        get() = monthTransactions.take(8)

    /** 能否切换到下一个月（不能晚于当前月）。 */
    val canGoNextMonth: Boolean
        get() = month < DateUtils.currentMonth()
}

class HomeViewModel(private val repository: LedgerRepository) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.transactions.collect { list ->
                _state.update { it.copy(transactions = list, loading = false) }
            }
        }
        viewModelScope.launch {
            repository.accounts.collect { list ->
                _state.update { it.copy(accounts = list) }
            }
        }
        viewModelScope.launch {
            repository.categories.collect { list ->
                _state.update { it.copy(categories = list) }
            }
        }
        viewModelScope.launch {
            repository.budgets.collect { list ->
                _state.update { it.copy(budgets = list) }
            }
        }
    }

    /** 上一月。 */
    fun previousMonth() {
        _state.update { it.copy(month = it.month.minusMonths(1)) }
    }

    /** 下一月，不允许超过当前月。 */
    fun nextMonth() {
        _state.update { current ->
            if (current.month >= DateUtils.currentMonth()) current else current.copy(month = current.month.plusMonths(1))
        }
    }
}
