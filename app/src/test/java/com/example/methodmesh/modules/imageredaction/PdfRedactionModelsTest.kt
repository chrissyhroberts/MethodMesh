package com.example.methodmesh.modules.imageredaction

import org.junit.Assert.*
import org.junit.Test

class PdfRedactionModelsTest {
    @Test fun masksRoundTripWithExactPageAndCoordinates() {
        val masks = listOf(PdfRedactionRegion(0, 0.1f, 0.2f, 0.3f, 0.4f), PdfRedactionRegion(2, 0f, 0f, 1f, 1f))
        assertEquals(masks, PdfRedactionRegions.decode(PdfRedactionRegions.encode(masks)))
    }
    @Test fun invalidRectanglesAreRejectedRatherThanPartlyApplied() {
        listOf(floatArrayOf(-0.1f, 0f, 1f, 1f), floatArrayOf(0.2f, 0.2f, 0.1f, 1f),
            floatArrayOf(0f, 0f, Float.NaN, 1f), floatArrayOf(0f, 0f, 1f, Float.POSITIVE_INFINITY),
            floatArrayOf(0f, 0f, 0f, 1f)).forEach { v ->
            assertTrue(runCatching { PdfRedactionRegion(0, v[0], v[1], v[2], v[3]) }.isFailure)
        }
        assertTrue(runCatching { PdfRedactionRegion(-1, 0f, 0f, 1f, 1f) }.isFailure)
    }
    @Test fun masksRoundOutwardAndClampToThePage() {
        assertArrayEquals(intArrayOf(9, 19, 32, 42), PdfRedactionRegion(0, .105f, .205f, .305f, .405f).pixels(100, 100))
        assertArrayEquals(intArrayOf(0, 0, 100, 100), PdfRedactionRegion(0, 0f, 0f, 1f, 1f).pixels(100, 100))
    }
    @Test fun letterboxingMapsDragToTheActualImageNotTheViewport() {
        assertEquals(PdfRedactionRegion(1, .25f, .25f, .75f, .75f),
            PdfRedactionRegion.fromDrag(1, 75f, 50f, 125f, 150f, 200f, 200f, 100, 200))
        assertNull(PdfRedactionRegion.fromDrag(1, 10f, 10f, 125f, 150f, 200f, 200f, 100, 200))
    }
    @Test fun reversedDragClampsAtThePageBoundaryAndIgnoresTaps() {
        assertEquals(PdfRedactionRegion(0, 0f, 0f, .5f, .5f),
            PdfRedactionRegion.fromDrag(0, 100f, 100f, -20f, -20f, 200f, 200f, 200, 200))
        assertNull(PdfRedactionRegion.fromDrag(0, 100f, 100f, 101f, 101f, 200f, 200f, 200, 200))
    }
    @Test fun corruptOrFractionalPageMasksDoNotBecomeEmptySuccessfulMasks() {
        listOf("not-json", """[{"page":0.5,"left":0,"top":0,"right":1,"bottom":1}]""", """[{"page":0}]""").forEach {
            assertTrue(runCatching { PdfRedactionRegions.decode(it) }.isFailure)
        }
    }
    @Test fun regionCountIsBounded() {
        val masks = List(PdfRedactionRegions.MAX_REGIONS + 1) { PdfRedactionRegion(0, 0f, 0f, 1f, 1f) }
        assertTrue(runCatching { PdfRedactionRegions.encode(masks) }.isFailure)
    }
    @Test fun moduleExposesIndependentPdfAndUnchangedImageContracts() {
        assertEquals(setOf("image.redact", "pdf.redact"), ImageRedactionModule.as100Methods().map { it.id }.toSet())
        assertEquals(setOf("image.redact", "pdf.redact"), ImageRedactionModule.capabilityScreens().map { it.capabilityId }.toSet())
        assertEquals("0.1.0", As100ImageRedactionMethod.descriptor.version)
        assertTrue(ImageRedactionModule.capabilitySettings().getValue("pdf.redact").any { it.id == "pdf_uri" })
        assertEquals(As100PdfRedactionMethod.descriptor.outputs, As100PdfRedactionMethod.contract.producedFields)
        assertEquals("OFFLINE", As100PdfRedactionMethod.descriptor.parameters["connectivity"])
        assertFalse(PdfRedactionFields.outputs.any { it.startsWith("source_") || it.startsWith("original_") })
    }
}
