package com.example.paymentHistory.domain

class ObservePaymentHistoryUseCase(
    private val service: PaymentHistoryService
) {
    suspend operator fun invoke() = service.loadPaymentHistory()
}
