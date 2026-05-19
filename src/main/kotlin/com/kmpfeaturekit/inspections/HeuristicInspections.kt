package com.kmpfeaturekit.inspections

object HeuristicInspections {
    private val androidOnlyTokens = listOf(
        "android.content.",
        "android.app.",
        "android.os.Bundle",
        "androidx.lifecycle.ViewModel",
        "androidx.navigation.compose"
    )

    fun androidOnlyApiProblem(path: String, text: String): Boolean =
        path.contains("commonMain") && androidOnlyTokens.any { it in text }

    fun missingPreviewProblem(path: String, text: String): Boolean =
        path.contains("commonMain") && path.endsWith("Screen.kt") && "@Composable" in text && "@Preview" !in text

    fun missingTestProblem(path: String, projectFiles: Collection<String>): Boolean {
        if (!path.contains("commonMain") || !path.endsWith(".kt")) return false
        val base = path.substringAfterLast('/').removeSuffix(".kt")
        return projectFiles.none { it.contains("commonTest") && it.endsWith("${base}Test.kt") }
    }

    fun suspiciousArchitectureProblem(path: String): Boolean =
        (path.contains("/presentation/") && path.contains("Repository")) ||
            (path.contains("/data/") && path.contains("Screen"))
}
