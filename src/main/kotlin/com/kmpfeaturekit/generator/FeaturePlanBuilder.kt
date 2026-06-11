package com.kmpfeaturekit.generator

import com.kmpfeaturekit.model.ArchitectureCompatibility
import com.kmpfeaturekit.model.ArchitectureType
import com.kmpfeaturekit.model.DependencyInjectionType
import com.kmpfeaturekit.model.FeatureRequest
import com.kmpfeaturekit.model.FeatureOptions
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
import com.kmpfeaturekit.utils.KotlinSourcePatcher
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText

class FeaturePlanBuilder(private val renderer: (String, Map<String, String>) -> String) {
    fun build(request: FeatureRequest): List<PlannedFile> {
        if (request.architecture.projectStyle == ProjectStyle.LAYERED_GLOBAL &&
            request.architecture.architectureType == ArchitectureType.MVVM
        ) {
            return buildLayeredGlobalMvvm(request)
        }

        val names = request.info.names
        val sourceSetRoot = Path.of(request.info.sourceSetRoot).normalize()
        val layout = FeatureLayout.from(request, sourceSetRoot)
        val moduleRoot = if (sourceSetRoot.fileName?.toString() == "src") {
            sourceSetRoot.parent?.toString() ?: sourceSetRoot.toString()
        } else {
            sourceSetRoot.toString()
        }
        val options = normalizeOptions(request.options, request.architecture.navigationType)
        val normalizedRequest = request.copy(options = options)
        val vars = variables(normalizedRequest, layout.packageName)
        val files = mutableListOf<PlannedFile>()

        fun add(layer: String, fileName: String, template: String) {
            files += PlannedFile(layout.commonPath(layer, fileName), renderer(template, vars))
        }

        if (options.screenUi) add("presentation", "${names.pascalCase}Screen.kt", FeatureTemplates.screen)
        val needsItemModel = options.state ||
            options.repository ||
            options.service ||
            options.fakeRepository
        if (needsItemModel) add("domain", "${names.pascalCase}Item.kt", FeatureTemplates.domainModel)
        if (options.state) add("presentation", "${names.pascalCase}State.kt", FeatureTemplates.state)
        if (options.actionEventIntent) add("presentation", "${names.pascalCase}Action.kt", FeatureTemplates.action)
        if (options.effect) add("presentation", "${names.pascalCase}Effect.kt", FeatureTemplates.effect)
        if (options.preview && options.screenUi && options.state && options.actionEventIntent) {
            add("presentation", "${names.pascalCase}Preview.kt", FeatureTemplates.preview)
        }

        if (options.stateHolder) {
            val holder = stateHolderPlan(normalizedRequest)
            add("presentation", "${names.pascalCase}${holder.suffix}.kt", holder.template)
        }

        if (options.repository) add("domain", "${names.pascalCase}Repository.kt", FeatureTemplates.repository)
        if (options.repositoryImplementation) add("data", "Default${names.pascalCase}Repository.kt", FeatureTemplates.repositoryImpl)
        if (options.service) add("domain", "${names.pascalCase}Service.kt", FeatureTemplates.service)
        if (options.serviceImplementation) add("data", "Default${names.pascalCase}Service.kt", FeatureTemplates.serviceImpl)
        if (options.useCase) add("domain", "Load${names.pascalCase}UseCase.kt", FeatureTemplates.useCase)
        val generateNavigationRoute = options.navigationRoute &&
            request.architecture.navigationType != NavigationType.NONE
        if (generateNavigationRoute) add("navigation", "${names.pascalCase}Route.kt", FeatureTemplates.route)
        if (generateNavigationRoute) add("navigation", "${names.pascalCase}NavigationGraph.kt", FeatureTemplates.navigationGraph)
        if (options.diModule) {
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

        if (options.autoRegisterDi) {
            files += diRegistrationFile(normalizedRequest, moduleRoot, layout)
        }
        if (options.autoRegisterNavigation && generateNavigationRoute && options.screenUi && options.state) {
            files += navigationRegistrationFile(normalizedRequest, moduleRoot, layout)
        }

        if (options.fakeRepository) {
            files += PlannedFile(layout.testPath("testing", "Fake${names.pascalCase}Repository.kt"), renderer(FeatureTemplates.fakeRepository, vars))
        }
        if (options.unitTests) {
            files += PlannedFile(layout.testPath("presentation", "${names.pascalCase}StateTest.kt"), renderer(FeatureTemplates.test, vars))
        }
        if (options.readme) {
            files += PlannedFile(
                layout.readmePath(),
                "# ${names.pascalCase}\n\n${request.info.description.ifBlank { "Generated ${request.architecture.architectureType.label} feature." }}\n"
            )
        }

        if (options.expectActualPlatformAbstraction) {
            files += PlannedFile(layout.commonPath("platform", "${names.pascalCase}PlatformContext.kt"), renderer(FeatureTemplates.expectPlatform, vars))
            request.architecture.platforms.forEach { target ->
                files += PlannedFile(
                    layout.platformPath(target, "platform", "${names.pascalCase}PlatformContext.kt"),
                    renderer(FeatureTemplates.actualPlatform, vars + ("platformName" to target.label))
                )
            }
        }

        files += gradleFiles(moduleRoot, normalizedRequest, vars)

        return files
    }

    private fun buildLayeredGlobalMvvm(request: FeatureRequest): List<PlannedFile> {
        val names = request.info.names
        val sourceSetRoot = Path.of(request.info.sourceSetRoot).normalize()
        val moduleRoot = if (sourceSetRoot.fileName?.toString() == "src") {
            sourceSetRoot.parent?.toString() ?: sourceSetRoot.toString()
        } else {
            sourceSetRoot.toString()
        }
        val options = normalizeOptions(
            request.options.copy(
                actionEventIntent = false,
                effect = false,
                navigationRoute = false,
                autoRegisterNavigation = false,
                diModule = false
            ),
            request.architecture.navigationType
        )
        val normalizedRequest = request.copy(options = options)
        val layout = LayeredGlobalLayout.from(request, sourceSetRoot)
        val vars = layeredVariables(request, layout)
        val files = mutableListOf<PlannedFile>()

        fun add(path: Path, template: String) {
            files += PlannedFile(path.toString(), renderer(template, vars))
        }

        if (options.repository || options.service || options.state) {
            add(layout.common("domain/model", "${names.pascalCase}Item.kt"), FeatureTemplates.layeredDomainModel)
        }
        if (options.repository) {
            add(layout.common("domain/repository", "${names.pascalCase}Repository.kt"), FeatureTemplates.layeredRepository)
        }
        if (options.useCase) {
            add(layout.common("domain/usecase", "Load${names.pascalCase}UseCase.kt"), FeatureTemplates.layeredUseCase)
        }
        if (options.service) {
            add(layout.common("data/remote", "${names.pascalCase}Service.kt"), FeatureTemplates.layeredService)
        }
        if (options.repositoryImplementation) {
            add(layout.common("data/repository", "${names.pascalCase}RepositoryImpl.kt"), FeatureTemplates.layeredRepositoryImpl)
        }
        if (options.state) {
            add(layout.common("presentation/${names.camelCase}", "${names.pascalCase}State.kt"), FeatureTemplates.layeredState)
        }
        if (options.stateHolder) {
            add(layout.common("presentation/${names.camelCase}", "${names.pascalCase}ViewModel.kt"), FeatureTemplates.layeredViewModel)
        }
        if (options.screenUi) {
            add(layout.common("ui", "${names.pascalCase}Screen.kt"), FeatureTemplates.layeredScreen)
        }
        if (options.preview && options.screenUi && options.state) {
            add(layout.common("ui", "${names.pascalCase}Preview.kt"), FeatureTemplates.layeredPreview)
        }
        if (options.fakeRepository) {
            add(layout.test("testing", "Fake${names.pascalCase}Repository.kt"), FeatureTemplates.layeredFakeRepository)
        }
        if (options.unitTests) {
            add(layout.test("presentation/${names.camelCase}", "${names.pascalCase}StateTest.kt"), FeatureTemplates.layeredStateTest)
        }
        if (options.readme) {
            files += PlannedFile(
                layout.common("docs/${names.camelCase}", "README.md").toString(),
                "# ${names.pascalCase}\n\n${request.info.description.ifBlank { "Generated layered MVVM feature." }}\n"
            )
        }

        if (options.autoRegisterDi) {
            files += layeredManualDiRegistrationFile(normalizedRequest, moduleRoot, layout)
        }
        files += gradleFiles(moduleRoot, normalizedRequest, vars)

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
            DependencyInjectionType.MANUAL -> return manualDiRegistrationFile(request, moduleRoot, layout)
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
        if (gradlePatch.content.isBlank()) return emptyList()
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

    private fun layeredVariables(request: FeatureRequest, layout: LayeredGlobalLayout): Map<String, String> {
        val names = request.info.names
        return mapOf(
            "FeatureNamePascal" to names.pascalCase,
            "featureNameCamel" to names.camelCase,
            "feature_name_snake" to names.snakeCase,
            "feature-name-kebab" to names.kebabCase,
            "packageName" to request.info.basePackage,
            "moduleName" to request.info.targetModule,
            "architectureType" to request.architecture.architectureType.label,
            "navigationType" to request.architecture.navigationType.label,
            "domainModelPackage" to layout.packageFor("domain/model"),
            "domainRepositoryPackage" to layout.packageFor("domain/repository"),
            "domainUseCasePackage" to layout.packageFor("domain/usecase"),
            "dataRemotePackage" to layout.packageFor("data/remote"),
            "dataRepositoryPackage" to layout.packageFor("data/repository"),
            "presentationPackage" to layout.packageFor("presentation/${names.camelCase}"),
            "uiPackage" to layout.packageFor("ui"),
            "testingPackage" to layout.testPackageFor("testing"),
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

    private fun normalizeOptions(options: FeatureOptions, navigationType: NavigationType): FeatureOptions {
        var normalized = options
        if (normalized.preview) {
            normalized = normalized.copy(screenUi = true, state = true, actionEventIntent = true)
        }
        if (normalized.screenUi) {
            normalized = normalized.copy(state = true, actionEventIntent = true)
        }
        if (normalized.stateHolder) {
            normalized = normalized.copy(state = true, actionEventIntent = true, useCase = true)
        }
        if (normalized.useCase) {
            normalized = normalized.copy(repository = true)
        }
        if (normalized.repositoryImplementation) {
            normalized = normalized.copy(repository = true, service = true)
        }
        if (normalized.serviceImplementation) {
            normalized = normalized.copy(service = true)
        }
        if (normalized.diModule) {
            normalized = normalized.copy(
                repository = true,
                repositoryImplementation = true,
                service = true,
                serviceImplementation = true,
                useCase = true
            )
        }
        if (normalized.fakeRepository) {
            normalized = normalized.copy(repository = true, state = true)
        }
        if (normalized.unitTests) {
            normalized = normalized.copy(state = true)
        }
        if (navigationType == NavigationType.NONE) {
            normalized = normalized.copy(navigationRoute = false, autoRegisterNavigation = false)
        }
        if (normalized.navigationRoute || normalized.autoRegisterNavigation) {
            normalized = normalized.copy(screenUi = true, state = true, actionEventIntent = true)
        }
        return normalized
    }

    private fun manualDiRegistrationFile(
        request: FeatureRequest,
        moduleRoot: String,
        layout: FeatureLayout
    ): PlannedFile {
        val names = request.info.names
        val plan = planManualAppGraphRegistration(
            moduleRoot = Path.of(moduleRoot),
            featureName = names.pascalCase,
            dependenciesImport = "${layout.packageName}.di.${names.pascalCase}Dependencies",
            graphImport = "${layout.packageName}.di.${names.pascalCase}Graph",
            viewModelImport = "${layout.packageName}.presentation.${names.pascalCase}ViewModel",
            includeViewModelFactory = request.options.stateHolder &&
                stateHolderType(request) == StateHolderType.ANDROIDX_VIEWMODEL
        )
        return plan?.let { (target, content) ->
            PlannedFile(
                path = target.toString(),
                content = content,
                kind = PlannedFileKind.MODIFY,
                replacesFile = true
            )
        } ?: PlannedFile(
            path = layout.commonPath("integration", "${names.pascalCase}ManualDiRegistration.todo.md"),
            content = """
                # ${names.pascalCase} manual DI registration

                Reason: No manual AppGraph object was safe to update.

                Wire `${names.pascalCase}Dependencies` into your manual composition root:

                ```kotlin
                private val ${names.camelCase}Dependencies by lazy { ${names.pascalCase}Graph.createDependencies() }
                ```
            """.trimIndent()
        )
    }

    private fun layeredManualDiRegistrationFile(
        request: FeatureRequest,
        moduleRoot: String,
        layout: LayeredGlobalLayout
    ): PlannedFile {
        val names = request.info.names
        val plan = planLayeredAppGraphRegistration(
            moduleRoot = Path.of(moduleRoot),
            featureName = names.pascalCase,
            serviceImport = "${layout.packageFor("data/remote")}.${names.pascalCase}Service",
            repositoryInterfaceImport = "${layout.packageFor("domain/repository")}.${names.pascalCase}Repository",
            repositoryImplImport = "${layout.packageFor("data/repository")}.${names.pascalCase}RepositoryImpl",
            useCaseImport = "${layout.packageFor("domain/usecase")}.Load${names.pascalCase}UseCase",
            viewModelImport = "${layout.packageFor("presentation/${names.camelCase}")}.${names.pascalCase}ViewModel",
            includeViewModelFactory = request.options.stateHolder
        )
        return plan?.let { (target, content) ->
            PlannedFile(
                path = target.toString(),
                content = content,
                kind = PlannedFileKind.MODIFY,
                replacesFile = true
            )
        } ?: PlannedFile(
            path = layout.common("integration", "${names.pascalCase}ManualDiRegistration.todo.md").toString(),
            content = """
                # ${names.pascalCase} manual DI registration

                Reason: No manual AppGraph object was safe to update.

                Wire `${names.pascalCase}Service`, `${names.pascalCase}RepositoryImpl`, `Load${names.pascalCase}UseCase`, and `${names.pascalCase}ViewModel` into your manual composition root.
            """.trimIndent()
        )
    }

    private fun planLayeredAppGraphRegistration(
        moduleRoot: Path,
        featureName: String,
        serviceImport: String,
        repositoryInterfaceImport: String,
        repositoryImplImport: String,
        useCaseImport: String,
        viewModelImport: String,
        includeViewModelFactory: Boolean
    ): Pair<Path, String>? {
        if (!moduleRoot.exists()) return null
        val candidates = kotlin.runCatching {
            Files.walk(moduleRoot).use { stream ->
                stream
                    .filter { it.isRegularFile() && it.fileName.toString().endsWith(".kt") }
                    .filter { path -> "object AppGraph" in path.readTextSafely() }
                    .toList()
            }
        }.getOrDefault(emptyList())

        candidates.forEach { candidate ->
            registerLayeredAppGraph(
                content = candidate.readTextSafely(),
                featureName = featureName,
                serviceImport = serviceImport,
                repositoryInterfaceImport = repositoryInterfaceImport,
                repositoryImplImport = repositoryImplImport,
                useCaseImport = useCaseImport,
                viewModelImport = viewModelImport,
                includeViewModelFactory = includeViewModelFactory
            )?.let { return candidate to it }
        }
        return null
    }

    fun registerLayeredAppGraph(
        content: String,
        featureName: String,
        serviceImport: String,
        repositoryInterfaceImport: String,
        repositoryImplImport: String,
        useCaseImport: String,
        viewModelImport: String,
        includeViewModelFactory: Boolean
    ): String? {
        val camel = featureName.replaceFirstChar { it.lowercaseChar() }
        if ("${camel}Repository" in content || "${camel}ViewModel" in content) return null
        val objectMatch = Regex("""object\s+AppGraph\s*\{""").find(content) ?: return null
        val openBrace = objectMatch.range.last
        val closeBrace = findMatchingBrace(content, openBrace) ?: return null
        val lineStart = content.lastIndexOf('\n', closeBrace).let { if (it < 0) 0 else it + 1 }
        val indent = content.substring(lineStart, closeBrace).takeWhile { it.isWhitespace() } + "    "
        val member = buildString {
            appendLine()
            appendLine("${indent}private val ${camel}Repository: ${featureName}Repository by lazy { ${featureName}RepositoryImpl(${featureName}Service()) }")
            appendLine("${indent}private val load$featureName by lazy { Load${featureName}UseCase(${camel}Repository) }")
            if (includeViewModelFactory) {
                appendLine()
                appendLine("${indent}fun ${camel}ViewModel(): ${featureName}ViewModel = ${featureName}ViewModel(load$featureName)")
            }
        }.trimEnd()
        val updated = content.replaceRange(closeBrace, closeBrace, member)
        return listOf(serviceImport, repositoryInterfaceImport, repositoryImplImport, useCaseImport)
            .let { imports -> if (includeViewModelFactory) imports + viewModelImport else imports }
            .fold(updated) { text, importFqName -> KotlinSourcePatcher.addImport(text, importFqName) }
    }

    private fun planManualAppGraphRegistration(
        moduleRoot: Path,
        featureName: String,
        dependenciesImport: String,
        graphImport: String,
        viewModelImport: String,
        includeViewModelFactory: Boolean
    ): Pair<Path, String>? {
        if (!moduleRoot.exists()) return null
        val candidates = kotlin.runCatching {
            Files.walk(moduleRoot).use { stream ->
                stream
                    .filter { it.isRegularFile() && it.fileName.toString().endsWith(".kt") }
                    .filter { path ->
                        val text = path.readTextSafely()
                        "object AppGraph" in text || "object ${moduleRoot.fileName}Graph" in text
                    }
                    .toList()
            }
        }.getOrDefault(emptyList())

        candidates.forEach { candidate ->
            registerManualAppGraph(
                content = candidate.readTextSafely(),
                featureName = featureName,
                dependenciesImport = dependenciesImport,
                graphImport = graphImport,
                viewModelImport = viewModelImport,
                includeViewModelFactory = includeViewModelFactory
            )?.let { return candidate to it }
        }
        return null
    }

    fun registerManualAppGraph(
        content: String,
        featureName: String,
        dependenciesImport: String,
        graphImport: String,
        viewModelImport: String,
        includeViewModelFactory: Boolean
    ): String? {
        val camel = featureName.replaceFirstChar { it.lowercaseChar() }
        if ("${camel}Dependencies" in content) return null
        val objectMatch = Regex("""object\s+\w*Graph\s*\{""").find(content) ?: return null
        val openBrace = objectMatch.range.last
        val closeBrace = findMatchingBrace(content, openBrace) ?: return null
        val lineStart = content.lastIndexOf('\n', closeBrace).let { if (it < 0) 0 else it + 1 }
        val indent = content.substring(lineStart, closeBrace).takeWhile { it.isWhitespace() } + "    "
        val member = buildString {
            appendLine()
            appendLine("${indent}private val ${camel}Dependencies: ${featureName}Dependencies by lazy { ${featureName}Graph.createDependencies() }")
            if (includeViewModelFactory) {
                appendLine()
                appendLine("${indent}fun ${camel}ViewModel(): ${featureName}ViewModel = ${featureName}ViewModel(${camel}Dependencies.load$featureName)")
            }
        }.trimEnd()
        val updated = content.replaceRange(closeBrace, closeBrace, member)
        return listOf(graphImport, dependenciesImport)
            .let { imports -> if (includeViewModelFactory) imports + viewModelImport else imports }
            .fold(updated) { text, importFqName -> KotlinSourcePatcher.addImport(text, importFqName) }
    }

    private fun findMatchingBrace(content: String, openBrace: Int): Int? {
        var depth = 0
        for (index in openBrace until content.length) {
            when (content[index]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return index
                }
            }
        }
        return null
    }

    private fun Path.readTextSafely(): String =
        runCatching { takeIf { Files.size(it) < 200_000 }?.readText() }.getOrNull().orEmpty()

    private data class StateHolderPlan(val suffix: String, val template: String)

    private data class LayeredGlobalLayout(
        val sourceSetRoot: Path,
        val commonRoot: Path,
        val testRoot: Path,
        val basePackage: String
    ) {
        fun common(layer: String, fileName: String): Path =
            commonRoot.resolve(layer).resolve(fileName)

        fun test(layer: String, fileName: String): Path =
            testRoot.resolve(layer).resolve(fileName)

        fun packageFor(layer: String): String =
            "$basePackage.${layer.replace('/', '.')}"

        fun testPackageFor(layer: String): String =
            "$basePackage.$layer"

        companion object {
            fun from(request: FeatureRequest, sourceSetRoot: Path): LayeredGlobalLayout {
                val packagePath = request.info.basePackage.replace('.', '/')
                return LayeredGlobalLayout(
                    sourceSetRoot = sourceSetRoot,
                    commonRoot = sourceSetRoot.resolve("commonMain/kotlin/$packagePath"),
                    testRoot = sourceSetRoot.resolve("commonTest/kotlin/$packagePath"),
                    basePackage = request.info.basePackage
                )
            }
        }
    }

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
                ProjectStyle.LAYER_BASED,
                ProjectStyle.LAYERED_GLOBAL -> commonRoot.resolve("docs/$featureNameCamel/README.md").toString()
            }

        fun relativePath(layer: String, fileName: String): Path =
            when (style) {
                ProjectStyle.FEATURE_BASED,
                ProjectStyle.HYBRID -> Path.of(layer, fileName)
                ProjectStyle.LAYER_BASED,
                ProjectStyle.LAYERED_GLOBAL -> Path.of(layer, featureNameCamel, fileName)
            }

        companion object {
            fun from(request: FeatureRequest, sourceSetRoot: Path): FeatureLayout {
                val names = request.info.names
                val packageName = when (request.architecture.projectStyle) {
                    ProjectStyle.FEATURE_BASED -> "${request.info.basePackage}.${names.camelCase}"
                    ProjectStyle.LAYER_BASED -> request.info.basePackage
                    ProjectStyle.LAYERED_GLOBAL -> request.info.basePackage
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
