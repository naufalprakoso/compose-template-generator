package com.example.paymentHistory.domain

import com.example.paymentHistory.presentation.PaymentHistoryItem

interface PaymentHistoryService {
    suspend fun loadPaymentHistory(): List<PaymentHistoryItem>
}
