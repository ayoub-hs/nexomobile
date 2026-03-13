package com.nexopos.erp.feature.scanner.vm

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexopos.erp.core.network.onlineOnlyMessage
import com.nexopos.erp.core.repo.ProductAdminRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ScannerLookupState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val lastBarcode: String? = null
)

sealed interface ScannerLookupEvent {
    data class ProductFound(val productId: Long) : ScannerLookupEvent
    data class ProductMissing(val barcode: String) : ScannerLookupEvent
}

class ScannerLookupViewModel(
    private val repository: ProductAdminRepository
) : ViewModel() {
    private val _state = MutableStateFlow(ScannerLookupState())
    val state: StateFlow<ScannerLookupState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<ScannerLookupEvent>()
    val events: SharedFlow<ScannerLookupEvent> = _events.asSharedFlow()

    private var fetchJob: Job? = null
    private var lastProcessedCode: String? = null
    private var lastProcessedAt: Long = 0L
    private val scanCooldownMs = 2_500L

    fun lookupBarcode(code: String) {
        val normalized = code.trim()
        if (normalized.isBlank()) {
            _state.value = _state.value.copy(error = "Barcode is required")
            return
        }

        val now = SystemClock.elapsedRealtime()
        if (normalized == lastProcessedCode && now - lastProcessedAt < scanCooldownMs) {
            return
        }
        lastProcessedCode = normalized
        lastProcessedAt = now

        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null, lastBarcode = normalized)
            try {
                val product = repository.searchByBarcode(normalized)
                if (product != null) {
                    _events.emit(ScannerLookupEvent.ProductFound(product.id))
                } else {
                    _events.emit(ScannerLookupEvent.ProductMissing(normalized))
                }
                _state.value = _state.value.copy(isLoading = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.onlineOnlyMessage("Barcode lookup failed")
                )
            }
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }
}
