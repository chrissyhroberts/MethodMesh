package com.example.methodmesh.core.scheduling

import org.junit.Assert.*
import org.junit.Test

class DispatchCheckpointTest {
    private fun restore(state: DispatchCheckpoint) = DispatchCheckpoint.restore(
        state.phase.name, state.initialised, state.resumePhase.name
    )

    @Test fun `initialisation is retained instead of clearing the first step context again`() {
        val state = DispatchCheckpoint().initialise()
        val restored = restore(state)
        assertTrue(restored.initialised)
        assertSame(restored, restored.initialise())
    }

    @Test fun `restored pending child cannot launch twice and delivers one result`() {
        val waiting = DispatchCheckpoint(phase = DispatchCheckpoint.Phase.Ready).beginChild()!!
        val restored = restore(waiting)
        assertNull(restored.beginChild())
        val received = restored.takeResult()!!
        assertEquals(DispatchCheckpoint.Phase.ResultReceived, received.phase)
        assertNull(received.takeResult())
        assertNull(restore(received).takeResult())
    }

    @Test fun `manual prompt survives recreation and keep going returns to the prompt without relaunch`() {
        val prompt = DispatchCheckpoint(phase = DispatchCheckpoint.Phase.ManualCompletion, initialised = true)
        val cancel = restore(prompt.showCancel())
        val resumed = cancel.keepGoing()
        assertEquals(prompt.phase, resumed.phase)
        assertEquals(prompt.initialised, resumed.initialised)
        assertNull(resumed.beginChild())
        assertNull(resumed.takeResult())
    }

    @Test fun `only explicit ready state may start a child`() {
        DispatchCheckpoint.Phase.entries.filterNot { it == DispatchCheckpoint.Phase.Ready }.forEach {
            assertNull(it.name, DispatchCheckpoint(phase = it).beginChild())
        }
    }

    @Test fun `intro summary and completed state survive without becoming executable`() {
        listOf(DispatchCheckpoint.Phase.Intro, DispatchCheckpoint.Phase.Summary, DispatchCheckpoint.Phase.Finished).forEach {
            val restored = restore(DispatchCheckpoint(phase = it, initialised = true))
            assertEquals(it, restored.phase)
            assertNull(restored.beginChild())
            assertNull(restored.takeResult())
        }
    }

    @Test fun `unknown or missing restored checkpoint is interrupted not a fresh run`() {
        listOf(null, "unknown").forEach {
            val state = DispatchCheckpoint.restore(it, false, null)
            assertEquals(DispatchCheckpoint.Phase.Interrupted, state.phase)
            assertNull(state.beginChild())
        }
    }
}
