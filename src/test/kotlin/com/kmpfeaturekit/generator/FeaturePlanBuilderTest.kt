package com.kmpfeaturekit.generator

import com.kmpfeaturekit.model.ArchitectureSelection
import com.kmpfeaturekit.model.ArchitectureType
import com.kmpfeaturekit.model.DependencyInjectionType
import com.kmpfeaturekit.model.FeatureInfo
import com.kmpfeaturekit.model.FeatureOptions
import com.kmpfeaturekit.model.FeatureRequest
import com.kmpfeaturekit.model.NavigationType
import com.kmpfeaturekit.model.PlatformTarget
import com.kmpfeaturekit.model.PlannedFileKind
import com.kmpfeaturekit.model.ProjectStyle
import com.kmpfeaturekit.model.StateHolderType
import com.kmpfeaturekit.templates.PureTemplateRenderer
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertTrue

class FeaturePlanBuilderTest {
    private val builder = FeaturePlanBuilder(PureTemplateRenderer::render)

    @Test
    fun mvvmPlanContainsViewModelScreenAndTest() {
        val files = builder.build(request(ArchitectureType.MVVM))
        val paths = files.map { it.path }
        assertTrue(paths.any { it.endsWith("PaymentHistoryScreen.kt") })
        assertTrue(paths.any { it.endsWith("PaymentHistoryPreview.kt") })
        assertTrue(paths.any { it.endsWith("PaymentHistoryViewModel.kt") })
        assertTrue(paths.any { it.endsWith("PaymentHistoryService.kt") })
        assertTrue(paths.any { it.endsWith("DefaultPaymentHistoryService.kt") })
        assertTrue(paths.any { it.endsWith("PaymentHistoryGraph.kt") })
        assertTrue(paths.any { it.endsWith("PaymentHistoryNavigationGraph.kt") })
        assertTrue(paths.any { it.contains("commonTest") && it.endsWith("PaymentHistoryStateTest.kt") })
        assertTrue(paths.any { it.endsWith("PaymentHistoryKoinRegistration.todo.md") })
        assertTrue(paths.any { it.endsWith("PaymentHistoryNavigationRegistration.todo.md") })
    }

    @Test
    fun circuitPlanContainsPresenter() {
        val files = builder.build(request(ArchitectureType.SLACK_CIRCUIT))
        assertTrue(files.any { it.path.endsWith("PaymentHistoryPresenter.kt") && "PaymentHistoryPresenter" in it.content })
    }

    @Test
    fun decomposePlanContainsComponent() {
        val files = builder.build(request(ArchitectureType.DECOMPOSE))
        assertTrue(files.any { it.path.endsWith("PaymentHistoryComponent.kt") && "DefaultPaymentHistoryComponent" in it.content })
    }

    @Test
    fun stateHolderTypeControlsGeneratedHolderAndDiRegistration() {
        val files = builder.build(
            request(ArchitectureType.SIMPLE_FEATURE).copy(
                architecture = ArchitectureSelection(
                    architectureType = ArchitectureType.SIMPLE_FEATURE,
                    stateHolderType = StateHolderType.PLAIN_STATE_HOLDER,
                    dependencyInjectionType = DependencyInjectionType.KOIN
                )
            )
        )

        assertTrue(files.any { it.path.endsWith("PaymentHistoryStateHolder.kt") && "class PaymentHistoryStateHolder" in it.content })
        val koinModule = files.single { it.path.endsWith("PaymentHistoryModule.kt") }
        assertTrue("import com.example.paymentHistory.presentation.PaymentHistoryStateHolder" in koinModule.content)
        assertTrue("factory { PaymentHistoryStateHolder(get()) }" in koinModule.content)
        assertTrue(files.none { it.path.endsWith("PaymentHistoryViewModel.kt") })
    }

