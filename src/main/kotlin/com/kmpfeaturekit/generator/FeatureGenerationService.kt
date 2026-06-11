package com.kmpfeaturekit.generator

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.kmpfeaturekit.model.DryRunPreview
import com.kmpfeaturekit.model.FeatureRequest
import com.kmpfeaturekit.model.GenerationResult
import com.kmpfeaturekit.model.PlannedFileKind
import com.kmpfeaturekit.templates.TemplateRenderService
import com.kmpfeaturekit.utils.ValidationUtils
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.readText

@Service(Service.Level.PROJECT)
class FeatureGenerationService(private val project: Project) {
    fun preview(request: FeatureRequest): DryRunPreview {
        validateRequest(request)
        val templateService = project.service<TemplateRenderService>()
        val builder = FeaturePlanBuilder(templateService::render)
        val files = builder.build(request).map { file ->
            val path = Path(file.path)
            val existingContent = path.takeIf { it.exists() && file.kind == PlannedFileKind.MODIFY }
                ?.runCatching { readText() }
                ?.getOrNull()
            file.copy(
                conflict = file.kind != PlannedFileKind.MODIFY && path.exists(),
                existingContent = existingContent,
                diffPreview = existingContent?.let { UnifiedDiff.render(file.path, it, file.content) }
            )
        }
        val warnings = buildList {
            files.filter { it.conflict }.forEach { add("Conflict: ${it.path}") }
            files.filter { it.kind == PlannedFileKind.MODIFY && it.replacesFile }.forEach {
                add("Will update existing file: ${it.path}")
            }
            addAll(GeneratedCodeValidator.validate(files))
            files.filter { it.path.endsWith(".todo.md") }.forEach {
                val reason = it.content.lineSequence()
                    .firstOrNull { line -> line.trim().startsWith("Reason:") || line.trim().startsWith("// Reason:") }
                    ?.trim()
                    ?.removePrefix("// ")
                add("Manual review needed for ${it.path.substringAfterLast('/')}${reason?.let { detail -> ": $detail" }.orEmpty()}")
            }
        }
        return DryRunPreview(
            filesToCreate = files.filter { it.kind != PlannedFileKind.MODIFY },
            filesToModify = files.filter { it.kind == PlannedFileKind.MODIFY },
            warnings = warnings,
            tree = files.joinToString("\n") { "${if (it.conflict) "!" else "+"} ${it.path}" }
        )
    }

    fun generate(request: FeatureRequest, overwrite: Boolean = false): GenerationResult {
        val preview = preview(request)
        return project.service<FileWriteService>().write(preview.filesToCreate + preview.filesToModify, overwrite)
    }

    fun generate(files: List<com.kmpfeaturekit.model.PlannedFile>, overwrite: Boolean = false): GenerationResult =
        project.service<FileWriteService>().write(files, overwrite)

    private fun validateRequest(request: FeatureRequest) {
        val errors = ValidationUtils.validateFeatureInputs(
            featureName = request.info.featureName,
            packageName = request.info.basePackage,
            targetModule = request.info.targetModule,
            sourceSetRoot = request.info.sourceSetRoot,
            selectedPlatformCount = request.architecture.platforms.size
        )
        require(errors.isEmpty()) {
            errors.joinToString(separator = " ")
        }
    }
}

object GeneratedCodeValidator {
    fun validate(files: List<com.kmpfeaturekit.model.PlannedFile>): List<String> {
        val kotlinFiles = files.filter { it.path.endsWith(".kt") }
        val generatedClasses = kotlinFiles
            .flatMap { file -> classNames(file.content).map { it to file.path } }
            .toMap()
        val featurePrefixes = kotlinFiles
            .mapNotNull { file -> featurePrefix(file.path.substringAfterLast('/').removeSuffix(".kt")) }
            .toSet()
        return buildList {
            kotlinFiles.forEach { file ->
                if ("{{" in file.content || "}}" in file.content) {
                    add("Generated placeholder remains in ${file.path.substringAfterLast('/')}")
                }
                packageName(file.content)?.let { declaredPackage ->
                    if (!file.path.normalizedPath().contains(declaredPackage.replace('.', '/'))) {
                        add("Package `$declaredPackage` does not match path ${file.path}")
                    }
                }
                referencedGeneratedTypes(file.content, generatedClasses.keys).forEach { typeName ->
                    val referencedPath = generatedClasses[typeName]
                    val referencedPackage = referencedPath?.let(::packageNameForPath)
                    if (referencedPath == file.path) {
                        return@forEach
                    } else if (referencedPath == null) {
                        add("Generated reference `$typeName` in ${file.path.substringAfterLast('/')} has no planned file")
                    } else if (referencedPackage != null &&
                        packageName(file.content) != referencedPackage &&
                        "import $referencedPackage.$typeName" !in file.content
                    ) {
                        add("Generated reference `$typeName` in ${file.path.substringAfterLast('/')} is missing an import")
                    }
                }
                missingGeneratedTypeReferences(file.content, generatedClasses.keys, featurePrefixes).forEach { typeName ->
                    add("Generated reference `$typeName` in ${file.path.substringAfterLast('/')} has no planned file")
                }
            }
        }.distinct()
    }

    private fun classNames(content: String): List<String> =
        Regex("""\b(?:class|interface|object)\s+([A-Z][A-Za-z0-9_]*)""")
            .findAll(content)
            .map { it.groupValues[1] }
            .toList()

