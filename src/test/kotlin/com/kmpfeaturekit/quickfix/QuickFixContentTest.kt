package com.kmpfeaturekit.quickfix

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class QuickFixContentTest {
    @Test
    fun previewQuickFixAddsPreviewImportsAndStub() {
        val updated = CreatePreviewQuickFix.previewContent(
            content = """
                package com.example.ui

                import androidx.compose.runtime.Composable

                @Composable
                fun HomeScreen(state: HomeState) {
                }
            """.trimIndent(),
            fallbackName = "HomeScreen"
        )

        assertNotNull(updated)
        assertContains(updated, "import org.jetbrains.compose.ui.tooling.preview.Preview")
        assertContains(updated, "@Preview")
        assertContains(updated, "private fun HomeScreenPreview()")
        assertContains(updated, "TODO Provide preview state")
    }

    @Test
    fun testQuickFixCreatesCommonTestPath() {
        val stub = CreateFeatureTestQuickFix.testStub(
            sourcePath = "/repo/shared/src/commonMain/kotlin/com/example/ui/HomeScreen.kt",
            content = "package com.example.ui\n"
        )

        assertNotNull(stub)
        assertEquals("/repo/shared/src/commonTest/kotlin/com/example/ui/HomeScreenTest.kt", stub.path)
        assertContains(stub.content, "class HomeScreenTest")
    }

    @Test
    fun actualQuickFixCreatesAndroidAndIosStubs() {
        val stubs = CreateActualImplementationQuickFix.actualStubs(
            sourcePath = "/repo/shared/src/commonMain/kotlin/com/example/platform/DeviceName.kt",
            content = """
                package com.example.platform

                expect fun deviceName(): String
            """.trimIndent()
        )

        assertEquals(2, stubs.size)
        assertContains(stubs[0].path, "/androidMain/kotlin/")
        assertContains(stubs[0].content, "actual fun deviceName(): String = \"Android\"")
        assertContains(stubs[1].path, "/iosMain/kotlin/")
        assertContains(stubs[1].content, "actual fun deviceName(): String = \"iOS\"")
    }
}
