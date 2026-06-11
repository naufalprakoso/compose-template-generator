package com.kmpfeaturekit.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.components.service
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.kmpfeaturekit.model.ArchitectureType
import com.kmpfeaturekit.model.DependencyInjectionType
import com.kmpfeaturekit.model.NavigationType
import com.kmpfeaturekit.model.ProjectStyle
import javax.swing.JComponent
import javax.swing.JPanel

class KmpFeatureKitConfigurable(private val project: Project) : Configurable {
    private val defaultArchitecture = ComboBox(ArchitectureType.entries.toTypedArray())
    private val defaultNavigation = ComboBox(NavigationType.entries.toTypedArray())
    private val defaultDi = ComboBox(DependencyInjectionType.entries.toTypedArray())
    private val defaultProjectStyle = ComboBox(ProjectStyle.entries.toTypedArray())
    private val packagePattern = JBTextField()
    private val routeStyle = JBTextField()
    private val generatePreviews = JBCheckBox("Generate Compose previews by default")
    private val autoRegisterDi = JBCheckBox("Wire dependency injection by default")
    private val autoRegisterNavigation = JBCheckBox("Wire navigation by default")
    private var panel: JPanel? = null

    override fun getDisplayName(): String = "Compose Template Generator"

    override fun createComponent(): JComponent {
        val settings = project.service<KmpFeatureKitSettings>().state
        defaultArchitecture.selectedItem = settings.defaultArchitecture.toEnumOrDefault(ArchitectureType.MVVM)
        defaultNavigation.selectedItem = settings.defaultNavigation.toEnumOrDefault(NavigationType.NONE)
        defaultDi.selectedItem = settings.defaultDi.toEnumOrDefault(DependencyInjectionType.KOIN)
        defaultProjectStyle.selectedItem = settings.defaultProjectStyle.toEnumOrDefault(ProjectStyle.FEATURE_BASED)
        packagePattern.text = settings.defaultPackagePattern
        routeStyle.text = settings.routeStyle
        generatePreviews.isSelected = settings.generatePreviews
        autoRegisterDi.isSelected = settings.autoRegisterDi
        autoRegisterNavigation.isSelected = settings.autoRegisterNavigation
        panel = FormBuilder.createFormBuilder()
            .addLabeledComponent("Default architecture", defaultArchitecture)
            .addLabeledComponent("Default navigation", defaultNavigation)
            .addLabeledComponent("Default DI", defaultDi)
            .addLabeledComponent("Default project style", defaultProjectStyle)
            .addLabeledComponent("Default package pattern", packagePattern)
            .addLabeledComponent("Route style", routeStyle)
            .addComponent(generatePreviews)
            .addComponent(autoRegisterDi)
            .addComponent(autoRegisterNavigation)
            .addComponentFillVertically(JPanel(), 0)
            .panel
        return panel!!
    }

    override fun isModified(): Boolean {
        val settings = project.service<KmpFeatureKitSettings>().state
        return (defaultArchitecture.selectedItem as ArchitectureType).name != settings.defaultArchitecture ||
            (defaultNavigation.selectedItem as NavigationType).name != settings.defaultNavigation ||
            (defaultDi.selectedItem as DependencyInjectionType).name != settings.defaultDi ||
            (defaultProjectStyle.selectedItem as ProjectStyle).name != settings.defaultProjectStyle ||
            packagePattern.text != settings.defaultPackagePattern ||
            routeStyle.text != settings.routeStyle ||
            generatePreviews.isSelected != settings.generatePreviews ||
            autoRegisterDi.isSelected != settings.autoRegisterDi ||
            autoRegisterNavigation.isSelected != settings.autoRegisterNavigation
    }

    override fun apply() {
        val settings = project.service<KmpFeatureKitSettings>().state
        settings.defaultArchitecture = (defaultArchitecture.selectedItem as ArchitectureType).name
        settings.defaultNavigation = (defaultNavigation.selectedItem as NavigationType).name
        settings.defaultDi = (defaultDi.selectedItem as DependencyInjectionType).name
        settings.defaultProjectStyle = (defaultProjectStyle.selectedItem as ProjectStyle).name
        settings.defaultPackagePattern = packagePattern.text
        settings.routeStyle = routeStyle.text
        settings.generatePreviews = generatePreviews.isSelected
        settings.autoRegisterDi = autoRegisterDi.isSelected
        settings.autoRegisterNavigation = autoRegisterNavigation.isSelected
    }

    override fun reset() {
        val settings = project.service<KmpFeatureKitSettings>().state
        defaultArchitecture.selectedItem = settings.defaultArchitecture.toEnumOrDefault(ArchitectureType.MVVM)
        defaultNavigation.selectedItem = settings.defaultNavigation.toEnumOrDefault(NavigationType.NONE)
        defaultDi.selectedItem = settings.defaultDi.toEnumOrDefault(DependencyInjectionType.KOIN)
        defaultProjectStyle.selectedItem = settings.defaultProjectStyle.toEnumOrDefault(ProjectStyle.FEATURE_BASED)
        packagePattern.text = settings.defaultPackagePattern
        routeStyle.text = settings.routeStyle
        generatePreviews.isSelected = settings.generatePreviews
        autoRegisterDi.isSelected = settings.autoRegisterDi
        autoRegisterNavigation.isSelected = settings.autoRegisterNavigation
    }

    private inline fun <reified T : Enum<T>> String.toEnumOrDefault(defaultValue: T): T =
        enumValues<T>().firstOrNull { it.name == this } ?: defaultValue
}
