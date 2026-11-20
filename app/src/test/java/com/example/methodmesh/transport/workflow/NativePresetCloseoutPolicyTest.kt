package com.example.methodmesh.transport.workflow

import org.junit.Assert.*
import org.junit.Test

class NativePresetCloseoutPolicyTest {
    private val native = mapOf("methodmesh_native_preset_run" to "true")
    @Test fun `ordinary external and ODK returns never open Home`() {
        assertFalse(NativePresetCloseoutPolicy.opensHome(emptyMap()))
        assertFalse(NativePresetCloseoutPolicy.opensHome(mapOf("methodmesh_preset_result_action" to "home")))
    }
    @Test fun `native default Home and successful Share retain Home routing`() {
        assertTrue(NativePresetCloseoutPolicy.opensHome(native))
        assertTrue(NativePresetCloseoutPolicy.opensHome(native + ("methodmesh_preset_result_action" to "share")))
    }
    @Test fun `input prefixed flags follow the same native policy`() {
        assertTrue(NativePresetCloseoutPolicy.opensHome(mapOf("input_methodmesh_native_preset_run" to "true")))
        assertFalse(NativePresetCloseoutPolicy.opensHome(mapOf("input_methodmesh_native_preset_run" to "true", "input_methodmesh_finish_to_launcher" to "true")))
    }
    @Test fun `Save and finish returns through the caller instead of opening Home`() {
        assertFalse(NativePresetCloseoutPolicy.opensHome(native + ("methodmesh_preset_result_action" to "save")))
    }
    @Test fun `widget schedule headless protocol and sequence origins keep their return route`() {
        listOf("methodmesh_finish_to_launcher", "methodmesh_headless", "methodmesh_protocol_step_run", "methodmesh_sequence_step_run").forEach { flag ->
            assertFalse(flag, NativePresetCloseoutPolicy.opensHome(native + (flag to "true")))
            assertFalse("input_$flag", NativePresetCloseoutPolicy.opensHome(native + ("input_$flag" to "true")))
        }
    }
}
