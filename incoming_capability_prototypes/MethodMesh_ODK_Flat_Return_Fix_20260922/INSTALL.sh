#!/usr/bin/env bash
set -euo pipefail

ROOT="${1:-/Users/icrucrob/AndroidStudioProjects/MethodMesh}"
ACTIVITY="$ROOT/app/src/main/java/com/example/methodmesh/transport/android/ExternalWorkflowActivity.kt"
HELPER="$ROOT/app/src/main/java/com/example/methodmesh/transport/android/ExternalReturnFieldPolicy.kt"
TEST="$ROOT/app/src/test/java/com/example/methodmesh/transport/android/ExternalReturnFieldPolicyTest.kt"

if [[ ! -f "$ACTIVITY" ]]; then
  echo "ExternalWorkflowActivity.kt not found at:"
  echo "  $ACTIVITY"
  exit 1
fi

STAMP="$(date +%Y%m%dT%H%M%S)"
BACKUP="$ACTIVITY.backup-$STAMP"
cp "$ACTIVITY" "$BACKUP"
echo "Backed up ExternalWorkflowActivity.kt -> $BACKUP"

python3 - "$ACTIVITY" <<'PY'
from pathlib import Path
import sys

p = Path(sys.argv[1])
s = p.read_text()

old1 = '''        fields.forEach { (key, value) -> flatReturnFields[key] = value?.toString() }
        // An external form always receives the complete provenance JSON and
'''

new1 = '''        fields.forEach { (key, value) -> flatReturnFields[key] = value?.toString() }

        // ODK/XLSForm group intents send child question names as Android extras.
        // Canonical input fields use the input_ / input64_ namespaces; ordinary
        // child names are therefore explicit caller-declared return placeholders.
        //
        // Re-project any caller-declared canonical output from the unfiltered
        // compact result before building the FULL JSON sidecar. This preserves
        // useful identifiers, hashes and timestamps such as credential_id without
        // globally widening CORE presentation or making capability-specific
        // exceptions in OutputFormatter.
        val completeFields = OutputFormatter.fields(combined, includeProvenance = true)
        ExternalReturnFieldPolicy.mergeCallerDeclaredFields(
            incomingExtraKeys = intent.extras?.keySet().orEmpty(),
            canonicalFields = completeFields,
            selectedFields = selectedFields,
            target = flatReturnFields
        )

        // An external form always receives the complete provenance JSON and
'''

if old1 not in s:
    raise SystemExit(
        "Expected flat-return insertion anchor not found. "
        "No changes made; restore was not necessary."
    )
s = s.replace(old1, new1, 1)

old2 = '''        val completeFields = OutputFormatter.fields(combined, includeProvenance = true)
        completeFields.forEach { (key, value) ->
'''

new2 = '''        completeFields.forEach { (key, value) ->
'''

if old2 not in s:
    raise SystemExit(
        "Expected completeFields anchor not found after first edit. "
        "Restore the backup shown by the installer."
    )
s = s.replace(old2, new2, 1)

p.write_text(s)
print("Patched:", p)
PY

mkdir -p "$(dirname "$HELPER")"
cat > "$HELPER" <<'KT'
package com.example.methodmesh.transport.android

/**
 * Preserves the existing compact transport policy while allowing an Android
 * caller to explicitly request canonical flat outputs by declaring matching
 * return placeholders.
 *
 * ODK/XLSForm group intents send child question names as Intent extras.
 * MethodMesh inputs are namespaced as input_* / input64_*; unprefixed child
 * names can therefore be treated as caller-declared output placeholders.
 *
 * Only keys that also exist in the canonical or explicitly selected result are
 * copied. Unknown form fields cannot manufacture MethodMesh outputs.
 */
internal object ExternalReturnFieldPolicy {
    private val transportControlKeys = setOf(
        "action",
        "actions",
        "method_id",
        "return_mode",
        "returns",
        "ril",
        "methodmesh_return_namespace",
        "payload_mode",
        "return_payload",
        "caller",
        "entity_type",
        "entity_id",
        "participant_id",
        "specimen_id",
        "visit_id",
        "form_id",
        "operator_id"
    )

