package com.kmpfeaturekit.navigation

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.kmpfeaturekit.di.RegistrationPlan
import com.kmpfeaturekit.model.NavigationType
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText

@Service(Service.Level.PROJECT)
class NavigationRegistrationService(@Suppress("UNUSED_PARAMETER") private val project: Project) {
    fun planRouteRegistration(routeName: String, navigationType: NavigationType, candidateFiles: List<String>): RegistrationPlan {
        val target = candidateFiles.firstOrNull { it.contains("nav", ignoreCase = true) || it.contains("route", ignoreCase = true) }
        val patch = when (navigationType) {
            NavigationType.NAVIGATION_COMPOSE -> "+ composable(${routeName}Route.path) { ${routeName}Screen(...) }"
            NavigationType.CIRCUIT_NAVIGATION -> "+ Circuit screen binding for ${routeName}Screen"
            NavigationType.DECOMPOSE_NAVIGATION -> "+ Decompose child config for $routeName"
            NavigationType.VOYAGER -> "+ Voyager route object for $routeName"
            NavigationType.APPYX -> "+ Appyx node for $routeName"
        }
        return RegistrationPlan(target != null, target, patch, if (target == null) listOf("No safe navigation target found.") else emptyList())
    }
}

object NavigationRegistrationPlanner {
    fun planNavigationCompose(
        moduleRoot: Path,
        routeName: String,
        featurePackageName: String
    ): RegistrationPlan {
        if (!moduleRoot.exists()) {
            return todoPlan(routeName, "Module root does not exist yet.")
        }

        val candidates = kotlin.runCatching {
            Files.walk(moduleRoot).use { stream ->
                stream
                    .filter { it.isRegularFile() && it.fileName.toString().endsWith(".kt") }
                    .filter { "NavHost(" in it.readTextSafely() }
                    .toList()
            }
        }.getOrDefault(emptyList())

        candidates.forEach { candidate ->
            val existing = candidate.readTextSafely()
            val updated = registerRoute(existing, routeName, featurePackageName)
            if (updated != null) {
                return RegistrationPlan(
                    safeToApply = true,
                    targetFile = candidate.toString(),
                    diffPreview = "+ composable(${routeName}Route.path)",
                    warnings = emptyList(),
                    replacementContent = updated
                )
            }
        }

        return todoPlan(routeName, "No Navigation Compose NavHost was safe to update.")
    }

    fun registerRoute(content: String, routeName: String, featurePackageName: String): String? {
        val routeReference = "${routeName}Route.path"
        if (routeReference in content) return null
        val navHostLineIndex = content.lines().indexOfFirst { "NavHost(" in it }
        if (navHostLineIndex < 0) return null

        val lines = content.lines().toMutableList()
        val insertAfter = findNavHostOpeningBrace(lines, navHostLineIndex) ?: return null
        val indent = lines[insertAfter].takeWhile { it.isWhitespace() } + "    "
        lines.add(
            insertAfter + 1,
            "${indent}composable($routeReference) { ${routeName}Screen(state = ${routeName}State(), onAction = {}) }"
        )

        val withRoute = addImport(lines.joinToString("\n"), "$featurePackageName.navigation.${routeName}Route")
        val withScreen = addImport(withRoute, "$featurePackageName.presentation.${routeName}Screen")
        val withState = addImport(withScreen, "$featurePackageName.presentation.${routeName}State")
        return addImport(withState, "androidx.navigation.compose.composable")
    }

    private fun findNavHostOpeningBrace(lines: List<String>, navHostLineIndex: Int): Int? {
        for (index in navHostLineIndex until minOf(lines.size, navHostLineIndex + 12)) {
            if ("{" in lines[index]) return index
        }
        return null
    }

    private fun todoPlan(routeName: String, reason: String): RegistrationPlan =
        RegistrationPlan(
            safeToApply = false,
            targetFile = null,
            diffPreview = """
                // TODO Register this feature route in your Navigation Compose graph.
                // Reason: $reason
                composable(${routeName}Route.path) {
                    ${routeName}Screen(state = ${routeName}State(), onAction = {})
                }
            """.trimIndent(),
            warnings = listOf(reason)
        )

    private fun addImport(content: String, importFqName: String): String {
        val importLine = "import $importFqName"
        if (importLine in content) return content

        val lines = content.lines().toMutableList()
        val lastImportIndex = lines.indexOfLast { it.startsWith("import ") }
        return if (lastImportIndex >= 0) {
            lines.add(lastImportIndex + 1, importLine)
            lines.joinToString("\n")
        } else {
            val packageIndex = lines.indexOfFirst { it.startsWith("package ") }
            if (packageIndex >= 0) {
                lines.add(packageIndex + 1, "")
                lines.add(packageIndex + 2, importLine)
                lines.joinToString("\n")
            } else {
                "$importLine\n$content"
            }
        }
    }

    private fun Path.readTextSafely(): String =
        runCatching { takeIf { Files.size(it) < 200_000 }?.readText() }.getOrNull().orEmpty()
}
