package com.kmpfeaturekit.templates

object FeatureTemplates {
    val screen = """
        package {{packageName}}.presentation

        import androidx.compose.foundation.layout.Arrangement
        import androidx.compose.foundation.layout.Column
        import androidx.compose.foundation.layout.PaddingValues
        import androidx.compose.foundation.layout.fillMaxSize
        import androidx.compose.foundation.layout.fillMaxWidth
        import androidx.compose.foundation.layout.padding
        import androidx.compose.material3.Button
        import androidx.compose.material3.CircularProgressIndicator
        import androidx.compose.material3.MaterialTheme
        import androidx.compose.material3.Text
        import androidx.compose.runtime.Composable
        import androidx.compose.ui.Alignment
        import androidx.compose.ui.Modifier
        import androidx.compose.ui.unit.dp

        @Composable
        fun {{FeatureNamePascal}}Screen(
            state: {{FeatureNamePascal}}State,
            onAction: ({{FeatureNamePascal}}Action) -> Unit,
            modifier: Modifier = Modifier
        ) {
            when {
                state.isLoading -> {{FeatureNamePascal}}Loading(modifier)
                state.errorMessage != null -> {{FeatureNamePascal}}Error(state.errorMessage, onAction, modifier)
                state.items.isEmpty() -> {{FeatureNamePascal}}Empty(onAction, modifier)
                else -> {{FeatureNamePascal}}Content(state, onAction, modifier)
            }
        }

        @Composable
        private fun {{FeatureNamePascal}}Loading(modifier: Modifier = Modifier) {
            Column(
                modifier = modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
                Text("Loading {{FeatureNamePascal}}", style = MaterialTheme.typography.bodyMedium)
            }
        }

        @Composable
        private fun {{FeatureNamePascal}}Error(
            message: String,
            onAction: ({{FeatureNamePascal}}Action) -> Unit,
            modifier: Modifier = Modifier
        ) {
            Column(
                modifier = modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
                Button(onClick = { onAction({{FeatureNamePascal}}Action.Retry) }) {
                    Text("Retry")
                }
            }
        }

        @Composable
        private fun {{FeatureNamePascal}}Empty(
            onAction: ({{FeatureNamePascal}}Action) -> Unit,
            modifier: Modifier = Modifier
        ) {
            Column(
                modifier = modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("No {{feature-name-kebab}} data yet", style = MaterialTheme.typography.titleMedium)
                Button(onClick = { onAction({{FeatureNamePascal}}Action.Refresh) }) {
                    Text("Refresh")
                }
            }
        }

        @Composable
        private fun {{FeatureNamePascal}}Content(
            state: {{FeatureNamePascal}}State,
            onAction: ({{FeatureNamePascal}}Action) -> Unit,
            modifier: Modifier = Modifier,
            contentPadding: PaddingValues = PaddingValues(16.dp)
        ) {
            Column(
                modifier = modifier.fillMaxWidth().padding(contentPadding),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("{{FeatureNamePascal}}", style = MaterialTheme.typography.headlineSmall)
                state.items.forEach { item ->
                    Text(item.title, style = MaterialTheme.typography.bodyLarge)
                }
                Button(onClick = { onAction({{FeatureNamePascal}}Action.Refresh) }) {
                    Text("Refresh")
                }
            }
        }
    """.trimIndent()

    val state = """
        package {{packageName}}.presentation

        import {{packageName}}.domain.{{FeatureNamePascal}}Item

        data class {{FeatureNamePascal}}State(
            val isLoading: Boolean = false,
            val errorMessage: String? = null,
            val items: List<{{FeatureNamePascal}}Item> = emptyList()
        )
    """.trimIndent()

    val domainModel = """
        package {{packageName}}.domain

        data class {{FeatureNamePascal}}Item(
            val id: String,
            val title: String,
            val subtitle: String? = null
        )
    """.trimIndent()

