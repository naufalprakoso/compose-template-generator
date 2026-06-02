package com.kmpfeaturekit.toolwindow

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.kmpfeaturekit.sourceSet.SourceSetDetectionService
import com.kmpfeaturekit.services.ProjectScanService
import java.awt.BorderLayout
import javax.swing.JButton
import javax.swing.JPanel

class KmpFeatureKitToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = JPanel(BorderLayout())
        val output = JBTextArea().apply {
            isEditable = false
            text = renderHealth(project)
        }
        val refresh = JButton("Refresh").apply {
            addActionListener {
                output.text = renderHealth(project, forceRefresh = true)
                output.caretPosition = 0
            }
        }
        panel.add(refresh, BorderLayout.NORTH)
        panel.add(JBScrollPane(output), BorderLayout.CENTER)
        val content = ContentFactory.getInstance().createContent(panel, "Health", false)
        toolWindow.contentManager.addContent(content)
    }

    private fun renderHealth(project: Project, forceRefresh: Boolean = false): String {
        val scan = project.service<ProjectScanService>().scan(forceRefresh)
        val sourceSets = project.service<SourceSetDetectionService>().detect()
        return buildString {
            appendLine("Compose Template Generator")
            appendLine()
            appendLine("Detected libraries:")
            scan.detectedLibraries.sorted().ifEmpty { listOf("None") }.forEach { appendLine("- $it") }
            appendLine()
            appendLine("Suggested defaults:")
            appendLine("- Architecture: ${scan.suggestedArchitecture.label}")
            appendLine("- Navigation: ${scan.suggestedNavigation.label}")
            appendLine("- DI: ${scan.suggestedDi.label}")
            appendLine("- Gradle DSL: ${scan.gradleDsl}")
            appendLine()
            appendLine("Source sets:")
            sourceSets.sourceSets.forEach { appendLine("- ${it.name}: ${if (it.exists) "present" else "missing"}") }
            appendLine()
            appendLine("Health checks:")
            appendLine("- Missing actuals: run inspections")
            appendLine("- Missing DI registrations: review generated integration preview")
            appendLine("- Missing navigation routes: review generated integration preview")
            appendLine("- Architecture inconsistencies: run inspections")
        }
    }
}
