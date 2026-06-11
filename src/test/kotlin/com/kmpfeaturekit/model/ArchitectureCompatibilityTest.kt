package com.kmpfeaturekit.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArchitectureCompatibilityTest {
    @Test
    fun circuitOnlyAllowsCircuitPresenterAndNavigation() {
        assertEquals(listOf(StateHolderType.CIRCUIT_PRESENTER), ArchitectureCompatibility.stateHoldersFor(ArchitectureType.SLACK_CIRCUIT))
        assertEquals(listOf(NavigationType.CIRCUIT_NAVIGATION), ArchitectureCompatibility.navigationFor(ArchitectureType.SLACK_CIRCUIT))
    }

    @Test
    fun decomposeOnlyAllowsDecomposeComponentAndNavigation() {
        assertEquals(listOf(StateHolderType.DECOMPOSE_COMPONENT), ArchitectureCompatibility.stateHoldersFor(ArchitectureType.DECOMPOSE))
        assertEquals(listOf(NavigationType.DECOMPOSE_NAVIGATION), ArchitectureCompatibility.navigationFor(ArchitectureType.DECOMPOSE))
    }

    @Test
    fun mvvmDoesNotAllowCircuitOrDecomposeSpecificChoices() {
        val stateHolders = ArchitectureCompatibility.stateHoldersFor(ArchitectureType.MVVM)
        val navigation = ArchitectureCompatibility.navigationFor(ArchitectureType.MVVM)
        assertFalse(StateHolderType.CIRCUIT_PRESENTER in stateHolders)
        assertFalse(StateHolderType.DECOMPOSE_COMPONENT in stateHolders)
        assertFalse(NavigationType.CIRCUIT_NAVIGATION in navigation)
        assertFalse(NavigationType.DECOMPOSE_NAVIGATION in navigation)
        assertTrue(NavigationType.NONE in navigation)
        assertTrue(NavigationType.NAVIGATION_COMPOSE in navigation)
        assertTrue(NavigationType.VOYAGER in navigation)
    }

    @Test
    fun incompatibleDetectedNavigationIsCoercedToArchitectureDefault() {
        assertEquals(
            NavigationType.CIRCUIT_NAVIGATION,
            ArchitectureCompatibility.coerceNavigation(ArchitectureType.SLACK_CIRCUIT, NavigationType.VOYAGER)
        )
    }
}
