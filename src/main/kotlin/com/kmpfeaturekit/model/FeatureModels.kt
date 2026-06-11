package com.kmpfeaturekit.model

import com.kmpfeaturekit.utils.NameVariants

enum class ArchitectureType(val label: String) {
    MVVM("MVVM"),
    MVI("MVI"),
    SLACK_CIRCUIT("Slack Circuit"),
    DECOMPOSE("Decompose"),
    SIMPLE_FEATURE("Simple Feature"),
    CLEAN_ARCHITECTURE("Clean Architecture");

    override fun toString(): String = label
}

enum class StateHolderType(val label: String) {
    ANDROIDX_VIEWMODEL("AndroidX ViewModel"),
    CIRCUIT_PRESENTER("Circuit Presenter"),
    DECOMPOSE_COMPONENT("Decompose Component"),
    PLAIN_STATE_HOLDER("Plain State Holder");

    override fun toString(): String = label
}

enum class NavigationType(val label: String) {
    NONE("Custom / none"),
    NAVIGATION_COMPOSE("Navigation Compose"),
    VOYAGER("Voyager"),
    CIRCUIT_NAVIGATION("Circuit Navigation"),
    DECOMPOSE_NAVIGATION("Decompose Navigation"),
    APPYX("Appyx");

    override fun toString(): String = label
}

enum class DependencyInjectionType(val label: String) {
    KOIN("Koin"),
    KOTLIN_INJECT("Kotlin Inject"),
    HILT_ANDROID_ONLY("Hilt Android only"),
    MANUAL("Manual DI");

    override fun toString(): String = label
}

enum class NetworkingType(val label: String) {
    KTOR("Ktor"),
    APOLLO("Apollo GraphQL"),
    RETROFIT_ANDROID_ONLY("Retrofit Android only"),
    NONE("None");

    override fun toString(): String = label
}

enum class PersistenceType(val label: String) {
    SQLDELIGHT("SQLDelight"),
    ROOM_ANDROID_ONLY("Room Android only"),
    DATASTORE("DataStore"),
    NONE("None");

    override fun toString(): String = label
}

enum class PlatformTarget(val sourceSetName: String, val label: String) {
    ANDROID("androidMain", "Android"),
    IOS("iosMain", "iOS");

    override fun toString(): String = label
}

enum class ProjectStyle(val label: String) {
    FEATURE_BASED("Feature-based"),
    LAYER_BASED("Layer-based"),
    LAYERED_GLOBAL("Layered global"),
    HYBRID("Hybrid");

    override fun toString(): String = label
}

data class FeatureInfo(
    val featureName: String,
    val basePackage: String,
    val targetModule: String,
    val sourceSetRoot: String,
    val description: String = ""
) {
    val names: NameVariants = NameVariants.from(featureName)
}

data class ArchitectureSelection(
    val architectureType: ArchitectureType = ArchitectureType.MVVM,
    val stateHolderType: StateHolderType = StateHolderType.ANDROIDX_VIEWMODEL,
    val navigationType: NavigationType = NavigationType.NAVIGATION_COMPOSE,
    val dependencyInjectionType: DependencyInjectionType = DependencyInjectionType.KOIN,
    val networkingType: NetworkingType = NetworkingType.NONE,
    val persistenceType: PersistenceType = PersistenceType.NONE,
    val platforms: Set<PlatformTarget> = setOf(PlatformTarget.ANDROID, PlatformTarget.IOS),
    val projectStyle: ProjectStyle = ProjectStyle.FEATURE_BASED
)

data class FeatureOptions(
    val screenUi: Boolean = true,
    val stateHolder: Boolean = true,
    val state: Boolean = true,
    val actionEventIntent: Boolean = true,
    val effect: Boolean = true,
    val repository: Boolean = true,
    val repositoryImplementation: Boolean = true,
    val service: Boolean = true,
    val serviceImplementation: Boolean = true,
    val useCase: Boolean = true,
    val navigationRoute: Boolean = true,
    val diModule: Boolean = true,
    val preview: Boolean = true,
    val readme: Boolean = false,
    val fakeRepository: Boolean = true,
    val unitTests: Boolean = true,
    val expectActualPlatformAbstraction: Boolean = false,
    val autoRegisterNavigation: Boolean = true,
    val autoRegisterDi: Boolean = true
)

