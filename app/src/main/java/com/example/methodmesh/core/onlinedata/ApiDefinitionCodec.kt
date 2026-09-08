package com.example.methodmesh.core.onlinedata

import java.security.MessageDigest
import java.time.Instant

object ApiDefinitionCodec {
    const val BUNDLE_VERSION = "1"

    fun encode(definition: ApiDefinition, includeEditableState: Boolean = true): String =
        encodeDefinitionTree(definition, includeEditableState).toJsonString()

    fun decode(payload: String): ApiDefinition {
        val root = JsonResultTreeParser(payload).parse().asObject("API definition")
        return decodeDefinition(root)
    }

    fun exportBundle(definitions: List<ApiDefinition>, exportedAt: Instant = Instant.now()): String {
        val canonicalDefinitions = definitions.sortedBy { it.id }
        val canonical = ResultTree.objectNode(
            "definitions" to ResultTree.ArrayNode(canonicalDefinitions.map { encodeDefinitionTree(it, includeEditableState = false) })
        ).toJsonString()
        return ResultTree.objectNode(
            "methodmesh_api_registry_version" to ResultTree.string(BUNDLE_VERSION),
            "exported_at" to ResultTree.string(exportedAt.toString()),
            "definition_count" to ResultTree.number(canonicalDefinitions.size),
            "definitions" to ResultTree.ArrayNode(canonicalDefinitions.map { encodeDefinitionTree(it, includeEditableState = false) }),
            "payload_sha256" to ResultTree.string(sha256(canonical))
        ).toJsonString()
    }

    fun importBundle(payload: String): ImportedApiDefinitions {
        val root = JsonResultTreeParser(payload).parse().asObject("API definition bundle")
        val version = root.string("methodmesh_api_registry_version")
        require(version == BUNDLE_VERSION) { "Unsupported API registry version '$version'." }

        val definitions = root.array("definitions")
            .values
            .map { decodeDefinition(it.asObject("API definition")) }
        val expectedHash = root.string("payload_sha256")
        val canonical = ResultTree.objectNode(
            "definitions" to ResultTree.ArrayNode(definitions.sortedBy { it.id }.map { encodeDefinitionTree(it, includeEditableState = false) })
        ).toJsonString()
        require(expectedHash.equals(sha256(canonical), ignoreCase = true)) {
            "API definition bundle hash verification failed."
        }

        return ImportedApiDefinitions(definitions = definitions, hash = expectedHash.lowercase())
    }

    private fun encodeDefinitionTree(definition: ApiDefinition, includeEditableState: Boolean): ResultTree.ObjectNode =
        ResultTree.objectNode(
            "id" to ResultTree.string(definition.id),
            "name" to ResultTree.string(definition.name),
            "description" to ResultTree.string(definition.description),
            "origin" to ResultTree.string(definition.origin.name.lowercase()),
            "version" to ResultTree.number(definition.version),
            "editable" to ResultTree.bool(if (includeEditableState) definition.editable else true),
            "cloneable" to ResultTree.bool(definition.cloneable),
            "method" to ResultTree.string(definition.method.name),
            "url_template" to ResultTree.string(definition.urlTemplate),
            "query_parameters" to stringMapTree(definition.queryParameters),
            "headers" to stringMapTree(definition.headers),
            "inputs" to ResultTree.ArrayNode(definition.inputs.map { input ->
                ResultTree.objectNode(
                    "id" to ResultTree.string(input.id),
                    "name" to ResultTree.string(input.name),
                    "description" to ResultTree.string(input.description),
                    "type" to ResultTree.string(input.type.name),
                    "required" to ResultTree.bool(input.required),
                    "default_value" to ResultTree.string(input.defaultValue),
                    "sensitive" to ResultTree.bool(input.sensitive)
                )
            }),
            "auth" to authTree(definition.auth),
            "response" to ResultTree.objectNode(
                "type" to ResultTree.string(definition.response.type.name),
                "expected_paths" to ResultTree.ArrayNode(definition.response.expectedPaths.map { ResultTree.string(it) })
            ),
            "cache" to ResultTree.objectNode(
                "mode" to ResultTree.string(definition.cache.mode.name),
                "ttl_seconds" to ResultTree.number(definition.cache.ttlSeconds),
                "minimum_refresh_interval_seconds" to ResultTree.number(definition.cache.minimumRefreshIntervalSeconds)
            ),
            "privacy" to ResultTree.objectNode(
                "sends_location" to ResultTree.bool(definition.privacy.sendsLocation),
                "sends_identifiers" to ResultTree.bool(definition.privacy.sendsIdentifiers),
                "location_mode" to ResultTree.string(definition.privacy.locationMode.name),
                "rounded_location_radius_meters" to ResultTree.number(definition.privacy.roundedLocationRadiusMeters)
            ),
            "attribution" to ResultTree.objectNode(
                "provider_name" to ResultTree.string(definition.attribution.providerName),
                "provider_url" to ResultTree.string(definition.attribution.providerUrl),
                "license" to ResultTree.string(definition.attribution.license),
                "required_text" to ResultTree.string(definition.attribution.requiredText)
            ),
            "documentation_url" to ResultTree.string(definition.documentationUrl)
        )

