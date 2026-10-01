package com.example.methodmesh.modules.nfc

import com.example.methodmesh.modules.attestation.As100CreateAttestationMethod
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/** Small, dependency-free XLSForm mapper for the in-app NFC compiler. */
object NfcOdkFormCompiler {
    enum class Mode { PROVISIONING, VERIFICATION }

    data class Compilation(
        val bytes: ByteArray,
        val outputName: String,
        val formVersion: String,
        val statement: String,
        val methodId: String,
        val methodVersion: String,
        val contractVersion: String,
        val credentialFormatVersion: String,
        val changedFields: List<String>
    )

    fun compile(input: ByteArray, originalName: String, mode: Mode, contractVersion: String = NfcCredentialFormContract.CURRENT_VERSION, now: Instant = Instant.now()): Compilation {
        val mapping = NfcCredentialFormContract.require(
            if (mode == Mode.PROVISIONING) As100NfcCredentialProvisioningMethod.ID else As100NfcCredentialVerificationMethod.ID,
            contractVersion
        )
        val entries = unzip(input)
        require(entries.keys.any { it.endsWith("xl/workbook.xml") }) { "The selected file is not a valid XLSX workbook." }
        val workbookPath = entries.keys.first { it.endsWith("xl/workbook.xml") }
        val workbook = parse(entries.getValue(workbookPath))
        val relsPath = workbookPath.substringBeforeLast('/') + "/_rels/" + workbookPath.substringAfterLast('/') + ".rels"
        val rels = parse(entries[relsPath] ?: error("XLSX workbook relationships are missing."))
        val surveyPath = sheetPath(workbook, rels, "survey") ?: error("The XLSX has no survey sheet.")
        val settingsPath = sheetPath(workbook, rels, "settings") ?: error("The XLSX has no settings sheet. Add the standard XLSForm settings sheet and retry.")
        val sharedStrings = entries["xl/sharedStrings.xml"]?.let(::parseSharedStrings).orEmpty()

        val version = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS").withZone(ZoneOffset.UTC).format(now)
        val changed = mutableListOf<String>()
        val settings = parse(entries.getValue(settingsPath))
        updateSettings(settings, version, originalName, sharedStrings, changed)
        val survey = parse(entries.getValue(surveyPath))
        val formId = settingsFormId(settings, sharedStrings)
        var choicesDocument: org.w3c.dom.Document? = null
        var choicesPath = sheetPath(workbook, rels, "choices")
        if (mode == Mode.VERIFICATION && choicesPath == null) {
            choicesPath = createChoicesSheet(entries, workbook, rels)
        }
        if (choicesPath != null) {
            val choices = parse(entries.getValue(choicesPath))
            if (mode == Mode.VERIFICATION) ensureYesChoice(choices, sharedStrings, changed)
            updateDimension(choices.documentElement)
            entries[choicesPath] = serialize(choices)
            choicesDocument = choices
        } else if (mode == Mode.VERIFICATION) {
            error("Verification mapping requires a choices sheet so the finalization control can use the mm_yes list.")
        }
        updateSurvey(survey, mode, mapping, contractVersion, formId, version, sharedStrings, changed, choicesDocument)
        entries[settingsPath] = serialize(settings)
        entries[surveyPath] = serialize(survey)

        val safeStem = originalName.substringBeforeLast('.').replace(Regex("[^A-Za-z0-9._-]+"), "_").ifBlank { "odk_form" }
        val outputName = "${safeStem}__methodmesh_nfc_${contractVersion}_$version.xlsx"
        val statement = buildString {
            append("Mapped ordinary XLSForm to MethodMesh NFC ")
            append(mode.name.lowercase())
            append(" using contract $contractVersion, method ${mapping.methodId} ${mapping.methodVersion}, credential format ${mapping.credentialFormatVersion}. ")
            append("Added/updated settings version $version, pinned the NFC contract and method versions in body::intent, ")
            append("added current expiry/diagnostic/full-JSON return fields, and added a signed-attestation group with a methodmesh.commitment_recipe.v1 recipe over the source study fields. NFC identifies the staff operator only; participant/study identifiers remain ordinary study data and are never passed to provisioning or verification. For verification forms, attestation reuses the authenticated NFC execution as WHO/WHEN evidence. ")
            append("Study/provisioner legitimacy remains with Sentinel.")
        }
        return Compilation(zip(entries), outputName, version, statement, mapping.methodId, mapping.methodVersion, contractVersion, mapping.credentialFormatVersion, changed)
    }

    private fun updateSettings(doc: org.w3c.dom.Document, version: String, originalName: String, sharedStrings: List<String>, changed: MutableList<String>) {
        val sheet = doc.documentElement
        val rows = sheet.getElementsByTagNameNS(MAIN_NS, "row")
        require(rows.length >= 2) { "The settings sheet has no data row." }
        val header = rowValues(rows.item(0) as org.w3c.dom.Element, sharedStrings)
        val data = rows.item(1) as org.w3c.dom.Element
        val cells = mutableMapOf<String, org.w3c.dom.Element>()
        data.getElementsByTagNameNS(MAIN_NS, "c").let { list -> for (i in 0 until list.length) { val cell = list.item(i) as org.w3c.dom.Element; cells[cell.getAttribute("r").filter(Char::isLetter).uppercase()] = cell } }
        fun set(name: String, value: String) {
            val col = header.entries.firstOrNull { it.value.equals(name, true) }?.key ?: return
            val cell = cells[col] ?: data.ownerDocument.createElementNS(MAIN_NS, "c").also { it.setAttribute("r", "$col${data.getAttribute("r")}"); data.appendChild(it) }
            setInline(cell, value)
            changed += "settings.$name"
        }
        set("version", version)
        if (header.values.none { it.equals("form_id", true) }) error("The settings sheet must contain form_id.")
        if (header.values.none { it.equals("form_title", true) }) error("The settings sheet must contain form_title.")
        if (cells.isEmpty()) error("The settings sheet data row is empty.")
        if (header.values.none { it.equals("form_id", true) }) error("The settings sheet must contain form_id.")
    }

