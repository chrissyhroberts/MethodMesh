package com.example.methodmesh.ui.kobo

import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.LinkedHashMap
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/**
 * Kobo's XLSForm import currently requires survey node names to be globally
 * unique, while ODK/pyxform can disambiguate repeated leaf names by group path.
 *
 * MethodMesh keeps the module-owned XLSForm canonical. When Kobo rejects a
 * syntactically valid example for global duplicate node names, this helper can
 * derive a temporary provider-specific copy by applying MethodMesh return
 * namespaces to the affected result groups. The source asset is never changed.
 */
object KoboXlsFormCompatibility {
    private const val MAIN_NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
    private const val DOC_REL_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    private const val PKG_REL_NS = "http://schemas.openxmlformats.org/package/2006/relationships"
    private val namespaceArgument = Regex(
        "(?:(input_)?methodmesh_return_namespace)\\s*=\\s*(['\"])([A-Za-z_][A-Za-z0-9_]*)\\2"
    )
    private val resultSuffix = Regex("_(?:result|results|output|outputs)$", RegexOption.IGNORE_CASE)
    private val validNamespace = Regex("^[A-Za-z_][A-Za-z0-9_]*$")

    data class CollisionOccurrence(
        val row: Int,
        val path: String
    )

    data class NameCollision(
        val name: String,
        val occurrences: List<CollisionOccurrence>
    )

    data class Plan(
        val collisions: List<NameCollision>,
        val canAutoRepair: Boolean,
        val reason: String = "",
        val namespaces: Map<String, String> = emptyMap(),
        val renamedFields: Int = 0
    ) {
        fun guidance(): String = buildString {
            if (collisions.isEmpty()) {
                append("No global duplicate survey node names were found in the local XLSForm.")
                reason.takeIf(String::isNotBlank)?.let { append(" ").append(it) }
                return@buildString
            }
            append("Kobo requires node names to be unique across the whole XLSForm. ")
            append("This form reuses ")
            append(collisions.joinToString(", ") { "'${it.name}'" })
            append(" in different group paths. This is valid for the canonical MethodMesh/ODK example but Kobo rejects it.\n\n")
            collisions.forEach { collision ->
                append("• ").append(collision.name).append(": ")
                append(collision.occurrences.joinToString("; ") { "${it.path} (row ${it.row})" })
                append('\n')
            }
            if (canAutoRepair) {
                append("\nMethodMesh can create a temporary Kobo-compatible copy by namespacing the affected MethodMesh result groups, updating local references, validating that global duplicates are gone, and retrying the upload. The canonical module XLSForm is not modified.")
            } else {
                append("\nAutomatic repair is not safe for this form. ")
                append(reason.ifBlank { "Rename the colliding nodes or add explicit methodmesh_return_namespace values in the source XLSForm, then retry." })
            }
        }
    }

    data class RepairResult(
        val bytes: ByteArray,
        val plan: Plan
    )

    fun plan(bytes: ByteArray): Plan = Workbook(bytes).plan()

    fun repair(bytes: ByteArray): RepairResult {
        val workbook = Workbook(bytes)
        val plan = workbook.plan()
        require(plan.collisions.isNotEmpty()) { "This XLSForm does not need Kobo duplicate-name repair." }
        require(plan.canAutoRepair) { plan.reason.ifBlank { "Kobo compatibility repair is not safe for this XLSForm." } }
        return RepairResult(workbook.repair(plan), plan)
    }

    private data class Group(
        val name: String,
        val startRow: Int,
        var endRow: Int = Int.MAX_VALUE,
        val intent: String,
        val parent: Group? = null
    ) {
        val path: String get() = generateSequence(this) { it.parent }.toList().asReversed().joinToString("/") { it.name }
        val isMethodMeshIntent: Boolean get() = intent.contains("com.example.methodmesh.EXECUTE_METHOD(")
    }

