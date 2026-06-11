package com.kmpfeaturekit.quickfix

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.project.Project
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists

class CreateFeatureTestQuickFix : LocalQuickFix {
    override fun getFamilyName(): String = "Create feature test stub"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val file = descriptor.psiElement.containingFile ?: return
        val sourcePath = file.virtualFile?.path ?: return
        val plan = testStub(sourcePath, file.text) ?: return
        WriteCommandAction.runWriteCommandAction(project, familyName, null, Runnable {
            val path = Path.of(plan.path)
            if (!path.exists()) {
                Files.createDirectories(path.parent)
                Files.writeString(path, plan.content)
                LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path)
            }
        })
    }

    data class StubFile(val path: String, val content: String)

    companion object {
        fun testStub(sourcePath: String, content: String): StubFile? {
            val normalizedPath = sourcePath.replace('\\', '/')
            if ("/commonMain/kotlin/" !in normalizedPath) return null
            val packageName = Regex("""(?m)^\s*package\s+([A-Za-z_][A-Za-z0-9_.]*)""")
                .find(content)
                ?.groupValues
                ?.get(1)
                ?: return null
            val className = normalizedPath.substringAfterLast('/').removeSuffix(".kt")
            val testName = "${className}Test"
            val testPath = normalizedPath
                .replace("/commonMain/kotlin/", "/commonTest/kotlin/")
                .replaceAfterLast('/', "$testName.kt")
            return StubFile(
                path = testPath,
                content = """
                    package $packageName

                    import kotlin.test.Test
                    import kotlin.test.assertTrue

                    class $testName {
                        @Test
                        fun generatedStubRuns() {
                            assertTrue(true)
                        }
                    }
                """.trimIndent() + "\n"
            )
        }
    }
}
