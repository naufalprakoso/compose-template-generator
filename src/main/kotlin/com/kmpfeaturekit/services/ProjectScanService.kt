package com.kmpfeaturekit.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.kmpfeaturekit.model.ArchitectureType
import com.kmpfeaturekit.model.ArchitectureCompatibility
import com.kmpfeaturekit.model.DependencyInjectionType
import com.kmpfeaturekit.model.NavigationType
import com.kmpfeaturekit.model.ProjectStyle
import java.nio.file.Path

data class ProjectScanResult(
    val detectedLibraries: Set<String>,
    val suggestedArchitecture: ArchitectureType,
    val suggestedNavigation: NavigationType,
    val suggestedDi: DependencyInjectionType,
    val suggestedProjectStyle: ProjectStyle,
    val gradleDsl: String,
    val confidence: ScanConfidence = ScanConfidence.MEDIUM,
    val evidence: List<String> = emptyList()
)

enum class ScanConfidence(val label: String) {
    LOW("Low"),
    MEDIUM("Medium"),
    HIGH("High")
}

data class ProjectScanInput(
    val text: String,
    val sourceDirectories: Set<String>,
    val hasKotlinGradle: Boolean,
    val hasGroovyGradle: Boolean
)

object ProjectScanAnalyzer {
    fun analyze(input: ProjectScanInput): ProjectScanResult {
        val text = input.text
        val lowerText = text.lowercase()
        val evidence = mutableListOf<String>()
        val libraries = buildSet {
            fun detect(name: String, condition: Boolean, reason: String) {
                if (condition) {
                    add(name)
                    evidence += reason
                }
            }
            detect("Slack Circuit", "slack.circuit" in text || "circuit" in lowerText, "Detected Circuit references in Kotlin/Gradle files.")
            detect("Voyager", "voyager" in lowerText, "Detected Voyager references in Kotlin/Gradle files.")
            detect("Decompose", "decompose" in lowerText, "Detected Decompose references in Kotlin/Gradle files.")
            detect("Koin", "insert-koin" in text || "io.insert-koin" in text, "Detected Koin dependency or package reference.")
            detect("Kotlin Inject", "kotlin-inject" in text || "me.tatarka.inject" in text, "Detected kotlin-inject dependency or package reference.")
            detect("Apollo", "apollo" in lowerText, "Detected Apollo references in Kotlin/Gradle files.")
            detect("Ktor", "ktor" in lowerText, "Detected Ktor references in Kotlin/Gradle files.")
            detect("SQLDelight", "sqldelight" in lowerText, "Detected SQLDelight references in Kotlin/Gradle files.")
            detect("Room", "androidx.room" in text, "Detected AndroidX Room references.")
            detect("Appyx", "appyx" in lowerText, "Detected Appyx references in Kotlin/Gradle files.")
            detect(
                "Navigation Compose",
                "navigation-compose" in text || "androidx.navigation.compose" in text || "NavHost(" in text,
                "Detected Navigation Compose dependency or NavHost usage."
            )
        }

        val architecture = when {
            "Slack Circuit" in libraries -> ArchitectureType.SLACK_CIRCUIT
            "Decompose" in libraries -> ArchitectureType.DECOMPOSE
            else -> ArchitectureType.MVVM
        }
        evidence += "Suggested ${architecture.label} architecture from detected framework signals."

        val detectedNavigation = when {
            "Slack Circuit" in libraries -> NavigationType.CIRCUIT_NAVIGATION
            "Decompose" in libraries -> NavigationType.DECOMPOSE_NAVIGATION
            "Voyager" in libraries -> NavigationType.VOYAGER
            "Appyx" in libraries -> NavigationType.APPYX
            "Navigation Compose" in libraries -> NavigationType.NAVIGATION_COMPOSE
            else -> NavigationType.NONE
        }
        val projectStyleDetection = detectProjectStyle(input.sourceDirectories)
        evidence += projectStyleDetection.evidence

        val confidence = when {
            projectStyleDetection.confidence == ScanConfidence.HIGH && libraries.isNotEmpty() -> ScanConfidence.HIGH
            projectStyleDetection.confidence == ScanConfidence.LOW && libraries.isEmpty() -> ScanConfidence.LOW
            else -> ScanConfidence.MEDIUM
        }

        return ProjectScanResult(
            detectedLibraries = libraries,
            suggestedArchitecture = architecture,
            suggestedNavigation = ArchitectureCompatibility.coerceNavigation(architecture, detectedNavigation),
            suggestedDi = when {
                "Kotlin Inject" in libraries -> DependencyInjectionType.KOTLIN_INJECT
                "Koin" in libraries -> DependencyInjectionType.KOIN
                else -> DependencyInjectionType.MANUAL
            },
            suggestedProjectStyle = projectStyleDetection.style,
            gradleDsl = when {
                input.hasKotlinGradle -> "Kotlin DSL"
                input.hasGroovyGradle -> "Groovy DSL"
                else -> "Kotlin DSL"
            },
            confidence = confidence,
            evidence = evidence.distinct()
        )
    }

