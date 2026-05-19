package com.kmpfeaturekit.model

import kotlin.test.Test
import kotlin.test.assertEquals

class DisplayLabelTest {
    @Test
    fun selectValuesUseHumanReadableLabels() {
        assertEquals("Slack Circuit", ArchitectureType.SLACK_CIRCUIT.toString())
        assertEquals("Plain State Holder", StateHolderType.PLAIN_STATE_HOLDER.toString())
        assertEquals("Navigation Compose", NavigationType.NAVIGATION_COMPOSE.toString())
        assertEquals("Hilt Android only", DependencyInjectionType.HILT_ANDROID_ONLY.toString())
        assertEquals("Room Android only", PersistenceType.ROOM_ANDROID_ONLY.toString())
    }

    @Test
    fun platformLabelsUseProductCasing() {
        assertEquals("Android", PlatformTarget.ANDROID.toString())
        assertEquals("iOS", PlatformTarget.IOS.toString())
    }
}
