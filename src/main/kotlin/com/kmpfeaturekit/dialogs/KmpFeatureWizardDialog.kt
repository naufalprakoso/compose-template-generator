package com.kmpfeaturekit.dialogs

import com.intellij.openapi.components.service
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.CheckBoxList
import com.intellij.ui.EditorTextField
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.kmpfeaturekit.generator.FeatureGenerationService
import com.kmpfeaturekit.model.ArchitectureCompatibility
import com.kmpfeaturekit.model.ArchitectureSelection
import com.kmpfeaturekit.model.ArchitectureType
import com.kmpfeaturekit.model.DependencyInjectionType
import com.kmpfeaturekit.model.FeatureInfo
import com.kmpfeaturekit.model.FeatureOptions
import com.kmpfeaturekit.model.FeatureRequest
import com.kmpfeaturekit.model.NavigationType
import com.kmpfeaturekit.model.NetworkingType
import com.kmpfeaturekit.model.PersistenceType
import com.kmpfeaturekit.model.PlannedFile
import com.kmpfeaturekit.model.PlannedFileKind
import com.kmpfeaturekit.model.PlatformTarget
import com.kmpfeaturekit.model.ProjectStyle
import com.kmpfeaturekit.model.StateHolderType
import com.kmpfeaturekit.services.ProjectScanResult
import com.kmpfeaturekit.services.ProjectScanService
import com.kmpfeaturekit.utils.ValidationUtils
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.Dimension
import java.awt.GridLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.BoxLayout
import javax.swing.DefaultComboBoxModel
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants
import javax.swing.Timer
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class KmpFeatureWizardDialog(
    private val project: Project,
    defaults: FeatureDialogDefaults = FeatureDialogDefaults.fallback(project)
) : DialogWrapper(project) {
    private enum class FeaturePreset(private val label: String) {
        STANDARD("Standard feature"),
        MINIMAL("Minimal"),
        FULL("Full scaffold"),
        PRESENTATION_ONLY("Presentation only"),
        DATA_DOMAIN("Data + domain");

        override fun toString(): String = label
    }

    private val featureName = JBTextField()
    private val basePackage = JBTextField(defaults.basePackage)
    private val targetModule = ComboBox(moduleNames(defaults.targetModule)).apply {
        isEditable = true
        selectedItem = defaults.targetModule
    }
    private val sourceSetRoot = TextFieldWithBrowseButton().apply {
        text = defaults.sourceSetRoot
    }
    private val preset = ComboBox(FeaturePreset.entries.toTypedArray())
    private val architecture = ComboBox(ArchitectureType.entries.toTypedArray())
    private val stateHolder = ComboBox(StateHolderType.entries.toTypedArray())
    private val navigation = ComboBox(NavigationType.entries.toTypedArray())
    private val di = ComboBox(DependencyInjectionType.entries.toTypedArray())
    private val networking = ComboBox(NetworkingType.entries.toTypedArray())
    private val persistence = ComboBox(PersistenceType.entries.toTypedArray())
    private val projectStyle = ComboBox(ProjectStyle.entries.toTypedArray())
    private val autoRegisterDi = JCheckBox("Wire dependency injection when a safe target is found", true)
    private val autoRegisterNavigation = JCheckBox("Wire navigation when a safe target is found", true)
    private val screenUi = JCheckBox("Screen UI", true)
    private val screenPreview = JCheckBox("Compose preview", true)
    private val stateHolderFile = JCheckBox("State holder", true)
    private val stateFile = JCheckBox("State model", true)
    private val actionFile = JCheckBox("Action / intent", true)
    private val effectFile = JCheckBox("Effect", true)
    private val repositoryFile = JCheckBox("Repository contract", true)
    private val repositoryImplFile = JCheckBox("Repository implementation", true)
    private val serviceFile = JCheckBox("Service contract", true)
    private val serviceImplFile = JCheckBox("Service implementation", true)
    private val useCaseFile = JCheckBox("Use case", true)
    private val navigationFile = JCheckBox("Navigation route + graph", true)
    private val diFile = JCheckBox("DI module", true)
    private val fakeRepositoryFile = JCheckBox("Fake repository", true)
    private val unitTestFile = JCheckBox("Unit test", true)
    private val readmeFile = JCheckBox("Feature README", false)
    private val expectActualFile = JCheckBox("Expect/actual platform context", false)
    private val platforms = CheckBoxList<PlatformTarget>()
    private val previewList = JPanel()
    private val previewSelections = linkedMapOf<String, Pair<PlannedFile, JCheckBox>>()
    private val detectedSummary = JBTextArea(5, 48)
    private val warningList = JPanel()
    private val scanResult: ProjectScanResult = project.service<ProjectScanService>().scan()
    private val previewRefreshTimer = Timer(350) { refreshPreviewNow() }.apply {
        isRepeats = false
    }

    init {
        title = "Compose Template Generator"
        applyDetectedDefaults()
        networking.selectedItem = NetworkingType.NONE
        persistence.selectedItem = PersistenceType.NONE
        preset.selectedItem = FeaturePreset.STANDARD
        PlatformTarget.entries.forEach { platforms.addItem(it, it.label, true) }
        detectedSummary.isEditable = false
        detectedSummary.text = buildDetectedSummary()
        previewList.layout = BoxLayout(previewList, BoxLayout.Y_AXIS)
        warningList.layout = BoxLayout(warningList, BoxLayout.Y_AXIS)
        sourceSetRoot.addActionListener { chooseSourceSetRoot() }
        wireListeners()
        init()
        refreshPreviewNow()
    }

    fun request(): FeatureRequest {
        applyDependentToggles()
        return FeatureRequest(
            info = FeatureInfo(
                featureName = featureName.text.trim(),
                basePackage = basePackage.text.trim(),
                targetModule = selectedModuleName(),
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
            options = FeatureOptions(
                screenUi = screenUi.isSelected,
                stateHolder = stateHolderFile.isSelected,
                state = stateFile.isSelected,
                actionEventIntent = actionFile.isSelected,
                effect = effectFile.isSelected,
                repository = repositoryFile.isSelected,
                repositoryImplementation = repositoryImplFile.isSelected,
                service = serviceFile.isSelected,
                serviceImplementation = serviceImplFile.isSelected,
                useCase = useCaseFile.isSelected,
                navigationRoute = navigationFile.isSelected,
                diModule = diFile.isSelected,
                preview = screenPreview.isSelected,
                readme = readmeFile.isSelected,
                fakeRepository = fakeRepositoryFile.isSelected,
                unitTests = unitTestFile.isSelected,
                expectActualPlatformAbstraction = expectActualFile.isSelected,
                autoRegisterDi = autoRegisterDi.isSelected,
                autoRegisterNavigation = autoRegisterNavigation.isSelected
            )
        )
    }

    fun selectedFiles(): List<PlannedFile> =
        previewSelections.values
            .filter { (_, checkbox) -> checkbox.isSelected }
            .map { (file, _) -> file }

    override fun doValidate(): ValidationInfo? {
        ValidationUtils.validateFeatureName(featureName.text.trim()).firstOrNull()?.let {
            return ValidationInfo(it, featureName)
        }
        ValidationUtils.validatePackage(basePackage.text.trim()).firstOrNull()?.let {
            return ValidationInfo(it, basePackage)
        }
        ValidationUtils.validateTargetModule(selectedModuleName()).firstOrNull()?.let {
            return ValidationInfo(it, targetModule)
        }
        ValidationUtils.validateSourceSetRoot(sourceSetRoot.text.trim()).firstOrNull()?.let {
            return ValidationInfo(it, sourceSetRoot)
        }
        ValidationUtils.validatePlatformSelection(selectedPlatformCount()).firstOrNull()?.let {
            return ValidationInfo(it, platforms)
        }
        return null
    }

    override fun createCenterPanel(): JComponent {
        return JPanel(BorderLayout()).apply {
            preferredSize = Dimension(940, 760)
            add(
                JBScrollPane(mainPanel()).apply {
                    horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
                },
                BorderLayout.CENTER
            )
        }
    }

    private fun mainPanel(): JComponent =
        JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(section("Target", targetPanel()))
            add(section("Architecture", architecturePanel()))
            add(section("Files", fileTogglePanel()))
            add(section("Preview", previewPanel()))
        }

    private fun targetPanel(): JComponent =
        FormBuilder.createFormBuilder()
            .addLabeledComponent("Feature name", featureName)
            .addLabeledComponent("Base package", basePackage)
            .addLabeledComponent("Target module", targetModule)
            .addLabeledComponent("Source set root", sourceSetRoot)
            .addComponent(JBLabel("Pick a module src directory, for example /project/shared/src."))
            .addLabeledComponent("Detected project", JBScrollPane(detectedSummary))
            .panel

    private fun architecturePanel(): JComponent =
        FormBuilder.createFormBuilder()
            .addLabeledComponent("Preset", preset)
            .addLabeledComponent("Architecture pattern", architecture)
            .addLabeledComponent("State holder", stateHolder)
            .addLabeledComponent("Navigation", navigation)
            .addLabeledComponent("Dependency injection", di)
            .addLabeledComponent("Networking", networking)
            .addLabeledComponent("Persistence", persistence)
            .addLabeledComponent("Platforms", platforms)
            .addLabeledComponent("Project style", projectStyle)
            .addComponent(autoRegisterDi)
            .addComponent(autoRegisterNavigation)
            .panel

    private fun fileTogglePanel(): JComponent =
        JPanel(GridLayout(0, 2, 12, 4)).apply {
            listOf(
                screenUi,
                screenPreview,
                stateHolderFile,
                stateFile,
                actionFile,
                effectFile,
                repositoryFile,
                repositoryImplFile,
                serviceFile,
                serviceImplFile,
                useCaseFile,
                navigationFile,
                diFile,
                fakeRepositoryFile,
                unitTestFile,
                readmeFile,
                expectActualFile
            ).forEach(::add)
        }

    private fun previewPanel(): JComponent =
        FormBuilder.createFormBuilder()
            .addComponent(JBLabel("Project changes preview"))
            .addComponent(
                JBScrollPane(previewList).apply {
                    preferredSize = Dimension(860, 260)
                    horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
                }
            )
            .addLabeledComponent(
                "Warnings",
                JBScrollPane(warningList).apply {
                    preferredSize = Dimension(860, 120)
                    horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
                }
            )
            .panel

    private fun section(title: String, content: JComponent): JComponent =
        JPanel(BorderLayout()).apply {
            border = BorderFactory.createTitledBorder(title)
            add(content, BorderLayout.CENTER)
            maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
        }

    private fun wireListeners() {
        architecture.addActionListener {
            applyArchitectureCompatibility(
                architectureType = architecture.selectedItem as ArchitectureType,
                preferredStateHolder = stateHolder.selectedItem as? StateHolderType,
                preferredNavigation = navigation.selectedItem as? NavigationType
            )
            schedulePreviewRefresh()
        }
        preset.addActionListener {
            applyPreset(preset.selectedItem as FeaturePreset)
            schedulePreviewRefresh()
        }
        targetModule.addActionListener {
            applySelectedModuleSourceRoot()
            schedulePreviewRefresh()
        }
        listOf(featureName, basePackage).forEach { field ->
            field.document.addDocumentListener(previewDocumentListener())
        }
        sourceSetRoot.textField.document.addDocumentListener(previewDocumentListener())
        listOf(di, networking, persistence, projectStyle).forEach { comboBox ->
            comboBox.addActionListener { schedulePreviewRefresh() }
        }
        listOf(autoRegisterDi, autoRegisterNavigation).forEach { checkbox ->
            checkbox.addActionListener { schedulePreviewRefresh() }
        }
        fileToggles().forEach { checkbox ->
            checkbox.addActionListener {
                applyDependentToggles()
                schedulePreviewRefresh()
            }
        }
    }

    private fun previewDocumentListener(): DocumentListener =
        object : DocumentListener {
            override fun insertUpdate(event: DocumentEvent) = schedulePreviewRefresh()
            override fun removeUpdate(event: DocumentEvent) = schedulePreviewRefresh()
            override fun changedUpdate(event: DocumentEvent) = schedulePreviewRefresh()
        }

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

    private fun applyPreset(featurePreset: FeaturePreset) {
        when (featurePreset) {
            FeaturePreset.MINIMAL -> setToggles(
                screen = true,
                preview = false,
                holder = true,
                state = true,
                action = true,
                effect = false,
                repository = false,
                repositoryImpl = false,
                service = false,
                serviceImpl = false,
                useCase = false,
                navigation = false,
                di = false,
                fakeRepository = false,
                unitTest = false,
                readme = false,
                expectActual = false
            )
            FeaturePreset.PRESENTATION_ONLY -> setToggles(
                screen = true,
                preview = true,
                holder = true,
                state = true,
                action = true,
                effect = true,
                repository = false,
                repositoryImpl = false,
                service = false,
                serviceImpl = false,
                useCase = false,
                navigation = true,
                di = false,
                fakeRepository = false,
                unitTest = true,
                readme = false,
                expectActual = false
            )
            FeaturePreset.DATA_DOMAIN -> setToggles(
                screen = false,
                preview = false,
                holder = false,
                state = true,
                action = false,
                effect = false,
                repository = true,
                repositoryImpl = true,
                service = true,
                serviceImpl = true,
                useCase = true,
                navigation = false,
                di = true,
                fakeRepository = true,
                unitTest = true,
                readme = true,
                expectActual = false
            )
            FeaturePreset.FULL -> setToggles(
                screen = true,
                preview = true,
                holder = true,
                state = true,
                action = true,
                effect = true,
                repository = true,
                repositoryImpl = true,
                service = true,
                serviceImpl = true,
                useCase = true,
                navigation = true,
                di = true,
                fakeRepository = true,
                unitTest = true,
                readme = true,
                expectActual = true
            )
            FeaturePreset.STANDARD -> setToggles(
                screen = true,
                preview = true,
                holder = true,
                state = true,
                action = true,
                effect = true,
                repository = true,
                repositoryImpl = true,
                service = true,
                serviceImpl = true,
                useCase = true,
                navigation = true,
                di = true,
                fakeRepository = true,
                unitTest = true,
                readme = false,
                expectActual = false
            )
        }
        applyDependentToggles()
    }

    private fun setToggles(
        screen: Boolean,
        preview: Boolean,
        holder: Boolean,
        state: Boolean,
        action: Boolean,
        effect: Boolean,
        repository: Boolean,
        repositoryImpl: Boolean,
        service: Boolean,
        serviceImpl: Boolean,
        useCase: Boolean,
        navigation: Boolean,
        di: Boolean,
        fakeRepository: Boolean,
        unitTest: Boolean,
        readme: Boolean,
        expectActual: Boolean
    ) {
        screenUi.isSelected = screen
        screenPreview.isSelected = preview
        stateHolderFile.isSelected = holder
        stateFile.isSelected = state
        actionFile.isSelected = action
        effectFile.isSelected = effect
        repositoryFile.isSelected = repository
        repositoryImplFile.isSelected = repositoryImpl
        serviceFile.isSelected = service
        serviceImplFile.isSelected = serviceImpl
        useCaseFile.isSelected = useCase
        navigationFile.isSelected = navigation
        diFile.isSelected = di
        fakeRepositoryFile.isSelected = fakeRepository
        unitTestFile.isSelected = unitTest
        readmeFile.isSelected = readme
        expectActualFile.isSelected = expectActual
    }

    private fun applyDependentToggles() {
        if (screenPreview.isSelected) {
            screenUi.isSelected = true
            stateFile.isSelected = true
            actionFile.isSelected = true
        }
        if (screenUi.isSelected) {
            stateFile.isSelected = true
            actionFile.isSelected = true
        }
        if (stateHolderFile.isSelected) {
            stateFile.isSelected = true
            actionFile.isSelected = true
            useCaseFile.isSelected = true
        }
        if (useCaseFile.isSelected) {
            repositoryFile.isSelected = true
        }
        if (repositoryImplFile.isSelected) {
            repositoryFile.isSelected = true
            serviceFile.isSelected = true
        }
        if (serviceImplFile.isSelected) {
            serviceFile.isSelected = true
        }
        if (diFile.isSelected) {
            repositoryFile.isSelected = true
            repositoryImplFile.isSelected = true
            serviceFile.isSelected = true
            serviceImplFile.isSelected = true
            useCaseFile.isSelected = true
        }
        if (fakeRepositoryFile.isSelected) {
            repositoryFile.isSelected = true
            stateFile.isSelected = true
        }
        if (unitTestFile.isSelected) {
            stateFile.isSelected = true
        }
    }

    private fun buildDetectedSummary(): String = buildString {
        appendLine("Libraries: ${scanResult.detectedLibraries.sorted().joinToString().ifBlank { "none detected" }}")
        appendLine("Default architecture: ${scanResult.suggestedArchitecture.label}")
        appendLine("Default navigation: ${scanResult.suggestedNavigation.label}")
        appendLine("Default DI: ${scanResult.suggestedDi.label}")
        appendLine("Gradle DSL: ${scanResult.gradleDsl}")
    }

    private fun schedulePreviewRefresh() {
        previewRefreshTimer.restart()
    }

    private fun refreshPreviewNow() {
        val excludedPaths = previewSelections
            .filter { (_, pair) -> !pair.second.isSelected }
            .keys
            .toSet()
        previewSelections.clear()
        previewList.removeAll()

        if (ValidationUtils.validateFeatureInputs(
                featureName = featureName.text.trim(),
                packageName = basePackage.text.trim(),
                targetModule = selectedModuleName(),
                sourceSetRoot = sourceSetRoot.text.trim(),
                selectedPlatformCount = selectedPlatformCount()
            ).isEmpty()
        ) {
            val preview = project.service<FeatureGenerationService>().preview(request())
            addPreviewGroup("Conflicts", preview.filesToCreate.filter { it.conflict }, excludedPaths)
            addPreviewGroup("Modify existing", preview.filesToModify, excludedPaths)
            addPreviewGroup("Manual review", preview.filesToCreate.filter { it.isManualReview() && !it.conflict }, excludedPaths)
            addPreviewGroup(
                "Create new",
                preview.filesToCreate.filter { !it.conflict && !it.isManualReview() },
                excludedPaths
            )
            updateWarnings(
                preview.warnings.ifEmpty {
                    listOf("No warnings. Existing create targets are skipped unless overwrite is explicitly enabled.")
                }
            )
        } else {
            previewList.add(JBLabel("Enter valid target and feature details to preview generated files."))
            updateWarnings(emptyList())
        }
        previewList.revalidate()
        previewList.repaint()
        warningList.revalidate()
        warningList.repaint()
    }

    private fun addPreviewGroup(title: String, files: List<PlannedFile>, excludedPaths: Set<String>) {
        if (files.isEmpty()) return
        previewList.add(JBLabel("<html><b>${escapeHtml(title)} (${files.size})</b></html>"))
        files.forEach { plannedFile ->
            val checkbox = JCheckBox("", plannedFile.path !in excludedPaths)
            val label = JBLabel(previewLabel(plannedFile, checkbox.isSelected)).apply {
                toolTipText = plannedFile.path
                cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                addMouseListener(object : MouseAdapter() {
                    override fun mouseClicked(event: MouseEvent) {
                        showSelectedChangePopup(plannedFile)
                    }
                })
            }
            checkbox.addActionListener {
                label.text = previewLabel(plannedFile, checkbox.isSelected)
            }
            previewSelections[plannedFile.path] = plannedFile to checkbox
            previewList.add(
                JPanel(BorderLayout()).apply {
                    add(checkbox, BorderLayout.WEST)
                    add(label, BorderLayout.CENTER)
                    maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
                }
            )
        }
    }

    private fun selectedPlatformCount(): Int =
        PlatformTarget.entries.count { platforms.isItemSelected(it) }

    private fun selectedModuleName(): String =
        targetModule.selectedItem?.toString()?.trim().orEmpty()

    private fun previewLabel(plannedFile: PlannedFile, selected: Boolean): String {
        val selectedMarker = if (selected) "[selected] " else ""
        val marker = when {
            plannedFile.conflict -> "Conflict"
            plannedFile.isManualReview() -> "Manual"
            plannedFile.kind == PlannedFileKind.MODIFY -> "Modify"
            else -> "Create"
        }
        return "$selectedMarker$marker: ${plannedFile.path}"
    }

    private fun renderSelectedChange(plannedFile: PlannedFile): String =
        buildString {
            appendLine(previewLabel(plannedFile, selected = true))
            appendLine()
            appendLine(plannedFile.content)
        }

    private fun showSelectedChangePopup(plannedFile: PlannedFile) {
        object : DialogWrapper(project) {
            private val editor = createPreviewEditor(plannedFile)
            private val fileName = plannedFile.path.substringAfterLast('/')

            init {
                title = fileName
                init()
            }

            override fun createCenterPanel(): JComponent =
                JPanel(BorderLayout()).apply {
                    preferredSize = Dimension(860, 520)
                    add(
                        JBLabel(plannedFile.path).apply {
                            border = BorderFactory.createEmptyBorder(0, 0, 8, 0)
                        },
                        BorderLayout.NORTH
                    )
                    add(editor, BorderLayout.CENTER)
                }
        }.show()
    }

    private fun createPreviewEditor(plannedFile: PlannedFile): EditorTextField {
        val fileName = plannedFile.path.substringAfterLast('/')
        val fileType = FileTypeManager.getInstance().getFileTypeByFileName(fileName)
        return EditorTextField(renderSelectedChange(plannedFile), project, fileType).apply {
            isViewer = true
            setOneLineMode(false)
        }
    }

    private fun updateWarnings(warnings: List<String>) {
        warningList.removeAll()
        warnings.forEach { warning ->
            warningList.add(JBLabel("<html><body style='width: 820px'>&bull; ${escapeHtml(warning)}</body></html>"))
        }
    }

    private fun chooseSourceSetRoot() {
        FileChooser.chooseFile(
            FileChooserDescriptorFactory.createSingleFolderDescriptor(),
            project,
            null
        ) { file ->
            sourceSetRoot.text = file.takeIf { it.name == "src" }?.path
                ?: file.findChild("src")?.path
                ?: file.path
            schedulePreviewRefresh()
        }
    }

    private fun applySelectedModuleSourceRoot() {
        val candidate = sourceSetRootForSelectedModule() ?: return
        sourceSetRoot.text = candidate
    }

    private fun sourceSetRootForSelectedModule(): String? {
        val moduleName = selectedModuleName()
        val module = ModuleManager.getInstance(project).modules.firstOrNull { it.name == moduleName } ?: return null
        val roots = ModuleRootManager.getInstance(module).sourceRoots
            .mapNotNull { FeatureDialogDefaultsResolver.sourceSetRootForPath(it.path) }
            .ifEmpty {
                ModuleRootManager.getInstance(module).contentRoots
                    .mapNotNull { root -> root.findChild("src")?.path }
            }
        return roots.firstOrNull()
    }

    private fun moduleNames(defaultModule: String): Array<String> =
        (ModuleManager.getInstance(project).modules.map { it.name } + defaultModule)
            .filter { it.isNotBlank() }
            .distinct()
            .toTypedArray()

    private fun fileToggles(): List<JCheckBox> =
        listOf(
            screenUi,
            screenPreview,
            stateHolderFile,
            stateFile,
            actionFile,
            effectFile,
            repositoryFile,
            repositoryImplFile,
            serviceFile,
            serviceImplFile,
            useCaseFile,
            navigationFile,
            diFile,
            fakeRepositoryFile,
            unitTestFile,
            readmeFile,
            expectActualFile
        )

    private fun PlannedFile.isManualReview(): Boolean =
        path.endsWith(".todo.md")

    private fun escapeHtml(value: String): String =
        value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
}