    internal fun callerDeclaredOutputKeys(incomingExtraKeys: Set<String>): Set<String> =
        incomingExtraKeys
            .asSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .filterNot { it.startsWith("input_") || it.startsWith("input64_") }
            .filterNot { it in transportControlKeys }
            .toCollection(linkedSetOf())

    internal fun mergeCallerDeclaredFields(
        incomingExtraKeys: Set<String>,
        canonicalFields: Map<String, Any?>,
        selectedFields: Map<String, Any?>,
        target: MutableMap<String, String?>
    ) {
        callerDeclaredOutputKeys(incomingExtraKeys).forEach { key ->
            when {
                selectedFields.containsKey(key) ->
                    target[key] = selectedFields[key]?.toString()
                canonicalFields.containsKey(key) ->
                    target[key] = canonicalFields[key]?.toString()
            }
        }
    }
}
KT

mkdir -p "$(dirname "$TEST")"
cat > "$TEST" <<'KT'
package com.example.methodmesh.transport.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalReturnFieldPolicyTest {

    @Test
    fun callerDeclaredAuditFieldsAreReturnedWithoutWideningInputControls() {
        val incoming = linkedSetOf(
            "credential_id",
            "credential_subject_id",
            "credential_envelope_hash",
            "credential_verified_time_iso",
            "issuer_key_id",
            "verification_evidence_hash",
            "methodmesh_full_json",
            "input_pin_length",
            "input_payload_mode",
            "return_mode",
            "method_id"
        )

        val canonical = linkedMapOf<String, Any?>(
            "credential_id" to "cred_123",
            "credential_subject_id" to "PD09875",
            "credential_envelope_hash" to "abc123",
            "credential_verified_time_iso" to "2026-09-22T12:34:56Z",
            "issuer_key_id" to "issuer_001",
            "verification_evidence_hash" to "def456"
        )

        val target = linkedMapOf<String, String?>()
        ExternalReturnFieldPolicy.mergeCallerDeclaredFields(
            incomingExtraKeys = incoming,
            canonicalFields = canonical,
            selectedFields = emptyMap(),
            target = target
        )

        assertEquals("cred_123", target["credential_id"])
        assertEquals("PD09875", target["credential_subject_id"])
        assertEquals("abc123", target["credential_envelope_hash"])
        assertEquals("2026-09-22T12:34:56Z", target["credential_verified_time_iso"])
        assertEquals("issuer_001", target["issuer_key_id"])
        assertEquals("def456", target["verification_evidence_hash"])

        assertFalse(target.containsKey("input_pin_length"))
        assertFalse(target.containsKey("input_payload_mode"))
        assertFalse(target.containsKey("return_mode"))
        assertFalse(target.containsKey("method_id"))

        // methodmesh_full_json is constructed explicitly by ExternalWorkflowActivity.
        assertFalse(target.containsKey("methodmesh_full_json"))
    }

    @Test
    fun selectedAliasWinsCanonicalValueWhenCallerDeclaredIt() {
        val target = linkedMapOf<String, String?>()
        ExternalReturnFieldPolicy.mergeCallerDeclaredFields(
            incomingExtraKeys = setOf("credential_id"),
            canonicalFields = mapOf("credential_id" to "canonical"),
            selectedFields = mapOf("credential_id" to "selected"),
            target = target
        )

        assertEquals("selected", target["credential_id"])
    }

    @Test
    fun unknownCallerFieldCannotCreateAnOutput() {
        val target = linkedMapOf<String, String?>()
        ExternalReturnFieldPolicy.mergeCallerDeclaredFields(
            incomingExtraKeys = setOf("made_up_field"),
            canonicalFields = mapOf("credential_id" to "cred_123"),
            selectedFields = emptyMap(),
            target = target
        )

        assertTrue(target.isEmpty())
    }
}
KT

echo
echo "Patch installed."
echo "Run:"
echo "  cd \"$ROOT\""
echo "  ./gradlew :app:testDebugUnitTest --tests 'com.example.methodmesh.transport.android.ExternalReturnFieldPolicyTest'"
echo "  ./gradlew :app:compileDebugKotlin"