    private data class RowInfo(
        val number: Int,
        val type: String,
        val name: String,
        val values: Map<String, String>,
        val groups: List<Group>,
        val beginGroup: Group? = null
    ) {
        val nearestMethodMeshGroup: Group? get() = groups.asReversed().firstOrNull { it.isMethodMeshIntent }
        val isGroupStart: Boolean get() = type.lowercase() in setOf("begin_group", "begin group", "begin_repeat", "begin repeat")
        val isGroupEnd: Boolean get() = type.lowercase() in setOf("end_group", "end group", "end_repeat", "end repeat")
    }

    private class Workbook(private val originalBytes: ByteArray) {
        private val entries = unzip(originalBytes)
        private val surveyPath = surveyWorksheetPath(entries)
            ?: error("Kobo compatibility repair could not locate the survey worksheet.")
        private val sharedStrings = readSharedStrings(entries["xl/sharedStrings.xml"])
        private val surveyDocument = parseXml(entries[surveyPath] ?: error("Missing $surveyPath"))
        private val rows: List<RowInfo>
        private val rowElements: Map<Int, Element>
        private val headerColumns: Map<String, Int>

        init {
            val parsed = parseRows(surveyDocument, sharedStrings)
            rowElements = parsed.first
            val rawRows = parsed.second
            val header = rawRows.firstOrNull { it.first == 1 }?.second.orEmpty()
            headerColumns = header.entries.associate { it.value.trim() to it.key }
            rows = buildScopes(rawRows)
        }

        fun plan(): Plan {
            val namedRows = rows.filter { it.name.isNotBlank() && !it.isGroupEnd }
            val collisions = namedRows.groupBy { it.name }
                .filterValues { it.size > 1 }
                .toSortedMap()
                .map { (name, occurrences) ->
                    NameCollision(
                        name = name,
                        occurrences = occurrences.map { row ->
                            CollisionOccurrence(row.number, pathFor(row))
                        }
                    )
                }

            if (collisions.isEmpty()) return Plan(emptyList(), canAutoRepair = false, reason = "Kobo reported a duplicate name, but the packaged XLSForm no longer contains one.")

            val collidedNames = collisions.mapTo(linkedSetOf()) { it.name }
            val collisionRows = namedRows.filter { it.name in collidedNames }
            val unsafeCollision = collisionRows.firstOrNull { row ->
                row.isGroupStart || row.nearestMethodMeshGroup == null
            }
            if (unsafeCollision != null) {
                return Plan(
                    collisions = collisions,
                    canAutoRepair = false,
                    reason = "'${unsafeCollision.name}' at ${pathFor(unsafeCollision)} is not a leaf return field inside a MethodMesh intent group, so MethodMesh cannot safely derive a provider-only namespace."
                )
            }

            val groups = collisionRows.mapNotNull { it.nearestMethodMeshGroup }.distinctBy { it.startRow }
            if (groups.isEmpty()) {
                return Plan(collisions, false, "No MethodMesh result groups could be identified for the colliding fields.")
            }

            val outputRowsByGroup = groups.associateWith { group ->
                rows.filter { row ->
                    row.number > group.startRow && row.number < group.endRow &&
                        !row.isGroupStart && !row.isGroupEnd && row.name.isNotBlank() &&
                        row.nearestMethodMeshGroup?.startRow == group.startRow
                }
            }
            if (outputRowsByGroup.any { it.value.isEmpty() }) {
                return Plan(collisions, false, "At least one affected MethodMesh intent group has no return fields to namespace.")
            }

            val untouchedNames = namedRows
                .filter { row -> groups.none { group -> row.number > group.startRow && row.number < group.endRow && row.nearestMethodMeshGroup?.startRow == group.startRow } }
                .mapTo(linkedSetOf()) { it.name }
            val reserved = untouchedNames.toMutableSet()
            val namespaces = linkedMapOf<String, String>()
            val renameByRow = linkedMapOf<Int, String>()

            for (group in groups.sortedBy { it.startRow }) {
                val outputRows = outputRowsByGroup.getValue(group)
                val requested = existingNamespace(group.intent).ifBlank { deriveNamespace(group.name) }
                val namespace = chooseNamespace(requested, outputRows.map { it.name }, reserved)
                namespaces[group.path] = namespace
                outputRows.forEach { row ->
                    val renamed = "${namespace}_${row.name}"
                    renameByRow[row.number] = renamed
                    reserved += renamed
                }
            }

            val ambiguousReference = findAmbiguousExternalReference(collidedNames, renameByRow)
            if (ambiguousReference != null) {
                return Plan(
                    collisions = collisions,
                    canAutoRepair = false,
                    reason = "The repeated field '${ambiguousReference.first}' is referenced outside a uniquely identifiable result group at survey row ${ambiguousReference.second}. Resolve that reference explicitly before creating a Kobo-compatible copy."
                )
            }

            return Plan(
                collisions = collisions,
                canAutoRepair = true,
                namespaces = namespaces,
                renamedFields = renameByRow.size
            )
        }

