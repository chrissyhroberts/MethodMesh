package com.example.methodmesh.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.test.platform.app.InstrumentationRegistry
import com.example.methodmesh.core.methodmesh.runtime.CapabilityFavoritesRepository
import com.example.methodmesh.core.protocols.*
import com.example.methodmesh.core.scheduling.SchedulerDispatchActivity
import com.example.methodmesh.modules.qrcode.As100BarcodeGenerateMethod
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class ProtocolFavouritesTest : FavouritesUiFixture() {
    private fun protocol(name: String = "Protocol favourite fixture", id: String = UUID.randomUUID().toString(), presetId: String = preset().id): ProtocolDefinition =
        ProtocolLibraryRepository.saveProtocol(context, ProtocolDefinition(
            id = id, name = name, description = "Protocol favourite regression fixture",
            steps = listOf(
                ProtocolStep(name = "First step", presetId = presetId, order = 0, outputMode = ProtocolOutputMode.SAVE),
                ProtocolStep(name = "Second step", presetId = presetId, order = 1, outputMode = ProtocolOutputMode.NONE)
            )
        ))

    @Test fun protocolPresetAndCapabilityFavouritesAreIndependentAndDoNotEditTheChain() {
        val sharedId = "barcode.generate"
        val savedPreset = preset(id = sharedId)
        val savedProtocol = protocol(id = sharedId, presetId = savedPreset.id)
        val another = protocol("Another protocol")
        CapabilityFavoritesRepository.set(context, sharedId, true)
        PresetFavoritesRepository.set(context, sharedId, true)
        assertTrue(ProtocolFavoritesRepository.toggle(context, sharedId))
        assertTrue(ProtocolFavoritesRepository.toggle(context, another.id))
        assertEquals(setOf(sharedId, another.id), ProtocolFavoritesRepository.all(context))
        assertFalse(ProtocolFavoritesRepository.toggle(context, sharedId))
        assertTrue(PresetFavoritesRepository.contains(context, sharedId))
        assertTrue(CapabilityFavoritesRepository.contains(context, sharedId))
        assertTrue(ProtocolFavoritesRepository.contains(context, another.id))
        assertEquals(savedProtocol, ProtocolLibraryRepository.protocol(context, sharedId))
        assertEquals(savedPreset, ProtocolLibraryRepository.preset(context, sharedId))
    }

    @Test fun editingAndRenamingKeepsTheFavouriteWhileDeletionRemovesOnlyItsOwnReference() {
        val saved = protocol()
        val another = protocol("Other favourite protocol")
        ProtocolFavoritesRepository.set(context, saved.id, true)
        ProtocolFavoritesRepository.set(context, another.id, true)
        val changedSteps = saved.steps.reversed().mapIndexed { index, step -> step.copy(order = index) }
        val edited = ProtocolLibraryRepository.saveProtocol(context, saved.copy(name = "Renamed protocol", steps = changedSteps))
        assertTrue(ProtocolFavoritesRepository.contains(context, edited.id))
        assertEquals(changedSteps, ProtocolLibraryRepository.protocol(context, edited.id)!!.steps)
        assertTrue(ProtocolLibraryRepository.archivedProtocols(context).any { it.id == saved.id && it.versionIso == saved.versionIso })
        ProtocolLibraryRepository.removeProtocol(context, saved.id)
        assertFalse(ProtocolFavoritesRepository.contains(context, saved.id))
        assertTrue(ProtocolFavoritesRepository.contains(context, another.id))
        assertEquals(another, ProtocolLibraryRepository.protocol(context, another.id))
    }

    @Test fun favouriteSnapshotsDoNotChangeAfterAnotherProtocolIsStarred() {
        ProtocolFavoritesRepository.set(context, "first", true)
        val snapshot = ProtocolFavoritesRepository.all(context)
        ProtocolFavoritesRepository.set(context, "second", true)
        assertEquals(setOf("first"), snapshot)
        assertEquals(setOf("first", "second"), ProtocolFavoritesRepository.all(context))
    }

    @Test fun archivedProtocolsAreNotDashboardShortcutsAndRestoredCopiesKeepTheirNewIdentity() {
        val saved = protocol()
        ProtocolFavoritesRepository.set(context, saved.id, true)
        assertTrue(ProtocolLibraryRepository.archiveProtocol(context, saved.id))
        fixture(content = { HomeScreen() }) { scenario ->
            query("")
            assertNull(text(saved.name))
            assertTrue(ProtocolLibraryRepository.unarchiveProtocol(context, saved.id))
            val restored = ProtocolLibraryRepository.protocols(context).single()
            assertNotEquals(saved.id, restored.id)
            assertFalse(ProtocolFavoritesRepository.contains(context, restored.id))
            scenario.recreate()
            query("")
            assertNull(text(restored.name))
            assertTrue(launches.isEmpty())
        }
    }

    @Test fun protocolLibraryStarsToggleWithoutRunningAndSurviveRecreation() {
        val saved = protocol()
        fixture(content = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ProtocolLibraryCard(0, showPresets = false, showProtocols = true, expandedByDefault = true)
            }
        }) { scenario ->
            click("Add ${saved.name} to favourites", byDescription = true)
            assertTrue(ProtocolFavoritesRepository.contains(context, saved.id))
            await { description("Remove ${saved.name} from favourites") != null }
            assertTrue(launches.isEmpty())
            scenario.recreate()
            click("Remove ${saved.name} from favourites", byDescription = true)
            assertFalse(ProtocolFavoritesRepository.contains(context, saved.id))
            assertTrue(launches.isEmpty())
        }
    }

    @Test fun dashboardSearchStarsProtocolsWithoutChangingPresetAndCapabilityFavourites() {
        val savedPreset = preset("Existing favourite preset")
        val saved = protocol("Unique protocol search fixture", presetId = savedPreset.id)
        CapabilityFavoritesRepository.set(context, "barcode.generate", true)
        PresetFavoritesRepository.set(context, savedPreset.id, true)
        fixture(content = { HomeScreen() }) { scenario ->
            query(saved.name)
            click("Add ${saved.name} to favourites", byDescription = true)
            assertTrue(ProtocolFavoritesRepository.contains(context, saved.id))
            assertTrue(launches.isEmpty())
            query("")
            await { text(saved.name) != null }
            scenario.recreate()
            click("Remove ${saved.name} from favourites", byDescription = true)
            await { text(saved.name) == null }
            assertTrue(PresetFavoritesRepository.contains(context, savedPreset.id))
            assertTrue(CapabilityFavoritesRepository.contains(context, "barcode.generate"))
            await { text(savedPreset.name) != null && text(As100BarcodeGenerateMethod.descriptor.name) != null }
            assertTrue(launches.isEmpty())
        }
    }

    @Test fun dashboardShortcutRunsTheExistingProtocolDispatcherWithTheUnchangedChain() {
        val saved = protocol()
        ProtocolFavoritesRepository.set(context, saved.id, true)
        fixture(content = { HomeScreen() }) {
            click(saved.name)
            await { launches.size == 1 }
            val launch = launches.single()
            assertEquals(SchedulerDispatchActivity::class.java.name, launch.component!!.className)
            assertEquals("com.example.methodmesh.RUN_PROTOCOL", launch.action)
            assertEquals(saved.id, launch.getStringExtra("protocol_id"))
            assertTrue(launch.getBooleanExtra("transient_protocol_run", false))
            assertFalse(launch.getBooleanExtra("test_chain", true))
            assertFalse(launch.getBooleanExtra("suppress_output", true))
            assertTrue(launch.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
            assertEquals(saved, ProtocolLibraryRepository.protocol(context, saved.id))
        }
    }
}

