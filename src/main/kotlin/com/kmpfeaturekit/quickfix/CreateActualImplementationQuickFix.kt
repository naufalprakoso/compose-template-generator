package com.kmpfeaturekit.quickfix

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project

class CreateActualImplementationQuickFix : LocalQuickFix {
    override fun getFamilyName(): String = "Create actual implementation stubs"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        // Full PSI generation is intentionally conservative in the first release.
        // The generator creates actual files when expect/actual is selected.
    }
}
