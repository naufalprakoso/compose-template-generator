package com.kmpfeaturekit.generator

import com.kmpfeaturekit.model.DependencyInjectionType
import com.kmpfeaturekit.model.FeatureRequest
import com.kmpfeaturekit.model.NavigationType
import com.kmpfeaturekit.model.NetworkingType
import com.kmpfeaturekit.model.PersistenceType
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.readText

data class GradleBuildPatch(
    val content: String,
    val replacesFile: Boolean,
    val warnings: List<String>
)

object GradleBuildPatchPlanner {
    fun plan(buildFile: Path, request: FeatureRequest, fallbackPatch: String): GradleBuildPatch {
        if (!buildFile.exists() || buildFile.name != "build.gradle.kts") {
            return GradleBuildPatch(fallbackPatch, replacesFile = false, warnings = emptyList())
        }

        val catalog = findVersionCatalog(buildFile.parent)
        val aliases = catalog?.let { parseAliases(it.readText()) }.orEmpty()
        val dependencyLines = dependencyAliases(request)
            .filter { it.alias in aliases }
            .map { "implementation(libs.${it.alias.replace("-", ".")})" }
            .distinct()

        if (dependencyLines.isEmpty()) {
            return GradleBuildPatch(
                content = fallbackPatch,
                replacesFile = false,
                warnings = listOf("No matching version catalog aliases were found for generated dependencies.")
            )
        }

        val existing = buildFile.readText()
        val updated = insertCommonMainDependencies(existing, dependencyLines)
            ?: return GradleBuildPatch(
                content = fallbackPatch,
                replacesFile = false,
                warnings = listOf("No commonMain.dependencies block was safe to update.")
            )

        return GradleBuildPatch(updated, replacesFile = true, warnings = emptyList())
    }

    fun insertCommonMainDependencies(content: String, dependencyLines: List<String>): String? {
        val lines = content.lines().toMutableList()
        val commonMainIndex = lines.indexOfFirst { "commonMain.dependencies" in it }
        if (commonMainIndex >= 0) {
            val insertIndex = findBlockClose(lines, commonMainIndex) ?: return null
            val indent = lines[insertIndex].takeWhile { it.isWhitespace() } + "    "
            val missing = dependencyLines.filterNot { line -> line in content }
            if (missing.isEmpty()) return null
            lines.addAll(insertIndex, missing.map { "$indent$it" })
            return lines.joinToString("\n")
        }

        val commonMainBlockIndex = lines.indexOfFirst { it.trim().startsWith("commonMain") && "{" in it }
        if (commonMainBlockIndex < 0) return null
        val commonMainClose = findBlockClose(lines, commonMainBlockIndex) ?: return null
        val indent = lines[commonMainBlockIndex].takeWhile { it.isWhitespace() } + "    "
        val missing = dependencyLines.filterNot { line -> line in content }
        if (missing.isEmpty()) return null
        lines.addAll(
            commonMainClose,
            listOf("${indent}dependencies {") + missing.map { "$indent    $it" } + listOf("$indent}")
        )
        return lines.joinToString("\n")
    }

    private fun dependencyAliases(request: FeatureRequest): List<DependencyAlias> = buildList {
        when (request.architecture.dependencyInjectionType) {
            DependencyInjectionType.KOIN -> add(DependencyAlias("koin-core"))
            DependencyInjectionType.KOTLIN_INJECT -> add(DependencyAlias("kotlin-inject-runtime"))
            DependencyInjectionType.HILT_ANDROID_ONLY,
            DependencyInjectionType.MANUAL -> Unit
        }
        when (request.architecture.navigationType) {
            NavigationType.NAVIGATION_COMPOSE -> add(DependencyAlias("androidx-navigation-compose"))
            NavigationType.VOYAGER -> add(DependencyAlias("voyager-navigator"))
            NavigationType.CIRCUIT_NAVIGATION -> add(DependencyAlias("circuit-foundation"))
            NavigationType.DECOMPOSE_NAVIGATION -> add(DependencyAlias("decompose"))
            NavigationType.APPYX -> add(DependencyAlias("appyx-navigation"))
        }
        when (request.architecture.networkingType) {
            NetworkingType.KTOR -> add(DependencyAlias("ktor-client-core"))
            NetworkingType.APOLLO -> add(DependencyAlias("apollo-runtime"))
            NetworkingType.RETROFIT_ANDROID_ONLY,
            NetworkingType.NONE -> Unit
        }
        when (request.architecture.persistenceType) {
            PersistenceType.SQLDELIGHT -> add(DependencyAlias("sqldelight-runtime"))
            PersistenceType.DATASTORE -> add(DependencyAlias("androidx-datastore-preferences"))
            PersistenceType.ROOM_ANDROID_ONLY,
            PersistenceType.NONE -> Unit
        }
    }

    private fun findVersionCatalog(start: Path): Path? {
        var current: Path? = start
        repeat(6) {
            val catalog = current?.resolve("gradle")?.resolve("libs.versions.toml")
            if (catalog != null && catalog.exists()) return catalog
            current = current?.parent
        }
        return null
    }

    private fun parseAliases(catalog: String): Set<String> =
        catalog.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("#") && "=" in it }
            .map { it.substringBefore("=").trim() }
            .toSet()

    private fun findBlockClose(lines: List<String>, startIndex: Int): Int? {
        var depth = 0
        for (index in startIndex until lines.size) {
            val line = lines[index]
            depth += line.count { it == '{' }
            depth -= line.count { it == '}' }
            if (depth == 0 && index > startIndex) return index
        }
        return null
    }

    private data class DependencyAlias(val alias: String)
}
