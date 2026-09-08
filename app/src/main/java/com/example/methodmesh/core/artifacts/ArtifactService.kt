package com.example.methodmesh.core.artifacts

import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.Properties
import java.util.UUID

enum class ArtifactOrigin { BUNDLED, MANAGED, EXTERNAL }
enum class ArtifactLifecycle { PERSISTENT, SESSION, TRANSIENT }

data class ArtifactRef(val id: String) {
    init { require(id.matches(Regex("[A-Za-z0-9._-]+"))) }
    override fun toString() = "artifact://$id"
}

data class Artifact(
    val ref: ArtifactRef,
    val displayName: String,
    val mimeType: String,
    val origin: ArtifactOrigin,
    val lifecycle: ArtifactLifecycle,
    val location: String,
    val sha256: String? = null,
    val derivedFrom: ArtifactRef? = null,
    val operation: String? = null,
    val sessionId: String? = null
)

data class ArtifactPickerRequest(
    val mimeTypes: Set<String> = setOf("*/*"),
    val lifecycles: Set<ArtifactLifecycle> = ArtifactLifecycle.entries.toSet(),
    val origins: Set<ArtifactOrigin> = ArtifactOrigin.entries.toSet(),
    val query: String = ""
) {
    fun accepts(a: Artifact) = a.lifecycle in lifecycles && a.origin in origins &&
        a.displayName.contains(query, true) && mimeTypes.any { it == "*/*" ||
            it.equals(a.mimeType, true) || (it.endsWith("/*") && a.mimeType.startsWith(it.dropLast(1), true)) }
}

