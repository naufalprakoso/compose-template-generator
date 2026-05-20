package com.example.paymentHistory.data

import com.example.paymentHistory.domain.PaymentHistoryRepository
import com.example.paymentHistory.presentation.PaymentHistoryItem

class DefaultPaymentHistoryRepository : PaymentHistoryRepository {
    override suspend fun observePaymentHistory(): List<PaymentHistoryItem> =
        listOf(PaymentHistoryItem(id = "sample", title = "PaymentHistory item"))
}
