package com.kmpfeaturekit.generator

import com.kmpfeaturekit.model.ArchitectureSelection
import com.kmpfeaturekit.model.ArchitectureType
import com.kmpfeaturekit.model.DependencyInjectionType
import com.kmpfeaturekit.model.FeatureInfo
import com.kmpfeaturekit.model.FeatureOptions
import com.kmpfeaturekit.model.FeatureRequest
import com.kmpfeaturekit.templates.PureTemplateRenderer
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.pathString
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler

class GeneratedSampleCompileTest {
    private val builder = FeaturePlanBuilder(PureTemplateRenderer::render)

    @Test
    fun generatedCommonMainCompilesForEveryArchitecture() {
        ArchitectureType.entries.forEach { architecture ->
            val request = FeatureRequest(
                info = FeatureInfo(
                    featureName = "Payment History",
                    basePackage = "com.example",
                    targetModule = "shared",
                    sourceSetRoot = createTempDirectory().resolve("shared/src").pathString
                ),
                architecture = ArchitectureSelection(
                    architectureType = architecture,
                    dependencyInjectionType = DependencyInjectionType.MANUAL
                ),
                options = FeatureOptions(
                    autoRegisterDi = false,
                    autoRegisterNavigation = false,
                    unitTests = false
                )
            )

            val sources = builder.build(request)
                .filter { it.path.contains("/commonMain/") && it.path.endsWith(".kt") }
                .associate { it.path.substringAfter("/commonMain/kotlin/") to it.content }

            compileKotlinSources("architecture-${architecture.name}", sources + commonStubs())
        }
    }

    private fun compileKotlinSources(name: String, sources: Map<String, String>) {
        val root = createTempDirectory(name)
        val out = root.resolve("out").also { it.createDirectories() }
        val sourcePaths = sources.map { (relativePath, content) ->
            root.resolve("src").resolve(relativePath).also { path ->
                path.parent.createDirectories()
                path.writeText(content)
            }.pathString
        }
        val compilerOutput = ByteArrayOutputStream()
        val exitCode = K2JVMCompiler().exec(
            PrintStream(compilerOutput),
            "-d",
            out.pathString,
            "-no-stdlib",
            "-no-reflect",
            "-Xdisable-default-scripting-plugin",
            "-classpath",
            kotlinStdlibClasspath(),
            *sourcePaths.toTypedArray()
        )

        assertEquals(ExitCode.OK, exitCode, compilerOutput.toString())
    }

    private fun kotlinStdlibClasspath(): String =
        System.getProperty("java.class.path")
            .split(System.getProperty("path.separator"))
            .filter { path ->
                val name = path.substringAfterLast('/')
                name.startsWith("kotlin-stdlib") || name.startsWith("annotations")
            }
            .joinToString(System.getProperty("path.separator"))

    private fun commonStubs(): Map<String, String> = mapOf(
        "androidx/compose/runtime/Composable.kt" to """
            package androidx.compose.runtime

            @Target(AnnotationTarget.FUNCTION, AnnotationTarget.TYPE)
            annotation class Composable
        """.trimIndent(),
        "androidx/compose/ui/Modifier.kt" to """
            package androidx.compose.ui

            object Modifier
        """.trimIndent(),
        "androidx/compose/ui/unit/Dp.kt" to """
            package androidx.compose.ui.unit

            class Dp
            val Int.dp: Dp get() = Dp()
        """.trimIndent(),
        "androidx/compose/ui/Alignment.kt" to """
            package androidx.compose.ui

            object Alignment {
                val CenterHorizontally: Any = Any()
                val CenterVertically: Any = Any()
            }
        """.trimIndent(),
        "androidx/compose/foundation/layout/Layout.kt" to """
            package androidx.compose.foundation.layout

            import androidx.compose.runtime.Composable
            import androidx.compose.ui.Modifier
            import androidx.compose.ui.unit.Dp

            class PaddingValues(val value: Dp)
            object Arrangement {
                val Center: Any = Any()
                fun spacedBy(space: Dp): Any = Any()
                fun spacedBy(space: Dp, alignment: Any): Any = Any()
            }

            fun Modifier.fillMaxSize(): Modifier = this
            fun Modifier.fillMaxWidth(): Modifier = this
            fun Modifier.padding(value: Dp): Modifier = this
            fun Modifier.padding(values: PaddingValues): Modifier = this

            @Composable
            fun Column(
                modifier: Modifier = Modifier,
                verticalArrangement: Any? = null,
                horizontalAlignment: Any? = null,
                content: @Composable () -> Unit = {}
            ) = content()
        """.trimIndent(),
        "androidx/compose/material3/Material3.kt" to """
            package androidx.compose.material3

            import androidx.compose.runtime.Composable

            object MaterialTheme {
                val typography: Typography = Typography()
                val colorScheme: ColorScheme = ColorScheme()
            }
            class Typography {
                val bodyMedium: Any = Any()
                val bodyLarge: Any = Any()
                val titleMedium: Any = Any()
                val headlineSmall: Any = Any()
            }
            class ColorScheme {
                val error: Any = Any()
            }

            @Composable
            fun Button(onClick: () -> Unit, content: @Composable () -> Unit) = content()

            @Composable
            fun CircularProgressIndicator() {}

            @Composable
            fun Text(text: String, style: Any? = null, color: Any? = null) {}
        """.trimIndent(),
        "kotlinx/coroutines/Coroutines.kt" to """
            package kotlinx.coroutines

            import kotlin.coroutines.CoroutineContext
            import kotlin.coroutines.EmptyCoroutineContext

            interface CoroutineScope {
                val coroutineContext: CoroutineContext
            }

            fun CoroutineScope(context: CoroutineContext): CoroutineScope = object : CoroutineScope {
                override val coroutineContext: CoroutineContext = context
            }

            object Dispatchers {
                val Main: CoroutineContext = EmptyCoroutineContext
            }

            fun SupervisorJob(): CoroutineContext = EmptyCoroutineContext
            fun CoroutineScope.launch(block: suspend () -> Unit) {}
        """.trimIndent(),
        "kotlinx/coroutines/flow/Flow.kt" to """
            package kotlinx.coroutines.flow

            interface StateFlow<T>

            class MutableStateFlow<T>(var value: T) : StateFlow<T>

            fun <T> MutableStateFlow<T>.asStateFlow(): StateFlow<T> = this
        """.trimIndent()
    )
}
