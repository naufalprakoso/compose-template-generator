package com.kmpfeaturekit.dialogs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FeatureDialogDefaultsResolverTest {
    @Test
    fun resolvesSourceSetRootFromCommonMainKotlinFile() {
        assertEquals(
            "/repo/shared/src",
            FeatureDialogDefaultsResolver.sourceSetRootForPath("/repo/shared/src/commonMain/kotlin/com/example/HomeScreen.kt")
        )
    }

    @Test
    fun resolvesSourceSetRootFromAndroidMainDirectory() {
        assertEquals(
            "/repo/feature/payment/src",
            FeatureDialogDefaultsResolver.sourceSetRootForPath("/repo/feature/payment/src/androidMain/kotlin/com/example/payment")
        )
    }

    @Test
    fun resolvesSourceSetRootFromWindowsStylePath() {
        assertEquals(
            "C:/repo/shared/src",
            FeatureDialogDefaultsResolver.sourceSetRootForPath(
                "C:\\repo\\shared\\src\\commonMain\\kotlin\\com\\example\\HomeScreen.kt"
            )
        )
    }

    @Test
    fun resolvesSourceSetRootFromMixedSeparators() {
        assertEquals(
            "C:/repo/shared/src",
            FeatureDialogDefaultsResolver.sourceSetRootForPath(
                "C:\\repo\\shared/src/androidMain/kotlin/com/example/payment"
            )
        )
    }

    @Test
    fun returnsNullWhenPathIsOutsideKmpSourceSets() {
        assertNull(FeatureDialogDefaultsResolver.sourceSetRootForPath("/repo/settings.gradle.kts"))
    }
}
