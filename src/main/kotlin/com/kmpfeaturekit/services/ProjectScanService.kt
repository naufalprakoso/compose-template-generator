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
    val gradleDsl: String
)

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

        val libraries = buildSet {
            if ("slack.circuit" in text || "circuit" in text.lowercase()) add("Slack Circuit")
            if ("voyager" in text.lowercase()) add("Voyager")
            if ("decompose" in text.lowercase()) add("Decompose")
            if ("insert-koin" in text || "io.insert-koin" in text) add("Koin")
            if ("kotlin-inject" in text || "me.tatarka.inject" in text) add("Kotlin Inject")
            if ("apollo" in text.lowercase()) add("Apollo")
            if ("ktor" in text.lowercase()) add("Ktor")
            if ("sqldelight" in text.lowercase()) add("SQLDelight")
            if ("androidx.room" in text) add("Room")
            if ("appyx" in text.lowercase()) add("Appyx")
            if ("navigation-compose" in text || "androidx.navigation.compose" in text) add("Navigation Compose")
            if ("NavHost(" in text) add("Navigation Compose")
        }

        val architecture = when {
                "Slack Circuit" in libraries -> ArchitectureType.SLACK_CIRCUIT
                "Decompose" in libraries -> ArchitectureType.DECOMPOSE
                else -> ArchitectureType.MVVM
            }
        val detectedNavigation = when {
            "Slack Circuit" in libraries -> NavigationType.CIRCUIT_NAVIGATION
            "Decompose" in libraries -> NavigationType.DECOMPOSE_NAVIGATION
            "Voyager" in libraries -> NavigationType.VOYAGER
            "Appyx" in libraries -> NavigationType.APPYX
            else -> NavigationType.NONE
        }
        val projectStyle = detectProjectStyle(sourceDirectories)

        return ProjectScanResult(
            detectedLibraries = libraries,
            suggestedArchitecture = architecture,
            suggestedNavigation = ArchitectureCompatibility.coerceNavigation(architecture, detectedNavigation),
            suggestedDi = when {
                "Kotlin Inject" in libraries -> DependencyInjectionType.KOTLIN_INJECT
                "Koin" in libraries -> DependencyInjectionType.KOIN
                else -> DependencyInjectionType.MANUAL
            },
            suggestedProjectStyle = projectStyle,
            gradleDsl = when {
                hasKotlinGradle -> "Kotlin DSL"
                hasGroovyGradle -> "Groovy DSL"
                else -> "Kotlin DSL"
            }
        )
    }

    private fun detectProjectStyle(directories: Set<String>): ProjectStyle {
        val commonRoots = directories
            .filter { "/src/commonMain/kotlin/" in it }
            .map { it.substringBeforeLast("/src/commonMain/kotlin/") to it.substringAfter("/src/commonMain/kotlin/") }

        val layeredRoot = commonRoots
            .groupBy { (_, packagePath) -> packagePath.substringBefore('/') }
            .values
            .firstOrNull { entries ->
                val packagePaths = entries.map { it.second }.toSet()
                packagePaths.any { it.endsWith("/data") || it.contains("/data/") } &&
                    packagePaths.any { it.endsWith("/domain") || it.contains("/domain/") } &&
                    packagePaths.any { it.endsWith("/presentation") || it.contains("/presentation/") } &&
                    packagePaths.any { it.endsWith("/ui") || it.contains("/ui/") }
            }
        if (layeredRoot != null) return ProjectStyle.LAYERED_GLOBAL

        val hasFeaturePackages = commonRoots.any { (_, packagePath) ->
            packagePath.contains("/features/") || packagePath.split('/').any { it in setOf("data", "domain", "presentation") }
        }
        return if (hasFeaturePackages) ProjectStyle.FEATURE_BASED else ProjectStyle.FEATURE_BASED
    }
}
