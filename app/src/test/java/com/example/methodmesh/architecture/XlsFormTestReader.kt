package com.example.methodmesh.architecture

import java.io.File
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/** Read actual worksheet cells, including shared strings, rather than searching ZIP XML. */
internal object XlsFormTestReader {
    fun sheet(file: File, name: String): List<Map<String, String>> = ZipFile(file).use { zip ->
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        fun elements(path: String, tag: String): List<Element> {
            val entry = zip.getEntry(path) ?: return emptyList()
            val doc = zip.getInputStream(entry).use { factory.newDocumentBuilder().parse(it) }
            val nodes = doc.getElementsByTagNameNS("*", tag)
            return (0 until nodes.length).map { nodes.item(it) as Element }
        }
        val strings = elements("xl/sharedStrings.xml", "si").map { it.textContent }
        val sheet = elements("xl/workbook.xml", "sheet").first { it.getAttribute("name") == name }
        val rel = elements("xl/_rels/workbook.xml.rels", "Relationship")
            .first { it.getAttribute("Id") == sheet.getAttributeNS("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id") }
        val target = rel.getAttribute("Target")
        val path = if (target.startsWith("/")) target.removePrefix("/") else "xl/$target"
        val rows = elements(path, "row").map { row ->
            val cells = row.getElementsByTagNameNS("*", "c")
            (0 until cells.length).associate { i ->
                val cell = cells.item(i) as Element
                val value = when (cell.getAttribute("t")) {
                    "s" -> strings[cell.getElementsByTagNameNS("*", "v").item(0).textContent.toInt()]
                    "inlineStr" -> cell.getElementsByTagNameNS("*", "is").item(0)?.textContent.orEmpty()
                    else -> cell.getElementsByTagNameNS("*", "v").item(0)?.textContent.orEmpty()
                }
                cell.getAttribute("r").filter(Char::isLetter) to value
            }
        }
        val headers = rows.firstOrNull().orEmpty()
        rows.drop(1).map { row -> row.mapKeys { (column, _) -> headers[column].orEmpty() } }
    }
}
