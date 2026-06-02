package com.kmpfeaturekit.utils

import java.nio.file.InvalidPathException
import java.nio.file.Path

object ValidationUtils {
    private val packageSegment = Regex("[A-Za-z_][A-Za-z0-9_]*")
    private val kotlinIdentifier = Regex("[A-Za-z_][A-Za-z0-9_]*")
    private val kotlinHardKeywords = setOf(
        "as",
        "break",
        "class",
        "continue",
        "do",
        "else",
        "false",
        "for",
        "fun",
        "if",
        "in",
        "interface",
        "is",
        "null",
        "object",
        "package",
        "return",
        "super",
        "this",
        "throw",
        "true",
        "try",
        "typealias",
        "typeof",
        "val",
        "var",
        "when",
        "while"
    )

    fun validateFeatureName(name: String): List<String> {
        val errors = mutableListOf<String>()
        if (name.isBlank()) errors += "Feature name is required."
        val names = NameVariants.from(name)
        if (names.pascalCase.isBlank()) errors += "Feature name must contain letters or digits."
        if (names.pascalCase.isNotBlank() && !kotlinIdentifier.matches(names.pascalCase)) {
            errors += "Feature name must start with a letter or underscore after normalization."
        }
        if (names.camelCase.isNotBlank() && !kotlinIdentifier.matches(names.camelCase)) {
            errors += "Feature name must produce a valid Kotlin property name."
        }
        if (names.pascalCase.lowercase() in kotlinHardKeywords || names.camelCase.lowercase() in kotlinHardKeywords) {
            errors += "Feature name cannot normalize to a Kotlin keyword."
        }
        if (name.any { it == '/' || it == '\\' || it == ':' }) errors += "Feature name contains invalid path characters."
        return errors
    }

    fun validatePackage(packageName: String): List<String> {
        if (packageName.isBlank()) return listOf("Base package is required.")
        val invalid = packageName.split('.').any { !packageSegment.matches(it) || it.lowercase() in kotlinHardKeywords }
        return if (invalid) listOf("Base package is not a valid Kotlin package.") else emptyList()
    }

    fun validateTargetModule(targetModule: String): List<String> =
        when {
            targetModule.isBlank() -> listOf("Target module is required.")
            targetModule.any { it == '\n' || it == '\r' } -> listOf("Target module must be a single line.")
            else -> emptyList()
        }

    fun validateSourceSetRoot(sourceSetRoot: String): List<String> {
        if (sourceSetRoot.isBlank()) return listOf("Source set root is required.")
        val path = try {
            Path.of(sourceSetRoot)
        } catch (_: InvalidPathException) {
            return listOf("Source set root is not a valid path.")
        }
        return buildList {
            if (!path.isAbsolute) add("Source set root must be an absolute path.")
            if (path.normalize().fileName?.toString() != "src") {
                add("Source set root must point to the module src directory.")
            }
        }
    }

    fun validatePlatformSelection(selectedCount: Int): List<String> =
        if (selectedCount <= 0) listOf("Select at least one target platform.") else emptyList()

    fun validateFeatureInputs(featureName: String, packageName: String): List<String> =
        validateFeatureName(featureName) + validatePackage(packageName)

    fun validateFeatureInputs(
        featureName: String,
        packageName: String,
        targetModule: String,
        sourceSetRoot: String,
        selectedPlatformCount: Int
    ): List<String> =
        validateFeatureName(featureName) +
            validatePackage(packageName) +
            validateTargetModule(targetModule) +
            validateSourceSetRoot(sourceSetRoot) +
            validatePlatformSelection(selectedPlatformCount)
}
