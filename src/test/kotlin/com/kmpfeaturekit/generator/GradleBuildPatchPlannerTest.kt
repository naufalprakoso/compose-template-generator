package com.kmpfeaturekit.generator

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertNull

class GradleBuildPatchPlannerTest {
    @Test
    fun insertsDependenciesIntoCommonMainDependenciesBlock() {
        val content = """
            kotlin {
                sourceSets {
                    commonMain.dependencies {
                        implementation(kotlin("stdlib"))
                    }
                }
            }
        """.trimIndent()

        val updated = GradleBuildPatchPlanner.insertCommonMainDependencies(
            content,
            listOf("implementation(libs.koin.core)", "implementation(libs.androidx.navigation.compose)")
        )

        assertContains(updated.orEmpty(), "implementation(libs.koin.core)")
        assertContains(updated.orEmpty(), "implementation(libs.androidx.navigation.compose)")
    }

    @Test
    fun skipsWhenDependencyAlreadyExists() {
        val content = """
            kotlin {
                sourceSets {
                    commonMain.dependencies {
                        implementation(libs.koin.core)
                    }
                }
            }
        """.trimIndent()

        assertNull(
            GradleBuildPatchPlanner.insertCommonMainDependencies(
                content,
                listOf("implementation(libs.koin.core)")
            )
        )
    }
}
