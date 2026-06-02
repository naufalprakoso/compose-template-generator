package com.kmpfeaturekit.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NameVariantsTest {
    @Test
    fun convertsHumanNameIntoAllVariants() {
        val names = NameVariants.from("Payment History")
        assertEquals("PaymentHistory", names.pascalCase)
        assertEquals("paymentHistory", names.camelCase)
        assertEquals("payment_history", names.snakeCase)
        assertEquals("payment-history", names.kebabCase)
    }

    @Test
    fun rejectsInvalidPackageNames() {
        assertTrue(ValidationUtils.validatePackage("com.example.features").isEmpty())
        assertTrue(ValidationUtils.validatePackage("1.bad").isNotEmpty())
        assertTrue(ValidationUtils.validatePackage("com.class.feature").isNotEmpty())
    }

    @Test
    fun rejectsFeatureNamesThatCannotBecomeKotlinIdentifiers() {
        assertTrue(ValidationUtils.validateFeatureName("Payment History").isEmpty())
        assertTrue(ValidationUtils.validateFeatureName("123").isNotEmpty())
        assertTrue(ValidationUtils.validateFeatureName("!!!").isNotEmpty())
        assertTrue(ValidationUtils.validateFeatureName("fun").isNotEmpty())
    }

    @Test
    fun validatesGenerationTargetFields() {
        assertTrue(ValidationUtils.validateTargetModule("shared").isEmpty())
        assertTrue(ValidationUtils.validateTargetModule("").isNotEmpty())
        assertTrue(ValidationUtils.validateSourceSetRoot("/repo/shared/src").isEmpty())
        assertTrue(ValidationUtils.validateSourceSetRoot("shared/src").isNotEmpty())
        assertTrue(ValidationUtils.validateSourceSetRoot("/repo/shared").isNotEmpty())
        assertTrue(ValidationUtils.validatePlatformSelection(1).isEmpty())
        assertTrue(ValidationUtils.validatePlatformSelection(0).isNotEmpty())
    }
}