    private fun settingsFormId(doc: org.w3c.dom.Document, sharedStrings: List<String>): String {
        val rows = doc.getElementsByTagNameNS(MAIN_NS, "row")
        if (rows.length < 2) return ""
        val header = rowValues(rows.item(0) as org.w3c.dom.Element, sharedStrings)
        val values = rowValues(rows.item(1) as org.w3c.dom.Element, sharedStrings)
        val column = header.entries.firstOrNull { it.value.equals("form_id", true) }?.key ?: return ""
        return values[column].orEmpty().trim()
    }

    private fun updateSurvey(doc: org.w3c.dom.Document, mode: Mode, mapping: NfcCredentialFormContract.MethodMapping, contractVersion: String, formId: String, formVersion: String, sharedStrings: List<String>, changed: MutableList<String>, choicesDocument: org.w3c.dom.Document?) {
        val root = doc.documentElement
        val rows = root.getElementsByTagNameNS(MAIN_NS, "row")
        require(rows.length >= 1) { "The survey sheet is empty." }
        val header = rowValues(rows.item(0) as org.w3c.dom.Element, sharedStrings).toMutableMap()
        ensureSurveyColumn(rows.item(0) as org.w3c.dom.Element, header, "body::intent")
        val intentCol = header.entries.firstOrNull { it.value.equals("body::intent", true) }?.key
            ?: error("The survey sheet must contain a body::intent column after normalization.")
        ensureSurveyColumn(rows.item(0) as org.w3c.dom.Element, header, "calculation")
        ensureSurveyColumn(rows.item(0) as org.w3c.dom.Element, header, "appearance")
        ensureSurveyColumn(rows.item(0) as org.w3c.dom.Element, header, "default")
        ensureSurveyColumn(rows.item(0) as org.w3c.dom.Element, header, "relevant")
        ensureSurveyColumn(rows.item(0) as org.w3c.dom.Element, header, "readonly")
        ensureSurveyColumn(rows.item(0) as org.w3c.dom.Element, header, "required")
        ensureSurveyColumn(rows.item(0) as org.w3c.dom.Element, header, "constraint")
        ensureSurveyColumn(rows.item(0) as org.w3c.dom.Element, header, "constraint_message")
        ensureSurveyColumn(rows.item(0) as org.w3c.dom.Element, header, "mm_commit")
        val nameColumn = header.entries.firstOrNull { it.value.equals("name", true) }?.key
            ?: error("The survey sheet must contain a name column.")
        val typeColumn = header.entries.firstOrNull { it.value.equals("type", true) }?.key
            ?: error("The survey sheet must contain a type column.")
        val existingNames = (1 until rows.length).mapNotNull {
            rowValues(rows.item(it) as org.w3c.dom.Element, sharedStrings)[nameColumn]?.trim()?.takeIf(String::isNotEmpty)
        }.toSet()
        require(existingNames.size == (1 until rows.length).mapNotNull {
            rowValues(rows.item(it) as org.w3c.dom.Element, sharedStrings)[nameColumn]?.trim()?.takeIf(String::isNotEmpty)
        }.size) { "The survey sheet contains duplicate non-blank node names; generated references would be ambiguous." }
        val reserved = setOf(
            "mm_compiler_marker", "mm_workflow_instance_id", "mm_study_id", "mm_form_id", "mm_form_version", "mm_timestamp_policy", "mm_auth_ok",
            "mm_authenticate_operator", "mm_authenticate_operator_end", "mm_credential_id_sha256", "mm_credential_subject_id_sha256",
            "mm_canonical_commitment_live", "mm_current_event_payload_hash", "mm_finalize_for_attestation", "mm_frozen_canonical_commitment",
            "mm_event_payload_hash", "mm_commitment_recipe", "mm_current_data_matches_frozen_commitment", "mm_create_attestation",
            "mm_create_attestation_end", "mm_attested_hash_matches_odk_hash", "mm_ready_to_submit", "mm_submission_guard",
            "mm_status_ready", "mm_status_not_ready"
        ) + AUTH_RETURN_FIELDS.map { it } + ATTESTATION_RETURN_FIELDS.map { "mm_att_$it" }
        require(existingNames.none { it in reserved }) {
            "Source form already uses reserved MethodMesh generated node name(s): ${existingNames.filter { it in reserved }}"
        }
        val members = mutableListOf<CommitMember>()
        val excludedRepeatStack = ArrayDeque<String>()
        val originalRowsForPolicy = rows.length
        for (index in 1 until originalRowsForPolicy) {
            val row = rows.item(index) as org.w3c.dom.Element
            val values = rowValues(row, sharedStrings)
            val name = values[nameColumn]?.trim().orEmpty()
            val type = values[typeColumn]?.trim().orEmpty()
            val rawCommit = values[header.entries.first { it.value.equals("mm_commit", true) }.key].orEmpty()
            require(name.isBlank() || Regex("[A-Za-z_][A-Za-z0-9_.-]*").matches(name)) {
                "'$name' at survey row ${row.getAttribute("r")} is not a compiler-safe node name."
            }
            if (NfcOdkCommitPolicy.isBeginRepeat(type)) {
                require(rawCommit.trim().lowercase() == "exclude") {
                    "${name.ifBlank { "repeat at row ${row.getAttribute("r")}" }}: repeat groups must use mm_commit=exclude."
                }
                excludedRepeatStack.addLast(name)
                continue
            }
            if (NfcOdkCommitPolicy.isEndRepeat(type)) {
                require(excludedRepeatStack.isNotEmpty()) { "survey row ${row.getAttribute("r")}: end_repeat without matching begin_repeat." }
                excludedRepeatStack.removeLast()
                continue
            }
            if (name.isBlank()) continue
            val decision = NfcOdkCommitPolicy.decide(type, rawCommit, name, excludedRepeatStack.isNotEmpty())
            if (decision.mode != NfcOdkCommitPolicy.Mode.EXCLUDE) {
                members += CommitMember(name, type, decision.mode, decision.transform, NfcOdkCommitPolicy.choiceList(type))
            }
        }
        require(excludedRepeatStack.isEmpty()) { "Unclosed repeat group(s) in survey sheet." }
        require(members.isNotEmpty()) { "No fields are committed. At least one source field must be committed for an attested form." }
        validateChoiceLists(choicesDocument, members, sharedStrings)
        val originalRows = (1 until rows.length).map { rows.item(it) as org.w3c.dom.Element }
        val fields = if (mode == Mode.PROVISIONING) listOf(
            Triple("text", "credential_subject_id_input", "Staff member name"),
            Triple("date", "valid_until_date", "Credential valid until (YYYY-MM-DD)"),
            Triple("calculate", "valid_until_date_iso", ""),
            Triple("text", "pin_length_input", "PIN length"),
            Triple("text", "overwrite_policy_input", "Existing card content")
        ) else emptyList()
        val returns = if (mode == Mode.VERIFICATION) {
            AUTH_RETURN_FIELDS.map { it.removePrefix("mm_auth_") }.filterNot { it == "methodmesh_full_json" }
        } else {
            mapping.requiredReturnFields.filterNot { it == "methodmesh_full_json" }
        }
        val returnPrefix = if (mode == Mode.VERIFICATION) "mm_auth_" else "mm_provision_"
        val all = fields + returns.map { field ->
            val generatedName = "$returnPrefix$field"
            Triple("text", generatedName, field.replace('_', ' '))
        } + listOf(Triple("text", "${returnPrefix}methodmesh_full_json", "MethodMesh FULL JSON"))
        val intent = if (mode == Mode.PROVISIONING) "com.example.methodmesh.EXECUTE_METHOD(method_id='${mapping.methodId}',caller='odk',input_nfc_form_contract_version='$contractVersion',input_nfc_method_version='${mapping.methodVersion}',input_credential_subject_id=\${credential_subject_id_input},input_pin_length=\${pin_length_input},input_valid_until_date=\${valid_until_date_iso},input_overwrite_policy=\${overwrite_policy_input},input_payload_mode='FULL',return_mode='flat',methodmesh_return_namespace='mm_provision')" else "com.example.methodmesh.EXECUTE_METHOD(method_id='${mapping.methodId}',caller='odk',input_nfc_form_contract_version='$contractVersion',input_nfc_method_version='${mapping.methodVersion}',input_payload_mode='FULL',return_mode='flat',methodmesh_return_namespace='mm_auth')"
        var rowNumber = originalRows.maxOfOrNull { it.getAttribute("r").toIntOrNull() ?: 1 }?.plus(1) ?: 2
        val groupName = "methodmesh_nfc_${mode.name.lowercase()}"
        if (mode == Mode.VERIFICATION) {
            val prefixNames = mutableListOf("mm_workflow_instance_id", "mm_study_id", "mm_form_id", "mm_form_version", "mm_timestamp_policy", "mm_authenticate_operator")
            prefixNames += AUTH_RETURN_FIELDS.filterNot { it == "mm_auth_methodmesh_full_json" }
            prefixNames += "mm_auth_methodmesh_full_json"
            prefixNames += listOf("mm_authenticate_operator_end", "mm_auth_ok")
            val shift = prefixNames.size
            originalRows.forEach { row -> row.setAttribute("r", ((row.getAttribute("r").toIntOrNull() ?: 1) + shift).toString()) }
            val generated = prefixNames.mapIndexed { index, name ->
                val calculation = when (name) {
                    "mm_workflow_instance_id" -> "once(uuid())"
                    "mm_study_id" -> "''"
                    "mm_form_id" -> "'$formId'"
                    "mm_form_version" -> "'$formVersion'"
                    "mm_timestamp_policy" -> "'preferred'"
                    "mm_auth_ok" -> "if(\${mm_auth_methodmesh_status} = 'Succeeded' and \${mm_auth_credential_verified} = 'true' and \${mm_auth_pin_verified} = 'true' and \${mm_auth_issuer_signature_valid} = 'true', 'true', 'false')"
                    else -> ""
                }
                val intent = if (name == "mm_authenticate_operator") "com.example.methodmesh.EXECUTE_METHOD(method_id='${mapping.methodId}',caller='odk',study_id=\${mm_study_id},form_id=\${mm_form_id},form_version=\${mm_form_version},form_instance_id=\${mm_workflow_instance_id},input_nfc_form_contract_version='$contractVersion',input_nfc_method_version='${mapping.methodVersion}',input_payload_mode='FULL',return_mode='flat',methodmesh_return_namespace='mm_auth')" else ""
                val type = when {
                    name == "mm_authenticate_operator" -> "begin group"
                    name.endsWith("_end") -> "end group"
                    name == "mm_workflow_instance_id" || name == "mm_study_id" || name == "mm_form_id" || name == "mm_form_version" || name == "mm_timestamp_policy" || name == "mm_auth_ok" -> "calculate"
                    else -> "text"
                }
                val label = when {
                    name == "mm_authenticate_operator" -> "Authenticate operator"
                    name == "mm_auth_issuer_public_key_fingerprint_sha256" -> "Issuer key evidence"
                    type == "text" -> "MethodMesh authentication return: ${name.removePrefix("mm_auth_").replace('_', ' ')}"
                    else -> ""
                }
                appendSurveyRow(root, index + 2, type, name, label, calculation, header, intent, if (name == "mm_authenticate_operator") "field-list" else "hidden-answer")
            }
            val sheetData = root.getElementsByTagNameNS(MAIN_NS, "sheetData").item(0)
            val firstSource = originalRows.firstOrNull()
            generated.forEach { sheetData.insertBefore(it, firstSource) }
            originalRows.forEach { row ->
                val type = rowValues(row, sharedStrings)[typeColumn]?.trim()?.lowercase().orEmpty()
                if (type !in setOf("begin group", "end group", "note", "calculate", "begin repeat", "end repeat")) {
                    appendOrCombineCell(row, header, "relevant", "\${mm_auth_ok} = 'true'", "and", sharedStrings)
                    appendOrCombineCell(row, header, "readonly", "\${mm_finalize_for_attestation} = 'yes'", "or", sharedStrings)
                }
            }
            changed += "survey.mm_authenticate_operator.body::intent"
            rowNumber = originalRows.maxOfOrNull { it.getAttribute("r").toIntOrNull() ?: 1 }?.plus(1) ?: rowNumber
        }
        if (mode == Mode.PROVISIONING && groupName !in existingNames) {
            appendSurveyRow(root, rowNumber++, "begin group", groupName, "MethodMesh NFC ${mode.name.lowercase()}", "", header, intent, appearance = "field-list")
            changed += "survey.body::intent"
        }
        if (mode == Mode.PROVISIONING) all.forEach { (type, name, label) -> if (name !in existingNames) { appendSurveyRow(root, rowNumber++, type, name, label, if (name == "valid_until_date_iso") "format-date(\${valid_until_date}, '%Y-%m-%d')" else "", header); changed += "survey.$name" } }
        if (mode == Mode.PROVISIONING && groupName !in existingNames) appendSurveyRow(root, rowNumber, "end group", "${groupName}_end", "", "", header)
        if (mode == Mode.PROVISIONING) rowNumber++
        if (mode == Mode.VERIFICATION) {
            appendAttestationRows(root, rowNumber, mode, existingNames, members, header, changed)
        }
        removeSurveyColumn(root, header.entries.firstOrNull { it.value.equals("mm_commit", true) }?.key ?: error("mm_commit column was not created."))
        updateDimension(root)
    }