    val action = """
        package {{packageName}}.presentation

        sealed interface {{FeatureNamePascal}}Action {
            data object Started : {{FeatureNamePascal}}Action
            data object Refresh : {{FeatureNamePascal}}Action
            data object Retry : {{FeatureNamePascal}}Action
            data class ItemSelected(val id: String) : {{FeatureNamePascal}}Action
        }
    """.trimIndent()

    val effect = """
        package {{packageName}}.presentation

        sealed interface {{FeatureNamePascal}}Effect {
            data class ShowMessage(val message: String) : {{FeatureNamePascal}}Effect
            data class NavigateToDetail(val id: String) : {{FeatureNamePascal}}Effect
        }
    """.trimIndent()

    val preview = """
        package {{packageName}}.presentation

        import {{packageName}}.domain.{{FeatureNamePascal}}Item
        import androidx.compose.runtime.Composable
        import org.jetbrains.compose.ui.tooling.preview.Preview

        @Preview
        @Composable
        private fun {{FeatureNamePascal}}ScreenPreview() {
            {{FeatureNamePascal}}Screen(
                state = {{FeatureNamePascal}}State(
                    items = listOf({{FeatureNamePascal}}Item(id = "preview", title = "Preview {{FeatureNamePascal}}"))
                ),
                onAction = {}
            )
        }
    """.trimIndent()

    val mvvmViewModel = """
        package {{packageName}}.presentation

        import {{packageName}}.domain.Load{{FeatureNamePascal}}UseCase
        import kotlinx.coroutines.CoroutineScope
        import kotlinx.coroutines.Dispatchers
        import kotlinx.coroutines.SupervisorJob
        import kotlinx.coroutines.cancel
        import kotlinx.coroutines.flow.MutableStateFlow
        import kotlinx.coroutines.flow.StateFlow
        import kotlinx.coroutines.flow.asStateFlow
        import kotlinx.coroutines.launch

        class {{FeatureNamePascal}}ViewModel(
            private val load{{FeatureNamePascal}}: Load{{FeatureNamePascal}}UseCase,
            private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        ) {
            private val _state = MutableStateFlow({{FeatureNamePascal}}State(isLoading = true))
            val state: StateFlow<{{FeatureNamePascal}}State> = _state.asStateFlow()

            init {
                load()
            }

            fun onAction(action: {{FeatureNamePascal}}Action) {
                when (action) {
                    {{FeatureNamePascal}}Action.Started,
                    {{FeatureNamePascal}}Action.Refresh,
                    {{FeatureNamePascal}}Action.Retry -> load()
                    is {{FeatureNamePascal}}Action.ItemSelected -> Unit
                }
            }

            private fun load() {
                scope.launch {
                    _state.value = _state.value.copy(isLoading = true, errorMessage = null)
                    runCatching { load{{FeatureNamePascal}}() }
                        .onSuccess { _state.value = {{FeatureNamePascal}}State(items = it) }
                        .onFailure { _state.value = {{FeatureNamePascal}}State(errorMessage = it.message ?: "Unable to load {{FeatureNamePascal}}") }
                }
            }

            fun clear() {
                scope.cancel()
            }
        }
    """.trimIndent()

