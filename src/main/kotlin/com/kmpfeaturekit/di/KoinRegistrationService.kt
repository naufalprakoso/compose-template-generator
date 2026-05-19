package com.kmpfeaturekit.di

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

data class RegistrationPlan(val safeToApply: Boolean, val targetFile: String?, val diffPreview: String, val warnings: List<String>)

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
