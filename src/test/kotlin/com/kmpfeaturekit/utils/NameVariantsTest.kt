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
    }
}
