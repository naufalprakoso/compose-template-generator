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
    val warnings: List<String>,
    val catalogPatch: VersionCatalogPatch? = null
)

data class VersionCatalogPatch(
    val path: String,
    val content: String,
    val replacesFile: Boolean
)

object GradleBuildPatchPlanner {
    fun plan(buildFile: Path, request: FeatureRequest, fallbackPatch: String): GradleBuildPatch {
        if (!buildFile.exists() || buildFile.name != "build.gradle.kts") {
            return GradleBuildPatch(fallbackPatch, replacesFile = false, warnings = emptyList())
        }

        val catalog = findVersionCatalog(buildFile.parent)
        val aliases = catalog?.let { parseAliases(it.readText()) }.orEmpty()
        val dependencies = dependencyAliases(request)
        val catalogPatch = catalog?.let { catalogPath ->
            val missingAliases = dependencies.filterNot { it.alias in aliases }
            if (missingAliases.isEmpty()) {
                null
            } else {
                VersionCatalogPatch(
                    path = catalogPath.toString(),
                    content = insertLibraryAliases(catalogPath.readText(), missingAliases),
                    replacesFile = true
                )
            }
        }
        val availableAliases = aliases + (catalogPatch?.let { dependencies.map { it.alias } }.orEmpty())
        val dependencyLines = dependencies
            .filter { it.alias in availableAliases }
            .map { "implementation(libs.${it.alias.replace("-", ".")})" }
            .distinct()

        if (dependencyLines.isEmpty()) {
            return GradleBuildPatch(
                content = fallbackPatch,
                replacesFile = false,
                warnings = listOf("No version catalog aliases were available for generated dependencies.")
            )
        }

        val existing = buildFile.readText()
        val updated = insertCommonMainDependencies(existing, dependencyLines)
            ?: return GradleBuildPatch(
                content = fallbackPatch,
                replacesFile = false,
                warnings = listOf("No commonMain.dependencies block was safe to update."),
                catalogPatch = catalogPatch
            )

        return GradleBuildPatch(updated, replacesFile = true, warnings = emptyList(), catalogPatch = catalogPatch)
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
            DependencyInjectionType.KOIN -> add(DependencyAlias("koin-core", "io.insert-koin:koin-core", "4.1.0"))
            DependencyInjectionType.KOTLIN_INJECT -> add(DependencyAlias("kotlin-inject-runtime", "me.tatarka.inject:kotlin-inject-runtime", "0.8.0"))
            DependencyInjectionType.HILT_ANDROID_ONLY,
            DependencyInjectionType.MANUAL -> Unit
        }
        when (request.architecture.navigationType) {
            NavigationType.NAVIGATION_COMPOSE -> add(DependencyAlias("androidx-navigation-compose", "androidx.navigation:navigation-compose", "2.9.0"))
            NavigationType.VOYAGER -> add(DependencyAlias("voyager-navigator", "cafe.adriel.voyager:voyager-navigator", "1.1.0-beta03"))
            NavigationType.CIRCUIT_NAVIGATION -> add(DependencyAlias("circuit-foundation", "com.slack.circuit:circuit-foundation", "0.27.0"))
            NavigationType.DECOMPOSE_NAVIGATION -> add(DependencyAlias("decompose", "com.arkivanov.decompose:decompose", "3.3.0"))
            NavigationType.APPYX -> add(DependencyAlias("appyx-navigation", "com.bumble.appyx:appyx-navigation", "2.0.0"))
        }
        when (request.architecture.networkingType) {
            NetworkingType.KTOR -> add(DependencyAlias("ktor-client-core", "io.ktor:ktor-client-core", "3.1.3"))
            NetworkingType.APOLLO -> add(DependencyAlias("apollo-runtime", "com.apollographql.apollo:apollo-runtime", "4.2.0"))
            NetworkingType.RETROFIT_ANDROID_ONLY,
            NetworkingType.NONE -> Unit
        }
        when (request.architecture.persistenceType) {
            PersistenceType.SQLDELIGHT -> add(DependencyAlias("sqldelight-runtime", "app.cash.sqldelight:runtime", "2.1.0"))
            PersistenceType.DATASTORE -> add(DependencyAlias("androidx-datastore-preferences", "androidx.datastore:datastore-preferences", "1.1.7"))
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

    fun insertLibraryAliases(catalog: String, dependencies: List<DependencyAlias>): String {
        if (dependencies.isEmpty()) return catalog

        val lines = catalog.lines().toMutableList()
        val librariesIndex = lines.indexOfFirst { it.trim() == "[libraries]" }
        val entries = dependencies
            .distinctBy { it.alias }
            .filterNot { dependency -> catalog.lineSequence().any { it.trim().startsWith("${dependency.alias} ") || it.trim().startsWith("${dependency.alias}=") } }
            .map { dependency -> "${dependency.alias} = { module = \"${dependency.module}\", version = \"${dependency.version}\" }" }
        if (entries.isEmpty()) return catalog

        if (librariesIndex < 0) {
            if (lines.isNotEmpty() && lines.last().isNotBlank()) lines.add("")
            lines.add("[libraries]")
            lines.addAll(entries)
            return lines.joinToString("\n")
        }

        val insertIndex = ((librariesIndex + 1) until lines.size)
            .firstOrNull { lines[it].trim().startsWith("[") }
            ?: lines.size
        lines.addAll(insertIndex, entries)
        return lines.joinToString("\n")
    }

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

    data class DependencyAlias(val alias: String, val module: String, val version: String)
}
