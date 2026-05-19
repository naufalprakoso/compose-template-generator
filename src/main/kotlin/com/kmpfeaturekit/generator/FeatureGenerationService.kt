package com.kmpfeaturekit.generator

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.kmpfeaturekit.model.DryRunPreview
import com.kmpfeaturekit.model.FeatureRequest
import com.kmpfeaturekit.model.GenerationResult
import com.kmpfeaturekit.model.PlannedFileKind
import com.kmpfeaturekit.templates.TemplateRenderService
import kotlin.io.path.Path
import kotlin.io.path.exists

@Service(Service.Level.PROJECT)
class FeatureGenerationService(private val project: Project) {
    fun preview(request: FeatureRequest): DryRunPreview {
        val templateService = project.service<TemplateRenderService>()
        val builder = FeaturePlanBuilder(templateService::render)
        val files = builder.build(request).map { file ->
            file.copy(conflict = file.kind != PlannedFileKind.MODIFY && Path(file.path).exists())
        }
        val warnings = buildList {
            files.filter { it.conflict }.forEach { add("Conflict: ${it.path}") }
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
}
