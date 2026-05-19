package com.kmpfeaturekit.generator

import com.kmpfeaturekit.model.ArchitectureSelection
import com.kmpfeaturekit.model.ArchitectureType
import com.kmpfeaturekit.model.FeatureInfo
import com.kmpfeaturekit.model.FeatureOptions
import com.kmpfeaturekit.model.FeatureRequest
import com.kmpfeaturekit.model.PlatformTarget
import com.kmpfeaturekit.model.PlannedFileKind
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
        assertTrue(paths.any { it.endsWith("PaymentHistoryViewModel.kt") })
        assertTrue(paths.any { it.endsWith("PaymentHistoryService.kt") })
        assertTrue(paths.any { it.endsWith("DefaultPaymentHistoryService.kt") })
        assertTrue(paths.any { it.endsWith("PaymentHistoryGraph.kt") })
        assertTrue(paths.any { it.endsWith("PaymentHistoryNavigationGraph.kt") })
        assertTrue(paths.any { it.contains("commonTest") && it.endsWith("PaymentHistoryStateTest.kt") })
        assertTrue(paths.none { it.endsWith("Registration.todo.kt") })
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
