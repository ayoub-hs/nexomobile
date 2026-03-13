package com.nexopos.erp.feature.salespos.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexopos.shared.models.Register
import com.nexopos.shared.models.RegisterHistory
import com.nexopos.shared.repo.RegisterRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class RegisterUiState(
    val currentRegister: Register? = null,
    val registers: List<Register> = emptyList(),
    val registerHistory: List<RegisterHistory> = emptyList(),
    val isLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val error: String? = null
)

sealed interface RegisterEvent {
    data class Message(val message: String) : RegisterEvent
}

class RegisterViewModel(
    private val registerRepository: RegisterRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<RegisterEvent>()
    val events: SharedFlow<RegisterEvent> = _events.asSharedFlow()

    private var hasLoaded = false

    fun loadIfNeeded() {
        if (hasLoaded) {
            return
        }
        hasLoaded = true
        refreshAll()
    }

    fun refreshAll() {
        viewModelScope.launch {
            refreshSnapshot(showLoading = true)
        }
    }

    fun refreshAfterOrder() {
        val current = _uiState.value.currentRegister ?: return
        viewModelScope.launch {
            refreshCurrentRegisterState(registerId = current.id, fallback = current)
        }
    }

    suspend fun openRegister(registerId: Int, amount: Double, description: String = ""): Result<Register> {
        return runSubmitting {
            val opened = withContext(Dispatchers.IO) {
                registerRepository.openRegister(registerId, amount, description)
            }
            refreshCurrentRegisterState(registerId = opened.id, fallback = opened)
            _uiState.value.currentRegister ?: opened
        }
    }

    suspend fun closeCurrentRegister(amount: Double, description: String = ""): Result<Unit> {
        val current = _uiState.value.currentRegister
            ?: return Result.failure(IllegalStateException("No register is currently active."))

        return runSubmitting {
            withContext(Dispatchers.IO) {
                registerRepository.closeRegister(current.id, amount, description)
            }
            val refreshedRegisters = runCatching {
                withContext(Dispatchers.IO) { registerRepository.getRegisters() }
            }.getOrElse { _uiState.value.registers }
            _uiState.update {
                it.copy(
                    currentRegister = null,
                    registerHistory = emptyList(),
                    registers = refreshedRegisters,
                    error = null
                )
            }
            Unit
        }
    }

    suspend fun cashInCurrentRegister(amount: Double, description: String = ""): Result<Register> {
        val current = _uiState.value.currentRegister
            ?: return Result.failure(IllegalStateException("No register is currently active."))

        return runSubmitting {
            withContext(Dispatchers.IO) {
                registerRepository.cashIn(current.id, amount, description)
            }
            refreshCurrentRegisterState(registerId = current.id, fallback = current)
            _uiState.value.currentRegister ?: current
        }
    }

    suspend fun cashOutCurrentRegister(amount: Double, description: String = ""): Result<Register> {
        val current = _uiState.value.currentRegister
            ?: return Result.failure(IllegalStateException("No register is currently active."))

        return runSubmitting {
            withContext(Dispatchers.IO) {
                registerRepository.cashOut(current.id, amount, description)
            }
            refreshCurrentRegisterState(registerId = current.id, fallback = current)
            _uiState.value.currentRegister ?: current
        }
    }

    private suspend fun refreshCurrentRegisterState(registerId: Int, fallback: Register? = null) {
        val previous = _uiState.value
        _uiState.update { it.copy(error = null) }

        val registerResult = runCatching {
            withContext(Dispatchers.IO) { registerRepository.getRegister(registerId) }
        }
        val historyResult = runCatching {
            withContext(Dispatchers.IO) { registerRepository.getSessionHistory(registerId) }
        }
        val registersResult = runCatching {
            withContext(Dispatchers.IO) { registerRepository.getRegisters() }
        }

        val error = registerResult.exceptionOrNull()?.message
            ?: historyResult.exceptionOrNull()?.message
            ?: registersResult.exceptionOrNull()?.message

        _uiState.value = previous.copy(
            currentRegister = registerResult.getOrElse { fallback ?: previous.currentRegister },
            registers = registersResult.getOrElse { previous.registers },
            registerHistory = historyResult.getOrElse { previous.registerHistory },
            isLoading = false,
            error = error
        )

        if (!error.isNullOrBlank()) {
            _events.emit(RegisterEvent.Message(error))
        }
    }

    private suspend fun refreshSnapshot(showLoading: Boolean) {
        val previous = _uiState.value
        if (showLoading) {
            _uiState.update { it.copy(isLoading = true, error = null) }
        } else {
            _uiState.update { it.copy(error = null) }
        }

        val registersResult = runCatching {
            withContext(Dispatchers.IO) { registerRepository.getRegisters() }
        }
        val currentResult = runCatching {
            withContext(Dispatchers.IO) { registerRepository.getUsedRegister() }
        }
        val currentRegister = currentResult.getOrNull()?.let { current ->
            runCatching {
                withContext(Dispatchers.IO) { registerRepository.getRegister(current.id) }
            }.getOrElse { current }
        }
        val historyResult = runCatching {
            if (currentRegister != null) {
                withContext(Dispatchers.IO) { registerRepository.getSessionHistory(currentRegister.id) }
            } else {
                emptyList()
            }
        }

        val error = registersResult.exceptionOrNull()?.message
            ?: historyResult.exceptionOrNull()?.message

        _uiState.value = previous.copy(
            currentRegister = currentRegister,
            registers = registersResult.getOrElse { previous.registers },
            registerHistory = historyResult.getOrElse { previous.registerHistory },
            isLoading = false,
            error = error
        )

        if (!error.isNullOrBlank()) {
            _events.emit(RegisterEvent.Message(error))
        }
    }

    private suspend fun <T> runSubmitting(action: suspend () -> T): Result<T> {
        _uiState.update { it.copy(isSubmitting = true, error = null) }
        return try {
            val result = action()
            _uiState.update { it.copy(isSubmitting = false, error = null) }
            Result.success(result)
        } catch (error: Throwable) {
            val message = error.message ?: "Register operation failed."
            _uiState.update { it.copy(isSubmitting = false, error = message) }
            _events.emit(RegisterEvent.Message(message))
            Result.failure(error)
        }
    }
}
