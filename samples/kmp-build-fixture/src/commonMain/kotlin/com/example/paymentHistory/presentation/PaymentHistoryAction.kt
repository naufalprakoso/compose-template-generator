package com.example.paymentHistory.presentation

sealed interface PaymentHistoryAction {
    data object Started : PaymentHistoryAction
    data object Refresh : PaymentHistoryAction
    data object Retry : PaymentHistoryAction
    data class ItemSelected(val id: String) : PaymentHistoryAction
}