    val plainStateHolder = """
        package {{packageName}}.presentation

        import {{packageName}}.domain.Load{{FeatureNamePascal}}UseCase
        import kotlinx.coroutines.CoroutineScope
        import kotlinx.coroutines.Dispatchers
        import kotlinx.coroutines.SupervisorJob
        import kotlinx.coroutines.cancel
        import kotlinx.coroutines.flow.MutableStateFlow
        import kotlinx.coroutines.flow.StateFlow
        import kotlinx.coroutines.flow.asStateFlow
        import kotlinx.coroutines.launch

        class {{FeatureNamePascal}}StateHolder(
            private val load{{FeatureNamePascal}}: Load{{FeatureNamePascal}}UseCase,
            private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        ) {
            private val _state = MutableStateFlow({{FeatureNamePascal}}State())
            val state: StateFlow<{{FeatureNamePascal}}State> = _state.asStateFlow()

            init {
                load()
            }

            fun onAction(action: {{FeatureNamePascal}}Action) {
                when (action) {
                    {{FeatureNamePascal}}Action.Started,
                    {{FeatureNamePascal}}Action.Refresh,
                    {{FeatureNamePascal}}Action.Retry -> load()
                    is {{FeatureNamePascal}}Action.ItemSelected -> Unit
                }
            }

            private fun load() {
                scope.launch {
                    _state.value = _state.value.copy(isLoading = true, errorMessage = null)
                    _state.value = runCatching { load{{FeatureNamePascal}}() }
                        .fold(
                            onSuccess = { {{FeatureNamePascal}}State(items = it) },
                            onFailure = { {{FeatureNamePascal}}State(errorMessage = it.message ?: "Unable to load {{FeatureNamePascal}}") }
                        )
                }
            }

            fun clear() {
                scope.cancel()
            }
        }
    """.trimIndent()

    val mviStore = """
        package {{packageName}}.presentation

        import {{packageName}}.domain.Load{{FeatureNamePascal}}UseCase
        import kotlinx.coroutines.CoroutineScope
        import kotlinx.coroutines.flow.MutableStateFlow
        import kotlinx.coroutines.flow.StateFlow
        import kotlinx.coroutines.flow.asStateFlow
        import kotlinx.coroutines.launch

        typealias {{FeatureNamePascal}}Intent = {{FeatureNamePascal}}Action

        class {{FeatureNamePascal}}Store(
            private val load{{FeatureNamePascal}}: Load{{FeatureNamePascal}}UseCase,
            private val scope: CoroutineScope
        ) {
            private val _state = MutableStateFlow({{FeatureNamePascal}}State())
            val state: StateFlow<{{FeatureNamePascal}}State> = _state.asStateFlow()

            fun accept(intent: {{FeatureNamePascal}}Intent) {
                when (intent) {
                    {{FeatureNamePascal}}Action.Started,
                    {{FeatureNamePascal}}Action.Refresh,
                    {{FeatureNamePascal}}Action.Retry -> scope.launch { reduceLoading() }
                    is {{FeatureNamePascal}}Action.ItemSelected -> Unit
                }
            }

            private suspend fun reduceLoading() {
                _state.value = _state.value.copy(isLoading = true, errorMessage = null)
                _state.value = runCatching { load{{FeatureNamePascal}}() }
                    .fold(
                        onSuccess = { {{FeatureNamePascal}}State(items = it) },
                        onFailure = { {{FeatureNamePascal}}State(errorMessage = it.message ?: "Unable to load") }
                    )
            }
        }
    """.trimIndent()

    val circuitPresenter = """
        package {{packageName}}.presentation

        import {{packageName}}.domain.Load{{FeatureNamePascal}}UseCase

        data object {{FeatureNamePascal}}Screen

        class {{FeatureNamePascal}}Presenter(
            private val load{{FeatureNamePascal}}: Load{{FeatureNamePascal}}UseCase
        ) {
            suspend fun present(event: {{FeatureNamePascal}}Event = {{FeatureNamePascal}}Event.Started): {{FeatureNamePascal}}State =
                runCatching { load{{FeatureNamePascal}}() }
                    .fold(
                        onSuccess = { {{FeatureNamePascal}}State(items = it) },
                        onFailure = { {{FeatureNamePascal}}State(errorMessage = it.message ?: "Unable to load") }
                    )
        }

        sealed interface {{FeatureNamePascal}}Event {
            data object Started : {{FeatureNamePascal}}Event
            data object Refresh : {{FeatureNamePascal}}Event
        }
    """.trimIndent()

