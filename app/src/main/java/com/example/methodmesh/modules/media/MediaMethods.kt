package com.example.methodmesh.modules.media

import android.content.Context
import com.example.methodmesh.core.methodmesh.ArchitectureId
import com.example.methodmesh.core.methodmesh.ArchitectureRef
import com.example.methodmesh.core.methodmesh.Entity
import com.example.methodmesh.core.methodmesh.ExecutionRequest
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.core.methodmesh.InvocationContext
import com.example.methodmesh.core.methodmesh.KnowledgeObjectType
import com.example.methodmesh.core.methodmesh.MethodContract
import com.example.methodmesh.core.methodmesh.MethodDescriptor
import com.example.methodmesh.core.methodmesh.MethodObjectType
import com.example.methodmesh.core.methodmesh.Observation
import com.example.methodmesh.core.methodmesh.ProvenanceContext
import com.example.methodmesh.core.methodmesh.Signal
import com.example.methodmesh.core.methodmesh.Transformation
import com.example.methodmesh.core.methodmesh.TransformationStatus
import com.example.methodmesh.core.methodmesh.runtime.As100ExecutionEngine
import com.example.methodmesh.core.methodmesh.runtime.As100Method
import com.example.methodmesh.core.methodmesh.withInvocationContext
import com.example.methodmesh.settings.SettingsState
import org.json.JSONArray
import java.time.Instant

private const val MEDIA_VERSION = "0.4.8"

private fun completeMedia(
    method: As100Method,
    request: ExecutionRequest,
    values: Map<String, String>,
    invocation: InvocationContext? = InvocationContext.from(request.context)
): ExecutionResult {
    val ok = values[MediaFields.STATUS] == "succeeded"
    val provenance = ProvenanceContext("methodmesh.media", method.id, MEDIA_VERSION)
    val observation = Observation(
        phenomenon = method.id,
        subject = null,
        values = values,
        temporalContext = request.temporalContext,
        provenance = provenance
    )
    val transformation = Transformation(
        action = method.id,
        method = method.ref,
        outputs = listOf(ArchitectureRef(observation.id, observation.objectType, observation.phenomenon)),
        status = if (ok) TransformationStatus.Succeeded else TransformationStatus.Failed,
        temporalContext = request.temporalContext,
        provenance = provenance
    )
    return As100ExecutionEngine.complete(
        request = request,
        status = if (ok) TransformationStatus.Succeeded else TransformationStatus.Failed,
        entities = listOf(Entity(ArchitectureId("${method.id}:${System.currentTimeMillis()}"), "MediaResult", temporalContext = request.temporalContext)),
        observations = listOf(observation),
        transformations = listOf(transformation),
        diagnostics = values[MediaFields.ERROR].orEmpty().takeIf { it.isNotBlank() }?.let { mapOf(MediaFields.ERROR to it) }.orEmpty()
    ).withInvocationContext(invocation)
}

abstract class BaseMediaMethod : As100Method {
    override fun request(action: String, context: Map<String, String>, signals: List<Signal>, inputs: List<ArchitectureRef>) =
        As100ExecutionEngine.request(action = action, method = ref, context = context, signals = signals, inputs = inputs)
}