    private fun appendSurveyRow(root: org.w3c.dom.Element, number: Int, type: String, name: String, label: String, calculation: String, header: Map<String, String>, intent: String = "", appearance: String = "", defaultValue: String = "", relevant: String = "", required: String = "", constraint: String = "", constraintMessage: String = ""): org.w3c.dom.Element {
        val doc = root.ownerDocument
        val row = doc.createElementNS(MAIN_NS, "row").also { it.setAttribute("r", number.toString()) }
        fun add(col: String, value: String) { if (value.isNotBlank()) appendCell(row, col, value) }
        add(header.entries.firstOrNull { it.value.equals("type", true) }?.key ?: "A", type)
        add(header.entries.firstOrNull { it.value.equals("name", true) }?.key ?: "B", name)
        add(header.entries.firstOrNull { it.value.equals("label", true) }?.key ?: "C", label)
        header.entries.firstOrNull { it.value.equals("calculation", true) }?.key?.let { add(it, calculation) }
        header.entries.firstOrNull { it.value.equals("appearance", true) }?.key?.let { add(it, appearance) }
        header.entries.firstOrNull { it.value.equals("default", true) }?.key?.let { add(it, defaultValue) }
        header.entries.firstOrNull { it.value.equals("relevant", true) }?.key?.let { add(it, relevant) }
        header.entries.firstOrNull { it.value.equals("required", true) }?.key?.let { add(it, required) }
        header.entries.firstOrNull { it.value.equals("constraint", true) }?.key?.let { add(it, constraint) }
        header.entries.firstOrNull { it.value.equals("constraint_message", true) }?.key?.let { add(it, constraintMessage) }
        header.entries.firstOrNull { it.value.equals("body::intent", true) }?.key?.let { add(it, intent) }
        root.getElementsByTagNameNS(MAIN_NS, "sheetData").item(0).appendChild(row)
        return row
    }