    val decomposeComponent = """
        package {{packageName}}.presentation

        import {{packageName}}.domain.Load{{FeatureNamePascal}}UseCase
        import kotlinx.coroutines.flow.MutableStateFlow
        import kotlinx.coroutines.flow.StateFlow
        import kotlinx.coroutines.flow.asStateFlow

        interface {{FeatureNamePascal}}Component {
            val state: StateFlow<{{FeatureNamePascal}}State>
            fun onAction(action: {{FeatureNamePascal}}Action)
        }

        class Default{{FeatureNamePascal}}Component(
            private val load{{FeatureNamePascal}}: Load{{FeatureNamePascal}}UseCase,
            private val onFinished: () -> Unit
        ) : {{FeatureNamePascal}}Component {
            private val _state = MutableStateFlow({{FeatureNamePascal}}State())
            override val state: StateFlow<{{FeatureNamePascal}}State> = _state.asStateFlow()

            override fun onAction(action: {{FeatureNamePascal}}Action) {
                if (action is {{FeatureNamePascal}}Action.ItemSelected) onFinished()
            }
        }
    """.trimIndent()

    val repository = """
        package {{packageName}}.domain

        interface {{FeatureNamePascal}}Repository {
            suspend fun load{{FeatureNamePascal}}(): List<{{FeatureNamePascal}}Item>
        }
    """.trimIndent()

    val service = """
        package {{packageName}}.domain

        interface {{FeatureNamePascal}}Service {
            suspend fun load{{FeatureNamePascal}}(): List<{{FeatureNamePascal}}Item>
        }
    """.trimIndent()

    val serviceImpl = """
        package {{packageName}}.data

        import {{packageName}}.domain.{{FeatureNamePascal}}Item
        import {{packageName}}.domain.{{FeatureNamePascal}}Service

        class Default{{FeatureNamePascal}}Service : {{FeatureNamePascal}}Service {
            override suspend fun load{{FeatureNamePascal}}(): List<{{FeatureNamePascal}}Item> =
                listOf({{FeatureNamePascal}}Item(id = "sample", title = "{{FeatureNamePascal}} item"))
        }
    """.trimIndent()

    val repositoryImpl = """
        package {{packageName}}.data

        import {{packageName}}.domain.{{FeatureNamePascal}}Item
        import {{packageName}}.domain.{{FeatureNamePascal}}Repository
        import {{packageName}}.domain.{{FeatureNamePascal}}Service

        class Default{{FeatureNamePascal}}Repository(
            private val service: {{FeatureNamePascal}}Service
        ) : {{FeatureNamePascal}}Repository {
            override suspend fun load{{FeatureNamePascal}}(): List<{{FeatureNamePascal}}Item> =
                service.load{{FeatureNamePascal}}()
        }
    """.trimIndent()

    val useCase = """
        package {{packageName}}.domain

        class Load{{FeatureNamePascal}}UseCase(
            private val repository: {{FeatureNamePascal}}Repository
        ) {
            suspend operator fun invoke() = repository.load{{FeatureNamePascal}}()
        }
    """.trimIndent()

    val route = """
        package {{packageName}}.navigation

        object {{FeatureNamePascal}}Route {
            const val path = "{{feature-name-kebab}}"
        }
    """.trimIndent()

    val koinModule = """
        package {{packageName}}.di

        import {{packageName}}.data.Default{{FeatureNamePascal}}Repository
        import {{packageName}}.data.Default{{FeatureNamePascal}}Service
        import {{packageName}}.domain.Load{{FeatureNamePascal}}UseCase
        import {{packageName}}.domain.{{FeatureNamePascal}}Repository
        import {{packageName}}.domain.{{FeatureNamePascal}}Service
        {{stateHolderImport}}
        import org.koin.dsl.module

        val {{featureNameCamel}}Module = module {
            single<{{FeatureNamePascal}}Service> { Default{{FeatureNamePascal}}Service() }
            single<{{FeatureNamePascal}}Repository> { Default{{FeatureNamePascal}}Repository(get()) }
            factory { Load{{FeatureNamePascal}}UseCase(get()) }
            {{stateHolderKoinRegistration}}
        }
    """.trimIndent()

