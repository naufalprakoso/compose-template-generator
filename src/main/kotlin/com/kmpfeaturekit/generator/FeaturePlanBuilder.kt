package com.kmpfeaturekit.generator

import com.kmpfeaturekit.model.ArchitectureType
import com.kmpfeaturekit.model.DependencyInjectionType
import com.kmpfeaturekit.model.FeatureRequest
import com.kmpfeaturekit.model.PlatformTarget
import com.kmpfeaturekit.model.PlannedFile
import com.kmpfeaturekit.model.PlannedFileKind
import com.kmpfeaturekit.templates.FeatureTemplates
import java.nio.file.Path
import java.time.LocalDate
import kotlin.io.path.exists

class FeaturePlanBuilder(private val renderer: (String, Map<String, String>) -> String) {
    fun build(request: FeatureRequest): List<PlannedFile> {
        val vars = variables(request)
        val names = request.info.names
        val packagePath = request.info.basePackage.replace('.', '/')
        val featureRoot = "${request.info.sourceSetRoot}/commonMain/kotlin/$packagePath/${names.camelCase}"
        val testRoot = "${request.info.sourceSetRoot}/commonTest/kotlin/$packagePath/${names.camelCase}"
        val moduleRoot = request.info.sourceSetRoot.removeSuffix("/src")
        val files = mutableListOf<PlannedFile>()

        fun add(relative: String, template: String) {
            files += PlannedFile("$featureRoot/$relative", renderer(template, vars))
        }

        if (request.options.screenUi) add("presentation/${names.pascalCase}Screen.kt", FeatureTemplates.screen)
        if (request.options.state) add("presentation/${names.pascalCase}State.kt", FeatureTemplates.state)
        if (request.options.actionEventIntent) add("presentation/${names.pascalCase}Action.kt", FeatureTemplates.action)
        if (request.options.effect) add("presentation/${names.pascalCase}Effect.kt", FeatureTemplates.effect)

        if (request.options.stateHolder) {
            val holder = when (request.architecture.architectureType) {
                ArchitectureType.MVVM, ArchitectureType.CLEAN_ARCHITECTURE, ArchitectureType.SIMPLE_FEATURE -> FeatureTemplates.mvvmViewModel
                ArchitectureType.MVI -> FeatureTemplates.mviStore
                ArchitectureType.SLACK_CIRCUIT -> FeatureTemplates.circuitPresenter
                ArchitectureType.DECOMPOSE -> FeatureTemplates.decomposeComponent
            }
            val suffix = when (request.architecture.architectureType) {
                ArchitectureType.MVI -> "Store"
                ArchitectureType.SLACK_CIRCUIT -> "Presenter"
                ArchitectureType.DECOMPOSE -> "Component"
                else -> "ViewModel"
            }
            add("presentation/${names.pascalCase}$suffix.kt", holder)
        }

        if (request.options.repository) add("domain/${names.pascalCase}Repository.kt", FeatureTemplates.repository)
        if (request.options.repositoryImplementation) add("data/Default${names.pascalCase}Repository.kt", FeatureTemplates.repositoryImpl)
        if (request.options.service) add("domain/${names.pascalCase}Service.kt", FeatureTemplates.service)
        if (request.options.serviceImplementation) add("data/Default${names.pascalCase}Service.kt", FeatureTemplates.serviceImpl)
        if (request.options.useCase) add("domain/Observe${names.pascalCase}UseCase.kt", FeatureTemplates.useCase)
        if (request.options.navigationRoute) add("navigation/${names.pascalCase}Route.kt", FeatureTemplates.route)
        if (request.options.navigationRoute) add("navigation/${names.pascalCase}NavigationGraph.kt", FeatureTemplates.navigationGraph)
        if (request.options.diModule) {
            val template = when (request.architecture.dependencyInjectionType) {
                DependencyInjectionType.KOIN -> FeatureTemplates.koinModule
                DependencyInjectionType.KOTLIN_INJECT,
                DependencyInjectionType.HILT_ANDROID_ONLY,
                DependencyInjectionType.MANUAL -> FeatureTemplates.manualDi
            }
            add("di/${names.pascalCase}Module.kt", template)
            val graphTemplate = when (request.architecture.dependencyInjectionType) {
                DependencyInjectionType.KOIN -> FeatureTemplates.koinGraph
                DependencyInjectionType.KOTLIN_INJECT,
                DependencyInjectionType.HILT_ANDROID_ONLY,
                DependencyInjectionType.MANUAL -> FeatureTemplates.manualGraph
            }
            add("di/${names.pascalCase}Graph.kt", graphTemplate)
        }
        if (request.options.fakeRepository) add("testing/Fake${names.pascalCase}Repository.kt", FeatureTemplates.fakeRepository)
        if (request.options.unitTests) {
            files += PlannedFile("$testRoot/presentation/${names.pascalCase}StateTest.kt", renderer(FeatureTemplates.test, vars))
        }
        if (request.options.readme) {
            files += PlannedFile(
                "$featureRoot/README.md",
                "# ${names.pascalCase}\n\n${request.info.description.ifBlank { "Generated ${request.architecture.architectureType.label} feature." }}\n"
            )
        }

        if (request.options.expectActualPlatformAbstraction) {
            files += PlannedFile("$featureRoot/platform/${names.pascalCase}PlatformContext.kt", renderer(FeatureTemplates.expectPlatform, vars))
            request.architecture.platforms.forEach { target ->
                val platformRoot = "${request.info.sourceSetRoot}/${target.sourceSetName}/kotlin/$packagePath/${names.camelCase}/platform"
                files += PlannedFile(
                    "$platformRoot/${names.pascalCase}PlatformContext.kt",
                    renderer(FeatureTemplates.actualPlatform, vars + ("platformName" to target.label))
                )
            }
        }

        files += gradleFile(moduleRoot, vars)

        return files
    }

    private fun gradleFile(moduleRoot: String, vars: Map<String, String>): PlannedFile {
        val ktsPath = Path.of(moduleRoot, "build.gradle.kts")
        val groovyPath = Path.of(moduleRoot, "build.gradle")
        val useKts = ktsPath.exists() || !groovyPath.exists()
        val path = if (useKts) ktsPath.toString() else groovyPath.toString()
        val exists = Path.of(path).exists()
        val template = when {
            useKts && exists -> FeatureTemplates.buildGradleKtsPatch
            useKts -> FeatureTemplates.buildGradleKts
            exists -> FeatureTemplates.buildGradleGroovyPatch
            else -> FeatureTemplates.buildGradleGroovy
        }
        return PlannedFile(
            path = path,
            content = renderer(template, vars),
            kind = if (exists) PlannedFileKind.MODIFY else PlannedFileKind.CREATE
        )
    }

    private fun variables(request: FeatureRequest): Map<String, String> {
        val names = request.info.names
        return mapOf(
            "FeatureNamePascal" to names.pascalCase,
            "featureNameCamel" to names.camelCase,
            "feature_name_snake" to names.snakeCase,
            "feature-name-kebab" to names.kebabCase,
            "packageName" to "${request.info.basePackage}.${names.camelCase}",
            "moduleName" to request.info.targetModule,
            "architectureType" to request.architecture.architectureType.label,
            "navigationType" to request.architecture.navigationType.label,
            "date" to LocalDate.now().toString(),
            "author" to System.getProperty("user.name", "")
        )
    }
}
