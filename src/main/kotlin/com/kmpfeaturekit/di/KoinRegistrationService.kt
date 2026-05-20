package com.kmpfeaturekit.di

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText

data class RegistrationPlan(
    val safeToApply: Boolean,
    val targetFile: String?,
    val diffPreview: String,
    val warnings: List<String>,
    val replacementContent: String? = null
)

@Service(Service.Level.PROJECT)
class KoinRegistrationService(@Suppress("UNUSED_PARAMETER") private val project: Project) {
    fun planRegistration(featureModuleName: String, candidateFiles: List<String>): RegistrationPlan {
        val target = candidateFiles.firstOrNull { it.endsWith("Module.kt") || it.contains("di", ignoreCase = true) }
        return if (target == null) {
            RegistrationPlan(false, null, "modules($featureModuleName)", listOf("No obvious Koin composition root found."))
        } else {
            RegistrationPlan(true, target, "+ modules($featureModuleName)", emptyList())
        }
    }
}

object KoinRegistrationPlanner {
    fun plan(moduleRoot: Path, featureModuleName: String, featureModuleImport: String): RegistrationPlan {
        if (!moduleRoot.exists()) {
            return todoPlan(featureModuleName, "Module root does not exist yet.")
        }

        val candidates = kotlin.runCatching {
            Files.walk(moduleRoot).use { stream ->
                stream
                    .filter { it.isRegularFile() && it.fileName.toString().endsWith(".kt") }
                    .filter { path ->
                        val text = path.readTextSafely()
                        "modules(" in text || "startKoin" in text
                    }
                    .sorted(compareByDescending<Path> { path -> "startKoin" in path.readTextSafely() })
                    .toList()
            }
        }.getOrDefault(emptyList())

        candidates.forEach { candidate ->
            val existing = candidate.readTextSafely()
            val updated = registerModule(existing, featureModuleName, featureModuleImport)
            if (updated != null) {
                return RegistrationPlan(
                    safeToApply = true,
                    targetFile = candidate.toString(),
                    diffPreview = "+ modules(..., $featureModuleName)",
                    warnings = emptyList(),
                    replacementContent = updated
                )
            }
        }

        return todoPlan(featureModuleName, "No Koin modules(...) call was safe to update.")
    }

    fun registerModule(content: String, featureModuleName: String, featureModuleImport: String): String? {
        if (featureModuleName in content) return null

        val listOfRegex = Regex("""modules\(\s*listOf\(([^)]*)\)\s*\)""", RegexOption.DOT_MATCHES_ALL)
        val listMatch = listOfRegex.find(content)
        if (listMatch != null) {
            val existingModules = listMatch.groupValues[1].trim()
            val replacement = if (existingModules.isBlank()) {
                "modules(listOf($featureModuleName))"
            } else {
                "modules(listOf($existingModules, $featureModuleName))"
            }
            return addImport(content.replaceRange(listMatch.range, replacement), featureModuleImport)
        }

        val modulesRegex = Regex("""modules\(([^)]*)\)""", RegexOption.DOT_MATCHES_ALL)
        val modulesMatch = modulesRegex.find(content) ?: return null
        val existingModules = modulesMatch.groupValues[1].trim()
        if ('\n' in existingModules || "{" in existingModules) return null

        val replacement = if (existingModules.isBlank()) {
            "modules($featureModuleName)"
        } else {
            "modules($existingModules, $featureModuleName)"
        }
        return addImport(content.replaceRange(modulesMatch.range, replacement), featureModuleImport)
    }