object As100MediaCatalogueSearchMethod : BaseMediaMethod() {
    const val ID = "media.catalogue.search"
    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "Search media catalogue")
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID), methodType = MethodObjectType.DeviceService, name = "Search media catalogue", version = MEDIA_VERSION,
        description = "Search the local offline media catalogue with explicit subscription-entitlement filters.",
        inputs = listOf("query", "media_type", "providers", "included_only", "allow_free_with_ads", "exclude_addons", "year_from", "year_to", "max_runtime_minutes", "genre", "language", "limit"),
        outputs = MediaFields.outputs, graphOutputs = listOf(ID),
        parameters = mapOf("category" to "Media", "status" to "Development", "offline" to "true")
    )
    override val contract = MethodContract(ref, producedKnowledgeTypes = listOf(KnowledgeObjectType.Observation), producedFields = descriptor.outputs, producedGraphOutputs = descriptor.graphOutputs)

    override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?): ExecutionResult =
        completeMedia(this, request, mapOf(
            MediaFields.STATUS to "failed", MediaFields.OPERATION to "search",
            MediaFields.ERROR to "Local catalogue search requires Android application storage. Launch the canonical capability screen or another Android-context surface."
        ))

    fun executeWithAndroidContext(request: ExecutionRequest, context: Context, values: Map<String, String> = request.context): ExecutionResult {
        val results = runCatching { MediaCatalogueRepository(context).search(MediaSearchSpec.from(values)) }
            .getOrElse { e -> return completeMedia(this, request, mapOf(MediaFields.STATUS to "failed", MediaFields.OPERATION to "search", MediaFields.ERROR to (e.message ?: "Catalogue search failed."))) }
        val json = JSONArray().apply { results.forEach { put(it.toJson()) } }.toString()
        val first = results.firstOrNull()
        val output = mutableMapOf(
            MediaFields.STATUS to "succeeded", MediaFields.OPERATION to "search", MediaFields.COUNT to results.size.toString(),
            MediaFields.RESULTS_JSON to json,
            MediaFields.VALUE to (first?.let { "${it.title}${it.year?.let { y -> " ($y)" } ?: ""} — ${it.provider} [${it.accessType.wire}]" } ?: "No matching titles")
        )
        first?.let {
            output[MediaFields.WORK_ID] = it.workId; output[MediaFields.TITLE] = it.title; output[MediaFields.CREATOR] = it.creator; output[MediaFields.TYPE] = it.mediaType
            output[MediaFields.YEAR] = it.year?.toString().orEmpty(); output[MediaFields.PROVIDER] = it.provider; output[MediaFields.REGION] = it.region
            output[MediaFields.ACCESS_TYPE] = it.accessType.wire; output[MediaFields.INCLUDED] = it.includedWithoutExtraPayment.toString(); output[MediaFields.ADDON_REQUIRED] = it.addonRequired.toString()
            output[MediaFields.PRICE] = it.price?.toString().orEmpty(); output[MediaFields.CURRENCY] = it.currency; output[MediaFields.GENRES] = it.genres
            output[MediaFields.LANGUAGE] = it.language; output[MediaFields.RUNTIME_MINUTES] = it.runtimeMinutes?.toString().orEmpty(); output[MediaFields.EXTERNAL_ID] = it.externalId; output[MediaFields.EXTERNAL_URI] = it.externalUri
        }
        return completeMedia(this, request, output)
    }
}

object As100MediaCatalogueImportMethod : BaseMediaMethod() {
    const val ID = "media.catalogue.import"
    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "Import media catalogue")
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID), methodType = MethodObjectType.DeviceService, name = "Import media catalogue", version = MEDIA_VERSION,
        description = "Import a downloaded JSON or CSV streaming/media catalogue into module-owned offline storage.",
        inputs = listOf("import_mode", "source_uri"), outputs = MediaFields.outputs, graphOutputs = listOf(ID),
        parameters = mapOf("category" to "Media", "status" to "Development", "offline" to "true")
    )
    override val contract = MethodContract(ref, producedKnowledgeTypes = listOf(KnowledgeObjectType.Observation), producedFields = descriptor.outputs, producedGraphOutputs = descriptor.graphOutputs)
    override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?): ExecutionResult = completeMedia(this, request, mapOf(
        MediaFields.STATUS to "failed", MediaFields.OPERATION to "import", MediaFields.ERROR to "Interactive file acquisition is required for catalogue import."
    ))

    fun resultForSummary(request: ExecutionRequest, summary: MediaCatalogueRepository.ImportSummary): ExecutionResult = completeMedia(this, request, mapOf(
        MediaFields.STATUS to "succeeded", MediaFields.OPERATION to "import", MediaFields.COUNT to summary.inserted.toString(),
        MediaFields.VALUE to "Imported ${summary.inserted} titles; ${summary.deleted} previous provider/region rows replaced; ${summary.total} catalogue rows now stored.",
        MediaFields.RESULTS_JSON to "{\"inserted\":${summary.inserted},\"deleted\":${summary.deleted},\"total\":${summary.total},\"source_sha256\":\"${summary.fingerprint}\"}"
    ))
}

