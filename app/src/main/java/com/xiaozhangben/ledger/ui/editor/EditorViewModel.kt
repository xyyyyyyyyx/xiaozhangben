package com.xiaozhangben.ledger.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xiaozhangben.ledger.data.repo.LedgerRepository
import com.xiaozhangben.ledger.domain.Money
import com.xiaozhangben.ledger.domain.model.Account
import com.xiaozhangben.ledger.domain.model.Category
import com.xiaozhangben.ledger.domain.model.Transaction
import com.xiaozhangben.ledger.domain.model.TxnType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditorUiState(
    val editingId: Long? = null,
    val type: TxnType = TxnType.EXPENSE,
    val amountText: String = "",
    val categoryId: Long? = null,
    val accountId: Long? = null,
    val note: String = "",
    val occurredAt: Long = System.currentTimeMillis(),
    val categories: List<Category> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val preparing: Boolean = true,
    val saving: Boolean = false,
    val message: String? = null,
    val finished: Boolean = false,
) {
    val isEditing: Boolean get() = editingId != null

    val amount: Long get() = Money.parse(amountText) ?: 0L

    val canSave: Boolean get() = amount > 0L && categoryId != null && accountId != null && !saving
}

/** 记一笔 / 编辑已有记录。 */
class EditorViewModel(private val repository: LedgerRepository) : ViewModel() {

    private val _state = MutableStateFlow(EditorUiState())
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    private var allCategories: List<Category> = emptyList()
    private var everLoaded = false

    init {
        viewModelScope.launch {
            repository.categories.collect { list ->
                allCategories = list
                _state.update { current ->
                    val visible = list.filter { it.type == current.type && !it.archived }
                    val keep = current.categoryId?.takeIf { id -> visible.any { it.id == id } }
                    current.copy(
                        categories = visible,
                        categoryId = keep ?: visible.firstOrNull()?.id,
                    )
                }
            }
        }
        viewModelScope.launch {
            repository.accounts.collect { list ->
                val visible = list.filter { !it.archived }
                _state.update { current ->
                    val keep = current.accountId?.takeIf { id -> visible.any { it.id == id } }
                    current.copy(
                        accounts = visible,
                        accountId = keep ?: visible.firstOrNull()?.id,
                    )
                }
            }
        }
    }

    fun prepare(txnId: Long?, initialType: TxnType?) {
        if (everLoaded) return
        everLoaded = true
        viewModelScope.launch {
            if (txnId != null && txnId > 0L) {
                val existing = repository.findTransaction(txnId)
                if (existing != null) {
                    _state.update { current ->
                        current.copy(
                            editingId = existing.id,
                            type = existing.type,
                            amountText = Money.formatPlain(existing.amount),
                            categoryId = existing.categoryId,
                            accountId = existing.accountId,
                            note = existing.note,
                            occurredAt = existing.occurredAt,
                            preparing = false,
                            categories = allCategories.filter { it.type == existing.type && !it.archived },
                        )
                    }
                    return@launch
                }
            }
            val type = initialType ?: TxnType.EXPENSE
            _state.update { current ->
                current.copy(
                    type = type,
                    preparing = false,
                    categories = allCategories.filter { it.type == type && !it.archived },
                    categoryId = allCategories.firstOrNull { it.type == type && !it.archived }?.id,
                )
            }
        }
    }

    fun setType(type: TxnType) {
        _state.update { current ->
            if (current.type == type) {
                current
            } else {
                val visible = allCategories.filter { it.type == type && !it.archived }
                current.copy(
                    type = type,
                    categories = visible,
                    categoryId = visible.firstOrNull()?.id,
                )
            }
        }
    }

    fun setAmount(text: String) {
        // 只保留数字与一个小数点，避免用户粘贴奇怪内容
        val filtered = text.filter { it.isDigit() || it == '.' || it == '-' }
        _state.update { it.copy(amountText = filtered, message = null) }
    }

    fun setCategory(id: Long) = _state.update { it.copy(categoryId = id) }

    fun setAccount(id: Long) = _state.update { it.copy(accountId = id) }

    fun setNote(text: String) = _state.update { it.copy(note = text) }

    fun setOccurredAt(millis: Long) = _state.update { it.copy(occurredAt = millis) }

    fun shiftDay(days: Int) {
        _state.update { current ->
            val shifted = current.occurredAt + days * 24L * 60L * 60L * 1000L
            current.copy(occurredAt = shifted)
        }
    }

    fun consumeMessage() = _state.update { it.copy(message = null) }

    fun save() {
        val current = _state.value
        if (!current.canSave) {
            _state.update { it.copy(message = "请填写金额并选择分类") }
            return
        }
        val categoryId = current.categoryId ?: return
        val accountId = current.accountId ?: return
        _state.update { it.copy(saving = true, message = null) }
        viewModelScope.launch {
            runCatching {
                val transaction = Transaction(
                    id = current.editingId ?: 0L,
                    type = current.type,
                    amount = current.amount,
                    categoryId = categoryId,
                    accountId = accountId,
                    note = current.note.trim(),
                    occurredAt = current.occurredAt,
                    createdAt = System.currentTimeMillis(),
                )
                if (current.editingId != null) {
                    repository.updateTransaction(transaction)
                } else {
                    repository.addTransaction(transaction)
                }
            }.onSuccess {
                _state.update { it.copy(saving = false, finished = true) }
            }.onFailure { error ->
                _state.update { it.copy(saving = false, message = "保存失败：${error.message ?: "未知错误"}") }
            }
        }
    }

    fun delete() {
        val id = _state.value.editingId ?: return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            runCatching { repository.deleteTransaction(id) }
                .onSuccess { _state.update { it.copy(saving = false, finished = true) } }
                .onFailure { error ->
                    _state.update { it.copy(saving = false, message = "删除失败：${error.message ?: "未知错误"}") }
                }
        }
    }
}
