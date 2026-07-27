package com.example.researchos.modules.gpstargetnavigator

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GpsTargetNavigatorLifecycleContractTest {
    @Test
    fun `external navigation starts when permission is available`() {
        assertTrue(
            shouldAutoStartNavigation(
                startsImmediately = true,
                hasLocationPermission = true,
                isIdle = true
            )
        )
    }

    @Test
    fun `navigation does not restart or bypass permission`() {
        assertFalse(
            shouldAutoStartNavigation(
                startsImmediately = true,
                hasLocationPermission = false,
                isIdle = true
            )
        )
        assertFalse(
            shouldAutoStartNavigation(
                startsImmediately = true,
                hasLocationPermission = true,
                isIdle = false
            )
        )
        assertFalse(
            shouldAutoStartNavigation(
                startsImmediately = false,
                hasLocationPermission = true,
                isIdle = true
            )
        )
    }

    @Test
    fun `location continues refining while arrival awaits save`() {
        assertTrue(
            shouldCollectLocationUpdates(
                hasLocationPermission = true,
                isNavigating = false,
                isAwaitingSave = true
            )
        )
        assertFalse(
            shouldCollectLocationUpdates(
                hasLocationPermission = true,
                isNavigating = false,
                isAwaitingSave = false
            )
        )
        assertFalse(
            shouldCollectLocationUpdates(
                hasLocationPermission = false,
                isNavigating = false,
                isAwaitingSave = true
            )
        )
    }

    @Test
    fun `AR instructions describe the shortest visible turn`() {
        assertEquals("Target is ahead", arTurnInstruction(8f, arrived = false))
        assertEquals("Turn left 32°", arTurnInstruction(-32f, arrived = false))
        assertEquals("Turn right 47°", arTurnInstruction(47f, arrived = false))
        assertEquals(
            "Target is within the arrival range",
            arTurnInstruction(90f, arrived = true)
        )
    }
}
