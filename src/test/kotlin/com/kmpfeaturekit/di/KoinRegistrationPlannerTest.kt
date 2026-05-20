package com.kmpfeaturekit.di

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertNull

class KoinRegistrationPlannerTest {
    @Test
    fun registersModuleInStartKoinModulesCall() {
        val content = """
            package com.example

            import org.koin.core.context.startKoin

            fun initKoin() {
                startKoin {
                    modules(appModule)
                }
            }
        """.trimIndent()

        val updated = KoinRegistrationPlanner.registerModule(
            content,
            "paymentHistoryModule",
            "com.example.paymentHistory.di.paymentHistoryModule"
        )

        assertContains(updated.orEmpty(), "import com.example.paymentHistory.di.paymentHistoryModule")
        assertContains(updated.orEmpty(), "modules(appModule, paymentHistoryModule)")
    }

    @Test
    fun registersModuleInListOfModulesCall() {
        val content = """
            package com.example

            fun initKoin() {
                modules(listOf(appModule, networkModule))
            }
        """.trimIndent()

        val updated = KoinRegistrationPlanner.registerModule(
            content,
            "paymentHistoryModule",
            "com.example.paymentHistory.di.paymentHistoryModule"
        )

        assertContains(updated.orEmpty(), "modules(listOf(appModule, networkModule, paymentHistoryModule))")
    }

    @Test
    fun skipsAlreadyRegisteredModule() {
        val content = "fun initKoin() { modules(appModule, paymentHistoryModule) }"

        assertNull(
            KoinRegistrationPlanner.registerModule(
                content,
                "paymentHistoryModule",
                "com.example.paymentHistory.di.paymentHistoryModule"
            )
        )
    }
}
