package com.kmpfeaturekit.generator

import com.kmpfeaturekit.model.ArchitectureCompatibility
import com.kmpfeaturekit.model.DependencyInjectionType
import com.kmpfeaturekit.model.FeatureRequest
import com.kmpfeaturekit.model.PlatformTarget
import com.kmpfeaturekit.model.PlannedFile
import com.kmpfeaturekit.model.PlannedFileKind
import com.kmpfeaturekit.model.ProjectStyle
import com.kmpfeaturekit.model.StateHolderType
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
        val names = request.info.names
        val sourceSetRoot = Path.of(request.info.sourceSetRoot).normalize()
        val layout = FeatureLayout.from(request, sourceSetRoot)
        val moduleRoot = if (sourceSetRoot.fileName?.toString() == "src") {
            sourceSetRoot.parent?.toString() ?: sourceSetRoot.toString()
        } else {
            sourceSetRoot.toString()
        }
        val vars = variables(request, layout.packageName)
        val files = mutableListOf<PlannedFile>()

        fun add(layer: String, fileName: String, template: String) {
            files += PlannedFile(layout.commonPath(layer, fileName), renderer(template, vars))
        }

        if (request.options.screenUi) add("presentation", "${names.pascalCase}Screen.kt", FeatureTemplates.screen)
        if (request.options.state) add("presentation", "${names.pascalCase}State.kt", FeatureTemplates.state)
        if (request.options.actionEventIntent) add("presentation", "${names.pascalCase}Action.kt", FeatureTemplates.action)
        if (request.options.effect) add("presentation", "${names.pascalCase}Effect.kt", FeatureTemplates.effect)
        if (request.options.preview && request.options.screenUi && request.options.state && request.options.actionEventIntent) {
            add("presentation", "${names.pascalCase}Preview.kt", FeatureTemplates.preview)
        }

        if (request.options.stateHolder) {
            val holder = stateHolderPlan(request)
            add("presentation", "${names.pascalCase}${holder.suffix}.kt", holder.template)
        }

        if (request.options.repository) add("domain", "${names.pascalCase}Repository.kt", FeatureTemplates.repository)
        if (request.options.repositoryImplementation) add("data", "Default${names.pascalCase}Repository.kt", FeatureTemplates.repositoryImpl)
        if (request.options.service) add("domain", "${names.pascalCase}Service.kt", FeatureTemplates.service)
        if (request.options.serviceImplementation) add("data", "Default${names.pascalCase}Service.kt", FeatureTemplates.serviceImpl)
        if (request.options.useCase) add("domain", "Observe${names.pascalCase}UseCase.kt", FeatureTemplates.useCase)
        if (request.options.navigationRoute) add("navigation", "${names.pascalCase}Route.kt", FeatureTemplates.route)
        if (request.options.navigationRoute) add("navigation", "${names.pascalCase}NavigationGraph.kt", FeatureTemplates.navigationGraph)
        if (request.options.diModule) {
            val template = when (request.architecture.dependencyInjectionType) {
                DependencyInjectionType.KOIN -> FeatureTemplates.koinModule
                DependencyInjectionType.KOTLIN_INJECT -> FeatureTemplates.kotlinInjectDi
                DependencyInjectionType.HILT_ANDROID_ONLY -> FeatureTemplates.hiltModule
                DependencyInjectionType.MANUAL -> FeatureTemplates.manualDi
            }
            if (request.architecture.dependencyInjectionType == DependencyInjectionType.HILT_ANDROID_ONLY) {
                files += PlannedFile(layout.androidPath("di", "${names.pascalCase}Module.kt"), renderer(template, vars))
            } else {
                add("di", "${names.pascalCase}Module.kt", template)
            }
            val graphTemplate = when (request.architecture.dependencyInjectionType) {
                DependencyInjectionType.KOIN -> FeatureTemplates.koinGraph
                DependencyInjectionType.HILT_ANDROID_ONLY,
                DependencyInjectionType.KOTLIN_INJECT,
                DependencyInjectionType.MANUAL -> FeatureTemplates.manualGraph
            }
            if (request.architecture.dependencyInjectionType != DependencyInjectionType.HILT_ANDROID_ONLY) {
                add("di", "${names.pascalCase}Graph.kt", graphTemplate)
            }
        }

        if (request.options.autoRegisterDi) {
            files += diRegistrationFile(request, moduleRoot, layout)
        }
        if (request.options.autoRegisterNavigation) {
            files += navigationRegistrationFile(request, moduleRoot, layout)
        }

        if (request.options.fakeRepository) add("testing", "Fake${names.pascalCase}Repository.kt", FeatureTemplates.fakeRepository)
        if (request.options.unitTests) {
            files += PlannedFile(layout.testPath("presentation", "${names.pascalCase}StateTest.kt"), renderer(FeatureTemplates.test, vars))
        }
        if (request.options.readme) {
            files += PlannedFile(
                layout.readmePath(),
                "# ${names.pascalCase}\n\n${request.info.description.ifBlank { "Generated ${request.architecture.architectureType.label} feature." }}\n"
            )
        }

        if (request.options.expectActualPlatformAbstraction) {
            files += PlannedFile(layout.commonPath("platform", "${names.pascalCase}PlatformContext.kt"), renderer(FeatureTemplates.expectPlatform, vars))
            request.architecture.platforms.forEach { target ->
                files += PlannedFile(
                    layout.platformPath(target, "platform", "${names.pascalCase}PlatformContext.kt"),
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
        layout: FeatureLayout
    ): PlannedFile {
        val names = request.info.names
        val plan = when (request.architecture.dependencyInjectionType) {
            DependencyInjectionType.KOIN -> KoinRegistrationPlanner.plan(
                moduleRoot = Path.of(moduleRoot),
                featureModuleName = "${names.camelCase}Module",
                featureModuleImport = "${layout.packageName}.di.${names.camelCase}Module"
            )
            DependencyInjectionType.KOTLIN_INJECT -> KotlinInjectRegistrationPlanner.plan(
                moduleRoot = Path.of(moduleRoot),
                moduleTypeName = "${names.pascalCase}InjectModule",
                moduleImport = "${layout.packageName}.di.${names.pascalCase}InjectModule"
            )
            DependencyInjectionType.HILT_ANDROID_ONLY -> HiltRegistrationPlanner.plan(
                moduleRoot = Path.of(moduleRoot),
                moduleName = "${names.pascalCase}Module",
                moduleImport = "${layout.packageName}.di.${names.pascalCase}Module"
            )
            DependencyInjectionType.MANUAL -> return PlannedFile(
                path = layout.commonPath("integration", "${names.pascalCase}ManualDiRegistration.todo.md"),
                content = """
                    # ${names.pascalCase} manual DI registration

                    Reason: Manual DI cannot be updated safely by the plugin.

                    Wire `${names.pascalCase}Dependencies` into your manual composition root:

                    ```kotlin
                    ${names.pascalCase}Graph.createDependencies()
                    ```
                """.trimIndent()
            )
        }
        val fallbackRoot = if (request.architecture.dependencyInjectionType == DependencyInjectionType.HILT_ANDROID_ONLY) {
            layout.androidRoot
        } else {
            layout.commonRoot
        }
        return plan.replacementContent?.let { replacement ->
            PlannedFile(
                path = requireNotNull(plan.targetFile),
                content = replacement,
                kind = PlannedFileKind.MODIFY,
                replacesFile = true
            )
        } ?: PlannedFile(
            path = fallbackRoot.resolve(layout.relativePath("integration", "${names.pascalCase}${request.architecture.dependencyInjectionType.name.toPascalToken()}Registration.todo.md")).toString(),
            content = plan.diffPreview,
            kind = PlannedFileKind.CREATE
        )
    }

    private fun String.toPascalToken(): String =
        lowercase().split('_').joinToString("") { it.replaceFirstChar(Char::uppercase) }

    private fun navigationRegistrationFile(request: FeatureRequest, moduleRoot: String, layout: FeatureLayout): PlannedFile {
        val names = request.info.names
        val plan = NavigationRegistrationPlanner.plan(
            moduleRoot = Path.of(moduleRoot),
            routeName = names.pascalCase,
            navigationType = request.architecture.navigationType,
            featurePackageName = layout.packageName
        )
        return plan.replacementContent?.let { replacement ->
            PlannedFile(
                path = requireNotNull(plan.targetFile),
                content = replacement,
                kind = PlannedFileKind.MODIFY,
                replacesFile = true
            )
        } ?: PlannedFile(
            path = layout.commonPath("integration", "${names.pascalCase}NavigationRegistration.todo.md"),
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

    private fun variables(request: FeatureRequest, packageName: String): Map<String, String> {
        val names = request.info.names
        return mapOf(
            "FeatureNamePascal" to names.pascalCase,
            "featureNameCamel" to names.camelCase,
            "feature_name_snake" to names.snakeCase,
            "feature-name-kebab" to names.kebabCase,
            "packageName" to packageName,
            "moduleName" to request.info.targetModule,
            "architectureType" to request.architecture.architectureType.label,
            "navigationType" to request.architecture.navigationType.label,
            "stateHolderImport" to stateHolderImport(request, packageName),
            "stateHolderKoinRegistration" to stateHolderKoinRegistration(request),
            "date" to LocalDate.now().toString(),
            "author" to System.getProperty("user.name", "")
        )
    }

    private fun stateHolderImport(request: FeatureRequest, packageName: String): String {
        if (!request.options.stateHolder) return ""
        val names = request.info.names
        val base = "$packageName.presentation"
        return when (stateHolderType(request)) {
            StateHolderType.ANDROIDX_VIEWMODEL -> "import $base.${names.pascalCase}ViewModel"
            StateHolderType.CIRCUIT_PRESENTER -> "import $base.${names.pascalCase}Presenter"
            StateHolderType.DECOMPOSE_COMPONENT -> ""
            StateHolderType.PLAIN_STATE_HOLDER -> "import $base.${names.pascalCase}StateHolder"
        }
    }

    private fun stateHolderKoinRegistration(request: FeatureRequest): String {
        if (!request.options.stateHolder) return ""
        val names = request.info.names
        return when (stateHolderType(request)) {
            StateHolderType.ANDROIDX_VIEWMODEL -> "factory { ${names.pascalCase}ViewModel(get()) }"
            StateHolderType.CIRCUIT_PRESENTER -> "factory { ${names.pascalCase}Presenter(get()) }"
            StateHolderType.DECOMPOSE_COMPONENT -> ""
            StateHolderType.PLAIN_STATE_HOLDER -> "factory { ${names.pascalCase}StateHolder(get()) }"
        }
    }

    private fun stateHolderPlan(request: FeatureRequest): StateHolderPlan =
        when (stateHolderType(request)) {
            StateHolderType.ANDROIDX_VIEWMODEL -> StateHolderPlan("ViewModel", FeatureTemplates.mvvmViewModel)
            StateHolderType.CIRCUIT_PRESENTER -> StateHolderPlan("Presenter", FeatureTemplates.circuitPresenter)
            StateHolderType.DECOMPOSE_COMPONENT -> StateHolderPlan("Component", FeatureTemplates.decomposeComponent)
            StateHolderType.PLAIN_STATE_HOLDER -> StateHolderPlan("StateHolder", FeatureTemplates.plainStateHolder)
        }

    private fun stateHolderType(request: FeatureRequest): StateHolderType =
        ArchitectureCompatibility.coerceStateHolder(
            request.architecture.architectureType,
            request.architecture.stateHolderType
        )

    private data class StateHolderPlan(val suffix: String, val template: String)

    private data class FeatureLayout(
        val style: ProjectStyle,
        val featureNameCamel: String,
        val sourceSetRoot: Path,
        val commonRoot: Path,
        val androidRoot: Path,
        val testRoot: Path,
        val packageName: String
    ) {
        fun commonPath(layer: String, fileName: String): String =
            commonRoot.resolve(relativePath(layer, fileName)).toString()

        fun androidPath(layer: String, fileName: String): String =
            androidRoot.resolve(relativePath(layer, fileName)).toString()

        fun testPath(layer: String, fileName: String): String =
            testRoot.resolve(relativePath(layer, fileName)).toString()

        fun platformPath(target: PlatformTarget, layer: String, fileName: String): String =
            sourceSetRoot
                .resolve("${target.sourceSetName}/kotlin")
                .resolve(packageName.replace('.', '/'))
                .resolve(relativePath(layer, fileName))
                .toString()

        fun readmePath(): String =
            when (style) {
                ProjectStyle.FEATURE_BASED,
                ProjectStyle.HYBRID -> commonRoot.resolve("README.md").toString()
                ProjectStyle.LAYER_BASED -> commonRoot.resolve("docs/$featureNameCamel/README.md").toString()
            }

        fun relativePath(layer: String, fileName: String): Path =
            when (style) {
                ProjectStyle.FEATURE_BASED,
                ProjectStyle.HYBRID -> Path.of(layer, fileName)
                ProjectStyle.LAYER_BASED -> Path.of(layer, featureNameCamel, fileName)
            }

        companion object {
            fun from(request: FeatureRequest, sourceSetRoot: Path): FeatureLayout {
                val names = request.info.names
                val packageName = when (request.architecture.projectStyle) {
                    ProjectStyle.FEATURE_BASED -> "${request.info.basePackage}.${names.camelCase}"
                    ProjectStyle.LAYER_BASED -> request.info.basePackage
                    ProjectStyle.HYBRID -> "${request.info.basePackage}.features.${names.camelCase}"
                }
                val packagePath = packageName.replace('.', '/')
                return FeatureLayout(
                    style = request.architecture.projectStyle,
                    featureNameCamel = names.camelCase,
                    sourceSetRoot = sourceSetRoot,
                    commonRoot = sourceSetRoot.resolve("commonMain/kotlin/$packagePath"),
                    androidRoot = sourceSetRoot.resolve("androidMain/kotlin/$packagePath"),
                    testRoot = sourceSetRoot.resolve("commonTest/kotlin/$packagePath"),
                    packageName = packageName
                )
            }
        }
    }
}
