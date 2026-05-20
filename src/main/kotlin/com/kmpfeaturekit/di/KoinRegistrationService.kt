package com.kmpfeaturekit.di

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.kmpfeaturekit.utils.KotlinSourcePatcher
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
        return KotlinSourcePatcher.appendArgumentToCall(
            content = content,
            callName = "modules",
            argument = featureModuleName,
            importFqName = featureModuleImport
        )
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

    private fun Path.readTextSafely(): String =
        runCatching { takeIf { Files.size(it) < 200_000 }?.readText() }.getOrNull().orEmpty()
}

object KotlinInjectRegistrationPlanner {
    fun plan(moduleRoot: Path, moduleTypeName: String, moduleImport: String): RegistrationPlan {
        if (!moduleRoot.exists()) return todoPlan(moduleTypeName, "Module root does not exist yet.")

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
            val updated = registerModule(candidate.readTextSafely(), moduleTypeName, moduleImport)
            if (updated != null) {
                return RegistrationPlan(
                    safeToApply = true,
                    targetFile = candidate.toString(),
                    diffPreview = "+ : $moduleTypeName",
                    warnings = emptyList(),
                    replacementContent = updated
                )
            }
        }

        return todoPlan(moduleTypeName, "No Kotlin Inject @Component was safe to update.")
    }

    fun registerModule(content: String, moduleTypeName: String, moduleImport: String): String? =
        KotlinSourcePatcher.addSuperTypeToAnnotatedDeclaration(
            content = content,
            annotationName = "Component",
            superTypeName = moduleTypeName,
            importFqName = moduleImport
        )

    fun registerDependency(content: String, dependencyName: String, dependencyImport: String): String? {
        return KotlinSourcePatcher.insertInsideAnnotatedDeclaration(
            content = content,
            annotationName = "Component",
            memberLine = "val $dependencyName: ${dependencyName.replaceFirstChar(Char::uppercase)}",
            importFqName = dependencyImport
        )
    }

    private fun todoPlan(moduleTypeName: String, reason: String): RegistrationPlan =
        RegistrationPlan(
            safeToApply = false,
            targetFile = null,
            diffPreview = """
                // TODO Add this generated module to your Kotlin Inject @Component supertypes.
                // Reason: $reason
                : $moduleTypeName
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

        return KotlinSourcePatcher.addImport(content.replaceRange(moduleAnnotation.range, replacement), moduleImport)
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

private fun Path.readTextSafely(): String =
    runCatching { takeIf { Files.size(it) < 200_000 }?.readText() }.getOrNull().orEmpty()
