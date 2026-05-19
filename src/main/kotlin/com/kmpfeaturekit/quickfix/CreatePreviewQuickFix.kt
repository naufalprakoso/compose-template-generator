package com.kmpfeaturekit.quickfix

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project

class CreatePreviewQuickFix : LocalQuickFix {
    override fun getFamilyName(): String = "Create Compose preview"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        // A future fixture-backed implementation will insert an @Preview block with imports.
    }
}