    private fun detectProjectStyle(directories: Set<String>): ProjectStyleDetection {
        val commonRoots = directories
            .filter { "/src/commonMain/kotlin/" in it }
            .map { it.substringBeforeLast("/src/commonMain/kotlin/") to it.substringAfter("/src/commonMain/kotlin/") }

        val groupedByPackageRoot = commonRoots.groupBy { (_, packagePath) -> packagePath.substringBefore('/') }
        val layeredRoot = groupedByPackageRoot.values.firstOrNull { entries ->
            val packagePaths = entries.map { it.second }.toSet()
            packagePaths.hasLayer("data") &&
                packagePaths.hasLayer("domain") &&
                packagePaths.hasLayer("presentation") &&
                packagePaths.hasLayer("ui")
        }
        if (layeredRoot != null) {
            val root = layeredRoot.first().second.substringBefore('/')
            return ProjectStyleDetection(
                ProjectStyle.LAYERED_GLOBAL,
                ScanConfidence.HIGH,
                listOf("Detected layered global structure under `$root` with data/domain/presentation/ui roots.")
            )
        }

        val hybrid = commonRoots.firstOrNull { (_, packagePath) ->
            "/features/" in "/$packagePath/" &&
                ("/data/" in "/$packagePath/" || "/domain/" in "/$packagePath/" || "/presentation/" in "/$packagePath/")
        }
        if (hybrid != null) {
            return ProjectStyleDetection(
                ProjectStyle.HYBRID,
                ScanConfidence.HIGH,
                listOf("Detected hybrid feature packages under `${hybrid.second.substringBefore("/features/")}.features`.")
            )
        }

        val layerBased = groupedByPackageRoot.values.firstOrNull { entries ->
            val packagePaths = entries.map { it.second }.toSet()
            packagePaths.hasLayer("data") &&
                packagePaths.hasLayer("domain") &&
                packagePaths.hasLayer("presentation")
        }
        if (layerBased != null) {
            val root = layerBased.first().second.substringBefore('/')
            return ProjectStyleDetection(
                ProjectStyle.LAYER_BASED,
                ScanConfidence.MEDIUM,
                listOf("Detected layer-based package roots under `$root`.")
            )
        }

        val featureBased = commonRoots.firstOrNull { (_, packagePath) ->
            packagePath.split('/').any { it == "feature" || it == "features" }
        }
        if (featureBased != null) {
            return ProjectStyleDetection(
                ProjectStyle.FEATURE_BASED,
                ScanConfidence.MEDIUM,
                listOf("Detected feature package marker in `${featureBased.second}`.")
            )
        }

        return ProjectStyleDetection(
            ProjectStyle.FEATURE_BASED,
            ScanConfidence.LOW,
            listOf("No strong project-style signal found; using feature-based defaults.")
        )
    }

    private fun Set<String>.hasLayer(layer: String): Boolean =
        any { it.endsWith("/$layer") || it.contains("/$layer/") }

    private data class ProjectStyleDetection(
        val style: ProjectStyle,
        val confidence: ScanConfidence,
        val evidence: List<String>
    )
}

@Service(Service.Level.PROJECT)
class ProjectScanService(private val project: Project) {
    private val skippedDirectories = setOf(".git", ".gradle", ".idea", ".kotlin", "build", "out")
    private val maxScannedCharacters = 2_000_000
    @Volatile
    private var cachedResult: ProjectScanResult? = null

    fun scan(forceRefresh: Boolean = false): ProjectScanResult {
        if (!forceRefresh) cachedResult?.let { return it }
        return scanProject().also { cachedResult = it }
    }

    private fun scanProject(): ProjectScanResult {
        var hasKotlinGradle = false
        var hasGroovyGradle = false
        var scannedCharacters = 0
        val sourceDirectories = mutableSetOf<String>()
        val text = buildString {
            project.basePath
                ?.let { LocalFileSystem.getInstance().findFileByNioFile(Path.of(it)) }
                ?.let { root ->
                VfsUtilCore.visitChildrenRecursively(root, object : com.intellij.openapi.vfs.VirtualFileVisitor<Unit>() {
                    override fun visitFile(file: VirtualFile): Boolean {
                        if (file.isDirectory && file.name in skippedDirectories) return false
                        if (scannedCharacters >= maxScannedCharacters) return false
                        if (file.isDirectory) sourceDirectories += file.path.replace('\\', '/')
                        if (file.name == "build.gradle.kts") hasKotlinGradle = true
                        if (file.name == "build.gradle") hasGroovyGradle = true
                        if (!file.isDirectory && (file.name.endsWith(".gradle.kts") || file.name.endsWith(".gradle") || file.name.endsWith(".kt"))) {
                            runCatching {
                                if (file.length < 200_000) {
                                    val content = file.inputStream.bufferedReader().readText()
                                    append(content).append('\n')
                                    scannedCharacters += content.length
                                }
                            }
                        }
                        return true
                    }
                })
            }
        }

        return ProjectScanAnalyzer.analyze(
            ProjectScanInput(
                text = text,
                sourceDirectories = sourceDirectories,
                hasKotlinGradle = hasKotlinGradle,
                hasGroovyGradle = hasGroovyGradle
            )
        )
    }
}
