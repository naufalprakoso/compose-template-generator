package com.kmpfeaturekit.templates

import kotlin.test.Test
import kotlin.test.assertEquals

class TemplateRenderServiceTest {
    @Test
    fun replacesVariables() {
        assertEquals(
            "Feature PaymentHistory in com.example",
            PureTemplateRenderer.render(
                "Feature {{FeatureNamePascal}} in {{packageName}}",
                mapOf("FeatureNamePascal" to "PaymentHistory", "packageName" to "com.example")
            )
        )
    }
}