    private fun packageName(content: String): String? =
        Regex("""(?m)^\s*package\s+([A-Za-z_][A-Za-z0-9_.]*)""")
            .find(content)
            ?.groupValues
            ?.get(1)

    private fun packageNameForPath(path: String): String? {
        val normalized = path.normalizedPath()
        val sourceRoot = listOf("/commonMain/kotlin/", "/commonTest/kotlin/", "/androidMain/kotlin/", "/iosMain/kotlin/")
            .firstOrNull { it in normalized }
            ?: return null
        return normalized
            .substringAfter(sourceRoot)
            .substringBeforeLast('/', missingDelimiterValue = "")
            .takeIf { it.isNotBlank() }
            ?.replace('/', '.')
    }

    private fun referencedGeneratedTypes(content: String, generatedTypes: Set<String>): Set<String> =
        generatedTypes
            .filter { typeName -> Regex("""\b$typeName\b""").containsMatchIn(content) }
            .toSet()

    private fun missingGeneratedTypeReferences(
        content: String,
        generatedTypes: Set<String>,
        featurePrefixes: Set<String>
    ): Set<String> {
        val generatedSuffixes = setOf(
            "Action",
            "Effect",
            "Graph",
            "Item",
            "Module",
            "Presenter",
            "Repository",
            "RepositoryImpl",
            "Route",
            "Screen",
            "Service",
            "State",
            "StateHolder",
            "UseCase",
            "ViewModel"
        )
        return Regex("""\b([A-Z][A-Za-z0-9_]*)\b""")
            .findAll(content)
            .map { it.groupValues[1] }
            .filter { typeName -> typeName !in generatedTypes }
            .filter { typeName -> featurePrefixes.any { prefix -> typeName.startsWith(prefix) } }
            .filter { typeName -> generatedSuffixes.any { suffix -> typeName.endsWith(suffix) } }
            .toSet()
    }

    private fun featurePrefix(fileName: String): String? {
        val generatedSuffixes = listOf(
            "RepositoryImpl",
            "NavigationGraph",
            "PlatformContext",
            "StateHolder",
            "ViewModel",
            "Presenter",
            "Component",
            "UseCase",
            "Service",
            "Repository",
            "Screen",
            "Preview",
            "State",
            "Action",
            "Effect",
            "Graph",
            "Module",
            "Route",
            "Item"
        )
        return generatedSuffixes
            .firstOrNull { fileName.endsWith(it) }
            ?.let { suffix -> fileName.removeSuffix(suffix).takeIf { it.isNotBlank() } }
    }

    private fun String.normalizedPath(): String = replace('\\', '/')
}

object UnifiedDiff {
    fun render(path: String, oldContent: String, newContent: String): String {
        if (oldContent == newContent) return "No textual changes for $path"
        val oldLines = oldContent.lines()
        val newLines = newContent.lines()
        val table = Array(oldLines.size + 1) { IntArray(newLines.size + 1) }
        for (oldIndex in oldLines.indices.reversed()) {
            for (newIndex in newLines.indices.reversed()) {
                table[oldIndex][newIndex] = if (oldLines[oldIndex] == newLines[newIndex]) {
                    table[oldIndex + 1][newIndex + 1] + 1
                } else {
                    maxOf(table[oldIndex + 1][newIndex], table[oldIndex][newIndex + 1])
                }
            }
        }

        val changes = mutableListOf<DiffLine>()
        var oldIndex = 0
        var newIndex = 0
        while (oldIndex < oldLines.size && newIndex < newLines.size) {
            when {
                oldLines[oldIndex] == newLines[newIndex] -> {
                    changes += DiffLine(' ', oldLines[oldIndex])
                    oldIndex++
                    newIndex++
                }
                table[oldIndex + 1][newIndex] >= table[oldIndex][newIndex + 1] -> {
                    changes += DiffLine('-', oldLines[oldIndex])
                    oldIndex++
                }
                else -> {
                    changes += DiffLine('+', newLines[newIndex])
                    newIndex++
                }
            }
        }
        while (oldIndex < oldLines.size) changes += DiffLine('-', oldLines[oldIndex++])
        while (newIndex < newLines.size) changes += DiffLine('+', newLines[newIndex++])

        return buildString {
            appendLine("--- $path")
            appendLine("+++ $path")
            appendLine("@@")
            compact(changes).forEach { line ->
                append(line.prefix).append(line.text).append('\n')
            }
        }.trimEnd()
    }

    private fun compact(lines: List<DiffLine>, contextSize: Int = 3): List<DiffLine> {
        val changedIndexes = lines.indices.filter { lines[it].prefix != ' ' }
        if (changedIndexes.isEmpty()) return emptyList()
        val included = mutableSetOf<Int>()
        changedIndexes.forEach { index ->
            ((index - contextSize)..(index + contextSize))
                .filter { it in lines.indices }
                .forEach { included += it }
        }
        val compacted = mutableListOf<DiffLine>()
        var previous = -1
        included.sorted().forEach { index ->
            if (previous >= 0 && index > previous + 1) compacted += DiffLine(' ', "...")
            compacted += lines[index]
            previous = index
        }
        return compacted
    }

    private data class DiffLine(val prefix: Char, val text: String)
}
