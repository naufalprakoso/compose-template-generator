package com.kmpfeaturekit.quickfix

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists

class CreateActualImplementationQuickFix : LocalQuickFix {
    override fun getFamilyName(): String = "Create actual implementation stubs"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val file = descriptor.psiElement.containingFile ?: return
        val sourcePath = file.virtualFile?.path ?: return
        val stubs = actualStubs(sourcePath, file.text)
        if (stubs.isEmpty()) return
        WriteCommandAction.runWriteCommandAction(project, familyName, null, Runnable {
            stubs.forEach { stub ->
                val path = Path.of(stub.path)
                if (!path.exists()) {
                    Files.createDirectories(path.parent)
                    Files.writeString(path, stub.content)
                    LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path)
                }
            }
        })
    }

    data class StubFile(val path: String, val content: String)

    companion object {
        fun actualStubs(sourcePath: String, content: String): List<StubFile> {
            val normalizedPath = sourcePath.replace('\\', '/')
            if ("/commonMain/kotlin/" !in normalizedPath || "expect " !in content) return emptyList()
            val packageName = Regex("""(?m)^\s*package\s+([A-Za-z_][A-Za-z0-9_.]*)""")
                .find(content)
                ?.groupValues
                ?.get(1)
                ?: return emptyList()
            val declaration = expectDeclaration(content) ?: return emptyList()
            return listOf("androidMain" to "Android", "iosMain" to "iOS").map { (sourceSet, platformName) ->
                StubFile(
                    path = normalizedPath.replace("/commonMain/kotlin/", "/$sourceSet/kotlin/"),
                    content = """
                        package $packageName

                        ${declaration.actualStub(platformName)}
                    """.trimIndent() + "\n"
                )
            }
        }

        private fun expectDeclaration(content: String): ExpectDeclaration? {
            Regex("""expect\s+class\s+([A-Z][A-Za-z0-9_]*)""").find(content)?.let {
                return ExpectDeclaration.Class(it.groupValues[1])
            }
            Regex("""expect\s+object\s+([A-Z][A-Za-z0-9_]*)""").find(content)?.let {
                return ExpectDeclaration.Object(it.groupValues[1])
            }
            Regex("""expect\s+fun\s+([A-Za-z_][A-Za-z0-9_]*)\s*\(([^)]*)\)\s*:?\s*([A-Za-z0-9_?.<>]+)?""")
                .find(content)
                ?.let {
                    return ExpectDeclaration.Function(
                        name = it.groupValues[1],
                        parameters = it.groupValues[2],
                        returnType = it.groupValues.getOrNull(3).orEmpty().ifBlank { "Unit" }
                    )
                }
            return null
        }

        private sealed interface ExpectDeclaration {
            fun actualStub(platformName: String): String

            data class Class(val name: String) : ExpectDeclaration {
                override fun actualStub(platformName: String): String =
                    "actual class $name"
            }

            data class Object(val name: String) : ExpectDeclaration {
                override fun actualStub(platformName: String): String =
                    "actual object $name"
            }

            data class Function(
                val name: String,
                val parameters: String,
                val returnType: String
            ) : ExpectDeclaration {
                override fun actualStub(platformName: String): String {
                    val body = when (returnType) {
                        "Unit" -> ""
                        "String" -> " = \"$platformName\""
                        "Boolean" -> " = true"
                        "Int" -> " = 0"
                        "Long" -> " = 0L"
                        else -> " = TODO(\"Provide $platformName implementation\")"
                    }
                    return if (returnType == "Unit") {
                        "actual fun $name($parameters) = Unit"
                    } else {
                        "actual fun $name($parameters): $returnType$body"
                    }
                }
            }
        }
    }
}