    @Test
    fun projectStyleControlsFeatureLayout() {
        val layerFiles = builder.build(
            request(ArchitectureType.MVVM).copy(
                architecture = ArchitectureSelection(projectStyle = ProjectStyle.LAYER_BASED)
            )
        )
        assertTrue(layerFiles.any {
            it.path.endsWith("commonMain/kotlin/com/example/presentation/paymentHistory/PaymentHistoryScreen.kt") &&
                "package com.example.presentation" in it.content
        })

        val hybridFiles = builder.build(
            request(ArchitectureType.MVVM).copy(
                architecture = ArchitectureSelection(projectStyle = ProjectStyle.HYBRID)
            )
        )
        assertTrue(hybridFiles.any {
            it.path.endsWith("commonMain/kotlin/com/example/features/paymentHistory/presentation/PaymentHistoryScreen.kt") &&
                "package com.example.features.paymentHistory.presentation" in it.content
        })
    }

    @Test
    fun expectActualPlanCreatesPlatformFiles() {
        val files = builder.build(
            request(ArchitectureType.MVVM).copy(
                options = FeatureOptions(expectActualPlatformAbstraction = true),
                architecture = ArchitectureSelection(platforms = setOf(PlatformTarget.ANDROID, PlatformTarget.IOS))
            )
        )
        assertTrue(files.any { it.path.contains("commonMain") && "expect class PaymentHistoryPlatformContext" in it.content })
        assertTrue(files.any { it.path.contains("androidMain") && "actual class PaymentHistoryPlatformContext" in it.content })
        assertTrue(files.any { it.path.contains("iosMain") && "actual class PaymentHistoryPlatformContext" in it.content })
    }

    @Test
    fun detectsKotlinGradleBuildFileForPatch() {
        val temp = createTempDirectory()
        temp.resolve("src").createDirectories()
        temp.resolve("build.gradle.kts").writeText("plugins { kotlin(\"multiplatform\") }\n")

        val files = builder.build(request(ArchitectureType.MVVM, temp.resolve("src").toString()))
        val gradle = files.single { it.path.endsWith("build.gradle.kts") }
        assertTrue(gradle.kind == PlannedFileKind.MODIFY)
        assertTrue("Compose Template Generator: PaymentHistory" in gradle.content)
    }

    @Test
    fun detectsGroovyGradleBuildFileForPatch() {
        val temp = createTempDirectory()
        temp.resolve("src").createDirectories()
        temp.resolve("build.gradle").writeText("plugins { id 'org.jetbrains.kotlin.multiplatform' }\n")

        val files = builder.build(request(ArchitectureType.MVVM, temp.resolve("src").toString()))
        val gradle = files.single { it.path.endsWith("build.gradle") }
        assertTrue(gradle.kind == PlannedFileKind.MODIFY)
        assertTrue("Compose Template Generator: PaymentHistory" in gradle.content)
    }

    @Test
    fun patchesRecognizedKoinAndNavigationTargets() {
        val temp = createTempDirectory()
        val module = temp.resolve("shared")
        module.resolve("src").createDirectories()
        module.resolve("src/commonMain/kotlin/com/example/AppDi.kt").apply {
            parent.createDirectories()
            writeText(
                """
                    package com.example

                    import org.koin.core.context.startKoin

                    fun initKoin() {
                        startKoin {
                            modules(appModule)
                        }
                    }
                """.trimIndent()
            )
        }
        module.resolve("src/commonMain/kotlin/com/example/AppGraph.kt").writeText(
            """
                package com.example

                import androidx.navigation.compose.NavHost

                fun AppGraph() {
                    NavHost(navController = navController, startDestination = "home") {
                        composable("home") {}
                    }
                }
            """.trimIndent()
        )

        val files = builder.build(
            request(ArchitectureType.MVVM, module.resolve("src").toString()).copy(
                architecture = ArchitectureSelection(
                    architectureType = ArchitectureType.MVVM,
                    navigationType = NavigationType.NAVIGATION_COMPOSE,
                    dependencyInjectionType = DependencyInjectionType.KOIN
                )
            )
        )

        val koinRoot = files.single { it.path.endsWith("AppDi.kt") }
        assertTrue(koinRoot.kind == PlannedFileKind.MODIFY)
        assertTrue(koinRoot.replacesFile)
        assertTrue("modules(appModule, paymentHistoryModule)" in koinRoot.content)

        val navRoot = files.single { it.path.endsWith("AppGraph.kt") }
        assertTrue(navRoot.kind == PlannedFileKind.MODIFY)
        assertTrue(navRoot.replacesFile)
        assertTrue("composable(PaymentHistoryRoute.path)" in navRoot.content)
    }

