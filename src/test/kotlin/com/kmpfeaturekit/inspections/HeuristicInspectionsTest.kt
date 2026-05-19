package com.kmpfeaturekit.inspections

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HeuristicInspectionsTest {
    @Test
    fun flagsAndroidApiInsideCommonMainOnly() {
        assertTrue(HeuristicInspections.androidOnlyApiProblem("src/commonMain/kotlin/Foo.kt", "import android.content.Context"))
        assertFalse(HeuristicInspections.androidOnlyApiProblem("src/androidMain/kotlin/Foo.kt", "import android.content.Context"))
    }

    @Test
    fun flagsScreenWithoutPreview() {
        assertTrue(HeuristicInspections.missingPreviewProblem("src/commonMain/kotlin/PaymentScreen.kt", "@Composable fun PaymentScreen() {}"))
        assertFalse(HeuristicInspections.missingPreviewProblem("src/commonMain/kotlin/PaymentScreen.kt", "@Preview @Composable fun Preview() {}"))
    }

    @Test
    fun flagsArchitecturePlacementSmell() {
        assertTrue(HeuristicInspections.suspiciousArchitectureProblem("feature/presentation/PaymentRepository.kt"))
        assertTrue(HeuristicInspections.suspiciousArchitectureProblem("feature/data/PaymentScreen.kt"))
    }
}
