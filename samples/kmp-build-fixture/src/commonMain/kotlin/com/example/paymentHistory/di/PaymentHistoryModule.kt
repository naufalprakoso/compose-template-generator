package com.example.paymentHistory.di

import com.example.paymentHistory.data.DefaultPaymentHistoryRepository
import com.example.paymentHistory.data.DefaultPaymentHistoryService
import com.example.paymentHistory.domain.ObservePaymentHistoryUseCase
import com.example.paymentHistory.domain.PaymentHistoryRepository
import com.example.paymentHistory.domain.PaymentHistoryService
import com.example.paymentHistory.presentation.PaymentHistoryViewModel
import org.koin.dsl.module

val paymentHistoryModule = module {
    single<PaymentHistoryRepository> { DefaultPaymentHistoryRepository() }
    single<PaymentHistoryService> { DefaultPaymentHistoryService(get()) }
    factory { ObservePaymentHistoryUseCase(get()) }
    factory { PaymentHistoryViewModel(get()) }
}
