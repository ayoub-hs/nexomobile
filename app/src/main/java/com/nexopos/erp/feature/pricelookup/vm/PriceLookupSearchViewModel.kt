package com.nexopos.erp.feature.pricelookup.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexopos.erp.core.network.Product
import com.nexopos.erp.core.repo.ProductRepository
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PriceLookupSearchViewModel(
    private val repository: ProductRepository
) : ViewModel() {

    var query: String by mutableStateOf("")
        private set

    var results: List<Product> by mutableStateOf(emptyList())
        private set

    var isLoading: Boolean by mutableStateOf(false)
        private set

    var error: String? by mutableStateOf(null)
        private set

    private var searchJob: Job? = null
    private val minQueryLength = 2
    private val debounceMs = 250L

    fun updateQuery(value: String) {
        query = value
        error = null
        if (value.length < minQueryLength) {
            results = emptyList()
            isLoading = false
            searchJob?.cancel()
            return
        }

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            isLoading = true
            delay(debounceMs)
            try {
                results = repository.searchByTerm(value)
                error = null
            } catch (e: Exception) {
                results = emptyList()
                error = e.message ?: "Search failed"
            } finally {
                isLoading = false
            }
        }
    }

    fun clearSearch() {
        query = ""
        results = emptyList()
        isLoading = false
        error = null
        searchJob?.cancel()
    }
}