object As100MediaCaptureMethod : BaseMediaMethod() {
    const val ID = "media.library.capture"
    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "Capture media")
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID), methodType = MethodObjectType.Method, name = "Capture media", version = MEDIA_VERSION,
        description = "Create a structured media record while keeping observed text distinct from resolved metadata.",
        inputs = listOf("media_type", "title", "creator", "year", "class", "tags", "notes", "observed_text", "capture_method", "external_id", "external_uri"),
        outputs = MediaFields.outputs, graphOutputs = listOf(ID), parameters = mapOf("category" to "Media", "status" to "Development", "offline" to "true")
    )
    override val contract = MethodContract(ref, producedKnowledgeTypes = listOf(KnowledgeObjectType.Observation), producedFields = descriptor.outputs, producedGraphOutputs = descriptor.graphOutputs)

    override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?): ExecutionResult = buildResult(request, request.context, persistId = "")

    fun executeWithAndroidContext(request: ExecutionRequest, context: Context, values: Map<String, String>): ExecutionResult {
        val id = MediaCatalogueRepository(context).savePersonal(values)
        return buildResult(request, values, id)
    }

    private fun buildResult(request: ExecutionRequest, values: Map<String, String>, persistId: String): ExecutionResult {
        val title = values["title"].orEmpty().trim()
        if (title.isBlank()) return completeMedia(this, request, mapOf(MediaFields.STATUS to "failed", MediaFields.OPERATION to "capture", MediaFields.ERROR to "Title is required."))
        val capturedAt = values["captured_at"].orEmpty().ifBlank { Instant.now().toString() }
        val output = mapOf(
            MediaFields.STATUS to "succeeded", MediaFields.OPERATION to "capture", MediaFields.VALUE to title,
            MediaFields.WORK_ID to persistId.ifBlank { values["work_id"].orEmpty().ifBlank { MediaIds.stableWorkId(title, values["year"]?.toIntOrNull(), values["external_id"].orEmpty()) } }, MediaFields.TITLE to title, MediaFields.CREATOR to values["creator"].orEmpty(), MediaFields.TYPE to values["media_type"].orEmpty().ifBlank { "other" },
            MediaFields.YEAR to values["year"].orEmpty(), MediaFields.CLASS to values["class"].orEmpty().ifBlank { "inbox" }, MediaFields.TAGS to values["tags"].orEmpty(),
            MediaFields.NOTES to values["notes"].orEmpty(), MediaFields.OBSERVED_TEXT to values["observed_text"].orEmpty(), MediaFields.CAPTURE_METHOD to values["capture_method"].orEmpty().ifBlank { "manual" },
            MediaFields.EXTERNAL_ID to values["external_id"].orEmpty(), MediaFields.EXTERNAL_URI to values["external_uri"].orEmpty(), MediaFields.CAPTURED_AT to capturedAt,
            MediaFields.RESULTS_JSON to "{\"library_id\":\"$persistId\",\"persisted\":${persistId.isNotBlank()}}"
        )
        return completeMedia(this, request, output)
    }
}

