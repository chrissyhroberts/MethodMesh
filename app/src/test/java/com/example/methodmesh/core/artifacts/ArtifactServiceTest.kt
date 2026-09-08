package com.example.methodmesh.core.artifacts

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ArtifactServiceTest {
    @get:Rule val temp = TemporaryFolder()
    private fun service() = ArtifactService(File(temp.root, "store"), File(temp.root, "work"),
        { "bundled PDF".byteInputStream() }, { "external PDF".byteInputStream() })

    @Test fun `consent derivatives pass between capabilities without entering Files`() {
        val s = service()
        val template = ArtifactRef("module.consent")
        s.registerBundled(Artifact(template, "Consent.pdf", "application/pdf", ArtifactOrigin.BUNDLED,
            ArtifactLifecycle.PERSISTENT, "consent.pdf"))
        val signed = s.create("Signed.pdf", "application/pdf", "enrolment", "signed".byteInputStream(),
            derivedFrom = template, operation = "ink.sign")
        val stamped = s.create("Stamped.pdf", "application/pdf", "enrolment", "stamped".byteInputStream(),
            derivedFrom = signed, operation = "tsa.timestamp")
        assertEquals("stamped", s.open(stamped).bufferedReader().use { it.readText() })
        assertEquals("stamped", s.open(stamped).bufferedReader().use { it.readText() })
        assertEquals(listOf(template), ArtifactStore(s).files().map { it.ref })
        s.endSession("enrolment")
        assertEquals(listOf(template), s.query().map { it.ref })
        assertTrue(File(temp.root, "work").listFiles().orEmpty().isEmpty())
    }

    @Test fun `explicit saving survives restart and session cleanup without mutating source`() {
        val s = service()
        val ref = s.create("a.pdf", "application/pdf", "run", "bytes".byteInputStream())
        val saved = ArtifactStore(s).save(ref)
        assertNotEquals(ref, saved)
        assertEquals(ArtifactLifecycle.TRANSIENT, s.resolve(ref).lifecycle)
        s.endSession("run")
        val restored = service()
        assertEquals("bytes", restored.open(saved).bufferedReader().use { it.readText() })
        assertEquals(ref, restored.resolve(saved).derivedFrom)
        assertEquals(64, restored.resolve(saved).sha256!!.length)
    }

    @Test fun `picker filters without persisting external files`() {
        val s = service()
        val ref = s.linkExternal("content://documents/123", "Consent.pdf", "application/pdf", "run")
        assertEquals(ref, s.query(ArtifactPickerRequest(mimeTypes = setOf("application/*"), query = "consent")).single().ref)
        assertTrue(ArtifactStore(s).files().isEmpty())
        assertTrue(s.query(ArtifactPickerRequest(mimeTypes = setOf("image/*"))).isEmpty())
        s.endSession("run")
        assertTrue(s.query().isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `artifact references reject filesystem traversal`() { ArtifactRef.parse("artifact://../../secret") }

    @Test fun `failed producer leaves no partial files or visible artifacts`() {
        val s = service()
        val broken = object : java.io.InputStream() { override fun read(): Int = error("producer failed") }
        assertTrue(runCatching { s.create("broken", "application/pdf", "run", broken) }.isFailure)
        assertTrue(s.query().isEmpty())
        assertTrue(File(temp.root, "work").listFiles().orEmpty().isEmpty())
    }

    @Test fun `ending one workflow leaves other workflow alive`() {
        val s = service()
        s.create("one", "text/plain", "one", "1".byteInputStream())
        val second = s.create("two", "text/plain", "two", "2".byteInputStream(), ArtifactLifecycle.SESSION)
        s.endSession("one")
        assertEquals(second, s.query().single().ref)
    }
}