    private fun appendOrCombineCell(row: org.w3c.dom.Element, header: Map<String, String>, columnName: String, value: String, operator: String, sharedStrings: List<String>) {
        val column = header.entries.firstOrNull { it.value.equals(columnName, true) }?.key ?: return
        val existing = rowValues(row, sharedStrings)[column].orEmpty().trim()
        val combined = if (existing.isBlank()) value else "($existing) $operator ($value)"
        val cell = row.getElementsByTagNameNS(MAIN_NS, "c").let { cells ->
            (0 until cells.length).map { cells.item(it) as org.w3c.dom.Element }.firstOrNull { it.getAttribute("r").filter(Char::isLetter).uppercase() == column }
        } ?: row.ownerDocument.createElementNS(MAIN_NS, "c").also { it.setAttribute("r", "$column${row.getAttribute("r")}"); row.appendChild(it) }
        setInline(cell, combined)
    }

    private fun removeSurveyColumn(sheet: org.w3c.dom.Element, column: String) {
        val removed = columnNumber(column)
        val rows = sheet.getElementsByTagNameNS(MAIN_NS, "row")
        for (i in 0 until rows.length) {
            val row = rows.item(i) as org.w3c.dom.Element
            val cellNodes = row.getElementsByTagNameNS(MAIN_NS, "c")
            val cells = (0 until cellNodes.length).map { cellNodes.item(it) as org.w3c.dom.Element }
            cells.forEach { cell ->
                val oldColumn = cell.getAttribute("r").filter(Char::isLetter).uppercase()
                val oldNumber = columnNumber(oldColumn)
                when {
                    oldNumber == removed -> row.removeChild(cell)
                    oldNumber > removed -> {
                        val newColumn = numberToColumn(oldNumber - 1)
                        cell.setAttribute("r", "$newColumn${row.getAttribute("r")}")
                    }
                }
            }
        }
    }

