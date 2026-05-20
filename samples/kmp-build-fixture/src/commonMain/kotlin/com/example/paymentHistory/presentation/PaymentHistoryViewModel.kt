package com.example.paymentHistory.presentation

import com.example.paymentHistory.domain.ObservePaymentHistoryUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PaymentHistoryViewModel(
    private val observePaymentHistory: ObservePaymentHistoryUseCase,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
) {
    private val _state = MutableStateFlow(PaymentHistoryState(isLoading = true))
    val state: StateFlow<PaymentHistoryState> = _state.asStateFlow()

    fun onAction(action: PaymentHistoryAction) {
        when (action) {
            PaymentHistoryAction.Started,
            PaymentHistoryAction.Refresh,
            PaymentHistoryAction.Retry -> load()
            is PaymentHistoryAction.ItemSelected -> Unit
        }
    }

    private fun load() {
        scope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null)
            runCatching { observePaymentHistory() }
                .onSuccess { _state.value = PaymentHistoryState(items = it) }
                .onFailure { _state.value = PaymentHistoryState(errorMessage = it.message ?: "Unable to load PaymentHistory") }
        }
    }
}
