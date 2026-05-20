package com.kmpfeaturekit.generator

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.kmpfeaturekit.model.GenerationResult
import com.kmpfeaturekit.model.PlannedFile
import com.kmpfeaturekit.model.PlannedFileKind
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists

@Service(Service.Level.PROJECT)
class FileWriteService(private val project: Project) {
    fun write(files: List<PlannedFile>, overwrite: Boolean = false): GenerationResult {
        val written = mutableListOf<String>()
        val skipped = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val kotlinNormalizer = KotlinPsiFileNormalizer(project)

        WriteAction.run<RuntimeException> {
            files.forEach { planned ->
                val path = Path.of(planned.path)
                val plannedContent = if (path.fileName.toString().endsWith(".kt")) {
                    kotlinNormalizer.normalize(path.fileName.toString(), planned.content)
                } else {
                    planned.content
                }
                if (planned.kind == PlannedFileKind.MODIFY && path.exists()) {
                    val existing = Files.readString(path)
                    if (planned.replacesFile) {
                        if (plannedContent == existing) {
                            skipped += planned.path
                        } else {
                            Files.writeString(path, plannedContent)
                            LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path)
                            written += planned.path
                        }
                    } else if (plannedContent.trim() in existing) {
                        skipped += planned.path
                    } else {
                        Files.writeString(path, existing.trimEnd() + "\n\n" + plannedContent.trimEnd() + "\n")
                        LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path)
                        written += planned.path
                    }
                } else if (path.exists() && !overwrite) {
                    skipped += planned.path
                    warnings += "Skipped existing file: ${planned.path}"
                } else {
                    Files.createDirectories(path.parent)
                    Files.writeString(path, plannedContent)
                    LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path)
                    written += planned.path
                }
            }
        }

        return GenerationResult(written, skipped, warnings)
    }
}
