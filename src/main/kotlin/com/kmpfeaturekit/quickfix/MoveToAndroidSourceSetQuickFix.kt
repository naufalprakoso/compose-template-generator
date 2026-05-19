package com.kmpfeaturekit.quickfix

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project

class MoveToAndroidSourceSetQuickFix : LocalQuickFix {
    override fun getFamilyName(): String = "Move file to androidMain"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        // Movement requires user review because source-set layout varies by project.
    }
}