    private fun decodeDefinition(root: ResultTree.ObjectNode): ApiDefinition =
        ApiDefinition(
            id = root.string("id"),
            name = root.string("name"),
            description = root.string("description"),
            origin = enumValue(root.string("origin"), ApiDefinitionOrigin.USER),
            version = root.int("version", 1),
            editable = root.boolean("editable", true),
            cloneable = root.boolean("cloneable", true),
            method = enumValue(root.string("method"), HttpMethod.GET),
            urlTemplate = root.string("url_template"),
            queryParameters = root.objectOrNull("query_parameters")?.toStringMap().orEmpty(),
            headers = root.objectOrNull("headers")?.toStringMap().orEmpty(),
            inputs = root.arrayOrNull("inputs")?.values.orEmpty().map { value ->
                val input = value.asObject("API input")
                ApiInputDefinition(
                    id = input.string("id"),
                    name = input.string("name"),
                    description = input.string("description"),
                    type = enumValue(input.string("type"), ApiInputType.STRING),
                    required = input.boolean("required", true),
                    defaultValue = input.string("default_value"),
                    sensitive = input.boolean("sensitive", false)
                )
            },
            auth = decodeAuth(root.objectOrNull("auth")),
            response = root.objectOrNull("response")?.let { response ->
                ApiResponseDefinition(
                    type = enumValue(response.string("type"), ApiResponseType.JSON),
                    expectedPaths = response.arrayOrNull("expected_paths")?.values.orEmpty()
                        .mapNotNull { (it as? ResultTree.StringNode)?.value }
                )
            } ?: ApiResponseDefinition(),
            cache = root.objectOrNull("cache")?.let { cache ->
                CachePolicy(
                    mode = enumValue(cache.string("mode"), CacheMode.TTL),
                    ttlSeconds = cache.long("ttl_seconds", 900),
                    minimumRefreshIntervalSeconds = cache.long("minimum_refresh_interval_seconds", 0)
                )
            } ?: CachePolicy.ttl(900),
            privacy = root.objectOrNull("privacy")?.let { privacy ->
                ApiPrivacy(
                    sendsLocation = privacy.boolean("sends_location", false),
                    sendsIdentifiers = privacy.boolean("sends_identifiers", false),
                    locationMode = enumValue(privacy.string("location_mode"), LocationDisclosureMode.DISABLED),
                    roundedLocationRadiusMeters = privacy.int("rounded_location_radius_meters", 5_000)
                )
            } ?: ApiPrivacy(),
            attribution = root.objectOrNull("attribution")?.let { attribution ->
                ApiAttribution(
                    providerName = attribution.string("provider_name"),
                    providerUrl = attribution.string("provider_url"),
                    license = attribution.string("license"),
                    requiredText = attribution.string("required_text")
                )
            } ?: ApiAttribution(),
            documentationUrl = root.string("documentation_url")
        )

