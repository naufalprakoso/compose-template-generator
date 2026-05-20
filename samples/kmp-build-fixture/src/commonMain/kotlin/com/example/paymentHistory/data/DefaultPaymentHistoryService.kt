package com.example.paymentHistory.data

import com.example.paymentHistory.domain.PaymentHistoryRepository
import com.example.paymentHistory.domain.PaymentHistoryService

class DefaultPaymentHistoryService(
    private val repository: PaymentHistoryRepository
) : PaymentHistoryService {
    override suspend fun loadPaymentHistory() = repository.observePaymentHistory()
}