        fun repair(plan: Plan): ByteArray {
            val groupsByPath = rows.mapNotNull { it.beginGroup }.associateBy { it.path }
            val selectedGroups = plan.namespaces.mapNotNull { (path, namespace) ->
                groupsByPath[path]?.let { it to namespace }
            }
            require(selectedGroups.size == plan.namespaces.size) { "Kobo compatibility repair could not resolve all affected result groups." }

            val renameByRow = linkedMapOf<Int, String>()
            val oldByRow = linkedMapOf<Int, String>()
            val renamedByGroupAndOld = linkedMapOf<Pair<Int, String>, String>()
            selectedGroups.forEach { (group, namespace) ->
                rows.filter { row ->
                    row.number > group.startRow && row.number < group.endRow &&
                        !row.isGroupStart && !row.isGroupEnd && row.name.isNotBlank() &&
                        row.nearestMethodMeshGroup?.startRow == group.startRow
                }.forEach { row ->
                    val renamed = "${namespace}_${row.name}"
                    renameByRow[row.number] = renamed
                    oldByRow[row.number] = row.name
                    renamedByGroupAndOld[group.startRow to row.name] = renamed
                }
            }

            val nameColumn = headerColumns["name"] ?: error("survey.name column missing")
            val intentColumn = headerColumns.entries.firstOrNull { it.key == "body::intent" }?.value
                ?: error("survey.body::intent column missing")

            renameByRow.forEach { (rowNumber, newName) ->
                setCellValue(surveyDocument, rowElements.getValue(rowNumber), nameColumn, newName)
            }

            selectedGroups.forEach { (group, namespace) ->
                val row = rowElements[group.startRow] ?: error("Missing survey row ${group.startRow}")
                setCellValue(surveyDocument, row, intentColumn, withNamespace(group.intent, namespace))
            }

            // Rewrite ${field} references. Unique old names can be updated globally.
            // Repeated names are updated only inside their own MethodMesh result group.
            val oldOccurrences = oldByRow.entries.groupBy({ it.value }, { it.key })
            rows.forEach rowLoop@ { row ->
                val rowElement = rowElements[row.number] ?: return@rowLoop
                val values = currentCellValues(rowElement, sharedStrings)
                values.forEach cellLoop@ { (column, text) ->
                    if (!text.contains("${'$'}{")) return@cellLoop
                    var updated = text
                    oldOccurrences.forEach occurrenceLoop@ { (old, occurrenceRows) ->
                        val token = "${'$'}{$old}"
                        if (!updated.contains(token)) return@occurrenceLoop
                        val replacement = if (occurrenceRows.size == 1) {
                            renameByRow[occurrenceRows.first()]
                        } else {
                            val group = when {
                                row.beginGroup?.isMethodMeshIntent == true -> row.beginGroup
                                else -> row.nearestMethodMeshGroup
                            }
                            group?.let { renamedByGroupAndOld[it.startRow to old] }
                        }
                        if (replacement != null) updated = updated.replace(token, "${'$'}{$replacement}")
                    }
                    if (updated != text) setCellValue(surveyDocument, rowElement, column, updated)
                }
            }

            val updatedEntries = LinkedHashMap(entries)
            updatedEntries[surveyPath] = serializeXml(surveyDocument)
            val bytes = zip(updatedEntries)
            val remaining = Workbook(bytes).globalDuplicateNames()
            require(remaining.isEmpty()) {
                "Kobo compatibility repair still contains global duplicate node name${if (remaining.size == 1) "" else "s"}: ${remaining.joinToString(", ")}"
            }
            return bytes
        }

