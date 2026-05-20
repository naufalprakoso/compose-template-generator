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
    fun registersModuleInMultilineModulesCall() {
        val content = """
            package com.example

            fun initKoin() {
                modules(
                    appModule,
                    networkModule,
                )
            }
        """.trimIndent()

        val updated = KoinRegistrationPlanner.registerModule(
            content,
            "paymentHistoryModule",
            "com.example.paymentHistory.di.paymentHistoryModule"
        )

        assertContains(updated.orEmpty(), "networkModule,\n        paymentHistoryModule,")
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

    @Test
    fun registersKotlinInjectModuleInComponentSuperTypes() {
        val content = """
            package com.example

            import me.tatarka.inject.annotations.Component

            @Component
            abstract class AppComponent
        """.trimIndent()

        val updated = KotlinInjectRegistrationPlanner.registerModule(
            content,
            "PaymentHistoryInjectModule",
            "com.example.paymentHistory.di.PaymentHistoryInjectModule"
        )

        assertContains(updated.orEmpty(), "import com.example.paymentHistory.di.PaymentHistoryInjectModule")
        assertContains(updated.orEmpty(), "abstract class AppComponent : PaymentHistoryInjectModule")
    }

    @Test
    fun keepsLegacyKotlinInjectDependencyExposureAvailable() {
        val content = """
            package com.example

            import me.tatarka.inject.annotations.Component

            @Component
            abstract class AppComponent {
                abstract val appDependencies: AppDependencies
            }
        """.trimIndent()

        val updated = KotlinInjectRegistrationPlanner.registerDependency(
            content,
            "paymentHistoryDependencies",
            "com.example.paymentHistory.di.PaymentHistoryDependencies"
        )

        assertContains(updated.orEmpty(), "import com.example.paymentHistory.di.PaymentHistoryDependencies")
        assertContains(updated.orEmpty(), "val paymentHistoryDependencies: PaymentHistoryDependencies")
    }

    @Test
    fun includesHiltModuleInAggregateModule() {
        val content = """
            package com.example

            import dagger.Module
            import dagger.hilt.InstallIn
            import dagger.hilt.components.SingletonComponent

            @Module
            @InstallIn(SingletonComponent::class)
            object AppModule
        """.trimIndent()

        val updated = HiltRegistrationPlanner.includeModule(
            content,
            "PaymentHistoryModule",
            "com.example.paymentHistory.di.PaymentHistoryModule"
        )

        assertContains(updated.orEmpty(), "import com.example.paymentHistory.di.PaymentHistoryModule")
        assertContains(updated.orEmpty(), "@Module(includes = [PaymentHistoryModule::class])")
    }
}