    @Test
    fun patchesGradleDependenciesWhenVersionCatalogAliasesExist() {
        val temp = createTempDirectory()
        val module = temp.resolve("shared")
        module.resolve("src").createDirectories()
        temp.resolve("gradle").createDirectories()
        temp.resolve("gradle/libs.versions.toml").writeText(
            """
                [libraries]
                koin-core = { module = "io.insert-koin:koin-core", version = "4.0.0" }
                androidx-navigation-compose = { module = "androidx.navigation:navigation-compose", version = "2.8.0" }
            """.trimIndent()
        )
        module.resolve("build.gradle.kts").writeText(
            """
                kotlin {
                    sourceSets {
                        commonMain.dependencies {
                            implementation(kotlin("stdlib"))
                        }
                    }
                }
            """.trimIndent()
        )

        val files = builder.build(request(ArchitectureType.MVVM, module.resolve("src").toString()))
        val gradle = files.single { it.path.endsWith("build.gradle.kts") }

        assertTrue(gradle.replacesFile)
        assertTrue("implementation(libs.koin.core)" in gradle.content)
        assertTrue("implementation(libs.androidx.navigation.compose)" in gradle.content)
    }

    @Test
    fun generatedFilesDoNotContainUnresolvedTemplatePlaceholders() {
        ArchitectureType.entries.forEach { architecture ->
            val files = builder.build(request(architecture))

            files.forEach { file ->
                assertTrue("{{" !in file.content, "Unresolved template placeholder in ${file.path}")
                assertTrue("}}" !in file.content, "Unresolved template placeholder in ${file.path}")
            }
        }
    }

    @Test
    fun repositoryUsesServiceAndUseCaseUsesRepository() {
        val files = builder.build(request(ArchitectureType.MVVM))

        val service = files.single { it.path.endsWith("DefaultPaymentHistoryService.kt") }
        assertTrue("class DefaultPaymentHistoryService : PaymentHistoryService" in service.content)
        assertTrue("DefaultPaymentHistoryService(" !in service.content)

        val repository = files.single { it.path.endsWith("DefaultPaymentHistoryRepository.kt") }
        assertTrue("private val service: PaymentHistoryService" in repository.content)
        assertTrue("service.loadPaymentHistory()" in repository.content)

        val useCase = files.single { it.path.endsWith("ObservePaymentHistoryUseCase.kt") }
        assertTrue("private val repository: PaymentHistoryRepository" in useCase.content)
        assertTrue("repository.observePaymentHistory()" in useCase.content)
    }

    @Test
    fun fileOptionsControlPreviewReadmeAndTests() {
        val files = builder.build(
            request(ArchitectureType.MVVM).copy(
                options = FeatureOptions(
                    preview = false,
                    readme = true,
                    unitTests = false,
                    autoRegisterDi = false,
                    autoRegisterNavigation = false
                )
            )
        )
        val paths = files.map { it.path }

        assertTrue(paths.none { it.endsWith("PaymentHistoryPreview.kt") })
        assertTrue(paths.any { it.endsWith("README.md") })
        assertTrue(paths.none { it.endsWith("PaymentHistoryStateTest.kt") })
    }

    private fun request(type: ArchitectureType, sourceSetRoot: String = "/tmp/shared") = FeatureRequest(
        info = FeatureInfo(
            featureName = "Payment History",
            basePackage = "com.example",
            targetModule = "shared",
            sourceSetRoot = sourceSetRoot
        ),
        architecture = ArchitectureSelection(architectureType = type)
    )
}
