package com.kmpfeaturekit.templates

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
class TemplateRenderService(@Suppress("UNUSED_PARAMETER") private val project: Project) {
    fun render(template: String, variables: Map<String, String>): String =
        PureTemplateRenderer.render(template, variables)
}

object PureTemplateRenderer {
    fun render(template: String, variables: Map<String, String>): String =
        variables.entries.fold(template) { result, (key, value) ->
            result.replace("{{${key}}}", value)
        }
}
