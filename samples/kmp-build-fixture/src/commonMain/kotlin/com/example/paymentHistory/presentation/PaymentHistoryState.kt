package com.example.paymentHistory.presentation

data class PaymentHistoryState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val items: List<PaymentHistoryItem> = emptyList()
)

data class PaymentHistoryItem(
    val id: String,
    val title: String,
    val subtitle: String? = null
)
