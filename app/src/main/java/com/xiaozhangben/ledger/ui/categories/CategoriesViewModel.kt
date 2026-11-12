// 分类管理页的状态与业务逻辑：按收支分组的排序、增删改与归档。
package com.xiaozhangben.ledger.ui.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xiaozhangben.ledger.data.repo.DeleteResult
import com.xiaozhangben.ledger.data.repo.LedgerRepository
import com.xiaozhangben.ledger.domain.model.Category
import com.xiaozhangben.ledger.domain.model.TxnType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 分类管理页界面状态。 */
data class CategoriesUiState(
    val categories: List<Category> = emptyList(),
    val selectedTab: Int = 0,
    val loading: Boolean = true,
    val errorMessage: String? = null,
    val archivedExpanded: Boolean = false,
    /** 删除被占用时，待确认归档的分类。 */
    val pendingArchive: Category? = null,
    val pendingArchiveUsage: String = "",
)

class CategoriesViewModel(private val repository: LedgerRepository) : ViewModel() {

    private val _state = MutableStateFlow(CategoriesUiState())
    val state: StateFlow<CategoriesUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.categories.collect { list ->
                _state.update { it.copy(categories = list, loading = false) }
            }
        }
    }

    // ---------------- 界面交互 ----------------

    fun selectTab(index: Int) {
        _state.update { it.copy(selectedTab = index) }
    }

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

    /** 上移（delta = -1）或下移（delta = +1），越界时仓库会自行忽略。 */
    suspend fun moveCategory(id: Long, delta: Int) {
        val result = runCatching { repository.moveCategory(id, delta) }
        result.onFailure { throwable ->
            _state.update { it.copy(errorMessage = "排序失败：${throwable.message ?: "未知错误"}") }
        }
    }

    suspend fun addCategory(name: String, type: TxnType, icon: String, color: Long): Boolean {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            _state.update { it.copy(errorMessage = "请填写分类名称") }
            return false
        }
        val duplicated = _state.value.categories.any { it.type == type && it.name == trimmed }
        if (duplicated) {
            _state.update { it.copy(errorMessage = "该分类下已有同名分类：$trimmed") }
            return false
        }
        val result = runCatching {
            repository.addCategory(Category(name = trimmed, type = type, icon = icon, color = color))
        }
        return result.fold(
            onSuccess = { true },
            onFailure = { throwable ->
                _state.update { it.copy(errorMessage = "保存失败：${throwable.message ?: "未知错误"}") }
                false
            },
        )
    }

    suspend fun updateCategory(category: Category, name: String, icon: String, color: Long): Boolean {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            _state.update { it.copy(errorMessage = "请填写分类名称") }
            return false
        }
        val duplicated = _state.value.categories.any {
            it.id != category.id && it.type == category.type && it.name == trimmed
        }
        if (duplicated) {
            _state.update { it.copy(errorMessage = "该分类下已有同名分类：$trimmed") }
            return false
        }
        val result = runCatching {
            repository.updateCategory(category.copy(name = trimmed, icon = icon, color = color))
        }
        return result.fold(
            onSuccess = { true },
            onFailure = { throwable ->
                _state.update { it.copy(errorMessage = "保存失败：${throwable.message ?: "未知错误"}") }
                false
            },
        )
    }

    /** 删除分类；被流水或周期账单引用时不删除，只记录待归档提示。 */
    suspend fun deleteCategory(category: Category) {
        val result = runCatching { repository.deleteCategory(category.id) }
        result.fold(
            onSuccess = { outcome ->
                if (outcome is DeleteResult.InUse) {
                    _state.update {
                        it.copy(pendingArchive = category, pendingArchiveUsage = usageText(outcome))
                    }
                }
            },
            onFailure = { throwable ->
                _state.update { it.copy(errorMessage = "删除失败：${throwable.message ?: "未知错误"}") }
            },
        )
    }

    suspend fun setArchived(id: Long, archived: Boolean) {
        val result = runCatching { repository.setCategoryArchived(id, archived) }
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