        private fun globalDuplicateNames(): Set<String> = rows.filter { it.name.isNotBlank() && !it.isGroupEnd }
            .groupBy { it.name }.filterValues { it.size > 1 }.keys

        private fun findAmbiguousExternalReference(
            collidedNames: Set<String>,
            renameByRow: Map<Int, String>
        ): Pair<String, Int>? {
            val groupByOccurrence = rows.filter { it.number in renameByRow.keys }
                .associate { it.number to it.nearestMethodMeshGroup?.startRow }
            val occurrencesByName = rows.filter { it.number in renameByRow.keys }
                .groupBy { it.name }
            rows.forEach rowLoop@ { row ->
                val values = row.values.values
                collidedNames.forEach collisionLoop@ { old ->
                    if (values.none { it.contains("${'$'}{$old}") }) return@collisionLoop
                    val occurrenceGroups = occurrencesByName[old].orEmpty().mapNotNull { groupByOccurrence[it.number] }.toSet()
                    if (occurrenceGroups.size <= 1) return@collisionLoop
                    val currentGroup = when {
                        row.beginGroup?.isMethodMeshIntent == true -> row.beginGroup.startRow
                        else -> row.nearestMethodMeshGroup?.startRow
                    }
                    if (currentGroup == null || currentGroup !in occurrenceGroups) return old to row.number
                }
            }
            return null
        }

        private fun buildScopes(rawRows: List<Pair<Int, Map<Int, String>>>): List<RowInfo> {
            val typeColumn = headerColumns["type"] ?: error("survey.type column missing")
            val nameColumn = headerColumns["name"] ?: error("survey.name column missing")
            val intentColumn = headerColumns.entries.firstOrNull { it.key == "body::intent" }?.value
            val stack = mutableListOf<Group>()
            val result = mutableListOf<RowInfo>()
            rawRows.filter { it.first > 1 }.forEach { (number, cells) ->
                val type = cells[typeColumn].orEmpty().trim()
                val name = cells[nameColumn].orEmpty().trim()
                val isEnd = type.lowercase() in setOf("end_group", "end group", "end_repeat", "end repeat")
                if (isEnd && stack.isNotEmpty()) {
                    stack.removeLast().endRow = number
                }
                val values = cells.mapKeys { (column, _) -> headerColumns.entries.firstOrNull { it.value == column }?.key ?: column.toString() }
                var begin: Group? = null
                if (type.lowercase() in setOf("begin_group", "begin group", "begin_repeat", "begin repeat") && name.isNotBlank()) {
                    begin = Group(
                        name = name,
                        startRow = number,
                        intent = intentColumn?.let { cells[it].orEmpty() }.orEmpty(),
                        parent = stack.lastOrNull()
                    )
                }
                result += RowInfo(number, type, name, values, stack.toList(), begin)
                if (begin != null) stack += begin
            }
            val lastRow = rawRows.maxOfOrNull { it.first } ?: 1
            stack.forEach { it.endRow = lastRow + 1 }
            return result
        }

        private fun pathFor(row: RowInfo): String {
            val prefix = row.groups.joinToString("/") { it.name }
            return listOf(prefix, row.name).filter { it.isNotBlank() }.joinToString("/")
        }
    }

    private fun deriveNamespace(groupName: String): String {
        val stripped = groupName.replace(resultSuffix, "").ifBlank { groupName }
        val sanitized = stripped.replace(Regex("[^A-Za-z0-9_]"), "_")
            .let { if (it.firstOrNull()?.isDigit() == true) "_$it" else it }
            .ifBlank { "methodmesh" }
        return sanitized.take(48)
    }

