package com.kmpfeaturekit.navigation

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertNull

class NavigationRegistrationPlannerTest {
    @Test
    fun registersRouteInsideNavHost() {
        val content = """
            package com.example

            import androidx.navigation.compose.NavHost

            fun AppGraph() {
                NavHost(navController = navController, startDestination = "home") {
                    composable("home") {}
                }
            }
        """.trimIndent()

        val updated = NavigationRegistrationPlanner.registerRoute(
            content,
            "PaymentHistory",
            "com.example.paymentHistory"
        )

        assertContains(updated.orEmpty(), "import androidx.navigation.compose.composable")
        assertContains(updated.orEmpty(), "import com.example.paymentHistory.navigation.PaymentHistoryRoute")
        assertContains(updated.orEmpty(), "import com.example.paymentHistory.presentation.PaymentHistoryScreen")
        assertContains(updated.orEmpty(), "import com.example.paymentHistory.presentation.PaymentHistoryState")
        assertContains(
            updated.orEmpty(),
            "composable(PaymentHistoryRoute.path) { PaymentHistoryScreen(state = PaymentHistoryState(), onAction = {}) }"
        )
    }

    @Test
    fun skipsAlreadyRegisteredRoute() {
        val content = "fun AppGraph() { NavHost(navController, \"home\") { composable(PaymentHistoryRoute.path) {} } }"

        assertNull(
            NavigationRegistrationPlanner.registerRoute(
                content,
                "PaymentHistory",
                "com.example.paymentHistory"
            )
        )
    }
}
