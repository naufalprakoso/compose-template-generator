package com.kmpfeaturekit.navigation

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.kmpfeaturekit.di.RegistrationPlan
import com.kmpfeaturekit.model.NavigationType

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
