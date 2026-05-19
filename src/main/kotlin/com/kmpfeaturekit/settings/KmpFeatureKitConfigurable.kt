package com.kmpfeaturekit.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.components.service
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JPanel

class KmpFeatureKitConfigurable(private val project: Project) : Configurable {
    private val packagePattern = JBTextField()
    private val routeStyle = JBTextField()
    private val generatePreviews = JBCheckBox("Generate Compose previews by default")
    private val telemetry = JBCheckBox("Enable telemetry")
    private var panel: JPanel? = null

    override fun getDisplayName(): String = "Compose Template Generator"

    override fun createComponent(): JComponent {
        val settings = project.service<KmpFeatureKitSettings>().state
        packagePattern.text = settings.defaultPackagePattern
        routeStyle.text = settings.routeStyle
        generatePreviews.isSelected = settings.generatePreviews
        telemetry.isSelected = settings.telemetryEnabled
        panel = FormBuilder.createFormBuilder()
            .addLabeledComponent("Default package pattern", packagePattern)
            .addLabeledComponent("Route style", routeStyle)
            .addComponent(generatePreviews)
            .addComponent(telemetry)
            .addComponentFillVertically(JPanel(), 0)
            .panel
        return panel!!
    }

    override fun isModified(): Boolean {
        val settings = project.service<KmpFeatureKitSettings>().state
        return packagePattern.text != settings.defaultPackagePattern ||
            routeStyle.text != settings.routeStyle ||
            generatePreviews.isSelected != settings.generatePreviews ||
            telemetry.isSelected != settings.telemetryEnabled
    }

    override fun apply() {
        val settings = project.service<KmpFeatureKitSettings>().state
        settings.defaultPackagePattern = packagePattern.text
        settings.routeStyle = routeStyle.text
        settings.generatePreviews = generatePreviews.isSelected
        settings.telemetryEnabled = telemetry.isSelected
    }

    override fun reset() {
        val settings = project.service<KmpFeatureKitSettings>().state
        packagePattern.text = settings.defaultPackagePattern
        routeStyle.text = settings.routeStyle
        generatePreviews.isSelected = settings.generatePreviews
        telemetry.isSelected = settings.telemetryEnabled
    }
}
