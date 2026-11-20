package com.example.methodmesh.modules.imageredaction

import org.junit.Assert.*
import org.junit.Test

class RedactionViewportGeometryTest {
    private fun layout(view: RedactionView = RedactionView()) = redactionViewportLayout(400f, 600f, 400, 400, view)!!
    @Test fun startsWithWholeImageFittedAndLetterboxesExcluded() {
        val l = layout()
        assertEquals(0f, l.originX); assertEquals(100f, l.originY)
        assertNull(l.point(100f, 20f)); assertEquals(RedactionPoint(.25f, .25f), l.point(100f, 200f))
    }
    @Test fun tallPageFitsWithoutCropping() {
        val l = redactionViewportLayout(400f, 400f, 200, 400)!!
        assertEquals(100f, l.originX); assertEquals(0f, l.originY)
        assertEquals(200f, l.width); assertEquals(400f, l.height)
    }
    @Test fun panClampsSoNoBlankSpaceCanBeDraggedAcrossAnEnlargedImage() {
        val l = layout(RedactionView(4f, -100f, 100f))
        assertEquals(0f, l.originX); assertEquals(600f, l.originY + l.height)
        val fitted = layout(RedactionView(1f, -100f, 100f))
        assertEquals(RedactionView(), fitted.view)
    }
    @Test fun pinchAnchorsTheSameDocumentPointUnderTheFingers() {
        val before = layout(RedactionView(2f))
        val after = layout(before.transform(130f, 260f, 0f, 0f, 2f))
        assertEquals(before.point(130f, 260f), after.point(130f, 260f))
    }
    @Test fun panChangesTheViewNotTheDocumentCoordinates() {
        val before = layout(RedactionView(3f))
        val after = layout(before.transform(200f, 300f, 60f, -60f, 1f))
        assertEquals(before.point(200f, 300f), after.point(260f, 240f))
    }
    @Test fun rectangleUsesTheInverseOfTheZoomedPannedDrawingTransform() {
        val l = layout(RedactionView(3f, .4f, .6f))
        val r = l.region(2, l.originX + .3f * l.width, l.originY + .4f * l.height,
            l.originX + .6f * l.width, l.originY + .8f * l.height)!!
        assertEquals(2, r.page)
        assertEquals(.3f, r.left, .00001f); assertEquals(.4f, r.top, .00001f)
        assertEquals(.6f, r.right, .00001f); assertEquals(.8f, r.bottom, .00001f)
    }
    @Test fun zoomedReversedDragClampsButTapsAndMarginStartsDoNotRedact() {
        val l = layout(RedactionView(2f))
        assertNull(l.region(0, 200f, 300f, 201f, 301f))
        assertEquals(PdfRedactionRegion(0, 0f, 0f, .5f, .5f), l.region(0, 200f, 300f, -999f, -999f))
        assertNull(layout().region(0, 10f, 10f, 300f, 400f))
    }
    @Test fun gridHitTestingUsesOriginalImageCellsAndClampsPageEdge() {
        val l = layout(RedactionView(2f, .4f, .4f))
        assertEquals("r1c1", l.point(200f, 300f)!!.gridCell(2, 2))
        assertEquals("r2c2", layout().point(200f, 300f)!!.gridCell(2, 2))
        assertEquals("r2c2", RedactionPoint(1f, 1f).gridCell(2, 2))
    }
    @Test fun zoomLimitsAndMalformedInputCannotMakeInvalidCoordinates() {
        assertEquals(16f, layout().transform(200f, 300f, 0f, 0f, 999f).zoom)
        assertEquals(RedactionView(), layout(RedactionView(4f)).transform(200f, 300f, 0f, 0f, .001f))
        assertEquals(layout().view, layout().transform(Float.NaN, 0f, 0f, 0f, 2f))
        assertNull(redactionViewportLayout(0f, 100f, 400, 400))
        assertNull(redactionViewportLayout(Float.POSITIVE_INFINITY, 100f, 400, 400))
        assertNull(redactionViewportLayout(Float.MAX_VALUE, Float.MAX_VALUE, 400, 400, RedactionView(16f)))
        assertNull(redactionViewportLayout(100f, 100f, 400, 400, RedactionView(Float.NaN)))
    }
    @Test fun changingViewportKeepsAValidNormalisedCenterAndDoesNotChangeMasks() {
        val view = RedactionView(4f, .35f, .65f)
        val portrait = layout(view)
        val landscape = redactionViewportLayout(600f, 400f, 400, 400, portrait.view)!!
        assertEquals(view, landscape.view)
        assertEquals(portrait.point(200f, 300f), landscape.point(300f, 200f))
    }
}
