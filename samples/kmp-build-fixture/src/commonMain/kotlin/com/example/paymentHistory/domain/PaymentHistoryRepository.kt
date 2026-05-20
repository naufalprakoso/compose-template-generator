package com.example.paymentHistory.domain

import com.example.paymentHistory.presentation.PaymentHistoryItem

interface PaymentHistoryRepository {
    suspend fun observePaymentHistory(): List<PaymentHistoryItem>
}
