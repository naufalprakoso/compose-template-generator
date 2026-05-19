package com.kmpfeaturekit.quickfix

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project

class CreateFeatureTestQuickFix : LocalQuickFix {
    override fun getFamilyName(): String = "Create feature test stub"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        // Test path selection depends on module/source-set resolution and remains preview-first.
    }
}
