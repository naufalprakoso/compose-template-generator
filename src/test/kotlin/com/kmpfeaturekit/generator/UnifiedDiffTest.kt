package com.kmpfeaturekit.generator

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class UnifiedDiffTest {
    @Test
    fun rendersCompactUnifiedDiffForModifiedFiles() {
        val diff = UnifiedDiff.render(
            path = "/repo/shared/build.gradle.kts",
            oldContent = """
                kotlin {
                    sourceSets {
                        commonMain.dependencies {
                            implementation(kotlin("stdlib"))
                        }
                    }
                }
            """.trimIndent(),
            newContent = """
                kotlin {
                    sourceSets {
                        commonMain.dependencies {
                            implementation(kotlin("stdlib"))
                            implementation("io.ktor:ktor-client-core:3.0.0")
                        }
                    }
                }
            """.trimIndent()
        )

        assertContains(diff, "--- /repo/shared/build.gradle.kts")
        assertContains(diff, "+++ /repo/shared/build.gradle.kts")
        assertContains(diff, "+                            implementation(\"io.ktor:ktor-client-core:3.0.0\")")
        assertFalse("-                            implementation(\"io.ktor:ktor-client-core:3.0.0\")" in diff)
    }
}
