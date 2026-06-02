package com.kmpfeaturekit.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.kmpfeaturekit.dialogs.FeatureDialogDefaultsResolver
import com.kmpfeaturekit.dialogs.KmpFeatureWizardDialog
import com.kmpfeaturekit.generator.FeatureGenerationService
import com.kmpfeaturekit.model.GenerationResult
import com.kmpfeaturekit.notifications.KmpFeatureKitNotifier
import java.nio.file.Path

class NewKmpFeatureAction : DumbAwareAction("Compose Feature") {
    override fun update(event: AnActionEvent) {
        event.presentation.isEnabledAndVisible = event.project != null
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val dialog = KmpFeatureWizardDialog(project, FeatureDialogDefaultsResolver.resolve(event, project))
        if (dialog.showAndGet()) {
            runCatching {
                val result = project.service<FeatureGenerationService>().generate(dialog.selectedFiles(), overwrite = false)
                val summary = buildGenerationSummary(result)
                if (result.writtenFiles.isNotEmpty()) {
                    openFirstGeneratedFile(project, result.writtenFiles)
                    KmpFeatureKitNotifier.info(project, "Compose feature generated", summary)
                } else {
                    KmpFeatureKitNotifier.warning(project, "No files generated", summary)
                }
            }.onFailure { error ->
                KmpFeatureKitNotifier.error(
                    project,
                    "Compose feature generation failed",
                    error.message ?: "Check the IDE log for details."
                )
            }
        }
    }

    private fun openFirstGeneratedFile(project: Project, paths: List<String>) {
        val first = paths.firstNotNullOfOrNull { path ->
            LocalFileSystem.getInstance().refreshAndFindFileByNioFile(Path.of(path))
        } ?: return
        OpenFileDescriptor(project, first).navigate(true)
    }

    private fun buildGenerationSummary(result: GenerationResult): String =
        buildString {
            append("<html><body>")
            append("Created or updated <b>${result.writtenFiles.size}</b> file(s).")
            if (result.skippedFiles.isNotEmpty()) {
                append("<br/>Skipped <b>${result.skippedFiles.size}</b> existing file(s).")
            }
            append(fileList("Written", result.writtenFiles))
            append(fileList("Skipped", result.skippedFiles))
            if (result.warnings.isNotEmpty()) {
                append("<br/><b>Warnings</b><ul>")
                result.warnings.take(5).forEach { warning ->
                    append("<li>${escapeHtml(warning)}</li>")
                }
                if (result.warnings.size > 5) append("<li>...and ${result.warnings.size - 5} more</li>")
                append("</ul>")
            }
            append("</body></html>")
        }

    private fun fileList(title: String, paths: List<String>): String {
        if (paths.isEmpty()) return ""
        return buildString {
            append("<br/><b>$title</b><ul>")
            paths.take(6).forEach { path ->
                append("<li>${escapeHtml(path.substringAfterLast('/'))}</li>")
            }
            if (paths.size > 6) append("<li>...and ${paths.size - 6} more</li>")
            append("</ul>")
        }
    }

    private fun escapeHtml(value: String): String =
        value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
}
