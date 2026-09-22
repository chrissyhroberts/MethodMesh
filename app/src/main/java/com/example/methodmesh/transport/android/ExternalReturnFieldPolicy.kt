package com.example.methodmesh.transport.android

/**
 * Preserves the existing compact transport policy while allowing an Android
 * caller to explicitly request canonical flat outputs by declaring matching
 * return placeholders.
 *
 * ODK/XLSForm group intents send child question names as Intent extras.
 * MethodMesh inputs are namespaced as input_* / input64_*; unprefixed child
 * names can therefore be treated as caller-declared output placeholders.
 *
 * Only keys that also exist in the canonical or explicitly selected result are
 * copied. Unknown form fields cannot manufacture MethodMesh outputs.
 */
internal object ExternalReturnFieldPolicy {
    private val transportControlKeys = setOf(
        "action",
        "actions",
        "method_id",
        "return_mode",
        "returns",
        "ril",
        "methodmesh_return_namespace",
        "payload_mode",
        "return_payload",
        "caller",
        "entity_type",
        "entity_id",
        "participant_id",
        "specimen_id",
        "visit_id",
        "form_id",
        "operator_id"
    )

    internal fun callerDeclaredOutputKeys(
        incomingExtraKeys: Set<String>,
        returnNamespace: String = ""
    ): Set<String> {
        val namespace = returnNamespace.trim()
        com.example.methodmesh.transport.ReturnNamespaceProjector.validate(namespace)
        val namespacePrefix = namespace.takeIf(String::isNotBlank)?.let { "${it}_" }.orEmpty()

        return incomingExtraKeys
            .asSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .filterNot { it.startsWith("input_") || it.startsWith("input64_") }
            .filterNot { it in transportControlKeys }
            .map { key ->
                if (namespacePrefix.isNotEmpty() && key.startsWith(namespacePrefix) && key.length > namespacePrefix.length) {
                    key.removePrefix(namespacePrefix)
                } else {
                    key
                }
            }
            .filterNot { it in transportControlKeys }
            .toCollection(linkedSetOf())
    }

    internal fun mergeCallerDeclaredFields(
        incomingExtraKeys: Set<String>,
        canonicalFields: Map<String, Any?>,
        selectedFields: Map<String, Any?>,
        target: MutableMap<String, String?>,
        returnNamespace: String = ""
    ) {
        callerDeclaredOutputKeys(incomingExtraKeys, returnNamespace).forEach { key ->
            when {
                selectedFields.containsKey(key) ->
                    target[key] = selectedFields[key]?.toString()
                canonicalFields.containsKey(key) ->
                    target[key] = canonicalFields[key]?.toString()
            }
        }
    }
}