    private fun ensureSurveyColumn(headerRow: org.w3c.dom.Element, header: MutableMap<String, String>, name: String) {
        val aliases = when (name.lowercase()) {
            "readonly" -> setOf("readonly", "read_only")
            else -> setOf(name)
        }
        val matches = header.entries.filter { it.value.lowercase() in aliases }
        require(matches.size <= 1) {
            "The survey sheet contains duplicate columns for '$name': ${matches.joinToString { it.value }}. Keep only one."
        }
        if (matches.size == 1) {
            val existing = matches.single()
            if (!existing.value.equals(name, true)) {
                header[existing.key] = name
                val cells = headerRow.getElementsByTagNameNS(MAIN_NS, "c")
                for (index in 0 until cells.length) {
                    val cell = cells.item(index) as org.w3c.dom.Element
                    if (cell.getAttribute("r").filter(Char::isLetter).uppercase() == existing.key) {
                        setInline(cell, name)
                        break
                    }
                }
            }
            return
        }
        val column = nextColumn(header.keys)
        header[column] = name
        appendCell(headerRow, column, name)
    }

    private data class CommitMember(
        val name: String,
        val type: String,
        val mode: NfcOdkCommitPolicy.Mode,
        val transform: String,
        val choiceList: String?
    )

    private fun validateChoiceLists(choices: org.w3c.dom.Document?, members: List<CommitMember>, sharedStrings: List<String>) {
        val selects = members.filter { it.choiceList != null && !it.type.trim().lowercase().contains("from_file") }
        if (selects.isEmpty()) return
        require(choices != null) { "Committed internal select field(s) found but the choices sheet is missing." }
        val rows = choices.getElementsByTagNameNS(MAIN_NS, "row")
        require(rows.length > 0) { "The choices sheet is empty." }
        val choiceHeader = rowValues(rows.item(0) as org.w3c.dom.Element, sharedStrings)
        val listColumn = choiceHeader.entries.firstOrNull { it.value.equals("list_name", true) }?.key
            ?: error("The choices sheet must contain a list_name column.")
        val nameColumn = choiceHeader.entries.firstOrNull { it.value.equals("name", true) }?.key
            ?: error("The choices sheet must contain a name column.")
        val byList = mutableMapOf<String, MutableList<String>>()
        for (index in 1 until rows.length) {
            val values = rowValues(rows.item(index) as org.w3c.dom.Element, sharedStrings)
            val list = values[listColumn].orEmpty()
            val name = values[nameColumn].orEmpty()
            if (list.isNotBlank() && name.isNotBlank()) byList.getOrPut(list) { mutableListOf() }.add(name)
        }
        selects.forEach { member ->
            val list = member.choiceList!!
            require(list in byList) { "${member.name}: choice list '$list' not found in choices sheet." }
            if (NfcOdkCommitPolicy.isSelectOne(member.type)) {
                val bad = byList.getValue(list).filter { '|' in it || '=' in it }
                require(bad.isEmpty()) { "${member.name}: committed select_one list '$list' contains choice name(s) with '|' or '=': $bad" }
            }
        }
    }

    private fun fieldExpression(member: CommitMember): String {
        val ref = "\${${member.name}}"
        return if (member.mode == NfcOdkCommitPolicy.Mode.SHA256) {
            "digest(string($ref), 'SHA-256', 'hex')"
        } else if (NfcOdkCommitPolicy.typeHead(member.type) == "date") {
            "if(string-length(string($ref)) > 0, format-date($ref, '%Y-%m-%d'), '')"
        } else {
            "string($ref)"
        }
    }

    private fun recipeType(member: CommitMember): String = when {
        member.mode == NfcOdkCommitPolicy.Mode.SHA256 -> "sha256"
        NfcOdkCommitPolicy.typeHead(member.type) == "integer" -> "integer"
        NfcOdkCommitPolicy.typeHead(member.type) in setOf("decimal", "range") -> "decimal"
        NfcOdkCommitPolicy.typeHead(member.type) == "date" -> "date"
        NfcOdkCommitPolicy.isSelectOne(member.type) -> "select_one"
        else -> "string"
    }