data class FeatureRequest(
    val info: FeatureInfo,
    val architecture: ArchitectureSelection = ArchitectureSelection(),
    val options: FeatureOptions = FeatureOptions()
)

object ArchitectureCompatibility {
    fun stateHoldersFor(architectureType: ArchitectureType): List<StateHolderType> =
        when (architectureType) {
            ArchitectureType.MVVM -> listOf(StateHolderType.ANDROIDX_VIEWMODEL, StateHolderType.PLAIN_STATE_HOLDER)
            ArchitectureType.MVI -> listOf(StateHolderType.ANDROIDX_VIEWMODEL, StateHolderType.PLAIN_STATE_HOLDER)
            ArchitectureType.SLACK_CIRCUIT -> listOf(StateHolderType.CIRCUIT_PRESENTER)
            ArchitectureType.DECOMPOSE -> listOf(StateHolderType.DECOMPOSE_COMPONENT)
            ArchitectureType.SIMPLE_FEATURE -> listOf(StateHolderType.PLAIN_STATE_HOLDER, StateHolderType.ANDROIDX_VIEWMODEL)
            ArchitectureType.CLEAN_ARCHITECTURE -> listOf(StateHolderType.ANDROIDX_VIEWMODEL, StateHolderType.PLAIN_STATE_HOLDER)
        }

    fun navigationFor(architectureType: ArchitectureType): List<NavigationType> =
        when (architectureType) {
            ArchitectureType.MVVM -> listOf(NavigationType.NONE, NavigationType.NAVIGATION_COMPOSE, NavigationType.VOYAGER)
            ArchitectureType.MVI -> listOf(NavigationType.NONE, NavigationType.NAVIGATION_COMPOSE, NavigationType.VOYAGER)
            ArchitectureType.SLACK_CIRCUIT -> listOf(NavigationType.CIRCUIT_NAVIGATION)
            ArchitectureType.DECOMPOSE -> listOf(NavigationType.DECOMPOSE_NAVIGATION)
            ArchitectureType.SIMPLE_FEATURE -> listOf(NavigationType.NONE, NavigationType.NAVIGATION_COMPOSE, NavigationType.VOYAGER)
            ArchitectureType.CLEAN_ARCHITECTURE -> listOf(NavigationType.NONE, NavigationType.NAVIGATION_COMPOSE, NavigationType.VOYAGER)
        }

    fun defaultStateHolderFor(architectureType: ArchitectureType): StateHolderType =
        stateHoldersFor(architectureType).first()

    fun defaultNavigationFor(architectureType: ArchitectureType): NavigationType =
        navigationFor(architectureType).first()

    fun coerceStateHolder(architectureType: ArchitectureType, requested: StateHolderType): StateHolderType =
        requested.takeIf { it in stateHoldersFor(architectureType) } ?: defaultStateHolderFor(architectureType)

    fun coerceNavigation(architectureType: ArchitectureType, requested: NavigationType): NavigationType =
        requested.takeIf { it in navigationFor(architectureType) } ?: defaultNavigationFor(architectureType)
}

enum class PlannedFileKind {
    CREATE,
    MODIFY
}

data class PlannedFile(
    val path: String,
    val content: String,
    val kind: PlannedFileKind = PlannedFileKind.CREATE,
    val conflict: Boolean = false,
    val replacesFile: Boolean = false
)

data class DryRunPreview(
    val filesToCreate: List<PlannedFile>,
    val filesToModify: List<PlannedFile>,
    val warnings: List<String>,
    val tree: String
)

data class GenerationResult(
    val writtenFiles: List<String>,
    val skippedFiles: List<String>,
    val warnings: List<String>
)