    private fun chooseNamespace(base: String, outputNames: List<String>, reserved: Set<String>): String {
        var candidate = base.takeIf(validNamespace::matches)?.take(48) ?: deriveNamespace(base)
        var suffix = 2
        while (outputNames.any { "${candidate}_$it" in reserved } || candidate.isBlank()) {
            val ending = "_$suffix"
            candidate = (base.take(48 - ending.length) + ending).replace(Regex("[^A-Za-z0-9_]"), "_")
            if (candidate.firstOrNull()?.isDigit() == true) candidate = "_$candidate"
            suffix += 1
        }
        return candidate
    }

    private fun existingNamespace(intent: String): String = namespaceArgument.find(intent)?.groupValues?.getOrNull(3).orEmpty()

    private fun withNamespace(intent: String, namespace: String): String {
        require(validNamespace.matches(namespace)) { "Invalid MethodMesh return namespace '$namespace'." }
        val existing = namespaceArgument.find(intent)
        if (existing != null) {
            val matched = existing.value
            val key = if (matched.trimStart().startsWith("input_")) "input_methodmesh_return_namespace" else "methodmesh_return_namespace"
            return intent.replaceRange(existing.range, "$key='$namespace'")
        }
        val close = intent.lastIndexOf(')')
        require(close >= 0) { "MethodMesh body::intent is not a function call." }
        val prefix = intent.substring(0, close).trimEnd()
        val separator = if (prefix.endsWith("(")) "" else ","
        return prefix + separator + "methodmesh_return_namespace='$namespace'" + intent.substring(close)
    }