object As100MediaLibraryListMethod : BaseMediaMethod() {
    const val ID = "media.library.list"
    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "Browse media library")
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID), methodType = MethodObjectType.DeviceService, name = "Browse media library", version = MEDIA_VERSION,
        description = "List MethodMesh favourites, watchlist or watched items, optionally filtered to a TV service and included availability.",
        inputs = listOf("mode", "provider", "included_only", "limit"), outputs = MediaFields.outputs, graphOutputs = listOf(ID),
        parameters = mapOf("category" to "Media", "status" to "Development", "offline" to "true")
    )
    override val contract = MethodContract(ref, producedKnowledgeTypes = listOf(KnowledgeObjectType.Observation), producedFields = descriptor.outputs, producedGraphOutputs = descriptor.graphOutputs)
    override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?): ExecutionResult = completeMedia(this, request, mapOf(
        MediaFields.STATUS to "failed", MediaFields.OPERATION to "library_list", MediaFields.ERROR to "Media library access requires Android application storage."
    ))

    fun executeWithAndroidContext(request: ExecutionRequest, context: Context, values: Map<String, String> = request.context): ExecutionResult {
        val mode = values["mode"].orEmpty().ifBlank { "favourites" }
        val provider = values["provider"].orEmpty().trim()
        val includedOnly = values["included_only"]?.toBooleanStrictOrNull() ?: false
        val limit = values["limit"]?.toIntOrNull()?.coerceIn(1, 500) ?: 200
        val items = MediaCatalogueRepository(context).listPersonal(mode, provider, includedOnly, limit)
        val json = JSONArray().apply { items.forEach { put(it.toJson()) } }.toString()
        val first = items.firstOrNull()
        val output = mutableMapOf(
            MediaFields.STATUS to "succeeded", MediaFields.OPERATION to "library_list", MediaFields.COUNT to items.size.toString(),
            MediaFields.RESULTS_JSON to json, MediaFields.VALUE to if (first == null) "No matching saved media" else "${items.size} saved · ${first.title}"
        )
        first?.let {
            output[MediaFields.WORK_ID] = it.workId; output[MediaFields.TITLE] = it.title; output[MediaFields.CREATOR] = it.creator
            output[MediaFields.TYPE] = it.mediaType; output[MediaFields.YEAR] = it.year?.toString().orEmpty(); output[MediaFields.FAVOURITE] = it.favourite.toString()
            output[MediaFields.WATCHLIST] = it.watchlist.toString(); output[MediaFields.WATCHED] = it.watched.toString(); output[MediaFields.CLASS] = it.className
            output[MediaFields.TAGS] = it.tags; output[MediaFields.NOTES] = it.notes; output[MediaFields.PROVIDER] = it.provider; output[MediaFields.REGION] = it.region
            output[MediaFields.ACCESS_TYPE] = it.accessType; output[MediaFields.INCLUDED] = it.included.toString()
        }
        return completeMedia(this, request, output)
    }
}

object As100MediaLibraryStateMethod : BaseMediaMethod() {
    const val ID = "media.library.state"
    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "Set media library state")
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID), methodType = MethodObjectType.DeviceService, name = "Set media library state", version = MEDIA_VERSION,
        description = "Set MethodMesh-owned favourite, watchlist and watched state without signing into a media provider.",
        inputs = listOf("work_id", "title", "media_type", "creator", "year", "favourite", "watchlist", "watched", "class", "tags", "notes"),
        outputs = MediaFields.outputs, graphOutputs = listOf(ID), parameters = mapOf("category" to "Media", "status" to "Development", "offline" to "true")
    )
    override val contract = MethodContract(ref, producedKnowledgeTypes = listOf(KnowledgeObjectType.Observation), producedFields = descriptor.outputs, producedGraphOutputs = descriptor.graphOutputs)
    override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?): ExecutionResult = completeMedia(this, request, mapOf(
        MediaFields.STATUS to "failed", MediaFields.OPERATION to "library_state", MediaFields.ERROR to "Media library state requires Android application storage."
    ))

    fun executeWithAndroidContext(request: ExecutionRequest, context: Context, values: Map<String, String>): ExecutionResult {
        val title = values["title"].orEmpty().trim()
        if (title.isBlank()) return completeMedia(this, request, mapOf(MediaFields.STATUS to "failed", MediaFields.OPERATION to "library_state", MediaFields.ERROR to "Title is required."))
        val state = MediaCatalogueRepository(context).setState(
            workId = values["work_id"].orEmpty(), title = title, mediaType = values["media_type"].orEmpty().ifBlank { "other" },
            creator = values["creator"].orEmpty(), year = values["year"]?.toIntOrNull(), favourite = values["favourite"]?.toBooleanStrictOrNull(),
            watchlist = values["watchlist"]?.toBooleanStrictOrNull(), watched = values["watched"]?.toBooleanStrictOrNull(), className = values["class"],
            tags = values["tags"], notes = values["notes"]
        )
        return completeMedia(this, request, mapOf(
            MediaFields.STATUS to "succeeded", MediaFields.OPERATION to "library_state", MediaFields.VALUE to state.title,
            MediaFields.WORK_ID to state.workId, MediaFields.TITLE to state.title, MediaFields.CREATOR to state.creator, MediaFields.TYPE to state.mediaType,
            MediaFields.YEAR to state.year?.toString().orEmpty(), MediaFields.FAVOURITE to state.favourite.toString(), MediaFields.WATCHLIST to state.watchlist.toString(),
            MediaFields.WATCHED to state.watched.toString(), MediaFields.CLASS to state.className, MediaFields.TAGS to state.tags, MediaFields.NOTES to state.notes
        ))
    }
}

