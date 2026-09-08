package com.example.methodmesh.core.artifacts

import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.Properties
import java.util.UUID

enum class ArtifactOrigin { BUNDLED, MANAGED, EXTERNAL }
enum class ArtifactLifecycle { PERSISTENT, SESSION, TRANSIENT }

data class ArtifactRef(val id: String) {
    init { require(id.matches(Regex("[A-Za-z0-9._-]+"))) { "Invalid artifact identity" } }
    override fun toString(): String = "artifact://$id"
    companion object {
        fun parse(value: String): ArtifactRef {
            require(value.startsWith("artifact://"))
            return ArtifactRef(value.removePrefix("artifact://"))
        }
    }
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

/** Common selection contract. Selecting never imports, persists or copies bytes. */
data class ArtifactPickerRequest(
    val mimeTypes: Set<String> = setOf("*/*"),
    val lifecycles: Set<ArtifactLifecycle> = ArtifactLifecycle.entries.toSet(),
    val origins: Set<ArtifactOrigin> = ArtifactOrigin.entries.toSet(),
    val query: String = ""
) {
    fun accepts(artifact: Artifact): Boolean = artifact.lifecycle in lifecycles && artifact.origin in origins &&
        artifact.displayName.contains(query, ignoreCase = true) && mimeTypes.any {
            it == "*/*" || it.equals(artifact.mimeType, ignoreCase = true) ||
                (it.endsWith("/*") && artifact.mimeType.startsWith(it.dropLast(1), ignoreCase = true))
        }
}

/** Identity and transport are independent of persistence. Callers own workflow completion.
 * Blocking byte operations must run off the UI thread. No module implementation dependencies.
 */
class ArtifactService(
    private val storeRoot: File,
    private val workspaceRoot: File,
    private val openBundled: (String) -> InputStream,
    private val openExternal: (String) -> InputStream
) {
    private val records = linkedMapOf<ArtifactRef, Artifact>()
    init {
        storeRoot.mkdirs()
        workspaceRoot.mkdirs()
        storeRoot.listFiles().orEmpty().filter { it.extension == "properties" }.forEach { file ->
            val p = Properties().apply { file.inputStream().use { load(it) } }
            val a = Artifact(ArtifactRef(p.getProperty("id")), p.getProperty("name"), p.getProperty("mime"),
                ArtifactOrigin.MANAGED, ArtifactLifecycle.PERSISTENT, p.getProperty("id"),
                p.getProperty("sha256"), p.getProperty("parent")?.let(::ArtifactRef), p.getProperty("operation"))
            check(File(storeRoot, a.location).isFile) { "Missing stored artifact ${a.ref}" }
            records[a.ref] = a
        }
    }

    @Synchronized fun registerBundled(artifact: Artifact) {
        require(artifact.origin == ArtifactOrigin.BUNDLED && artifact.lifecycle == ArtifactLifecycle.PERSISTENT)
        val existing = records[artifact.ref]
        require(existing == null || existing == artifact) { "Duplicate artifact identity" }
        records[artifact.ref] = artifact
    }

    @Synchronized fun linkExternal(uri: String, name: String, mime: String, sessionId: String): ArtifactRef {
        require(uri.startsWith("content://")) { "External artifacts require a content URI" }
        require(sessionId.isNotBlank())
        val ref = newRef()
        records[ref] = Artifact(ref, name, mime, ArtifactOrigin.EXTERNAL, ArtifactLifecycle.SESSION,
            uri, sessionId = sessionId)
        return ref
    }

    @Synchronized fun resolve(ref: ArtifactRef): Artifact = records[ref] ?: error("Artifact unavailable: $ref")
    @Synchronized fun query(request: ArtifactPickerRequest = ArtifactPickerRequest()): List<Artifact> =
        records.values.filter(request::accepts).sortedBy { it.displayName.lowercase() }

    fun open(ref: ArtifactRef): InputStream {
        val a = resolve(ref)
        return when (a.origin) {
            ArtifactOrigin.BUNDLED -> openBundled(a.location)
            ArtifactOrigin.EXTERNAL -> openExternal(a.location)
            ArtifactOrigin.MANAGED -> managedFile(a).inputStream()
        }
    }

    /** Generated results default to transient, and belong to an explicit workflow session. */
    @Synchronized fun create(
        name: String, mime: String, sessionId: String, input: InputStream,
        lifecycle: ArtifactLifecycle = ArtifactLifecycle.TRANSIENT,
        derivedFrom: ArtifactRef? = null, operation: String? = null
    ): ArtifactRef {
        require(lifecycle != ArtifactLifecycle.PERSISTENT) { "Use persist explicitly" }
        require(sessionId.isNotBlank())
        derivedFrom?.let(::resolve)
        return write(name, mime, input, lifecycle, sessionId, derivedFrom, operation)
    }

    /** Persist an immutable derivative; the input reference and bytes remain unchanged. */
    @Synchronized fun persist(ref: ArtifactRef): ArtifactRef {
        val a = resolve(ref)
        if (a.origin == ArtifactOrigin.MANAGED && a.lifecycle == ArtifactLifecycle.PERSISTENT) return ref
        return open(ref).use { write(a.displayName, a.mimeType, it, ArtifactLifecycle.PERSISTENT,
            null, ref, "artifact.persist") }
    }

    /** Release only after every receiver has consumed the result, never merely after launching Share. */
    @Synchronized fun release(ref: ArtifactRef) {
        val a = resolve(ref)
        require(a.lifecycle != ArtifactLifecycle.PERSISTENT) { "Persistent artifacts need explicit deletion" }
        if (a.origin == ArtifactOrigin.MANAGED) check(managedFile(a).delete() || !managedFile(a).exists())
        records.remove(ref)
    }

    @Synchronized fun endSession(sessionId: String) {
        records.values.filter { it.sessionId == sessionId && it.lifecycle != ArtifactLifecycle.PERSISTENT }
            .map { it.ref }.forEach(::release)
    }

    private fun write(name: String, mime: String, input: InputStream, lifecycle: ArtifactLifecycle,
        sessionId: String?, parent: ArtifactRef?, operation: String?): ArtifactRef {
        require(name.isNotBlank() && mime.isNotBlank())
        val ref = newRef()
        val root = if (lifecycle == ArtifactLifecycle.PERSISTENT) storeRoot else workspaceRoot
        val file = File(root, ref.id)
        val partial = File(root, "${ref.id}.partial")
        val digest = MessageDigest.getInstance("SHA-256")
        try {
            partial.outputStream().use { output ->
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                    digest.update(buffer, 0, count)
                }
            }
            check(partial.renameTo(file)) { "Cannot commit artifact bytes" }
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            val artifact = Artifact(ref, name, mime, ArtifactOrigin.MANAGED, lifecycle, ref.id,
                hash, parent, operation, sessionId)
            if (lifecycle == ArtifactLifecycle.PERSISTENT) {
                val p = Properties().apply {
                    setProperty("id", ref.id); setProperty("name", name); setProperty("mime", mime)
                    setProperty("sha256", hash)
                    parent?.let { setProperty("parent", it.id) }; operation?.let { setProperty("operation", it) }
                }
                val metadata = File(root, "${ref.id}.properties.partial")
                metadata.outputStream().use { p.store(it, null) }
                check(metadata.renameTo(File(root, "${ref.id}.properties")))
            }
            records[ref] = artifact
            return ref
        } catch (e: Exception) {
            partial.delete(); file.delete(); File(root, "${ref.id}.properties.partial").delete()
            throw e
        }
    }
    private fun managedFile(a: Artifact) = File(
        if (a.lifecycle == ArtifactLifecycle.PERSISTENT) storeRoot else workspaceRoot, a.ref.id)
    private fun newRef() = ArtifactRef(UUID.randomUUID().toString())
}

/** Files is a persistent view over the service, not the owner of the transport layer. */
class ArtifactStore(private val service: ArtifactService) {
    fun files(query: String = ""): List<Artifact> = service.query(ArtifactPickerRequest(
        lifecycles = setOf(ArtifactLifecycle.PERSISTENT), query = query))
    fun save(ref: ArtifactRef): ArtifactRef = service.persist(ref)
}
