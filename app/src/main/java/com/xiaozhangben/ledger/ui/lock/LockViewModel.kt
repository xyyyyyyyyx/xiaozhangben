package com.xiaozhangben.ledger.ui.lock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xiaozhangben.ledger.data.repo.LedgerRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

const val PIN_LENGTH = 4

data class LockUiState(
    val input: String = "",
    val error: String? = null,
    val checking: Boolean = false,
    val unlocked: Boolean = false,
    val hasPin: Boolean = false,
    val lockEnabled: Boolean = false,
    val biometricEnabled: Boolean = false,
    val biometricAvailable: Boolean = false,
)

/** 同时服务「解锁页」和「密码与指纹设置页」。 */
class LockViewModel(private val repository: LedgerRepository) : ViewModel() {

    private val _state = MutableStateFlow(LockUiState())
    val state: StateFlow<LockUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.settings.collect { settings ->
                _state.update {
                    it.copy(
                        hasPin = settings.hasPin,
                        lockEnabled = settings.lockEnabled,
                        biometricEnabled = settings.biometricEnabled,
                    )
                }
            }
        }
    }

    fun setBiometricAvailable(available: Boolean) =
        _state.update { it.copy(biometricAvailable = available) }

    // ---------- 解锁页 ----------

    fun appendDigit(digit: Char) {
        val current = _state.value
        if (current.checking || current.input.length >= PIN_LENGTH) return
        val next = current.input + digit
        _state.update { it.copy(input = next, error = null) }
        if (next.length == PIN_LENGTH) submit(next)
    }

    fun backspace() = _state.update {
        if (it.checking) it else it.copy(input = it.input.dropLast(1), error = null)
    }

    fun clearInput() = _state.update { it.copy(input = "", error = null) }

    private fun submit(pin: String) {
        _state.update { it.copy(checking = true) }
        viewModelScope.launch {
            val ok = runCatching { repository.verifyPin(pin) }.getOrDefault(false)
            _state.update {
                if (ok) {
                    it.copy(checking = false, unlocked = true, input = "", error = null)
                } else {
                    it.copy(checking = false, input = "", error = "密码不正确")
                }
            }
        }
    }

    /** 指纹验证成功后直接放行。 */
    fun unlockWithoutPin() = _state.update { it.copy(unlocked = true, input = "", error = null) }

    fun reportBiometricError(message: String) = _state.update { it.copy(error = message) }

    // ---------- 设置页 ----------

    suspend fun verify(pin: String): Boolean = runCatching { repository.verifyPin(pin) }.getOrDefault(false)

    suspend fun applyNewPin(pin: String) {
        repository.setPin(pin)
        repository.setLockEnabled(true)
    }

    suspend fun disableLock() {
        repository.clearPin()
    }

    suspend fun updateBiometric(enabled: Boolean) {
        repository.setBiometricEnabled(enabled)
    }
}
