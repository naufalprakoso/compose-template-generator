package com.kmpfeaturekit.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.ui.Messages
import com.kmpfeaturekit.dialogs.FeatureDialogDefaultsResolver
import com.kmpfeaturekit.dialogs.KmpFeatureWizardDialog
import com.kmpfeaturekit.generator.FeatureGenerationService

class NewKmpFeatureAction : AnAction("Compose Feature") {
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val dialog = KmpFeatureWizardDialog(project, FeatureDialogDefaultsResolver.resolve(event, project))
        if (dialog.showAndGet()) {
            val result = project.service<FeatureGenerationService>().generate(dialog.selectedFiles(), overwrite = false)
            Messages.showInfoMessage(
                project,
                "Created ${result.writtenFiles.size} files. Skipped ${result.skippedFiles.size} existing files.",
                "Compose Template Generator"
            )
        }
    }
}
