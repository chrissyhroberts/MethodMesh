package com.example.methodmesh.modules.nfc

import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertTrue
import org.junit.Test

class NfcOdkFormCompilerTest {
    @Test
    fun `maps ordinary shared-string XLSX and pins contract`() {
        val result = NfcOdkFormCompiler.compile(
            input = minimalWorkbook(),
            originalName = "ordinary_form.xlsx",
            mode = NfcOdkFormCompiler.Mode.PROVISIONING,
            contractVersion = "v1",
            now = Instant.parse("2026-09-30T15:10:53.737Z")
        )

        assertTrue(result.outputName.contains("ordinary_form__methodmesh_nfc_v1_20260930151053737.xlsx"))
        assertTrue(result.changedFields.contains("survey.valid_until_date"))
        assertTrue(result.statement.contains("Sentinel"))
        assertTrue(result.statement.contains("commitment_recipe.v1"))
        val entries = unzip(result.bytes)
        val survey = entries.getValue("xl/worksheets/sheet1.xml").toString(Charsets.UTF_8)
        val settings = entries.getValue("xl/worksheets/sheet2.xml").toString(Charsets.UTF_8)
        assertTrue(survey.contains("input_nfc_form_contract_version='v1'"))
        assertTrue(survey.contains("input_valid_until_date=${'$'}{valid_until_date_iso}"))
        assertTrue(survey.contains("Staff member name"))
        assertTrue(survey.contains("input_credential_subject_id=${'$'}{credential_subject_id_input}"))
        assertTrue(survey.contains("methodmesh_full_json"))
        assertTrue("compiler-only mm_commit leaked into release survey" , survey.contains(">mm_commit<").not())
        assertTrue(survey.contains("attestation.create").not())
        assertTrue(survey.contains("path\\\":\\\"start\\\"" ).not())
        assertTrue(survey.indexOf("methodmesh_nfc_provisioning") < survey.indexOf("credential_subject_id_input"))
        assertTrue(survey.indexOf("methodmesh_nfc_provisioning_end") > survey.indexOf("methodmesh_full_json"))
        assertTrue(Regex("ref=\"A1:[A-Z]+[1-9][0-9]+\"").containsMatchIn(survey))
        assertTrue(settings.contains("20260930151053737"))

        val verification = NfcOdkFormCompiler.compile(
            input = minimalWorkbook(),
            originalName = "ordinary_form.xlsx",
            mode = NfcOdkFormCompiler.Mode.VERIFICATION,
            now = Instant.parse("2026-09-30T15:10:53.737Z")
        )
        val verificationSurvey = unzip(verification.bytes).getValue("xl/worksheets/sheet1.xml").toString(Charsets.UTF_8)
        assertTrue(verificationSurvey.contains("input_verification_method='NfcCredential'"))
        assertTrue(verificationSurvey.contains("input_verification_execution_id=" + "${'$'}{mm_auth_methodmesh_execution_id}"))
        assertTrue(verificationSurvey.contains("methodmesh_return_namespace='mm_auth'"))
        assertTrue(verificationSurvey.contains("attestation.create"))
        assertTrue(verificationSurvey.contains("input_attestation_method_version='1.2.0'"))
        assertTrue(verificationSurvey.contains("input_attestation_schema_version='4'"))
        assertTrue(verificationSurvey.contains("methodmesh.commitment_recipe.v1"))
        assertTrue(verificationSurvey.contains("NfcCredential"))
        assertTrue(verificationSurvey.contains("Verification method (NFC credential)"))
        assertTrue(verificationSurvey.contains(">Fingerprint<").not())
        assertTrue(verificationSurvey.contains("input_credential_subject_id=${'$'}{participant_id}").not())
        assertTrue(verificationSurvey.contains("mm_authenticate_operator"))
        assertTrue(verificationSurvey.contains("mm_auth_methodmesh_status"))
        assertTrue(verificationSurvey.contains("mm_auth_verification_evidence_hash"))
        assertTrue(verificationSurvey.contains("mm_auth_issuer_public_key_base64"))
        assertTrue(verificationSurvey.contains("mm_auth_credential_id"))
        assertTrue(verificationSurvey.contains("Issuer key evidence"))
        assertTrue(verificationSurvey.contains("mm_finalize_for_attestation"))
        assertTrue(verificationSurvey.contains("mm_finalize_for_attestation} = 'yes'"))
        assertTrue(verificationSurvey.contains("string-length(\${mm_event_payload_hash}) = 64"))
        assertTrue(verificationSurvey.contains("mm_event_payload_hash"))
        listOf(
            "mm_auth_methodmesh_execution_id", "mm_auth_methodmesh_method_id", "mm_auth_methodmesh_status",
            "mm_auth_credential_verified", "mm_auth_credential_verification_message", "mm_auth_credential_id",
            "mm_auth_credential_subject_id", "mm_auth_pin_verified", "mm_auth_issuer_signature_valid",
            "mm_auth_issuer_trust_status", "mm_auth_issuer_trust_policy", "mm_auth_issuer_key_id",
            "mm_auth_issuer_public_key_fingerprint_sha256", "mm_auth_issuer_public_key_base64", "mm_auth_tag_uid_hex",
            "mm_auth_verification_evidence_hash", "mm_auth_credential_envelope_hash", "mm_auth_credential_verified_time_iso",
            "mm_auth_methodmesh_full_json"
        ).forEach { field -> assertTrue("missing generated field $field", verificationSurvey.contains(field)) }
        assertTrue(verificationSurvey.contains("MethodMesh authentication return"))
    }

