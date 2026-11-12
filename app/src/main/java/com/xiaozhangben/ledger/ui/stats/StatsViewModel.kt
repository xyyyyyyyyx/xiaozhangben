// 统计页 ViewModel：只负责把流水与筛选条件暴露给界面，聚合计算交给 StatsCalculator。
package com.xiaozhangben.ledger.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xiaozhangben.ledger.data.repo.LedgerRepository
import com.xiaozhangben.ledger.domain.model.Transaction
import com.xiaozhangben.ledger.domain.model.TxnType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 统计页可选的时间范围，months 为相对当前月向前包含的月数。 */
enum class StatsRange(val label: String, val months: Int) {
    SIX_MONTHS("近 6 个月", 6),
    TWELVE_MONTHS("近 12 个月", 12),
    THIS_MONTH("本月", 1),
}

/** 统计页界面状态：保留原始流水，派生统计在界面里用 StatsCalculator 计算。 */
data class StatsUiState(
    val transactions: List<Transaction> = emptyList(),
    val range: StatsRange = StatsRange.SIX_MONTHS,
    val categoryType: TxnType = TxnType.EXPENSE,
)

class StatsViewModel(repository: LedgerRepository) : ViewModel() {

    private val _state = MutableStateFlow(StatsUiState())
    val state: StateFlow<StatsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.transactions.collect { list ->
                _state.update { it.copy(transactions = list) }
            }
        }
    }

    fun setRange(range: StatsRange) {
        _state.update { it.copy(range = range) }
    }

    fun setCategoryType(type: TxnType) {
        _state.update { it.copy(categoryType = type) }
    }
}
