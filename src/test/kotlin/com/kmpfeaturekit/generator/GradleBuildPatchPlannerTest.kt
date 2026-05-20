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

    @Test
    fun insertsTopLevelKspDependencyBlock() {
        val content = """
            kotlin {
                sourceSets {
                    commonMain.dependencies {
                        implementation(kotlin("stdlib"))
                    }
                }
            }
        """.trimIndent()

        val updated = GradleBuildPatchPlanner.insertDependencies(
            content,
            listOf(
                GradleBuildPatchPlanner.DependencyAlias(
                    alias = "kotlin-inject-compiler-ksp",
                    module = "me.tatarka.inject:kotlin-inject-compiler-ksp",
                    version = "0.8.0",
                    configuration = "add(\"kspCommonMainMetadata\", %s)",
                    sourceSet = null
                )
            )
        )

        assertContains(updated.orEmpty(), "dependencies {")
        assertContains(updated.orEmpty(), "add(\"kspCommonMainMetadata\", libs.kotlin.inject.compiler.ksp)")
    }

    @Test
    fun insertsAndroidMainDependenciesWhenSourceSetExists() {
        val content = """
            kotlin {
                sourceSets {
                    androidMain {
                    }
                }
            }
        """.trimIndent()

        val updated = GradleBuildPatchPlanner.insertDependencies(
            content,
            listOf(
                GradleBuildPatchPlanner.DependencyAlias(
                    alias = "hilt-android",
                    module = "com.google.dagger:hilt-android",
                    version = "2.57.1",
                    sourceSet = "androidMain"
                )
            )
        )

        assertContains(updated.orEmpty(), "androidMain {")
        assertContains(updated.orEmpty(), "implementation(libs.hilt.android)")
    }

    @Test
    fun insertsMissingAliasesIntoVersionCatalogLibrariesSection() {
        val catalog = """
            [versions]
            kotlin = "2.2.21"

            [libraries]
            kotlin-test = { module = "org.jetbrains.kotlin:kotlin-test", version.ref = "kotlin" }
        """.trimIndent()

        val updated = GradleBuildPatchPlanner.insertLibraryAliases(
            catalog,
            listOf(
                GradleBuildPatchPlanner.DependencyAlias(
                    alias = "koin-core",
                    module = "io.insert-koin:koin-core",
                    version = "4.1.0"
                )
            )
        )

        assertContains(updated, "koin-core = { module = \"io.insert-koin:koin-core\", version = \"4.1.0\" }")
        assertContains(updated, "[libraries]")
    }
}
