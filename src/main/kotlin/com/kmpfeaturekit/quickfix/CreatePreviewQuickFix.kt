package com.kmpfeaturekit.quickfix

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.kmpfeaturekit.utils.KotlinSourcePatcher

class CreatePreviewQuickFix : LocalQuickFix {
    override fun getFamilyName(): String = "Create Compose preview"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val file = descriptor.psiElement.containingFile ?: return
        val document = PsiDocumentManager.getInstance(project).getDocument(file) ?: return
        val updated = previewContent(file.text, file.virtualFile?.nameWithoutExtension ?: "Screen") ?: return
        WriteCommandAction.runWriteCommandAction(project, familyName, null, Runnable {
            document.setText(updated)
            PsiDocumentManager.getInstance(project).commitDocument(document)
            FileDocumentManager.getInstance().saveDocument(document)
        }, file)
    }

    companion object {
        fun previewContent(content: String, fallbackName: String): String? {
            if ("@Preview" in content) return null
            val composableName = Regex("""@Composable\s+(?:private\s+)?fun\s+([A-Z][A-Za-z0-9_]*)\s*\(""")
                .find(content)
                ?.groupValues
                ?.get(1)
                ?: fallbackName
            val previewName = "${composableName}Preview"
            val canCallWithoutArguments = Regex("""fun\s+$composableName\s*\(\s*(?:modifier\s*:[^)]+=\s*[^)]+)?\)""")
                .containsMatchIn(content)
            val body = if (canCallWithoutArguments) {
                "    $composableName()"
            } else {
                "    // TODO Provide preview state for $composableName."
            }
            val preview = """

                @Preview
                @Composable
                private fun $previewName() {
                $body
                }
            """.trimIndent()
            return KotlinSourcePatcher
                .addImport(content.trimEnd(), "org.jetbrains.compose.ui.tooling.preview.Preview")
                .let { KotlinSourcePatcher.addImport(it, "androidx.compose.runtime.Composable") }
                .trimEnd() + "\n\n" + preview + "\n"
        }
    }
}
