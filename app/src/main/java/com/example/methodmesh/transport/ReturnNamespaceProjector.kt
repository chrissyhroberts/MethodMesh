package com.example.methodmesh.transport

object ReturnNamespaceProjector {
    private val namespaceRegex = Regex("^[A-Za-z_][A-Za-z0-9_]*$")

    fun namespaceFrom(settings: Map<String, String>): String =
        (settings["methodmesh_return_namespace"]
            ?: settings["input_methodmesh_return_namespace"])
            .orEmpty()
            .trim()

    fun validate(namespace: String) {
        if (namespace.isBlank()) return
        require(namespaceRegex.matches(namespace)) {
            "Invalid methodmesh_return_namespace '$namespace'. Use letters, numbers and underscores, starting with a letter or underscore."
        }
    }

    fun key(key: String, namespace: String): String {
        validate(namespace)
        return if (namespace.isBlank()) key else "${namespace}_$key"
    }

    fun fields(fields: Map<String, Any?>, namespace: String): Map<String, Any?> {
        validate(namespace)
        if (namespace.isBlank()) return fields
        return fields.entries.associate { (key, value) -> "${namespace}_$key" to value }
    }
}
