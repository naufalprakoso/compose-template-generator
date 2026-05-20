package com.kmpfeaturekit.generator

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.codeStyle.CodeStyleManager
import org.jetbrains.kotlin.idea.KotlinFileType

class KotlinPsiFileNormalizer(private val project: Project) {
    fun normalize(fileName: String, content: String): String =
        runCatching {
            val psiFile = PsiFileFactory.getInstance(project)
                .createFileFromText(fileName, KotlinFileType.INSTANCE, content)
            CodeStyleManager.getInstance(project).reformat(psiFile).text
        }.getOrElse { content }
}