    private fun appendAttestationRows(root: org.w3c.dom.Element, start: Int, mode: Mode, existingNames: Set<String>, studyFields: List<CommitMember>, header: Map<String, String>, changed: MutableList<String>) {
        val groupName = "methodmesh_attestation"
        if (groupName in existingNames) return
        val recipe = buildCommitmentRecipe(studyFields)
        val contextParts = listOf(
            "'study_id='", "string(\${mm_study_id})", "'|'",
            "'form_id='", "string(\${mm_form_id})", "'|'",
            "'form_version='", "string(\${mm_form_version})", "'|'",
            "'workflow_instance_id='", "string(\${mm_workflow_instance_id})", "'|'",
            "'credential_id_sha256='", "string(\${mm_credential_id_sha256})", "'|'",
            "'credential_subject_id_sha256='", "string(\${mm_credential_subject_id_sha256})", "'|'",
            "'issuer_public_key_fingerprint_sha256='", "string(\${mm_auth_issuer_public_key_fingerprint_sha256})", "'|'",
            "'verification_evidence_hash='", "string(\${mm_auth_verification_evidence_hash})"
        ).toMutableList()
        studyFields.forEach { field -> contextParts += listOf("'|${field.name}='", fieldExpression(field)) }
        val canonical = "concat(${contextParts.joinToString(",")})"
        var row = start
        val inputs = listOf(
            Triple("calculate", "mm_credential_id_sha256", ""),
            Triple("calculate", "mm_credential_subject_id_sha256", ""),
            Triple("calculate", "mm_canonical_commitment_live", ""),
            Triple("calculate", "mm_current_event_payload_hash", ""),
            Triple("select_one mm_yes", "mm_finalize_for_attestation", "Finalize these data for attestation?"),
            Triple("calculate", "mm_frozen_canonical_commitment", ""),
            Triple("calculate", "mm_event_payload_hash", ""),
            Triple("calculate", "mm_commitment_recipe", ""),
            Triple("calculate", "mm_current_data_matches_frozen_commitment", ""),
            Triple("calculate", "mm_attestation_verification_method", "")
        )
        inputs.forEach { (type, name, label) ->
            val calculation = when (name) {
                "mm_credential_id_sha256" -> "digest(string(\${mm_auth_credential_id}), 'SHA-256', 'hex')"
                "mm_credential_subject_id_sha256" -> "digest(string(\${mm_auth_credential_subject_id}), 'SHA-256', 'hex')"
                "mm_canonical_commitment_live" -> canonical
                "mm_current_event_payload_hash" -> "digest(\${mm_canonical_commitment_live}, 'SHA-256', 'hex')"
                "mm_frozen_canonical_commitment" -> "once(if(\${mm_finalize_for_attestation} = 'yes', \${mm_canonical_commitment_live}, ''))"
                "mm_event_payload_hash" -> "once(if(string-length(\${mm_frozen_canonical_commitment}) > 0, digest(\${mm_frozen_canonical_commitment}, 'SHA-256', 'hex'), ''))"
                "mm_commitment_recipe" -> "'$recipe'"
                "mm_current_data_matches_frozen_commitment" -> "if(\${mm_current_event_payload_hash} = \${mm_event_payload_hash}, 'true', 'false')"
                "mm_attestation_verification_method" -> "'NfcCredential'"
                else -> ""
            }
            appendSurveyRow(
                root, row++, type, name, label, calculation, header,
                relevant = if (name == "mm_finalize_for_attestation") "\${mm_auth_ok} = 'true'" else "",
                required = if (name == "mm_finalize_for_attestation") "yes" else "",
                constraint = if (name == "mm_finalize_for_attestation") ". = 'yes'" else "",
                constraintMessage = if (name == "mm_finalize_for_attestation") "Select Yes to finalize the form for attestation." else ""
            )
            changed += "survey.$name"
        }
        val verification = if (mode == Mode.VERIFICATION) {
            "input_verification_method='NfcCredential',input_verification_execution_id=\${mm_auth_methodmesh_execution_id}"
        } else {
            "input_verification_method=\${mm_attestation_verification_method}"
        }
        val intent = "com.example.methodmesh.EXECUTE_METHOD(method_id='attestation.create',caller='odk',study_id=\${mm_study_id},form_id=\${mm_form_id},form_version=\${mm_form_version},form_instance_id=\${mm_workflow_instance_id},operator_id=\${mm_auth_credential_subject_id},input_study_id=\${mm_study_id},input_attestation_method_version='${As100CreateAttestationMethod.VERSION}',input_attestation_schema_version='${As100CreateAttestationMethod.SCHEMA_VERSION}',input_event_type='odk_form_commitment',input_event_payload_hash=\${mm_event_payload_hash},input_commitment_recipe=\${mm_commitment_recipe},$verification,input_trusted_timestamp=\${mm_timestamp_policy},input_payload_mode='FULL',return_mode='flat',methodmesh_return_namespace='mm_att')"
        appendSurveyRow(
            root, row++, "begin group", groupName, "Signed form attestation", "", header, intent,
            appearance = "field-list",
            relevant = "string-length(\${mm_event_payload_hash}) = 64 and \${mm_current_data_matches_frozen_commitment} = 'true'"
        )
        listOf("mm_ready_to_submit", "mm_submission_guard").forEach { name ->
            appendSurveyRow(root, row++, "text", name, name.replace('_', ' '), if (name == "mm_ready_to_submit") "if(\${mm_att_methodmesh_status} = 'Succeeded' and \${mm_att_event_payload_hash} = \${mm_event_payload_hash} and \${mm_current_data_matches_frozen_commitment} = 'true' and string-length(\${mm_att_attestation_hash}) = 64 and string-length(\${mm_att_signature}) > 0, 'true', 'false')" else "", header, appearance = if (name == "mm_submission_guard") "" else "hidden-answer")
            changed += "survey.$name"
        }
        ATTESTATION_RETURN_FIELDS.forEach { name ->
            val label = when (name) {
                "methodmesh_status" -> "MethodMesh status"
                "verification_method" -> "Verification method (NFC credential)"
                "diagnostic_reason" -> "Diagnostic reason"
                "attestation_full_json" -> "MethodMesh FULL JSON"
                else -> "MethodMesh attestation return: ${name.replace('_', ' ')}"
            }
            appendSurveyRow(root, row++, "text", "mm_att_$name", label, "", header, appearance = if (name in setOf("methodmesh_status", "verification_method", "diagnostic_reason")) "" else "hidden-answer")
            changed += "survey.$name"
        }
        appendSurveyRow(root, row, "end group", "${groupName}_end", "", "", header)
        changed += "survey.attestation.body::intent"
    }

    private fun buildCommitmentRecipe(studyFields: List<CommitMember>): String = buildString {
        append("{\"schema\":\"methodmesh.commitment_recipe.v1\",\"canonicalization\":\"ordered-kv-v1\",\"hash_algorithm\":\"SHA-256\",\"encoding\":\"UTF-8\",\"pair_separator\":\"|\",\"key_value_separator\":\"=\",\"escaping\":\"none\",\"members\":[")
        val context = listOf(
            "study_id|value", "form_id|value", "form_version|value", "workflow_instance_id|value",
            "credential_id_sha256|text-utf8-sha256", "credential_subject_id_sha256|text-utf8-sha256",
            "issuer_public_key_fingerprint_sha256|value", "verification_evidence_hash|value"
        )
        context.forEachIndexed { index, item ->
            if (index > 0) append(',')
            val parts = item.split('|')
            append("{\"path\":\"").append(parts[0]).append("\",\"type\":\"string\",\"commitment\":\"").append(parts[1]).append("\"}")
        }
        studyFields.forEach { field ->
            append(',')
            append("{\"path\":\"")
            append(field.name.replace("\\", "\\\\").replace("\"", "\\\""))
            append("\",\"type\":\"").append(recipeType(field)).append("\",\"xlsform_type\":\"")
            append(field.type.replace("\\", "\\\\").replace("\"", "\\\""))
            append("\",\"commitment\":\"").append(if (field.mode == NfcOdkCommitPolicy.Mode.SHA256) "text-utf8-sha256" else "value")
            append("\",\"transform\":\"").append(field.transform).append("\"")
            field.choiceList?.let { append(",\"choice_list\":\"").append(it).append("\"") }
            append('}')
        }
        append("]}")
    }