    private fun minimalWorkbook(): ByteArray {
        val shared = listOf("type", "name", "label", "calculation", "version", "form_id", "form_title", "v1", "ordinary", "text", "start", "started_at", "Start", "value", "mm_yes", "yes", "Yes", "list_name", "mm_commit")
        fun s(index: Int, row: Int, col: String) = "<c r=\"$col$row\" t=\"s\"><v>$index</v></c>"
        val survey = """<?xml version="1.0"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>
            <row r="1">${s(0, 1, "A")}${s(1, 1, "B")}${s(2, 1, "C")}${s(3, 1, "D")}</row>
            <row r="2">${s(9, 2, "A")}${s(8, 2, "B")}${s(12, 2, "C")}${s(11, 2, "D")}</row>
        </sheetData></worksheet>""".replace("\n", "")
        val settings = """<?xml version="1.0"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>
            <row r="1">${s(4, 1, "A")}${s(5, 1, "B")}${s(6, 1, "C")}</row>
            <row r="2">${s(7, 2, "A")}${s(8, 2, "B")}${s(8, 2, "C")}</row>
        </sheetData></worksheet>""".replace("\n", "")
        val choices = """<?xml version="1.0"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>
            <row r="1">${s(17, 1, "A")}${s(1, 1, "B")}${s(2, 1, "C")}</row>
        </sheetData></worksheet>""".replace("\n", "")
        val strings = shared.joinToString("") { "<si><t>${it}</t></si>" }
        return zip(mapOf(
            "xl/workbook.xml" to """<?xml version="1.0"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="survey" sheetId="1" r:id="rId1"/><sheet name="settings" sheetId="2" r:id="rId2"/><sheet name="choices" sheetId="3" r:id="rId3"/></sheets></workbook>""".toByteArray(),
            "xl/_rels/workbook.xml.rels" to """<?xml version="1.0"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Target="worksheets/sheet1.xml" Type="worksheet"/><Relationship Id="rId2" Target="worksheets/sheet2.xml" Type="worksheet"/><Relationship Id="rId3" Target="worksheets/sheet3.xml" Type="worksheet"/></Relationships>""".toByteArray(),
            "xl/sharedStrings.xml" to "<sst xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">$strings</sst>".toByteArray(),
            "xl/worksheets/sheet1.xml" to survey.toByteArray(),
            "xl/worksheets/sheet2.xml" to settings.toByteArray(),
            "xl/worksheets/sheet3.xml" to choices.toByteArray()
        ))
    }

    private fun zip(entries: Map<String, ByteArray>): ByteArray = ByteArrayOutputStream().also { out ->
        ZipOutputStream(out).use { zip -> entries.forEach { (name, bytes) -> zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry() } }
    }.toByteArray()

    private fun unzip(bytes: ByteArray): Map<String, ByteArray> = buildMap {
        ZipInputStream(bytes.inputStream()).use { zip -> while (true) { val entry = zip.nextEntry ?: break; put(entry.name, zip.readBytes()) } }
    }
}
