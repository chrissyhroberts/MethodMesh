package com.example.methodmesh.transport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit tests for GraphSelector, GraphSelectorParser, and GraphSelectorParser.looksLikeSelector.
 */
class GraphSelectorTest {

    // ── GraphSelector.defaultAliasFor ────────────────────────────────────────

    @Test
    fun `dotted path gives leaf segment as alias`() {
        assertEquals("uid", GraphSelector.defaultAliasFor("observation.nfc.uid"))
    }

    @Test
    fun `graphUri prefix is stripped before deriving alias`() {
        assertEquals("uid", GraphSelector.defaultAliasFor("graph://latest/observation/nfc.uid"))
    }

    @Test
    fun `single-segment path uses itself as alias`() {
        assertEquals("value", GraphSelector.defaultAliasFor(""))
    }

    @Test
    fun `execution id path gives id as alias`() {
        assertEquals("id", GraphSelector.defaultAliasFor("execution.id"))
    }

    @Test
    fun `hyphens and dots in path are replaced by underscores`() {
        val alias = GraphSelector.defaultAliasFor("observation.nfc.tag-uid")
        assertFalse("alias should not contain hyphens", alias.contains('-'))
    }

    // ── GraphSelectorParser.parse ────────────────────────────────────────────

    @Test
    fun `as alias syntax is parsed correctly`() {
        val selectors = GraphSelectorParser.parse("observation.nfc.uid as tag_uid")
        assertEquals(1, selectors.size)
        assertEquals("observation.nfc.uid", selectors[0].path)
        assertEquals("tag_uid", selectors[0].alias)
    }

    @Test
    fun `colon alias syntax is parsed correctly`() {
        val selectors = GraphSelectorParser.parse("execution.id:exec_id")
        assertEquals(1, selectors.size)
        assertEquals("execution.id", selectors[0].path)
        assertEquals("exec_id", selectors[0].alias)
    }

    @Test
    fun `path with no alias uses default alias`() {
        val selectors = GraphSelectorParser.parse("observation.nfc.uid")
        assertEquals(1, selectors.size)
        assertEquals("uid", selectors[0].alias)
    }

    @Test
    fun `comma-separated multiple selectors are all parsed`() {
        val selectors = GraphSelectorParser.parse("execution.id as exec_id, observation.nfc.uid as tag_uid")
        assertEquals(2, selectors.size)
        assertEquals("exec_id", selectors[0].alias)
        assertEquals("tag_uid", selectors[1].alias)
    }

    @Test
    fun `semicolon-separated multiple selectors are all parsed`() {
        val selectors = GraphSelectorParser.parse("execution.id as exec_id; observation.nfc.uid as tag_uid")
        assertEquals(2, selectors.size)
    }

    @Test
    fun `null input returns empty list`() {
        assertTrue(GraphSelectorParser.parse(null).isEmpty())
    }

    @Test
    fun `blank input returns empty list`() {
        assertTrue(GraphSelectorParser.parse("").isEmpty())
        assertTrue(GraphSelectorParser.parse("   ").isEmpty())
    }

    @Test
    fun `graphUri path retains full path after stripping prefix`() {
        val selectors = GraphSelectorParser.parse("graph://latest/observation/nfc.uid as tag_uid")
        assertEquals(1, selectors.size)
        assertEquals("graph://latest/observation/nfc.uid", selectors[0].path)
        assertEquals("tag_uid", selectors[0].alias)
    }

    // ── GraphSelectorParser.looksLikeSelector ────────────────────────────────

    @Test
    fun `graphUri looks like a selector`() {
        assertTrue(GraphSelectorParser.looksLikeSelector("graph://latest/observation/nfc.uid"))
    }

    @Test
    fun `as alias expression looks like a selector`() {
        assertTrue(GraphSelectorParser.looksLikeSelector("execution.id as exec_id"))
    }

    @Test
    fun `execution dot path looks like a selector`() {
        assertTrue(GraphSelectorParser.looksLikeSelector("execution.id"))
    }

    @Test
    fun `observation dot path looks like a selector`() {
        assertTrue(GraphSelectorParser.looksLikeSelector("observation.nfc.uid"))
    }

    @Test
    fun `plain method id does not look like a selector`() {
        assertFalse(GraphSelectorParser.looksLikeSelector("not_a_graph_selector"))
    }

    @Test
    fun `null does not look like a selector`() {
        assertFalse(GraphSelectorParser.looksLikeSelector(null))
    }

    @Test
    fun `blank string does not look like a selector`() {
        assertFalse(GraphSelectorParser.looksLikeSelector(""))
        assertFalse(GraphSelectorParser.looksLikeSelector("   "))
    }
}