    val koinGraph = """
        package {{packageName}}.di

        import org.koin.core.module.Module

        object {{FeatureNamePascal}}Graph {
            val modules: List<Module> = listOf({{featureNameCamel}}Module)
        }
    """.trimIndent()

    val manualDi = """
        package {{packageName}}.di

        import {{packageName}}.data.Default{{FeatureNamePascal}}Repository
        import {{packageName}}.data.Default{{FeatureNamePascal}}Service
        import {{packageName}}.domain.Load{{FeatureNamePascal}}UseCase

        class {{FeatureNamePascal}}Dependencies {
            private val service = Default{{FeatureNamePascal}}Service()
            private val repository = Default{{FeatureNamePascal}}Repository(service)
            val load{{FeatureNamePascal}} = Load{{FeatureNamePascal}}UseCase(repository)
        }
    """.trimIndent()

    val kotlinInjectDi = """
        package {{packageName}}.di

        import {{packageName}}.data.Default{{FeatureNamePascal}}Repository
        import {{packageName}}.data.Default{{FeatureNamePascal}}Service
        import {{packageName}}.domain.Load{{FeatureNamePascal}}UseCase
        import {{packageName}}.domain.{{FeatureNamePascal}}Repository
        import {{packageName}}.domain.{{FeatureNamePascal}}Service
        {{stateHolderImport}}
        import me.tatarka.inject.annotations.Provides

        interface {{FeatureNamePascal}}InjectModule {
            @Provides
            fun bind{{FeatureNamePascal}}Service(): {{FeatureNamePascal}}Service = Default{{FeatureNamePascal}}Service()

            @Provides
            fun bind{{FeatureNamePascal}}Repository(service: {{FeatureNamePascal}}Service): {{FeatureNamePascal}}Repository =
                Default{{FeatureNamePascal}}Repository(service)

            @Provides
            fun provideLoad{{FeatureNamePascal}}UseCase(repository: {{FeatureNamePascal}}Repository): Load{{FeatureNamePascal}}UseCase =
                Load{{FeatureNamePascal}}UseCase(repository)
        }

        class {{FeatureNamePascal}}Dependencies(
            val load{{FeatureNamePascal}}: Load{{FeatureNamePascal}}UseCase =
                Load{{FeatureNamePascal}}UseCase(Default{{FeatureNamePascal}}Repository(Default{{FeatureNamePascal}}Service()))
        )
    """.trimIndent()

    val hiltModule = """
        package {{packageName}}.di

        import {{packageName}}.data.Default{{FeatureNamePascal}}Repository
        import {{packageName}}.data.Default{{FeatureNamePascal}}Service
        import {{packageName}}.domain.Load{{FeatureNamePascal}}UseCase
        import {{packageName}}.domain.{{FeatureNamePascal}}Repository
        import {{packageName}}.domain.{{FeatureNamePascal}}Service
        import dagger.Module
        import dagger.Provides
        import dagger.hilt.InstallIn
        import dagger.hilt.components.SingletonComponent

        @Module
        @InstallIn(SingletonComponent::class)
        object {{FeatureNamePascal}}Module {
            @Provides
            fun provide{{FeatureNamePascal}}Service(): {{FeatureNamePascal}}Service = Default{{FeatureNamePascal}}Service()

            @Provides
            fun provide{{FeatureNamePascal}}Repository(service: {{FeatureNamePascal}}Service): {{FeatureNamePascal}}Repository =
                Default{{FeatureNamePascal}}Repository(service)

            @Provides
            fun provideLoad{{FeatureNamePascal}}UseCase(repository: {{FeatureNamePascal}}Repository): Load{{FeatureNamePascal}}UseCase =
                Load{{FeatureNamePascal}}UseCase(repository)
        }
    """.trimIndent()

