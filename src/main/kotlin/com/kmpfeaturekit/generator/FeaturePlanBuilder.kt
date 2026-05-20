package com.kmpfeaturekit.generator

import com.kmpfeaturekit.model.ArchitectureType
import com.kmpfeaturekit.model.DependencyInjectionType
import com.kmpfeaturekit.model.FeatureRequest
import com.kmpfeaturekit.model.PlatformTarget
import com.kmpfeaturekit.model.PlannedFile
import com.kmpfeaturekit.model.PlannedFileKind
import com.kmpfeaturekit.di.HiltRegistrationPlanner
import com.kmpfeaturekit.di.KotlinInjectRegistrationPlanner
import com.kmpfeaturekit.di.KoinRegistrationPlanner
import com.kmpfeaturekit.model.NavigationType
import com.kmpfeaturekit.navigation.NavigationRegistrationPlanner
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
        val androidFeatureRoot = "${request.info.sourceSetRoot}/androidMain/kotlin/$packagePath/${names.camelCase}"
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
                DependencyInjectionType.KOTLIN_INJECT -> FeatureTemplates.kotlinInjectDi
                DependencyInjectionType.HILT_ANDROID_ONLY -> FeatureTemplates.hiltModule
                DependencyInjectionType.MANUAL -> FeatureTemplates.manualDi
            }
            if (request.architecture.dependencyInjectionType == DependencyInjectionType.HILT_ANDROID_ONLY) {
                files += PlannedFile("$androidFeatureRoot/di/${names.pascalCase}Module.kt", renderer(template, vars))
            } else {
                add("di/${names.pascalCase}Module.kt", template)
            }
            val graphTemplate = when (request.architecture.dependencyInjectionType) {
                DependencyInjectionType.KOIN -> FeatureTemplates.koinGraph
                DependencyInjectionType.HILT_ANDROID_ONLY,
                DependencyInjectionType.KOTLIN_INJECT,
                DependencyInjectionType.MANUAL -> FeatureTemplates.manualGraph
            }
            if (request.architecture.dependencyInjectionType != DependencyInjectionType.HILT_ANDROID_ONLY) {
                add("di/${names.pascalCase}Graph.kt", graphTemplate)
            }
        }

        if (request.options.autoRegisterDi) {
            files += diRegistrationFile(request, moduleRoot, featureRoot, androidFeatureRoot)
        }
        if (request.options.autoRegisterNavigation) {
            files += navigationRegistrationFile(request, moduleRoot, featureRoot)
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

        files += gradleFiles(moduleRoot, request, vars)

        return files
    }

    private fun diRegistrationFile(
        request: FeatureRequest,
        moduleRoot: String,
        featureRoot: String,
        androidFeatureRoot: String
    ): PlannedFile {
        val names = request.info.names
        val plan = when (request.architecture.dependencyInjectionType) {
            DependencyInjectionType.KOIN -> KoinRegistrationPlanner.plan(
                moduleRoot = Path.of(moduleRoot),
                featureModuleName = "${names.camelCase}Module",
                featureModuleImport = "${request.info.basePackage}.${names.camelCase}.di.${names.camelCase}Module"
            )
            DependencyInjectionType.KOTLIN_INJECT -> KotlinInjectRegistrationPlanner.plan(
                moduleRoot = Path.of(moduleRoot),
                moduleTypeName = "${names.pascalCase}InjectModule",
                moduleImport = "${request.info.basePackage}.${names.camelCase}.di.${names.pascalCase}InjectModule"
            )
            DependencyInjectionType.HILT_ANDROID_ONLY -> HiltRegistrationPlanner.plan(
                moduleRoot = Path.of(moduleRoot),
                moduleName = "${names.pascalCase}Module",
                moduleImport = "${request.info.basePackage}.${names.camelCase}.di.${names.pascalCase}Module"
            )
            DependencyInjectionType.MANUAL -> return PlannedFile(
                path = "$featureRoot/integration/${names.pascalCase}ManualDiRegistration.todo.kt",
                content = """
                    // TODO Wire ${names.pascalCase}Dependencies into your manual composition root.
                    ${names.pascalCase}Graph.createDependencies()
                """.trimIndent()
            )
        }
        val fallbackRoot = if (request.architecture.dependencyInjectionType == DependencyInjectionType.HILT_ANDROID_ONLY) {
            androidFeatureRoot
        } else {
            featureRoot
        }
        return plan.replacementContent?.let { replacement ->
            PlannedFile(
                path = requireNotNull(plan.targetFile),
                content = replacement,
                kind = PlannedFileKind.MODIFY,
                replacesFile = true
            )
        } ?: PlannedFile(
            path = "$fallbackRoot/integration/${names.pascalCase}${request.architecture.dependencyInjectionType.name.toPascalToken()}Registration.todo.kt",
            content = plan.diffPreview,
            kind = PlannedFileKind.CREATE
        )
    }

    private fun String.toPascalToken(): String =
        lowercase().split('_').joinToString("") { it.replaceFirstChar(Char::uppercase) }

    private fun navigationRegistrationFile(request: FeatureRequest, moduleRoot: String, featureRoot: String): PlannedFile {
        val names = request.info.names
        val plan = NavigationRegistrationPlanner.plan(
            moduleRoot = Path.of(moduleRoot),
            routeName = names.pascalCase,
            navigationType = request.architecture.navigationType,
            featurePackageName = "${request.info.basePackage}.${names.camelCase}"
        )
        return plan.replacementContent?.let { replacement ->
            PlannedFile(
                path = requireNotNull(plan.targetFile),
                content = replacement,
                kind = PlannedFileKind.MODIFY,
                replacesFile = true
            )
        } ?: PlannedFile(
            path = "$featureRoot/integration/${names.pascalCase}NavigationRegistration.todo.kt",
            content = plan.diffPreview,
            kind = PlannedFileKind.CREATE
        )
    }

    private fun gradleFiles(moduleRoot: String, request: FeatureRequest, vars: Map<String, String>): List<PlannedFile> {
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
        val gradlePatch = if (exists) {
            GradleBuildPatchPlanner.plan(Path.of(path), request, renderer(template, vars))
        } else {
            GradleBuildPatch(renderer(template, vars), replacesFile = false, warnings = emptyList())
        }
        val files = mutableListOf(
            PlannedFile(
                path = path,
                content = gradlePatch.content,
                kind = if (exists) PlannedFileKind.MODIFY else PlannedFileKind.CREATE,
                replacesFile = gradlePatch.replacesFile
            )
        )
        gradlePatch.catalogPatch?.let { catalog ->
            files += PlannedFile(
                path = catalog.path,
                content = catalog.content,
                kind = PlannedFileKind.MODIFY,
                replacesFile = catalog.replacesFile
            )
        }
        return files
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
            "stateHolderImport" to stateHolderImport(request),
            "stateHolderKoinRegistration" to stateHolderKoinRegistration(request),
            "date" to LocalDate.now().toString(),
            "author" to System.getProperty("user.name", "")
        )
    }

    private fun stateHolderImport(request: FeatureRequest): String {
        val names = request.info.names
        val base = "${request.info.basePackage}.${names.camelCase}.presentation"
        return when (request.architecture.architectureType) {
            ArchitectureType.MVVM,
            ArchitectureType.CLEAN_ARCHITECTURE,
            ArchitectureType.SIMPLE_FEATURE -> "import $base.${names.pascalCase}ViewModel"
            ArchitectureType.MVI -> "import $base.${names.pascalCase}Store"
            ArchitectureType.SLACK_CIRCUIT -> "import $base.${names.pascalCase}Presenter"
            ArchitectureType.DECOMPOSE -> ""
        }
    }

    private fun stateHolderKoinRegistration(request: FeatureRequest): String {
        val names = request.info.names
        return when (request.architecture.architectureType) {
            ArchitectureType.MVVM,
            ArchitectureType.CLEAN_ARCHITECTURE,
            ArchitectureType.SIMPLE_FEATURE -> "factory { ${names.pascalCase}ViewModel(get()) }"
            ArchitectureType.MVI -> "factory { ${names.pascalCase}Store(get(), get()) }"
            ArchitectureType.SLACK_CIRCUIT -> "factory { ${names.pascalCase}Presenter(get()) }"
            ArchitectureType.DECOMPOSE -> ""
        }
    }
}
