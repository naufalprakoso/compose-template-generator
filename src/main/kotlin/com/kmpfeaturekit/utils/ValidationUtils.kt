package com.kmpfeaturekit.utils

object ValidationUtils {
    private val packageSegment = Regex("[A-Za-z_][A-Za-z0-9_]*")

    fun validateFeatureName(name: String): List<String> {
        val errors = mutableListOf<String>()
        if (name.isBlank()) errors += "Feature name is required."
        if (NameVariants.from(name).pascalCase.isBlank()) errors += "Feature name must contain letters or digits."
        if (name.any { it == '/' || it == '\\' || it == ':' }) errors += "Feature name contains invalid path characters."
        return errors
    }

    fun validatePackage(packageName: String): List<String> {
        if (packageName.isBlank()) return listOf("Base package is required.")
        val invalid = packageName.split('.').any { !packageSegment.matches(it) }
        return if (invalid) listOf("Base package is not a valid Kotlin package.") else emptyList()
    }

    fun validateFeatureInputs(featureName: String, packageName: String): List<String> =
        validateFeatureName(featureName) + validatePackage(packageName)
}
