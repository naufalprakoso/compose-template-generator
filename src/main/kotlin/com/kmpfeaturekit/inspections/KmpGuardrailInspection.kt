package com.kmpfeaturekit.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.kmpfeaturekit.quickfix.CreateFeatureTestQuickFix
import com.kmpfeaturekit.quickfix.CreatePreviewQuickFix
import org.jetbrains.kotlin.psi.KtFile

class KmpGuardrailInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : PsiElementVisitor() {
            override fun visitFile(file: PsiFile) {
                if (file !is KtFile) return
                val path = file.virtualFile?.path.orEmpty()
                if (HeuristicInspections.missingPreviewProblem(path, file.text)) {
                    holder.registerProblem(file, "Compose screen is missing a preview", CreatePreviewQuickFix())
                }
                if (HeuristicInspections.suspiciousArchitectureProblem(path)) {
                    holder.registerProblem(file, "File placement looks suspicious for the selected architecture")
                }
            }
        }
}
