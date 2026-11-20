package com.example.methodmesh.modules

import com.example.methodmesh.core.methodmesh.*
import com.example.methodmesh.core.protocols.CapabilityPreset
import com.example.methodmesh.core.protocols.ProtocolDefinition
import com.example.methodmesh.core.protocols.ProtocolStep
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.android.ReturnIntentProjector
import com.example.methodmesh.transport.ril.RilTransportAdapter
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Enumerates the real build index without running capabilities or initialising device services. */
class CapabilityRegistryParityTest {
    companion object {
        private val appRoot = listOf(File("."), File("app"))
            .first { File(it, "src/main/java/com/example/methodmesh/modules").isDirectory }
        private val indexedNames by lazy {
            File(appRoot, "build/generated/res/methodmeshModuleIndex/raw/methodmesh_module_index.txt")
                .readLines().filter(String::isNotBlank)
        }
        private val modules by lazy {
            indexedNames.map { name ->
                try {
                    Class.forName(name).getField("INSTANCE").get(null) as MethodMeshModule
                } catch (error: Throwable) {
                    throw AssertionError("Cannot load indexed module $name", error)
                }
            }
        }
    }

    @Test fun `build index contains every module-owned object`() {
        val sourceNames = File(appRoot, "src/main/java/com/example/methodmesh/modules")
            .walkTopDown().filter { it.isFile && it.name.endsWith("Module.kt") }
            .mapNotNull { file ->
                val text = file.readText()
                val pkg = Regex("(?m)^package\\s+([A-Za-z0-9_.]+)").find(text)?.groupValues?.get(1)
                val name = Regex("(?m)^object\\s+([A-Za-z0-9_]+Module)\\s*:").find(text)?.groupValues?.get(1)
                if (pkg == null || name == null) null else "$pkg.$name"
            }.toSet()
        assertTrue("No module objects found", sourceNames.isNotEmpty())
        assertEquals(sourceNames, indexedNames.toSet())
        assertEquals("Duplicate module index entries", indexedNames.size, indexedNames.distinct().size)
    }

    @Test fun `all indexed modules expose unique internally consistent canonical identities`() {
        val methods = modules.flatMap { it.as100Methods() }
        val screens = modules.flatMap { it.capabilityScreens() }
        assertEquals(modules.size, modules.map { it.moduleId }.distinct().size)
        assertEquals(methods.size, methods.map { it.id }.distinct().size)
        assertEquals(screens.size, screens.map { it.capabilityId }.distinct().size)
        val violations = modules.flatMap { module ->
            module.as100Methods().flatMap { method -> buildList {
                if (method.ref.id.value != method.id) add("${module.moduleId}/${method.id}: ref=${method.ref.id.value}")
                if (method.descriptor.id.value != method.id) add("${module.moduleId}/${method.id}: descriptor=${method.descriptor.id.value}")
                if (method.contract.method.id.value != method.id) add("${module.moduleId}/${method.id}: contract=${method.contract.method.id.value}")
                if (method.descriptor.version.isNullOrBlank()) add("${module.moduleId}/${method.id}: missing version")
            } }
        }
        assertTrue(violations.joinToString("\n"), violations.isEmpty())
        println("Inventory: ${modules.size} modules, ${methods.size} methods, ${screens.size} native screens")
        modules.forEach { module ->
            println("${module.moduleId}: methods=${module.as100Methods().size}, screens=${module.capabilityScreens().size}, settings=${module.capabilitySettings().size}")
        }
    }

    @Test fun `module screens settings and language bindings refer to their canonical methods`() {
        val violations = modules.flatMap { module ->
            val ids = module.as100Methods().map { it.id }.toSet()
            buildList {
                module.capabilityScreens().filterNot { it.capabilityId in ids }.forEach {
                    add("${module.moduleId}: orphan screen ${it.capabilityId}")
                }
                module.capabilitySettings().keys.filterNot { it in ids }.forEach {
                    add("${module.moduleId}: orphan settings $it")
                }
                module.rilBindings().filterNot { it.actionId in ids }.forEach {
                    add("${module.moduleId}: orphan binding ${it.phrase} -> ${it.actionId}")
                }
            }
        }
        assertTrue(violations.joinToString("\n"), violations.isEmpty())
    }

    @Test fun `every canonical capability is representable in transport presets and protocol steps`() {
        val violations = mutableListOf<String>()
        modules.flatMap { it.as100Methods() }.forEach { method ->
            val parsed = RilTransportAdapter.parse(
                mapOf("method_id" to method.id, "input_parity_probe" to "unchanged", "return_mode" to "json"),
                "registry_parity_test"
            )
            if (parsed.actionIds != listOf(method.id)) {
                violations += "${method.id}: actions=${parsed.actionIds}, selectors=${parsed.returnSelectors}"
                return@forEach
            }
            assertEquals(method.id, "unchanged", parsed.settings["parity_probe"])
            assertTrue("${method.id}: ${parsed.warnings}", parsed.warnings.isEmpty())
            val preset = CapabilityPreset(name = method.descriptor.name, methodId = parsed.methodId!!)
            val protocol = ProtocolDefinition(name = "Test", steps = listOf(ProtocolStep(
                name = preset.name, presetId = preset.id, order = 0
            )))
            assertEquals(method.id, preset.methodId)
            assertEquals(preset.id, protocol.steps.single().presetId)
        }
        assertTrue(violations.joinToString("\n"), violations.isEmpty())
    }

    @Test fun `every declared scalar output can cross the generic external projection boundary`() {
        // Synthetic values test transport representability, not hardware execution or actual module output completeness.
        modules.flatMap { it.as100Methods() }.forEach { method ->
            val expected = (method.descriptor.outputs + method.contract.producedFields)
                .distinct().associateWith { "fixture:$it" }
            val result = ExecutionResult(
                request = ExecutionRequest(action = method.id, method = method.ref),
                status = TransformationStatus.Succeeded,
                observations = listOf(Observation(
                    phenomenon = "parity.fixture", values = expected,
                    provenance = ProvenanceContext("test")
                ))
            )
            val canonical = OutputFormatter.fields(result)
            // Use the unfiltered canonical scope. Caller-declared placeholders deliberately
            // exclude control/context keys (for example caller), even if a method also emits them.
            val projected = ReturnIntentProjector.projectFlatReturn(canonical.mapValues { it.value?.toString() }, "")
            expected.forEach { (key, value) -> assertEquals("${method.id}/$key", value, projected.extras[key]) }
        }
    }
}
