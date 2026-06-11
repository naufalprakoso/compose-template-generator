package com.kmpfeaturekit.generator

import com.kmpfeaturekit.model.PlannedFile
import kotlin.test.Test
import kotlin.test.assertTrue

class GeneratedCodeValidatorTest {
    @Test
    fun flagsUnresolvedPlaceholdersAndPackagePathMismatch() {
        val warnings = GeneratedCodeValidator.validate(
            listOf(
                PlannedFile(
                    path = "/repo/shared/src/commonMain/kotlin/com/example/ui/HomeScreen.kt",
                    content = """
                        package com.other.ui

                        fun HomeScreen() = "{{FeatureName}}"
                    """.trimIndent()
                )
            )
        )

        assertTrue(warnings.any { "placeholder" in it })
        assertTrue(warnings.any { "does not match path" in it })
    }

    @Test
    fun flagsMissingImportsForGeneratedCrossPackageReferences() {
        val warnings = GeneratedCodeValidator.validate(
            listOf(
                PlannedFile(
                    path = "/repo/shared/src/commonMain/kotlin/com/example/domain/model/HomeItem.kt",
                    content = """
                        package com.example.domain.model

                        data class HomeItem(val title: String)
                    """.trimIndent()
                ),
                PlannedFile(
                    path = "/repo/shared/src/commonMain/kotlin/com/example/ui/HomeScreen.kt",
                    content = """
                        package com.example.ui

                        fun render(item: HomeItem) = item.title
                    """.trimIndent()
                )
            )
        )

        assertTrue(warnings.any { "HomeItem" in it && "missing an import" in it })
    }

    @Test
    fun flagsGeneratedTypeReferenceWithoutPlannedFile() {
        val warnings = GeneratedCodeValidator.validate(
            listOf(
                PlannedFile(
                    path = "/repo/shared/src/commonMain/kotlin/com/example/ui/HomeScreen.kt",
                    content = """
                        package com.example.ui

                        fun render(state: HomeState): HomeEffect? = null
                    """.trimIndent()
                )
            )
        )

        assertTrue(warnings.any { "HomeState" in it && "no planned file" in it })
        assertTrue(warnings.any { "HomeEffect" in it && "no planned file" in it })
    }
}
