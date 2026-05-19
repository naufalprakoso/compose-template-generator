package com.kmpfeaturekit.sourceSet

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists

@Service(Service.Level.PROJECT)
class SourceSetDetectionService(private val project: Project) {
    private val knownSourceSets = listOf(
        "commonMain",
        "commonTest",
        "androidMain",
        "androidUnitTest",
        "iosMain",
        "iosTest"
    )

    fun detect(moduleRootPath: String = project.basePath.orEmpty()): SourceSetScanResult =
        detect(Path.of(moduleRootPath))

    fun detect(moduleRoot: Path): SourceSetScanResult {
        val sourceSets = knownSourceSets.map { name ->
            val kotlinPath = moduleRoot.resolve("src").resolve(name).resolve("kotlin")
            SourceSetInfo(name, kotlinPath.toString(), kotlinPath.exists())
        }
        return SourceSetScanResult(moduleRoot.toString(), sourceSets)
    }

    fun createMissingSourceSetFolders(moduleRootPath: String, names: Collection<String>): List<String> {
        val created = mutableListOf<String>()
        names.forEach { name ->
            val dir = Path.of(moduleRootPath).resolve("src").resolve(name).resolve("kotlin")
            if (!dir.exists()) {
                Files.createDirectories(dir)
                LocalFileSystem.getInstance().refreshAndFindFileByNioFile(dir)
                created += dir.toString()
            }
        }
        return created
    }
}
