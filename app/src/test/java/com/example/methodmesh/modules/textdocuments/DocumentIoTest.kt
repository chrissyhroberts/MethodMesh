package com.example.methodmesh.modules.textdocuments

import org.junit.Assert.assertEquals
import org.junit.Test

class DocumentIoTest {
    @Test
    fun `filename detection covers common data markup and source files`() {
        assertEquals(DocumentFormat.PYTHON, DocumentFormat.detect("analysis.py", "text/plain"))
        assertEquals(DocumentFormat.JSONL, DocumentFormat.detect("events.ndjson", "application/octet-stream"))
        assertEquals(DocumentFormat.YAML, DocumentFormat.detect("study.yml", "text/plain"))
        assertEquals(DocumentFormat.KOTLIN, DocumentFormat.detect("Module.kts", "text/plain"))
        assertEquals(DocumentFormat.SQL, DocumentFormat.detect("query.sql", "application/octet-stream"))
        assertEquals(DocumentFormat.HTML, DocumentFormat.detect("report.html", "text/plain"))
        assertEquals(DocumentFormat.TEXT, DocumentFormat.detect("notes.custom", "text/plain"))
    }

    @Test
    fun `save as preserves an existing filename extension`() {
        assertEquals("analysis.py", DocumentIo.ensureExtension("analysis.py", DocumentFormat.TEXT))
        assertEquals("events.ndjson", DocumentIo.ensureExtension("events.ndjson", DocumentFormat.JSONL))
        assertEquals("README.txt", DocumentIo.ensureExtension("README", DocumentFormat.TEXT))
        assertEquals("study.yaml", DocumentIo.ensureExtension("study.yaml", DocumentFormat.YAML))
    }

    @Test
    fun `contract formats round trip for source and configuration files`() {
        listOf(
            DocumentFormat.PYTHON,
            DocumentFormat.KOTLIN,
            DocumentFormat.SHELL,
            DocumentFormat.SQL,
            DocumentFormat.YAML,
            DocumentFormat.TOML,
            DocumentFormat.PROPERTIES
        ).forEach { format ->
            assertEquals(format, DocumentFormat.fromContract(format.contractValue))
        }
    }
}