/** Shared identity, resolution and handoff service. Persistence is explicit. */
class ArtifactService(private val store: File, private val workspace: File,
    private val openExternal: (String) -> InputStream) {
    private val records = linkedMapOf<ArtifactRef, Artifact>()
    init {
        store.mkdirs(); workspace.mkdirs()
        store.listFiles { file -> file.extension == "properties" }.orEmpty().forEach { metadata ->
            runCatching {
                val properties = Properties().apply { metadata.inputStream().use(::load) }
                val ref = ArtifactRef(properties.getProperty("id"))
                records[ref] = Artifact(ref, properties.getProperty("name", ref.id),
                    properties.getProperty("mime", "application/octet-stream"),
                    ArtifactOrigin.valueOf(properties.getProperty("origin", ArtifactOrigin.MANAGED.name)),
                    ArtifactLifecycle.valueOf(properties.getProperty("lifecycle", ArtifactLifecycle.PERSISTENT.name)),
                    properties.getProperty("location", ref.id), properties.getProperty("sha256"),
                    properties.getProperty("parent")?.takeIf(String::isNotBlank)?.let(::ArtifactRef),
                    properties.getProperty("operation"), properties.getProperty("session")?.takeIf(String::isNotBlank))
            }
        }
    }

    @Synchronized fun linkExternal(uri: String, name: String, mime: String, session: String): ArtifactRef {
        require(uri.startsWith("content://")); require(session.isNotBlank())
        return ArtifactRef(UUID.randomUUID().toString()).also { records[it] = Artifact(it, name, mime,
            ArtifactOrigin.EXTERNAL, ArtifactLifecycle.SESSION, uri, sessionId = session) }
    }

    @Synchronized fun registerExternal(ref: ArtifactRef, uri: String, name: String, mime: String,
        lifecycle: ArtifactLifecycle = ArtifactLifecycle.PERSISTENT): ArtifactRef {
        require(uri.startsWith("content://")); require(lifecycle != ArtifactLifecycle.TRANSIENT)
        records[ref] = Artifact(ref, name, mime, ArtifactOrigin.EXTERNAL, lifecycle, uri)
        if (lifecycle == ArtifactLifecycle.PERSISTENT) writeMetadata(records.getValue(ref))
        return ref
    }

    @Synchronized fun query(request: ArtifactPickerRequest = ArtifactPickerRequest()) = records.values.filter(request::accepts)
    @Synchronized fun resolve(ref: ArtifactRef) = records[ref] ?: error("Artifact unavailable: $ref")

    /** Remove an artifact from Files. External source files are never deleted. */
    @Synchronized fun delete(ref: ArtifactRef) {
        val artifact = records.remove(ref) ?: error("Artifact unavailable: $ref")
        if (artifact.origin == ArtifactOrigin.MANAGED) {
            File(workspace, artifact.location).delete()
            File(store, artifact.location).delete()
        }
        File(store, "${ref.id}.properties").delete()
    }

    fun open(ref: ArtifactRef): InputStream {
        val artifact = resolve(ref)
        return if (artifact.origin == ArtifactOrigin.MANAGED) File(workspace, artifact.location).inputStream()
        else openExternal(artifact.location)
    }

    @Synchronized fun persist(ref: ArtifactRef): ArtifactRef {
        val source = resolve(ref)
        if (source.origin == ArtifactOrigin.MANAGED && source.lifecycle == ArtifactLifecycle.PERSISTENT) return ref
        val target = File(store, UUID.randomUUID().toString())
        open(ref).use { input -> target.outputStream().use { input.copyTo(it) } }
        val persisted = Artifact(ArtifactRef(target.name), source.displayName, source.mimeType,
            ArtifactOrigin.MANAGED, ArtifactLifecycle.PERSISTENT, target.name, source.sha256, ref, "artifact.persist")
        records[persisted.ref] = persisted
        writeMetadata(persisted)
        return persisted.ref
    }

    @Synchronized fun create(name: String, mime: String, session: String, input: InputStream,
        lifecycle: ArtifactLifecycle = ArtifactLifecycle.TRANSIENT, parent: ArtifactRef? = null,
        operation: String? = null): ArtifactRef {
        require(lifecycle != ArtifactLifecycle.PERSISTENT); require(session.isNotBlank())
        val ref = ArtifactRef(UUID.randomUUID().toString()); val file = File(workspace, ref.id)
        val digest = MessageDigest.getInstance("SHA-256")
        input.use { source -> file.outputStream().use { target -> source.copyTo(target); digest.update(file.readBytes()) } }
        records[ref] = Artifact(ref, name, mime, ArtifactOrigin.MANAGED, lifecycle, file.name,
            digest.digest().joinToString("") { "%02x".format(it) }, parent, operation, session)
        return ref
    }

    @Synchronized fun createPersistent(name: String, mime: String, input: InputStream): ArtifactRef {
        val ref = ArtifactRef(UUID.randomUUID().toString())
        val file = File(store, ref.id)
        input.use { source -> file.outputStream().use { target -> source.copyTo(target) } }
        val artifact = Artifact(ref, name, mime, ArtifactOrigin.MANAGED, ArtifactLifecycle.PERSISTENT, file.name)
        records[ref] = artifact
        writeMetadata(artifact)
        return ref
    }

    @Synchronized fun release(ref: ArtifactRef) {
        val a = resolve(ref); require(a.lifecycle != ArtifactLifecycle.PERSISTENT)
        File(workspace, a.location).delete(); records.remove(ref)
    }
    @Synchronized fun endSession(session: String) { records.values.filter { it.sessionId == session }.map { it.ref }.forEach(::release) }

    private fun writeMetadata(artifact: Artifact) {
        if (artifact.lifecycle != ArtifactLifecycle.PERSISTENT) return
        val properties = Properties().apply {
            setProperty("id", artifact.ref.id)
            setProperty("name", artifact.displayName)
            setProperty("mime", artifact.mimeType)
            setProperty("origin", artifact.origin.name)
            setProperty("lifecycle", artifact.lifecycle.name)
            setProperty("location", artifact.location)
            artifact.sha256?.let { setProperty("sha256", it) }
            artifact.derivedFrom?.let { setProperty("parent", it.id) }
            artifact.operation?.let { setProperty("operation", it) }
            artifact.sessionId?.let { setProperty("session", it) }
        }
        val partial = File(store, "${artifact.ref.id}.properties.partial")
        partial.outputStream().use { properties.store(it, null) }
        check(partial.renameTo(File(store, "${artifact.ref.id}.properties")))
    }
}

class ArtifactStore(private val service: ArtifactService) {
    fun files(query: String = "") = service.query(ArtifactPickerRequest(
        lifecycles = setOf(ArtifactLifecycle.PERSISTENT), query = query))
    fun save(ref: ArtifactRef): ArtifactRef = service.persist(ref)
}