    val manualGraph = """
        package {{packageName}}.di

        object {{FeatureNamePascal}}Graph {
            fun createDependencies(): {{FeatureNamePascal}}Dependencies = {{FeatureNamePascal}}Dependencies()
        }
    """.trimIndent()

    val navigationGraph = """
        package {{packageName}}.navigation

        object {{FeatureNamePascal}}NavigationGraph {
            val route = {{FeatureNamePascal}}Route.path
            val voyagerRoute = route
            val circuitRoute = route
            val decomposeConfig = route
            val appyxNode = route
        }
    """.trimIndent()

    val buildGradleKts = """
        plugins {
            kotlin("multiplatform")
        }

        kotlin {
            androidTarget()
            iosArm64()
            iosSimulatorArm64()

            sourceSets {
                commonMain.dependencies {
                    implementation(kotlin("stdlib"))
                    // Add {{architectureType}} / {{navigationType}} dependencies here if this is a new module.
                }
                commonTest.dependencies {
                    implementation(kotlin("test"))
                }
            }
        }
    """.trimIndent()

    val buildGradleGroovy = """
        plugins {
            id 'org.jetbrains.kotlin.multiplatform'
        }

        kotlin {
            androidTarget()
            iosArm64()
            iosSimulatorArm64()

            sourceSets {
                commonMain {
                    dependencies {
                        implementation kotlin('stdlib')
                        // Add {{architectureType}} / {{navigationType}} dependencies here if this is a new module.
                    }
                }
                commonTest {
                    dependencies {
                        implementation kotlin('test')
                    }
                }
            }
        }
    """.trimIndent()

    val buildGradleKtsPatch = """

        // Compose Template Generator: {{FeatureNamePascal}}
        // Review dependency aliases in your version catalog before keeping this block.
        kotlin {
            sourceSets {
                commonMain.dependencies {
                    // {{architectureType}} feature: {{FeatureNamePascal}}
                    // Navigation: {{navigationType}}
                }
            }
        }
    """.trimIndent()

    val buildGradleGroovyPatch = """

        // Compose Template Generator: {{FeatureNamePascal}}
        // Review dependency aliases in your version catalog before keeping this block.
        kotlin {
            sourceSets {
                commonMain {
                    dependencies {
                        // {{architectureType}} feature: {{FeatureNamePascal}}
                        // Navigation: {{navigationType}}
                    }
                }
            }
        }
    """.trimIndent()

    val expectPlatform = """
        package {{packageName}}.platform

        expect class {{FeatureNamePascal}}PlatformContext {
            val platformName: String
        }
    """.trimIndent()

    val actualPlatform = """
        package {{packageName}}.platform

        actual class {{FeatureNamePascal}}PlatformContext {
            actual val platformName: String = "{{platformName}}"
        }
    """.trimIndent()

    val fakeRepository = """
        package {{packageName}}.testing

        import {{packageName}}.domain.{{FeatureNamePascal}}Item
        import {{packageName}}.domain.{{FeatureNamePascal}}Repository

        class Fake{{FeatureNamePascal}}Repository(
            private val items: List<{{FeatureNamePascal}}Item> = listOf({{FeatureNamePascal}}Item("fake", "Fake {{FeatureNamePascal}}"))
        ) : {{FeatureNamePascal}}Repository {
            override suspend fun load{{FeatureNamePascal}}(): List<{{FeatureNamePascal}}Item> = items
        }
    """.trimIndent()

    val test = """
        package {{packageName}}.presentation

        import kotlin.test.Test
        import kotlin.test.assertEquals

        class {{FeatureNamePascal}}StateTest {
            @Test
            fun emptyStateStartsWithoutItems() {
                assertEquals(emptyList(), {{FeatureNamePascal}}State().items)
            }
        }
    """.trimIndent()

