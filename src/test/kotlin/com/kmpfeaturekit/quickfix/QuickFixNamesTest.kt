package com.kmpfeaturekit.quickfix

import kotlin.test.Test
import kotlin.test.assertEquals

class QuickFixNamesTest {
    @Test
    fun quickFixesHaveUserFacingNames() {
        assertEquals("Create actual implementation stubs", CreateActualImplementationQuickFix().familyName)
        assertEquals("Move file to androidMain", MoveToAndroidSourceSetQuickFix().familyName)
        assertEquals("Create Compose preview", CreatePreviewQuickFix().familyName)
        assertEquals("Create feature test stub", CreateFeatureTestQuickFix().familyName)
    }
}