    private fun todoPlan(featureModuleName: String, reason: String): RegistrationPlan =
        RegistrationPlan(
            safeToApply = false,
            targetFile = null,
            diffPreview = """
                // TODO Register this feature module in your Koin composition root.
                // Reason: $reason
                modules($featureModuleName)
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

object KotlinInjectRegistrationPlanner {
    fun plan(moduleRoot: Path, dependencyName: String, dependencyImport: String): RegistrationPlan {
        if (!moduleRoot.exists()) return todoPlan(dependencyName, "Module root does not exist yet.")

        val candidates = kotlin.runCatching {
            Files.walk(moduleRoot).use { stream ->
                stream
                    .filter { it.isRegularFile() && it.fileName.toString().endsWith(".kt") }
                    .filter { path ->
                        val text = path.readTextSafely()
                        "@Component" in text || "me.tatarka.inject.annotations.Component" in text
                    }
                    .toList()
            }
        }.getOrDefault(emptyList())

        candidates.forEach { candidate ->
            val updated = registerDependency(candidate.readTextSafely(), dependencyName, dependencyImport)
            if (updated != null) {
                return RegistrationPlan(
                    safeToApply = true,
                    targetFile = candidate.toString(),
                    diffPreview = "+ val $dependencyName",
                    warnings = emptyList(),
                    replacementContent = updated
                )
            }
        }

        return todoPlan(dependencyName, "No Kotlin Inject @Component was safe to update.")
    }

    fun registerDependency(content: String, dependencyName: String, dependencyImport: String): String? {
        if (dependencyName in content) return null
        val componentLine = content.lines().indexOfFirst { "@Component" in it }
        if (componentLine < 0) return null

        val lines = content.lines().toMutableList()
        val declarationIndex = ((componentLine + 1) until lines.size).firstOrNull { index ->
            val line = lines[index]
            "interface " in line || "abstract class " in line
        } ?: return null
        val insertAfter = findOpeningBrace(lines, declarationIndex) ?: return null
        val indent = lines[insertAfter].takeWhile { it.isWhitespace() } + "    "
        lines.add(insertAfter + 1, "${indent}val $dependencyName: ${dependencyName.replaceFirstChar(Char::uppercase)}")

        return addImport(lines.joinToString("\n"), dependencyImport)
    }

    private fun todoPlan(dependencyName: String, reason: String): RegistrationPlan =
        RegistrationPlan(
            safeToApply = false,
            targetFile = null,
            diffPreview = """
                // TODO Expose this dependency from your Kotlin Inject @Component.
                // Reason: $reason
                val $dependencyName: ${dependencyName.replaceFirstChar(Char::uppercase)}
            """.trimIndent(),
            warnings = listOf(reason)
        )
}

object HiltRegistrationPlanner {
    fun plan(moduleRoot: Path, moduleName: String, moduleImport: String): RegistrationPlan {
        if (!moduleRoot.exists()) return todoPlan(moduleName, "Module root does not exist yet.")

        val candidates = kotlin.runCatching {
            Files.walk(moduleRoot).use { stream ->
                stream
                    .filter { it.isRegularFile() && it.fileName.toString().endsWith(".kt") }
                    .filter { path ->
                        val text = path.readTextSafely()
                        "@Module" in text && "@InstallIn" in text
                    }
                    .toList()
            }
        }.getOrDefault(emptyList())

        candidates.forEach { candidate ->
            val updated = includeModule(candidate.readTextSafely(), moduleName, moduleImport)
            if (updated != null) {
                return RegistrationPlan(
                    safeToApply = true,
                    targetFile = candidate.toString(),
                    diffPreview = "+ @Module(includes = [$moduleName::class])",
                    warnings = emptyList(),
                    replacementContent = updated
                )
            }
        }

        return todoPlan(moduleName, "No Hilt @Module/@InstallIn file was safe to update.")
    }

    fun includeModule(content: String, moduleName: String, moduleImport: String): String? {
        if ("$moduleName::class" in content) return null
        val moduleAnnotation = Regex("""@Module(\(([^)]*)\))?""").find(content) ?: return null
        val currentArgs = moduleAnnotation.groupValues.getOrNull(2).orEmpty().trim()
        val replacement = when {
            currentArgs.isBlank() -> "@Module(includes = [$moduleName::class])"
            "includes" in currentArgs -> {
                val includesRegex = Regex("""includes\s*=\s*\[([^]]*)]""")
                val match = includesRegex.find(currentArgs) ?: return null
                val existing = match.groupValues[1].trim()
                val newIncludes = if (existing.isBlank()) "$moduleName::class" else "$existing, $moduleName::class"
                "@Module(${currentArgs.replaceRange(match.range, "includes = [$newIncludes]")})"
            }
            else -> "@Module($currentArgs, includes = [$moduleName::class])"
        }

        return addImport(content.replaceRange(moduleAnnotation.range, replacement), moduleImport)
    }

    private fun todoPlan(moduleName: String, reason: String): RegistrationPlan =
        RegistrationPlan(
            safeToApply = false,
            targetFile = null,
            diffPreview = """
                // TODO Include this generated Hilt module from an existing @Module if your project requires an aggregate module.
                // Reason: $reason
                @Module(includes = [$moduleName::class])
            """.trimIndent(),
            warnings = listOf(reason)
        )
}

private fun findOpeningBrace(lines: List<String>, declarationIndex: Int): Int? {
    for (index in declarationIndex until minOf(lines.size, declarationIndex + 8)) {
        if ("{" in lines[index]) return index
    }
    return null
}

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