    val layeredDomainModel = """
        package {{domainModelPackage}}

        data class {{FeatureNamePascal}}Item(
            val id: String,
            val title: String,
            val subtitle: String? = null
        )
    """.trimIndent()

    val layeredRepository = """
        package {{domainRepositoryPackage}}

        import {{domainModelPackage}}.{{FeatureNamePascal}}Item

        interface {{FeatureNamePascal}}Repository {
            suspend fun load{{FeatureNamePascal}}(): List<{{FeatureNamePascal}}Item>
        }
    """.trimIndent()

    val layeredUseCase = """
        package {{domainUseCasePackage}}

        import {{domainRepositoryPackage}}.{{FeatureNamePascal}}Repository

        class Load{{FeatureNamePascal}}UseCase(
            private val repository: {{FeatureNamePascal}}Repository
        ) {
            suspend operator fun invoke() = repository.load{{FeatureNamePascal}}()
        }
    """.trimIndent()

    val layeredService = """
        package {{dataRemotePackage}}

        import {{domainModelPackage}}.{{FeatureNamePascal}}Item

        class {{FeatureNamePascal}}Service {
            suspend fun load{{FeatureNamePascal}}(): List<{{FeatureNamePascal}}Item> =
                listOf({{FeatureNamePascal}}Item(id = "sample", title = "{{FeatureNamePascal}} item"))
        }
    """.trimIndent()

    val layeredRepositoryImpl = """
        package {{dataRepositoryPackage}}

        import {{dataRemotePackage}}.{{FeatureNamePascal}}Service
        import {{domainModelPackage}}.{{FeatureNamePascal}}Item
        import {{domainRepositoryPackage}}.{{FeatureNamePascal}}Repository

        class {{FeatureNamePascal}}RepositoryImpl(
            private val service: {{FeatureNamePascal}}Service
        ) : {{FeatureNamePascal}}Repository {
            override suspend fun load{{FeatureNamePascal}}(): List<{{FeatureNamePascal}}Item> =
                service.load{{FeatureNamePascal}}()
        }
    """.trimIndent()

    val layeredState = """
        package {{presentationPackage}}

        import {{domainModelPackage}}.{{FeatureNamePascal}}Item

        data class {{FeatureNamePascal}}State(
            val items: List<{{FeatureNamePascal}}Item> = emptyList(),
            val isLoading: Boolean = true,
            val error: String? = null
        )
    """.trimIndent()

    val layeredViewModel = """
        package {{presentationPackage}}

        import {{domainUseCasePackage}}.Load{{FeatureNamePascal}}UseCase
        import kotlinx.coroutines.CoroutineScope
        import kotlinx.coroutines.Dispatchers
        import kotlinx.coroutines.SupervisorJob
        import kotlinx.coroutines.cancel
        import kotlinx.coroutines.flow.MutableStateFlow
        import kotlinx.coroutines.flow.StateFlow
        import kotlinx.coroutines.flow.asStateFlow
        import kotlinx.coroutines.flow.update
        import kotlinx.coroutines.launch

        class {{FeatureNamePascal}}ViewModel(
            private val load{{FeatureNamePascal}}: Load{{FeatureNamePascal}}UseCase
        ) {
            private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
            private val _state = MutableStateFlow({{FeatureNamePascal}}State())
            val state: StateFlow<{{FeatureNamePascal}}State> = _state.asStateFlow()

            init {
                refresh()
            }

            fun refresh() {
                scope.launch {
                    _state.update { it.copy(isLoading = true, error = null) }
                    runCatching { load{{FeatureNamePascal}}() }
                        .onSuccess { items ->
                            _state.update { it.copy(items = items, isLoading = false) }
                        }
                        .onFailure { throwable ->
                            _state.update { it.copy(isLoading = false, error = throwable.message ?: "Unable to load {{FeatureNamePascal}}") }
                        }
                }
            }

            fun clear() {
                scope.cancel()
            }
        }
    """.trimIndent()