/** Separate write/read phases also verify a real cold process, not just Activity recreation. */
class ProtocolFavouritesPersistenceTest {
    @Test fun storedProtocolKeepsItsStepsAndFavourite() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val phase = InstrumentationRegistry.getArguments().getString("favourite_phase", "combined")
        check(phase in setOf("write", "read", "combined"))
        val files = mutableSetOf<String>()
        val context = FavouriteFixtureContext(app, "protocol-favourite-cold-start-test", files)
        val id = "cold-start-protocol-fixture"
        val presetId = "cold-start-protocol-preset"
        if (phase != "read") {
            ProtocolLibraryRepository.savePreset(context, CapabilityPreset(id = presetId, name = "Cold-start step", methodId = "barcode.generate",
                settingsJson = "{\"barcode_payload\":\"Protocol cold-start payload\"}"))
            ProtocolLibraryRepository.saveProtocol(context, ProtocolDefinition(id = id, name = "Cold-start protocol",
                steps = listOf(ProtocolStep(id = "cold-start-step", name = "Saved step", presetId = presetId, order = 0, outputMode = ProtocolOutputMode.SHARE))))
            ProtocolFavoritesRepository.set(context, id, true)
            assertTrue(context.getSharedPreferences("methodmesh_protocol_library", Context.MODE_PRIVATE).edit().commit())
            assertTrue(context.getSharedPreferences("methodmesh_protocol_favorites", Context.MODE_PRIVATE).edit().commit())
        }
        if (phase != "write") {
            try {
                assertTrue(ProtocolFavoritesRepository.contains(context, id))
                val saved = requireNotNull(ProtocolLibraryRepository.protocol(context, id))
                assertEquals("Cold-start protocol", saved.name)
                assertEquals(listOf(ProtocolStep(id = "cold-start-step", name = "Saved step", presetId = presetId, order = 0, outputMode = ProtocolOutputMode.SHARE)), saved.steps)
                assertEquals("{\"barcode_payload\":\"Protocol cold-start payload\"}", ProtocolLibraryRepository.preset(context, presetId)!!.settingsJson)
            } finally { files.forEach { app.deleteSharedPreferences(it) } }
        }
    }
}
