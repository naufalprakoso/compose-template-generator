package com.kmpfeaturekit.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.kmpfeaturekit.quickfix.MoveToAndroidSourceSetQuickFix
import org.jetbrains.kotlin.psi.KtFile

class AndroidOnlyCommonMainInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : PsiElementVisitor() {
            override fun visitFile(file: PsiFile) {
                if (file is KtFile && HeuristicInspections.androidOnlyApiProblem(file.virtualFile?.path.orEmpty(), file.text)) {
                    holder.registerProblem(
                        file,
                        "Android-only API used inside commonMain",
                        MoveToAndroidSourceSetQuickFix()
                    )
                }
            }
        }
}
