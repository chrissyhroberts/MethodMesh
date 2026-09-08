package com.example.methodmesh.core.onlinedata

import android.content.Context
import java.time.Instant

object ApiDefinitionRepository : ApiDefinitionRegistry {
    private const val PREFS = "methodmesh_api_definition_registry"
    private const val USER_DEFINITIONS = "user_api_definitions_json"

    private var applicationContext: Context? = null

    fun initialise(context: Context) {
        applicationContext = context.applicationContext
    }

    override fun all(): List<ApiDefinition> {
        val user = applicationContext?.let { userDefinitions(it) }.orEmpty()
        return merged(BundledApiDefinitions.all, user)
    }

    override fun find(id: String): ApiDefinition? =
        all().firstOrNull { it.id == id }

    fun all(context: Context): List<ApiDefinition> =
        merged(BundledApiDefinitions.all, userDefinitions(context))

    fun userDefinitions(context: Context): List<ApiDefinition> =
        storedPayloads(context).mapNotNull { payload ->
            runCatching { ApiDefinitionCodec.decode(payload) }.getOrNull()
        }

    fun saveUserDefinition(context: Context, definition: ApiDefinition): ApiDefinition {
        val saved = definition.copy(
            origin = ApiDefinitionOrigin.USER,
            editable = true,
            cloneable = true,
            id = definition.id.trim()
        )
        require(saved.id.isNotBlank()) { "API definition id is required." }
        require(saved.name.isNotBlank()) { "API definition name is required." }
        require(saved.urlTemplate.isNotBlank()) { "API definition URL is required." }

        val updated = userDefinitions(context)
            .filterNot { it.id == saved.id } + saved
        writeUserDefinitions(context, updated)
        return saved
    }

    fun cloneBundledDefinition(context: Context, bundledId: String, newId: String, newName: String): ApiDefinition {
        val bundled = BundledApiDefinitions.all.firstOrNull { it.id == bundledId }
            ?: error("Bundled API definition '$bundledId' was not found.")
        require(bundled.cloneable) { "Bundled API definition '$bundledId' is not cloneable." }
        return saveUserDefinition(
            context,
            bundled.copy(
                id = newId,
                name = newName,
                origin = ApiDefinitionOrigin.USER,
                editable = true,
                cloneable = true
            )
        )
    }

    fun removeUserDefinition(context: Context, id: String) {
        writeUserDefinitions(context, userDefinitions(context).filterNot { it.id == id })
    }

    fun exportUserDefinitions(context: Context, onlyId: String? = null): String {
        val definitions = userDefinitions(context).filter { onlyId.isNullOrBlank() || it.id == onlyId }
        return ApiDefinitionCodec.exportBundle(definitions, exportedAt = Instant.now())
    }

    fun importDefinitions(context: Context, payload: String): ImportedApiDefinitions {
        val imported = ApiDefinitionCodec.importBundle(payload)
        imported.definitions.forEach { definition ->
            saveUserDefinition(context, definition.copy(origin = ApiDefinitionOrigin.USER, editable = true))
        }
        return imported
    }

    private fun storedPayloads(context: Context): List<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(USER_DEFINITIONS, "")
            .orEmpty()
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toList()

    private fun writeUserDefinitions(context: Context, definitions: List<ApiDefinition>) {
        val payload = definitions
            .sortedBy { it.id }
            .joinToString("\n") { ApiDefinitionCodec.encode(it) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(USER_DEFINITIONS, payload)
            .apply()
    }

    private fun merged(
        bundled: List<ApiDefinition>,
        user: List<ApiDefinition>
    ): List<ApiDefinition> =
        (bundled + user)
            .associateBy { it.id }
            .values
            .sortedWith(compareBy<ApiDefinition> { it.origin.name }.thenBy { it.name.lowercase() })
}