    private fun ensureYesChoice(doc: org.w3c.dom.Document, sharedStrings: List<String>, changed: MutableList<String>) {
        val sheet = doc.documentElement
        val rows = sheet.getElementsByTagNameNS(MAIN_NS, "row")
        require(rows.length >= 1) { "The choices sheet is empty." }
        val header = rowValues(rows.item(0) as org.w3c.dom.Element, sharedStrings)
        val listColumn = header.entries.firstOrNull { it.value.equals("list_name", true) }?.key
            ?: error("The choices sheet must contain a list_name column.")
        val nameColumn = header.entries.firstOrNull { it.value.equals("name", true) }?.key
            ?: error("The choices sheet must contain a name column.")
        val labelColumn = header.entries.firstOrNull { it.value.equals("label", true) }?.key
            ?: error("The choices sheet must contain a label column.")
        val exists = (1 until rows.length).any {
            rowValues(rows.item(it) as org.w3c.dom.Element, sharedStrings)[listColumn].equals("mm_yes", true) &&
                rowValues(rows.item(it) as org.w3c.dom.Element, sharedStrings)[nameColumn].equals("yes", true)
        }
        if (exists) return
        val rowNumber = rowsLengthMax(rows) + 1
        val row = sheet.ownerDocument.createElementNS(MAIN_NS, "row").also { it.setAttribute("r", rowNumber.toString()) }
        fun add(column: String, value: String) { appendCell(row, column, value) }
        add(listColumn, "mm_yes")
        add(nameColumn, "yes")
        add(labelColumn, "Yes")
        sheet.getElementsByTagNameNS(MAIN_NS, "sheetData").item(0).appendChild(row)
        changed += "choices.mm_yes.yes"
    }

    private fun createChoicesSheet(entries: MutableMap<String, ByteArray>, workbook: org.w3c.dom.Document, rels: org.w3c.dom.Document): String {
        val sheets = workbook.getElementsByTagNameNS(MAIN_NS, "sheets").item(0) as org.w3c.dom.Element
        val sheetNodes = sheets.getElementsByTagNameNS(MAIN_NS, "sheet")
        val nextSheetId = (0 until sheetNodes.length).mapNotNull {
            (sheetNodes.item(it) as org.w3c.dom.Element).getAttribute("sheetId").toIntOrNull()
        }.maxOrNull()?.plus(1) ?: 1
        val relNodes = rels.getElementsByTagName("Relationship")
        val nextRelNumber = (0 until relNodes.length).mapNotNull {
            (relNodes.item(it) as org.w3c.dom.Element).getAttribute("Id").removePrefix("rId").toIntOrNull()
        }.maxOrNull()?.plus(1) ?: 1
        val relId = "rId$nextRelNumber"
        val path = "xl/worksheets/sheet$nextSheetId.xml"
        val sheet = workbook.ownerDocument.createElementNS(MAIN_NS, "sheet").also {
            it.setAttribute("name", "choices")
            it.setAttribute("sheetId", nextSheetId.toString())
            it.setAttributeNS(REL_NS, "r:id", relId)
        }
        sheets.appendChild(sheet)
        val relationship = rels.createElementNS(PKG_REL_NS, "Relationship").also {
            it.setAttribute("Id", relId)
            it.setAttribute("Type", "http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet")
            it.setAttribute("Target", "worksheets/sheet$nextSheetId.xml")
        }
        rels.documentElement.appendChild(relationship)
        val choices = parse("""<?xml version="1.0"?><worksheet xmlns="$MAIN_NS"><sheetData><row r="1"><c r="A1" t="inlineStr"><is><t>list_name</t></is></c><c r="B1" t="inlineStr"><is><t>name</t></is></c><c r="C1" t="inlineStr"><is><t>label</t></is></c></row></sheetData></worksheet>""".toByteArray())
        entries[path] = serialize(choices)
        return path
    }

    private fun rowsLengthMax(rows: org.w3c.dom.NodeList): Int = (0 until rows.length)
        .maxOfOrNull { (rows.item(it) as org.w3c.dom.Element).getAttribute("r").toIntOrNull() ?: 1 } ?: 1

    private fun rowValues(row: org.w3c.dom.Element, sharedStrings: List<String>): Map<String, String> = buildMap {
        val cells = row.getElementsByTagNameNS(MAIN_NS, "c")
        for (i in 0 until cells.length) { val c = cells.item(i) as org.w3c.dom.Element; put(c.getAttribute("r").filter(Char::isLetter).uppercase(), cellText(c, sharedStrings)) }
    }

    private fun appendCell(row: org.w3c.dom.Element, column: String, value: String) { val cell = row.ownerDocument.createElementNS(MAIN_NS, "c"); cell.setAttribute("r", "$column${row.getAttribute("r")}"); setInline(cell, value); row.appendChild(cell) }
    private fun setInline(cell: org.w3c.dom.Element, value: String) { while (cell.hasChildNodes()) cell.removeChild(cell.firstChild); cell.setAttribute("t", "inlineStr"); val isNode = cell.ownerDocument.createElementNS(MAIN_NS, "is"); val t = cell.ownerDocument.createElementNS(MAIN_NS, "t"); t.textContent = value; isNode.appendChild(t); cell.appendChild(isNode) }
    private fun cellText(cell: org.w3c.dom.Element, sharedStrings: List<String>): String {
        val value = cell.getElementsByTagNameNS(MAIN_NS, "v").let { if (it.length == 0) "" else it.item(0).textContent }
        return if (cell.getAttribute("t") == "s") sharedStrings.getOrNull(value.toIntOrNull() ?: -1).orEmpty()
        else cell.getElementsByTagNameNS(MAIN_NS, "t").let { if (it.length == 0) value else it.item(0).textContent }
    }
    private fun nextColumn(columns: Set<String>): String {
        var index = columns.map(::columnNumber).maxOrNull()?.plus(1) ?: 1
        while (numberToColumn(index) in columns) index++
        return numberToColumn(index)
    }
    private fun columnNumber(column: String): Int = column.fold(0) { total, ch -> total * 26 + (ch.code - 'A'.code + 1) }
    private fun numberToColumn(number: Int): String { var n = number; val out = StringBuilder(); while (n > 0) { val rem = (n - 1) % 26; out.append(('A'.code + rem).toChar()); n = (n - 1) / 26 }; return out.reverse().toString() }

