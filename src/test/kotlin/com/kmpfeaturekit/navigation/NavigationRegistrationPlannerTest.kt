package com.kmpfeaturekit.navigation

import com.kmpfeaturekit.model.NavigationType
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
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

    @Test
    fun registersVoyagerEntryInRegistryList() {
        val content = """
            package com.example

            val voyagerScreens = listOf(
                HomeNavigationGraph.voyagerRoute,
            )
        """.trimIndent()

        val updated = NavigationRegistrationPlanner.registerListEntry(
            content,
            listOf("voyagerScreens", "screens"),
            "PaymentHistoryNavigationGraph.voyagerRoute",
            "com.example.paymentHistory.navigation.PaymentHistoryNavigationGraph"
        )

        assertContains(updated.orEmpty(), "import com.example.paymentHistory.navigation.PaymentHistoryNavigationGraph")
        assertContains(updated.orEmpty(), "PaymentHistoryNavigationGraph.voyagerRoute,")
    }

    @Test
    fun registersCircuitEntryInRegistryList() {
        val content = """
            package com.example

            val circuitScreens = listOf(
                HomeNavigationGraph.circuitRoute,
            )
        """.trimIndent()

        val updated = NavigationRegistrationPlanner.registerListEntry(
            content,
            listOf("circuitScreens", "screenBindings"),
            "PaymentHistoryNavigationGraph.circuitRoute",
            "com.example.paymentHistory.navigation.PaymentHistoryNavigationGraph"
        )

        assertContains(updated.orEmpty(), "PaymentHistoryNavigationGraph.circuitRoute,")
    }

    @Test
    fun registersDecomposeEntryInRegistryList() {
        val content = """
            package com.example

            val decomposeConfigs = listOf(
                HomeNavigationGraph.decomposeConfig,
            )
        """.trimIndent()

        val updated = NavigationRegistrationPlanner.registerListEntry(
            content,
            listOf("decomposeConfigs", "childConfigs"),
            "PaymentHistoryNavigationGraph.decomposeConfig",
            "com.example.paymentHistory.navigation.PaymentHistoryNavigationGraph"
        )

        assertContains(updated.orEmpty(), "PaymentHistoryNavigationGraph.decomposeConfig,")
    }

    @Test
    fun registersAppyxEntryInRegistryList() {
        val content = """
            package com.example

            val appyxNodes = listOf(
                HomeNavigationGraph.appyxNode,
            )
        """.trimIndent()

        val updated = NavigationRegistrationPlanner.registerListEntry(
            content,
            listOf("appyxNodes", "nodes"),
            "PaymentHistoryNavigationGraph.appyxNode",
            "com.example.paymentHistory.navigation.PaymentHistoryNavigationGraph"
        )

        assertContains(updated.orEmpty(), "PaymentHistoryNavigationGraph.appyxNode,")
    }

    @Test
    fun plansEverySupportedNavigationRegistry() {
        val temp = createTempDirectory()
        temp.resolve("src/commonMain/kotlin/com/example/Registries.kt").apply {
            parent.createDirectories()
            writeText(
                """
                    package com.example

                    val voyagerScreens = listOf(
                        HomeNavigationGraph.voyagerRoute,
                    )
                    val circuitScreens = listOf(
                        HomeNavigationGraph.circuitRoute,
                    )
                    val decomposeConfigs = listOf(
                        HomeNavigationGraph.decomposeConfig,
                    )
                    val appyxNodes = listOf(
                        HomeNavigationGraph.appyxNode,
                    )
                """.trimIndent()
            )
        }

        listOf(
            NavigationType.VOYAGER to "PaymentHistoryNavigationGraph.voyagerRoute",
            NavigationType.CIRCUIT_NAVIGATION to "PaymentHistoryNavigationGraph.circuitRoute",
            NavigationType.DECOMPOSE_NAVIGATION to "PaymentHistoryNavigationGraph.decomposeConfig",
            NavigationType.APPYX to "PaymentHistoryNavigationGraph.appyxNode"
        ).forEach { (type, expectedEntry) ->
            val plan = NavigationRegistrationPlanner.plan(
                moduleRoot = temp,
                routeName = "PaymentHistory",
                navigationType = type,
                featurePackageName = "com.example.paymentHistory"
            )

            assertContains(plan.replacementContent.orEmpty(), expectedEntry)
        }
    }
}