    private fun authTree(auth: ApiAuthDefinition): ResultTree.ObjectNode = when (auth) {
        ApiAuthDefinition.None -> ResultTree.objectNode("type" to ResultTree.string("none"))
        is ApiAuthDefinition.ApiKeyQuery -> ResultTree.objectNode(
            "type" to ResultTree.string("api_key_query"),
            "parameter_name" to ResultTree.string(auth.parameterName),
            "credential_id" to ResultTree.string(auth.credentialId)
        )
        is ApiAuthDefinition.ApiKeyHeader -> ResultTree.objectNode(
            "type" to ResultTree.string("api_key_header"),
            "header_name" to ResultTree.string(auth.headerName),
            "credential_id" to ResultTree.string(auth.credentialId)
        )
        is ApiAuthDefinition.BearerToken -> ResultTree.objectNode(
            "type" to ResultTree.string("bearer_token"),
            "credential_id" to ResultTree.string(auth.credentialId)
        )
        is ApiAuthDefinition.Basic -> ResultTree.objectNode(
            "type" to ResultTree.string("basic"),
            "credential_id" to ResultTree.string(auth.credentialId)
        )
    }

    private fun decodeAuth(root: ResultTree.ObjectNode?): ApiAuthDefinition {
        val type = root?.string("type")?.lowercase().orEmpty()
        return when (type) {
            "api_key_query" -> ApiAuthDefinition.ApiKeyQuery(
                parameterName = root!!.string("parameter_name"),
                credentialId = root.string("credential_id")
            )
            "api_key_header" -> ApiAuthDefinition.ApiKeyHeader(
                headerName = root!!.string("header_name"),
                credentialId = root.string("credential_id")
            )
            "bearer_token" -> ApiAuthDefinition.BearerToken(root!!.string("credential_id"))
            "basic" -> ApiAuthDefinition.Basic(root!!.string("credential_id"))
            else -> ApiAuthDefinition.None
        }
    }

    private fun stringMapTree(values: Map<String, String>): ResultTree.ObjectNode =
        ResultTree.ObjectNode(values.toSortedMap().mapValues { ResultTree.string(it.value) })

    private fun ResultTree.ObjectNode.toStringMap(): Map<String, String> =
        values.mapValues { (_, value) ->
            when (value) {
                is ResultTree.StringNode -> value.value
                is ResultTree.NumberNode -> value.value.toString()
                is ResultTree.BooleanNode -> value.value.toString()
                ResultTree.NullNode -> ""
                else -> value.toJsonString()
            }
        }

    private inline fun <reified T : Enum<T>> enumValue(raw: String, default: T): T =
        enumValues<T>().firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: default

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}

data class ImportedApiDefinitions(
    val definitions: List<ApiDefinition>,
    val hash: String
)

internal fun ResultTree.asObject(label: String): ResultTree.ObjectNode =
    this as? ResultTree.ObjectNode ?: error("$label must be a JSON object.")

internal fun ResultTree.ObjectNode.string(key: String, default: String = ""): String =
    when (val value = values[key]) {
        is ResultTree.StringNode -> value.value
        is ResultTree.NumberNode -> {
            val long = value.value.toLong()
            if (value.value == long.toDouble()) long.toString() else value.value.toString()
        }
        is ResultTree.BooleanNode -> value.value.toString()
        else -> default
    }

internal fun ResultTree.ObjectNode.boolean(key: String, default: Boolean): Boolean =
    when (val value = values[key]) {
        is ResultTree.BooleanNode -> value.value
        is ResultTree.StringNode -> value.value.equals("true", ignoreCase = true)
        else -> default
    }

internal fun ResultTree.ObjectNode.int(key: String, default: Int): Int =
    long(key, default.toLong()).toInt()

internal fun ResultTree.ObjectNode.long(key: String, default: Long): Long =
    when (val value = values[key]) {
        is ResultTree.NumberNode -> value.value.toLong()
        is ResultTree.StringNode -> value.value.toLongOrNull() ?: default
        else -> default
    }

internal fun ResultTree.ObjectNode.objectOrNull(key: String): ResultTree.ObjectNode? =
    values[key] as? ResultTree.ObjectNode

internal fun ResultTree.ObjectNode.array(key: String): ResultTree.ArrayNode =
    arrayOrNull(key) ?: error("'$key' must be an array.")

internal fun ResultTree.ObjectNode.arrayOrNull(key: String): ResultTree.ArrayNode? =
    values[key] as? ResultTree.ArrayNode