    private fun unzip(bytes: ByteArray): LinkedHashMap<String, ByteArray> {
        val out = LinkedHashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) out[entry.name] = zip.readBytes()
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return out
    }

    private fun zip(entries: LinkedHashMap<String, ByteArray>): ByteArray = ByteArrayOutputStream().use { sink ->
        ZipOutputStream(sink).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        sink.toByteArray()
    }

    private fun surveyWorksheetPath(entries: Map<String, ByteArray>): String? {
        val workbook = entries["xl/workbook.xml"]?.let(::parseXml) ?: return null
        val rels = entries["xl/_rels/workbook.xml.rels"]?.let(::parseXml) ?: return null
        val sheets = workbook.getElementsByTagNameNS(MAIN_NS, "sheet")
        var relId: String? = null
        for (i in 0 until sheets.length) {
            val sheet = sheets.item(i) as? Element ?: continue
            if (sheet.getAttribute("name").equals("survey", ignoreCase = true)) {
                relId = sheet.getAttributeNS(DOC_REL_NS, "id").ifBlank { sheet.getAttribute("r:id") }
                break
            }
        }
        val relationships = rels.getElementsByTagNameNS(PKG_REL_NS, "Relationship")
        for (i in 0 until relationships.length) {
            val rel = relationships.item(i) as? Element ?: continue
            if (rel.getAttribute("Id") == relId) {
                val target = rel.getAttribute("Target").replace('\\', '/')
                return if (target.startsWith("/")) target.removePrefix("/") else normalizeZipPath("xl/$target")
            }
        }
        return null
    }

    private fun normalizeZipPath(path: String): String {
        val stack = mutableListOf<String>()
        path.split('/').forEach { part ->
            when (part) {
                "", "." -> Unit
                ".." -> if (stack.isNotEmpty()) stack.removeLast()
                else -> stack += part
            }
        }
        return stack.joinToString("/")
    }

    private fun readSharedStrings(bytes: ByteArray?): List<String> {
        if (bytes == null) return emptyList()
        val document = parseXml(bytes)
        val items = document.getElementsByTagNameNS(MAIN_NS, "si")
        return (0 until items.length).map { i ->
            val item = items.item(i) as Element
            val texts = item.getElementsByTagNameNS(MAIN_NS, "t")
            buildString { for (j in 0 until texts.length) append(texts.item(j).textContent.orEmpty()) }
        }
    }

    private fun parseRows(document: Document, sharedStrings: List<String>): Pair<Map<Int, Element>, List<Pair<Int, Map<Int, String>>>> {
        val rowElements = linkedMapOf<Int, Element>()
        val rows = mutableListOf<Pair<Int, Map<Int, String>>>()
        val nodes = document.getElementsByTagNameNS(MAIN_NS, "row")
        for (i in 0 until nodes.length) {
            val row = nodes.item(i) as? Element ?: continue
            val number = row.getAttribute("r").toIntOrNull() ?: (i + 1)
            rowElements[number] = row
            rows += number to currentCellValues(row, sharedStrings)
        }
        return rowElements to rows
    }

    private fun currentCellValues(row: Element, sharedStrings: List<String>): Map<Int, String> {
        val values = linkedMapOf<Int, String>()
        val children = row.childNodes
        for (i in 0 until children.length) {
            val cell = children.item(i) as? Element ?: continue
            if (cell.localName != "c") continue
            val reference = cell.getAttribute("r")
            val column = columnIndex(reference.takeWhile { it.isLetter() })
            if (column <= 0) continue
            values[column] = cellValue(cell, sharedStrings)
        }
        return values
    }

    private fun cellValue(cell: Element, sharedStrings: List<String>): String {
        val type = cell.getAttribute("t")
        if (type == "inlineStr") {
            val texts = cell.getElementsByTagNameNS(MAIN_NS, "t")
            return buildString { for (i in 0 until texts.length) append(texts.item(i).textContent.orEmpty()) }
        }
        val values = cell.getElementsByTagNameNS(MAIN_NS, "v")
        val raw = if (values.length > 0) values.item(0).textContent.orEmpty() else ""
        return if (type == "s") raw.toIntOrNull()?.let { sharedStrings.getOrNull(it) }.orEmpty() else raw
    }

    private fun setCellValue(document: Document, row: Element, column: Int, value: String) {
        val rowNumber = row.getAttribute("r").toIntOrNull() ?: error("Survey row has no row number")
        val reference = columnLetters(column) + rowNumber
        var cell: Element? = null
        val children = row.childNodes
        for (i in 0 until children.length) {
            val candidate = children.item(i) as? Element ?: continue
            if (candidate.localName == "c" && candidate.getAttribute("r") == reference) {
                cell = candidate
                break
            }
        }
        if (cell == null) {
            cell = document.createElementNS(MAIN_NS, "c").apply { setAttribute("r", reference) }
            row.appendChild(cell)
        }
        val target = cell ?: error("Could not create survey cell $reference")
        while (target.firstChild != null) target.removeChild(target.firstChild)
        target.setAttribute("t", "inlineStr")
        val inline = document.createElementNS(MAIN_NS, "is")
        val text = document.createElementNS(MAIN_NS, "t")
        if (value != value.trim()) text.setAttributeNS(XMLConstants.XML_NS_URI, "xml:space", "preserve")
        text.textContent = value
        inline.appendChild(text)
        target.appendChild(inline)
    }

    private fun columnIndex(letters: String): Int {
        var value = 0
        letters.uppercase().forEach { char -> if (char in 'A'..'Z') value = value * 26 + (char - 'A' + 1) }
        return value
    }

    private fun columnLetters(index: Int): String {
        var n = index
        val out = StringBuilder()
        while (n > 0) {
            n -= 1
            out.append(('A'.code + (n % 26)).toChar())
            n /= 26
        }
        return out.reverse().toString()
    }

    private fun parseXml(bytes: ByteArray): Document {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        // Android XML implementations vary in which hardening features they expose.
        // Apply every supported protection without making XLSX parsing depend on a
        // particular parser implementation.
        runCatching { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        runCatching { factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
        runCatching { factory.isXIncludeAware = false }
        runCatching { factory.setExpandEntityReferences(false) }
        return factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
    }

    private fun serializeXml(document: Document): ByteArray = ByteArrayOutputStream().use { out ->
        val transformer = TransformerFactory.newInstance().newTransformer().apply {
            setOutputProperty(OutputKeys.ENCODING, "UTF-8")
            setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no")
            setOutputProperty(OutputKeys.INDENT, "no")
        }
        transformer.transform(DOMSource(document), StreamResult(out))
        out.toByteArray()
    }
}
