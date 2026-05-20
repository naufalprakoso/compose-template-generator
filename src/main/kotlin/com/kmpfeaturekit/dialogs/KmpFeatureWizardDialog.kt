package com.kmpfeaturekit.dialogs

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.CheckBoxList
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.kmpfeaturekit.generator.FeatureGenerationService
import com.kmpfeaturekit.model.ArchitectureSelection
import com.kmpfeaturekit.model.ArchitectureCompatibility
import com.kmpfeaturekit.model.ArchitectureType
import com.kmpfeaturekit.model.DependencyInjectionType
import com.kmpfeaturekit.model.FeatureInfo
import com.kmpfeaturekit.model.FeatureOptions
import com.kmpfeaturekit.model.FeatureRequest
import com.kmpfeaturekit.model.NavigationType
import com.kmpfeaturekit.model.NetworkingType
import com.kmpfeaturekit.model.PersistenceType
import com.kmpfeaturekit.model.PlatformTarget
import com.kmpfeaturekit.model.PlannedFile
import com.kmpfeaturekit.model.ProjectStyle
import com.kmpfeaturekit.model.StateHolderType
import com.kmpfeaturekit.services.ProjectScanResult
import com.kmpfeaturekit.services.ProjectScanService
import com.kmpfeaturekit.utils.ValidationUtils
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.BoxLayout
import javax.swing.DefaultComboBoxModel
import javax.swing.JCheckBox
import javax.swing.JPanel
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class KmpFeatureWizardDialog(
    private val project: Project,
    defaults: FeatureDialogDefaults = FeatureDialogDefaults.fallback(project)
) : DialogWrapper(project) {
    private val featureName = JBTextField()
    private val basePackage = JBTextField(defaults.basePackage)
    private val targetModule = JBTextField(defaults.targetModule)
    private val sourceSetRoot = JBTextField(defaults.sourceSetRoot)
    private val architecture = ComboBox(ArchitectureType.entries.toTypedArray())
    private val stateHolder = ComboBox(StateHolderType.entries.toTypedArray())
    private val navigation = ComboBox(NavigationType.entries.toTypedArray())
    private val di = ComboBox(DependencyInjectionType.entries.toTypedArray())
    private val networking = ComboBox(NetworkingType.entries.toTypedArray())
    private val persistence = ComboBox(PersistenceType.entries.toTypedArray())
    private val projectStyle = ComboBox(ProjectStyle.entries.toTypedArray())
    private val platforms = CheckBoxList<PlatformTarget>()
    private val previewList = JPanel()
    private val previewSelections = linkedMapOf<String, Pair<PlannedFile, JCheckBox>>()
    private val detectedSummary = JBTextArea(5, 48)
    private val previewWarnings = JBTextArea(4, 48)
    private val scanResult: ProjectScanResult = project.service<ProjectScanService>().scan()

    init {
        title = "Compose Template Generator"
        applyDetectedDefaults()
        architecture.addActionListener {
            applyArchitectureCompatibility(
                architectureType = architecture.selectedItem as ArchitectureType,
                preferredStateHolder = stateHolder.selectedItem as? StateHolderType,
                preferredNavigation = navigation.selectedItem as? NavigationType
            )
            refreshPreview()
        }
        networking.selectedItem = NetworkingType.NONE
        persistence.selectedItem = PersistenceType.NONE
        PlatformTarget.entries.forEach { platforms.addItem(it, it.label, true) }
        detectedSummary.isEditable = false
        detectedSummary.text = buildDetectedSummary()
        previewWarnings.isEditable = false
        previewList.layout = BoxLayout(previewList, BoxLayout.Y_AXIS)
        listOf(featureName, basePackage, targetModule, sourceSetRoot).forEach { field ->
            field.document.addDocumentListener(object : DocumentListener {
                override fun insertUpdate(event: DocumentEvent) = refreshPreview()
                override fun removeUpdate(event: DocumentEvent) = refreshPreview()
                override fun changedUpdate(event: DocumentEvent) = refreshPreview()
            })
        }
        listOf(di, networking, persistence, projectStyle).forEach { comboBox ->
            comboBox.addActionListener { refreshPreview() }
        }
        init()
        refreshPreview()
    }

    fun request(): FeatureRequest =
        FeatureRequest(
            info = FeatureInfo(
                featureName = featureName.text.trim(),
                basePackage = basePackage.text.trim(),
                targetModule = targetModule.text.trim(),
                sourceSetRoot = sourceSetRoot.text.trim()
            ),
            architecture = ArchitectureSelection(
                architectureType = architecture.selectedItem as ArchitectureType,
                stateHolderType = stateHolder.selectedItem as StateHolderType,
                navigationType = navigation.selectedItem as NavigationType,
                dependencyInjectionType = di.selectedItem as DependencyInjectionType,
                networkingType = networking.selectedItem as NetworkingType,
                persistenceType = persistence.selectedItem as PersistenceType,
                platforms = PlatformTarget.entries.filter { platforms.isItemSelected(it) }.toSet(),
                projectStyle = projectStyle.selectedItem as ProjectStyle
            ),
            options = FeatureOptions()
        )

    fun selectedFiles(): List<PlannedFile> =
        previewSelections.values
            .filter { (_, checkbox) -> checkbox.isSelected }
            .map { (file, _) -> file }

    override fun doValidate(): ValidationInfo? {
        val errors = ValidationUtils.validateFeatureInputs(featureName.text.trim(), basePackage.text.trim())
        return errors.firstOrNull()?.let { ValidationInfo(it, featureName) }
    }

    override fun createCenterPanel(): JComponent {
        return JPanel(BorderLayout()).apply {
            preferredSize = Dimension(920, 720)
            add(JBScrollPane(mainPanel()), BorderLayout.CENTER)
        }
    }

    private fun mainPanel(): JComponent =
        FormBuilder.createFormBuilder()
            .addLabeledComponent("Feature name", featureName)
            .addLabeledComponent("Base package", basePackage)
            .addLabeledComponent("Target module", targetModule)
            .addLabeledComponent("Source set root", sourceSetRoot)
            .addComponent(JBLabel("Names are normalized into PascalCase, camelCase, snake_case, and kebab-case."))
            .addLabeledComponent("Detected project", JBScrollPane(detectedSummary))
            .addLabeledComponent("Architecture pattern", architecture)
            .addLabeledComponent("State holder", stateHolder)
            .addLabeledComponent("Navigation", navigation)
            .addLabeledComponent("Dependency injection", di)
            .addLabeledComponent("Networking", networking)
            .addLabeledComponent("Persistence", persistence)
            .addLabeledComponent("Platforms", platforms)
            .addLabeledComponent("Project style", projectStyle)
            .addSeparator()
            .addComponent(JBLabel("Project changes preview"))
            .addComponent(JBScrollPane(previewList).apply { preferredSize = Dimension(860, 220) })
            .addLabeledComponent("Warnings", JBScrollPane(previewWarnings).apply { preferredSize = Dimension(860, 96) })
            .panel

    private fun applyDetectedDefaults() {
        architecture.selectedItem = scanResult.suggestedArchitecture
        di.selectedItem = scanResult.suggestedDi
        applyArchitectureCompatibility(
            architectureType = scanResult.suggestedArchitecture,
            preferredStateHolder = null,
            preferredNavigation = scanResult.suggestedNavigation
        )
    }

    private fun applyArchitectureCompatibility(
        architectureType: ArchitectureType,
        preferredStateHolder: StateHolderType?,
        preferredNavigation: NavigationType?
    ) {
        val allowedStateHolders = ArchitectureCompatibility.stateHoldersFor(architectureType)
        val allowedNavigation = ArchitectureCompatibility.navigationFor(architectureType)
        stateHolder.model = DefaultComboBoxModel(allowedStateHolders.toTypedArray())
        navigation.model = DefaultComboBoxModel(allowedNavigation.toTypedArray())
        stateHolder.selectedItem = preferredStateHolder
            ?.takeIf { it in allowedStateHolders }
            ?: ArchitectureCompatibility.defaultStateHolderFor(architectureType)
        navigation.selectedItem = preferredNavigation
            ?.takeIf { it in allowedNavigation }
            ?: ArchitectureCompatibility.defaultNavigationFor(architectureType)
    }

    private fun buildDetectedSummary(): String = buildString {
        appendLine("Auto-detected when the Compose Feature dialog opened.")
        appendLine("Libraries: ${scanResult.detectedLibraries.sorted().joinToString().ifBlank { "none detected" }}")
        appendLine("Default architecture: ${scanResult.suggestedArchitecture.label}")
        appendLine("Default navigation: ${scanResult.suggestedNavigation.label}")
        appendLine("Default DI: ${scanResult.suggestedDi.label}")
        appendLine("Gradle DSL: ${scanResult.gradleDsl}")
    }

    private fun refreshPreview() {
        val excludedPaths = previewSelections
            .filter { (_, pair) -> !pair.second.isSelected }
            .keys
            .toSet()
        previewSelections.clear()
        previewList.removeAll()

        if (ValidationUtils.validateFeatureInputs(featureName.text.trim(), basePackage.text.trim()).isEmpty()) {
            val preview = project.service<FeatureGenerationService>().preview(request())
            (preview.filesToCreate + preview.filesToModify).forEach { plannedFile ->
                val checkbox = JCheckBox(previewLabel(plannedFile), plannedFile.path !in excludedPaths)
                checkbox.toolTipText = plannedFile.path
                previewSelections[plannedFile.path] = plannedFile to checkbox
                previewList.add(checkbox)
            }
            previewWarnings.text = if (preview.warnings.isEmpty()) {
                "No warnings. Existing files are still skipped unless selected project changes are safe replacements."
            } else {
                preview.warnings.joinToString("\n")
            }
        } else {
            previewList.add(JBLabel("Enter a valid feature name and base package to preview generated files."))
            previewWarnings.text = ""
        }
        previewList.revalidate()
        previewList.repaint()
    }

    private fun previewLabel(plannedFile: PlannedFile): String {
        val marker = when {
            plannedFile.conflict -> "Conflict"
            else -> plannedFile.kind.name.lowercase().replaceFirstChar(Char::uppercase)
        }
        return "$marker: ${plannedFile.path}"
    }
}
