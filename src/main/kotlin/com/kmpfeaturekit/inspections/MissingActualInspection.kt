package com.kmpfeaturekit.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.kmpfeaturekit.quickfix.CreateActualImplementationQuickFix
import org.jetbrains.kotlin.psi.KtFile

class MissingActualInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : PsiElementVisitor() {
            override fun visitFile(file: PsiFile) {
                if (file is KtFile && file.virtualFile?.path.orEmpty().contains("commonMain") && Regex("\\bexpect\\s+(class|fun|object|interface)\\b").containsMatchIn(file.text)) {
                    holder.registerProblem(file, "Expected declaration requires platform actual implementations", CreateActualImplementationQuickFix())
                }
            }
        }
}