object As100MediaAudioIdentifyMethod : BaseMediaMethod() {
    const val ID = "media.audio.identify"
    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "Identify audio")
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID), methodType = MethodObjectType.DeviceService, name = "Identify audio", version = MEDIA_VERSION,
        description = "Identify ambient music from a deliberately recorded short microphone sample using an audio discovery service; no end-user media account is used.",
        inputs = listOf("sample_seconds"), outputs = MediaFields.outputs, graphOutputs = listOf(ID),
        parameters = mapOf("category" to "Media", "status" to "Development", "offline" to "false", "provider" to "AudD")
    )
    override val contract = MethodContract(ref, producedKnowledgeTypes = listOf(KnowledgeObjectType.Observation), producedFields = descriptor.outputs, producedGraphOutputs = descriptor.graphOutputs)
    override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?): ExecutionResult = completeMedia(this, request, mapOf(
        MediaFields.STATUS to "failed", MediaFields.OPERATION to "audio_identify", MediaFields.ERROR to "Audio identification requires microphone capture and Android network context."
    ))

    fun resultForMatch(request: ExecutionRequest, match: MediaAudioDiscovery.Match, sampleSeconds: Int): ExecutionResult {
        val matched = match.matched && match.title.isNotBlank()
        // Keep the human result first in observation insertion order. PresetLogWriter
        // deliberately takes the first practical CORE field as its readable log summary.
        // Putting media_value/title/creator ahead of operational metadata therefore makes
        // a preset log say “Song — Artist”, not “media_operation: audio_identify”.
        val values = linkedMapOf<String, String>()
        values[MediaFields.VALUE] = if (matched) "${match.title} — ${match.artist}" else "No match"
        if (matched) {
            values[MediaFields.TITLE] = match.title
            values[MediaFields.CREATOR] = match.artist
            values[MediaFields.ALBUM] = match.album
            values[MediaFields.RELEASE_DATE] = match.releaseDate
            values[MediaFields.SONG_LINK] = match.songLink
            values[MediaFields.EXTERNAL_URI] = match.songLink
            values[MediaFields.WORK_ID] = MediaIds.stableWorkId(match.title, match.releaseDate.take(4).toIntOrNull(), "$match.artist|${match.album}")
        }
        values[MediaFields.STATUS] = "succeeded"
        values[MediaFields.OPERATION] = "audio_identify"
        values[MediaFields.TYPE] = "music"
        values[MediaFields.DISCOVERY_PROVIDER] = match.provider
        values[MediaFields.MATCH_STATUS] = if (matched) "matched" else "no_match"
        values[MediaFields.SAMPLE_SECONDS] = sampleSeconds.toString()
        values[MediaFields.NETWORK_USED] = "true"
        values[MediaFields.RESULTS_JSON] = match.rawResult
        return completeMedia(this, request, values)
    }
}
