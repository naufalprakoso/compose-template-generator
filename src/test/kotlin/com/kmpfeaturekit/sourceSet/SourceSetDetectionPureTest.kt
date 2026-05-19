package com.kmpfeaturekit.sourceSet

import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SourceSetDetectionPureTest {
    @Test
    fun scanResultReportsKnownSourceSets() {
        val temp = createTempDirectory()
        temp.resolve("src/commonMain/kotlin").createDirectories()
        val result = SourceSetScanResult(
            moduleRoot = temp.toString(),
            sourceSets = listOf(
                SourceSetInfo("commonMain", temp.resolve("src/commonMain/kotlin").toString(), true),
                SourceSetInfo("iosMain", temp.resolve("src/iosMain/kotlin").toString(), false)
            )
        )
        assertTrue(result.has("commonMain"))
        assertFalse(result.has("iosMain"))
    }
}
