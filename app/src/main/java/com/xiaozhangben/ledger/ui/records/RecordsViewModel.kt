// 账单列表状态：承载搜索关键字、类型/月份/分类/账户筛选，并做内存过滤与按日分组。
package com.xiaozhangben.ledger.ui.records

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xiaozhangben.ledger.data.repo.LedgerRepository
import com.xiaozhangben.ledger.domain.DateUtils
import com.xiaozhangben.ledger.domain.StatsCalculator
import com.xiaozhangben.ledger.domain.model.Account
import com.xiaozhangben.ledger.domain.model.Category
import com.xiaozhangben.ledger.domain.model.PeriodSummary
import com.xiaozhangben.ledger.domain.model.Transaction
import com.xiaozhangben.ledger.domain.model.TransactionFilter
import com.xiaozhangben.ledger.domain.model.TxnType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.YearMonth

/** 月份筛选项：全部月份 / 本月 / 上月。 */
enum class MonthFilter(val label: String) {
    ALL("全部月份"),
    CURRENT("本月"),
    PREVIOUS("上月");

    /** 对应的年月；ALL 返回 null 表示不限制月份。 */
    fun yearMonth(today: YearMonth = DateUtils.currentMonth()): YearMonth? = when (this) {
        ALL -> null
        CURRENT -> today
        PREVIOUS -> today.minusMonths(1)
    }
}

/** 账单列表的某一天分组。 */
data class TransactionDayGroup(
    val dayStart: Long,
    val header: String,
    val transactions: List<Transaction>,
    val summary: PeriodSummary,
)

/** 账单列表界面状态。 */
data class RecordsUiState(
    val transactions: List<Transaction> = emptyList(),
    val categories: List<Category> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val filter: TransactionFilter = TransactionFilter(),
    val monthFilter: MonthFilter = MonthFilter.ALL,
    val loading: Boolean = true,
) {
    /** 按当前筛选条件过滤后的流水（repository 已按时间倒序）。 */
    val filtered: List<Transaction>
        get() = transactions.filter { filter.matches(it) }

    /** 筛选结果的支出 / 收入合计。 */
    val resultSummary: PeriodSummary
        get() = StatsCalculator.summarize(filtered)

    /** 筛选结果按自然日分组，保持时间倒序。 */
    val dayGroups: List<TransactionDayGroup>
        get() {
            val grouped = LinkedHashMap<Long, MutableList<Transaction>>()
            for (transaction in filtered) {
                val dayStart = DateUtils.startOfDay(DateUtils.toLocalDate(transaction.occurredAt))
                grouped.getOrPut(dayStart) { mutableListOf() }.add(transaction)
            }
            return grouped.map { (dayStart, items) ->
                TransactionDayGroup(
                    dayStart = dayStart,
                    header = DateUtils.friendlyDay(dayStart),
                    transactions = items,
                    summary = StatsCalculator.summarize(items),
                )
            }
        }

    /** 是否处于无筛选条件状态（用于区分「没有记录」和「没有符合条件的记录」）。 */
    val isFilterActive: Boolean
        get() = filter.isActive

    /** 分类筛选项的显示文字。 */
    val categoryFilterLabel: String
        get() = categories.firstOrNull { it.id == filter.categoryId }?.name ?: "分类"

    /** 账户筛选项的显示文字。 */
    val accountFilterLabel: String
        get() = accounts.firstOrNull { it.id == filter.accountId }?.name ?: "账户"
}

class RecordsViewModel(private val repository: LedgerRepository) : ViewModel() {

    private val _state = MutableStateFlow(RecordsUiState())
    val state: StateFlow<RecordsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.transactions.collect { list ->
                _state.update { it.copy(transactions = list, loading = false) }
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

    /** 搜索关键字，直接写进筛选条件。 */
    fun onKeywordChange(keyword: String) {
        _state.update { it.copy(filter = it.filter.copy(keyword = keyword)) }
    }

    /** 清空搜索框。 */
    fun clearKeyword() {
        _state.update { it.copy(filter = it.filter.copy(keyword = "")) }
    }

    /** 类型筛选：null 表示全部。 */
    fun onTypeChange(type: TxnType?) {
        _state.update { it.copy(filter = it.filter.copy(type = type)) }
    }

    /** 月份筛选。 */
    fun onMonthFilterChange(monthFilter: MonthFilter) {
        _state.update {
            it.copy(
                monthFilter = monthFilter,
                filter = it.filter.copy(yearMonth = monthFilter.yearMonth()),
            )
        }
    }

    /** 分类筛选：null 表示全部分类。 */
    fun onCategoryChange(categoryId: Long?) {
        _state.update { it.copy(filter = it.filter.copy(categoryId = categoryId)) }
    }

    /** 账户筛选：null 表示全部账户。 */
    fun onAccountChange(accountId: Long?) {
        _state.update { it.copy(filter = it.filter.copy(accountId = accountId)) }
    }

    /** 一键清空全部筛选条件（保留搜索关键字之外的所有条件也一并重置）。 */
    fun clearFilters() {
        _state.update { it.copy(filter = TransactionFilter(), monthFilter = MonthFilter.ALL) }
    }
}