    private fun parseSharedStrings(bytes: ByteArray): List<String> {
        val doc = parse(bytes)
        val strings = doc.getElementsByTagNameNS(MAIN_NS, "si")
        return (0 until strings.length).map { index ->
            val si = strings.item(index) as org.w3c.dom.Element
            val textNodes = si.getElementsByTagNameNS(MAIN_NS, "t")
            (0 until textNodes.length).joinToString("") { textNodes.item(it).textContent }
        }
    }

    private fun updateDimension(sheet: org.w3c.dom.Element) {
        val rows = sheet.getElementsByTagNameNS(MAIN_NS, "row")
        var maxRow = 1
        var maxColumn = 1
        for (i in 0 until rows.length) {
            val row = rows.item(i) as org.w3c.dom.Element
            maxRow = maxOf(maxRow, row.getAttribute("r").toIntOrNull() ?: 1)
            val cells = row.getElementsByTagNameNS(MAIN_NS, "c")
            for (j in 0 until cells.length) {
                val column = (cells.item(j) as org.w3c.dom.Element).getAttribute("r").filter(Char::isLetter).uppercase()
                if (column.isNotEmpty()) maxColumn = maxOf(maxColumn, columnNumber(column))
            }
        }
        val dimension = sheet.getElementsByTagNameNS(MAIN_NS, "dimension").let { if (it.length > 0) it.item(0) as org.w3c.dom.Element else null }
            ?: sheet.ownerDocument.createElementNS(MAIN_NS, "dimension").also { sheet.insertBefore(it, sheet.firstChild) }
        dimension.setAttribute("ref", "A1:${numberToColumn(maxColumn)}$maxRow")
    }

    private fun sheetPath(workbook: org.w3c.dom.Document, rels: org.w3c.dom.Document, name: String): String? {
        val relMap = mutableMapOf<String, String>(); val relNodes = rels.getElementsByTagName("Relationship"); for (i in 0 until relNodes.length) { val n = relNodes.item(i) as org.w3c.dom.Element; relMap[n.getAttribute("Id")] = n.getAttribute("Target") }
        val sheets = workbook.getElementsByTagNameNS(MAIN_NS, "sheet"); for (i in 0 until sheets.length) { val s = sheets.item(i) as org.w3c.dom.Element; if (s.getAttribute("name").equals(name, true)) return "xl/" + relMap[s.getAttributeNS(REL_NS, "id")].orEmpty().removePrefix("/") }
        return null
    }

    private fun parse(bytes: ByteArray): org.w3c.dom.Document = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
    private fun serialize(doc: org.w3c.dom.Document): ByteArray = ByteArrayOutputStream().also { out -> TransformerFactory.newInstance().newTransformer().apply { setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no"); transform(DOMSource(doc), StreamResult(out)) } }.toByteArray()
    private fun unzip(bytes: ByteArray): MutableMap<String, ByteArray> = mutableMapOf<String, ByteArray>().also { out -> ZipInputStream(ByteArrayInputStream(bytes)).use { zip -> while (true) { val e = zip.nextEntry ?: break; out[e.name] = zip.readBytes() } } }
    private fun zip(entries: Map<String, ByteArray>): ByteArray = ByteArrayOutputStream().also { out -> ZipOutputStream(out).use { zip -> entries.forEach { (name, bytes) -> zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry() } } }.toByteArray()

    private const val MAIN_NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    private const val REL_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    private const val PKG_REL_NS = "http://schemas.openxmlformats.org/package/2006/relationships"
    private val ATTESTATION_RETURN_FIELDS = listOf(
        "methodmesh_execution_id", "methodmesh_status", "attestation_id", "event_payload_hash",
        "attestation_schema_version", "study_id", "event_type", "event_payload_mode",
        "commitment_recipe", "commitment_recipe_sha256", "verification_method", "verification_evidence_format",
        "verification_evidence_hash", "device_event_time_iso", "device_monotonic_counter",
        "previous_attestation_hash", "attestation_hash", "hash_algorithm", "public_key_id",
        "public_key_algorithm", "public_key_format", "public_key_base64",
        "signature", "signature_algorithm", "trusted_timestamp_policy", "trusted_timestamp_status",
        "trusted_timestamp_authority", "trusted_timestamp_time_iso", "trusted_timestamp_serial",
        "trusted_timestamp_attested_hash", "trusted_timestamp_token_sha256", "trusted_timestamp_token_base64", "diagnostic_reason",
        "attestation_full_json"
    )

    /** Complete auth envelope projection from the standalone XLSForm compiler. */
    private val AUTH_RETURN_FIELDS = listOf(
        "mm_auth_methodmesh_execution_id", "mm_auth_methodmesh_method_id", "mm_auth_methodmesh_status",
        "mm_auth_credential_verified", "mm_auth_credential_verification_message", "mm_auth_credential_id",
        "mm_auth_credential_subject_id", "mm_auth_pin_verified", "mm_auth_issuer_signature_valid",
        "mm_auth_issuer_trust_status", "mm_auth_issuer_trust_policy", "mm_auth_issuer_key_id",
        "mm_auth_issuer_public_key_fingerprint_sha256", "mm_auth_issuer_public_key_base64", "mm_auth_tag_uid_hex",
        "mm_auth_verification_evidence_hash", "mm_auth_credential_envelope_hash", "mm_auth_credential_verified_time_iso",
        "mm_auth_methodmesh_full_json"
    )
}
