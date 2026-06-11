package com.kmpfeaturekit.services

import com.kmpfeaturekit.model.DependencyInjectionType
import com.kmpfeaturekit.model.NavigationType
import com.kmpfeaturekit.model.ProjectStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProjectScanAnalyzerTest {
    @Test
    fun detectsLayeredGlobalProjectWithEvidence() {
        val scan = ProjectScanAnalyzer.analyze(
            ProjectScanInput(
                text = """
                    plugins { kotlin("multiplatform") }
                    implementation("io.ktor:ktor-client-core")
                """.trimIndent(),
                sourceDirectories = setOf(
                    "/repo/shared/src/commonMain/kotlin/com/example/data/remote",
                    "/repo/shared/src/commonMain/kotlin/com/example/domain/model",
                    "/repo/shared/src/commonMain/kotlin/com/example/presentation/home",
                    "/repo/shared/src/commonMain/kotlin/com/example/ui"
                ),
                hasKotlinGradle = true,
                hasGroovyGradle = false
            )
        )

        assertEquals(ProjectStyle.LAYERED_GLOBAL, scan.suggestedProjectStyle)
        assertEquals(NavigationType.NONE, scan.suggestedNavigation)
        assertEquals(DependencyInjectionType.MANUAL, scan.suggestedDi)
        assertEquals(ScanConfidence.HIGH, scan.confidence)
        assertTrue(scan.evidence.any { "data/domain/presentation/ui" in it })
        assertTrue(scan.evidence.any { "Ktor" in it })
    }

    @Test
    fun detectsNavigationComposeWhenProjectUsesNavHost() {
        val scan = ProjectScanAnalyzer.analyze(
            ProjectScanInput(
                text = "fun AppGraph() { NavHost(navController, startDestination = \"home\") {} }",
                sourceDirectories = emptySet(),
                hasKotlinGradle = false,
                hasGroovyGradle = false
            )
        )

        assertEquals(NavigationType.NAVIGATION_COMPOSE, scan.suggestedNavigation)
        assertTrue("Navigation Compose" in scan.detectedLibraries)
    }
}
