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
    fun plan(
        moduleRoot: Path,
        routeName: String,
        navigationType: NavigationType,
        featurePackageName: String
    ): RegistrationPlan =
        when (navigationType) {
            NavigationType.NAVIGATION_COMPOSE -> planNavigationCompose(moduleRoot, routeName, featurePackageName)
            NavigationType.VOYAGER -> planListRegistration(
                moduleRoot = moduleRoot,
                routeName = routeName,
                featurePackageName = featurePackageName,
                registryNames = listOf("voyagerScreens", "screens"),
                entryExpression = "${routeName}NavigationGraph.voyagerRoute",
                importSuffix = "navigation.${routeName}NavigationGraph",
                label = "Voyager"
            )
            NavigationType.CIRCUIT_NAVIGATION -> planListRegistration(
                moduleRoot = moduleRoot,
                routeName = routeName,
                featurePackageName = featurePackageName,
                registryNames = listOf("circuitScreens", "screenBindings"),
                entryExpression = "${routeName}NavigationGraph.circuitRoute",
                importSuffix = "navigation.${routeName}NavigationGraph",
                label = "Circuit"
            )
            NavigationType.DECOMPOSE_NAVIGATION -> planListRegistration(
                moduleRoot = moduleRoot,
                routeName = routeName,
                featurePackageName = featurePackageName,
                registryNames = listOf("decomposeConfigs", "childConfigs"),
                entryExpression = "${routeName}NavigationGraph.decomposeConfig",
                importSuffix = "navigation.${routeName}NavigationGraph",
                label = "Decompose"
            )
            NavigationType.APPYX -> planListRegistration(
                moduleRoot = moduleRoot,
                routeName = routeName,
                featurePackageName = featurePackageName,
                registryNames = listOf("appyxNodes", "nodes"),
                entryExpression = "${routeName}NavigationGraph.appyxNode",
                importSuffix = "navigation.${routeName}NavigationGraph",
                label = "Appyx"
            )
        }

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

    fun registerListEntry(
        content: String,
        registryNames: List<String>,
        entryExpression: String,
        entryImport: String
    ): String? {
        if (entryExpression in content) return null
        val lines = content.lines().toMutableList()
        val registryIndex = lines.indexOfFirst { line ->
            registryNames.any { name -> "$name =" in line || "val $name" in line || "var $name" in line } && "listOf(" in line
        }
        if (registryIndex < 0) return null
        val insertIndex = findListClose(lines, registryIndex) ?: return null
        val indent = lines[insertIndex].takeWhile { it.isWhitespace() } + "    "
        lines.add(insertIndex, "$indent$entryExpression,")
        return addImport(lines.joinToString("\n"), entryImport)
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

    private fun findListClose(lines: List<String>, startIndex: Int): Int? {
        var depth = 0
        for (index in startIndex until minOf(lines.size, startIndex + 40)) {
            val line = lines[index]
            depth += line.count { it == '(' }
            depth -= line.count { it == ')' }
            if (depth == 0 && index > startIndex) return index
        }
        return null
    }

    private fun planListRegistration(
        moduleRoot: Path,
        routeName: String,
        featurePackageName: String,
        registryNames: List<String>,
        entryExpression: String,
        importSuffix: String,
        label: String
    ): RegistrationPlan {
        if (!moduleRoot.exists()) {
            return todoPlan(routeName, "Module root does not exist yet.", label, entryExpression)
        }

        val candidates = kotlin.runCatching {
            Files.walk(moduleRoot).use { stream ->
                stream
                    .filter { it.isRegularFile() && it.fileName.toString().endsWith(".kt") }
                    .filter { path ->
                        val text = path.readTextSafely()
                        registryNames.any { "$it =" in text || "val $it" in text || "var $it" in text }
                    }
                    .toList()
            }
        }.getOrDefault(emptyList())

        candidates.forEach { candidate ->
            val updated = registerListEntry(
                candidate.readTextSafely(),
                registryNames,
                entryExpression,
                "$featurePackageName.$importSuffix"
            )
            if (updated != null) {
                return RegistrationPlan(
                    safeToApply = true,
                    targetFile = candidate.toString(),
                    diffPreview = "+ $entryExpression",
                    warnings = emptyList(),
                    replacementContent = updated
                )
            }
        }

        return todoPlan(routeName, "No $label registry list was safe to update.", label, entryExpression)
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

    private fun todoPlan(routeName: String, reason: String, label: String, entryExpression: String): RegistrationPlan =
        RegistrationPlan(
            safeToApply = false,
            targetFile = null,
            diffPreview = """
                // TODO Register this feature in your $label navigation registry.
                // Reason: $reason
                $entryExpression
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
