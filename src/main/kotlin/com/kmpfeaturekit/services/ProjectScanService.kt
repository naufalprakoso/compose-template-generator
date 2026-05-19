package com.kmpfeaturekit.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.kmpfeaturekit.model.ArchitectureType
import com.kmpfeaturekit.model.ArchitectureCompatibility
import com.kmpfeaturekit.model.DependencyInjectionType
import com.kmpfeaturekit.model.NavigationType

data class ProjectScanResult(
    val detectedLibraries: Set<String>,
    val suggestedArchitecture: ArchitectureType,
    val suggestedNavigation: NavigationType,
    val suggestedDi: DependencyInjectionType,
    val gradleDsl: String
)

@Service(Service.Level.PROJECT)
class ProjectScanService(private val project: Project) {
    fun scan(): ProjectScanResult {
        var hasKotlinGradle = false
        var hasGroovyGradle = false
        val text = buildString {
            project.baseDir?.let { root ->
                VfsUtilCore.visitChildrenRecursively(root, object : com.intellij.openapi.vfs.VirtualFileVisitor<Unit>() {
                    override fun visitFile(file: VirtualFile): Boolean {
                        if (file.name == "build.gradle.kts") hasKotlinGradle = true
                        if (file.name == "build.gradle") hasGroovyGradle = true
                        if (!file.isDirectory && (file.name.endsWith(".gradle.kts") || file.name.endsWith(".gradle") || file.name.endsWith(".kt"))) {
                            runCatching {
                                if (file.length < 200_000) append(file.inputStream.bufferedReader().readText()).append('\n')
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
            else -> NavigationType.NAVIGATION_COMPOSE
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
            gradleDsl = when {
                hasKotlinGradle -> "Kotlin DSL"
                hasGroovyGradle -> "Groovy DSL"
                else -> "Kotlin DSL"
            }
        )
    }
}
