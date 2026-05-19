package com.kmpfeaturekit.dialogs

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import org.jetbrains.kotlin.psi.KtFile

data class FeatureDialogDefaults(
    val basePackage: String,
    val targetModule: String,
    val sourceSetRoot: String
) {
    companion object {
        fun fallback(project: Project): FeatureDialogDefaults =
            FeatureDialogDefaults(
                basePackage = "com.example.features",
                targetModule = project.name,
                sourceSetRoot = project.basePath?.let { "$it/src" }.orEmpty()
            )
    }
}

object FeatureDialogDefaultsResolver {
    private val sourceSetPathPattern = Regex("""(^|.*/)src/[^/]+/(kotlin|java)(/.*)?$""")

    fun resolve(event: AnActionEvent, project: Project): FeatureDialogDefaults {
        val fallback = FeatureDialogDefaults.fallback(project)
        val virtualFile = event.getData(CommonDataKeys.VIRTUAL_FILE)
            ?: event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)?.firstOrNull()
            ?: FileEditorManager.getInstance(project).selectedFiles.firstOrNull()

        if (virtualFile == null) return fallback

        val module = ModuleUtilCore.findModuleForFile(virtualFile, project)
        val psiFile = event.getData(CommonDataKeys.PSI_FILE)
            ?: virtualFile.takeUnless { it.isDirectory }?.let { PsiManager.getInstance(project).findFile(it) }

        return FeatureDialogDefaults(
            basePackage = (psiFile as? KtFile)
                ?.packageFqName
                ?.asString()
                ?.takeIf { it.isNotBlank() }
                ?: fallback.basePackage,
            targetModule = module?.name ?: fallback.targetModule,
            sourceSetRoot = sourceSetRootFor(virtualFile) ?: fallback.sourceSetRoot
        )
    }

    fun sourceSetRootFor(virtualFile: VirtualFile): String? =
        sourceSetRootForPath(virtualFile.path)

    fun sourceSetRootForPath(path: String): String? {
        val normalized = path.removeSuffix("/")
        val directoryPath = normalized.substringBeforeLast('/', normalized)
        val candidate = if (sourceSetPathPattern.matches(normalized)) normalized else directoryPath
        val srcIndex = candidate.indexOf("/src/")
        if (srcIndex < 0) return null
        return candidate.substring(0, srcIndex + "/src".length)
    }
}