    val layeredScreen = """
        package {{uiPackage}}

        import androidx.compose.foundation.layout.Arrangement
        import androidx.compose.foundation.layout.Column
        import androidx.compose.foundation.layout.fillMaxSize
        import androidx.compose.foundation.layout.fillMaxWidth
        import androidx.compose.foundation.layout.padding
        import androidx.compose.material3.Button
        import androidx.compose.material3.CircularProgressIndicator
        import androidx.compose.material3.MaterialTheme
        import androidx.compose.material3.Text
        import androidx.compose.runtime.Composable
        import androidx.compose.runtime.collectAsState
        import androidx.compose.runtime.getValue
        import androidx.compose.ui.Alignment
        import androidx.compose.ui.Modifier
        import androidx.compose.ui.unit.dp
        import {{presentationPackage}}.{{FeatureNamePascal}}State
        import kotlinx.coroutines.flow.StateFlow

        @Composable
        fun {{FeatureNamePascal}}Screen(
            stateFlow: StateFlow<{{FeatureNamePascal}}State>,
            onRefresh: () -> Unit,
            modifier: Modifier = Modifier
        ) {
            val state by stateFlow.collectAsState()
            {{FeatureNamePascal}}Screen(
                state = state,
                onRefresh = onRefresh,
                modifier = modifier
            )
        }

        @Composable
        fun {{FeatureNamePascal}}Screen(
            state: {{FeatureNamePascal}}State,
            onRefresh: () -> Unit,
            modifier: Modifier = Modifier
        ) {
            Column(
                modifier = modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("{{FeatureNamePascal}}", style = MaterialTheme.typography.headlineSmall)
                when {
                    state.isLoading -> CircularProgressIndicator()
                    state.error != null -> {
                        Text(state.error, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
                        Button(onClick = onRefresh) {
                            Text("Retry")
                        }
                    }
                    state.items.isEmpty() -> {
                        Text("No {{feature-name-kebab}} data yet", style = MaterialTheme.typography.bodyLarge)
                        Button(onClick = onRefresh) {
                            Text("Refresh")
                        }
                    }
                    else -> state.items.forEach { item ->
                        Text(item.title, modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    """.trimIndent()

    val layeredPreview = """
        package {{uiPackage}}

        import {{domainModelPackage}}.{{FeatureNamePascal}}Item
        import {{presentationPackage}}.{{FeatureNamePascal}}State
        import org.jetbrains.compose.ui.tooling.preview.Preview
        import androidx.compose.runtime.Composable

        @Preview
        @Composable
        private fun {{FeatureNamePascal}}ScreenPreview() {
            {{FeatureNamePascal}}Screen(
                state = {{FeatureNamePascal}}State(
                    items = listOf({{FeatureNamePascal}}Item(id = "preview", title = "Preview {{FeatureNamePascal}}")),
                    isLoading = false
                ),
                onRefresh = {}
            )
        }
    """.trimIndent()

    val layeredFakeRepository = """
        package {{testingPackage}}

        import {{domainModelPackage}}.{{FeatureNamePascal}}Item
        import {{domainRepositoryPackage}}.{{FeatureNamePascal}}Repository

        class Fake{{FeatureNamePascal}}Repository(
            private val items: List<{{FeatureNamePascal}}Item> = listOf({{FeatureNamePascal}}Item("fake", "Fake {{FeatureNamePascal}}"))
        ) : {{FeatureNamePascal}}Repository {
            override suspend fun load{{FeatureNamePascal}}(): List<{{FeatureNamePascal}}Item> = items
        }
    """.trimIndent()

    val layeredStateTest = """
        package {{presentationPackage}}

        import kotlin.test.Test
        import kotlin.test.assertEquals

        class {{FeatureNamePascal}}StateTest {
            @Test
            fun emptyStateStartsWithoutItems() {
                assertEquals(emptyList(), {{FeatureNamePascal}}State(isLoading = false).items)
            }
        }
    """.trimIndent()
}
