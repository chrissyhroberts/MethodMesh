package com.example.methodmesh.core.onlinedata

internal fun ResultTree.toJsonString(): String = when (this) {
    is ResultTree.ObjectNode -> values.entries.joinToString(
        prefix = "{",
        postfix = "}"
    ) { (key, value) -> "${key.jsonQuoted()}:${value.toJsonString()}" }

    is ResultTree.ArrayNode -> values.joinToString(prefix = "[", postfix = "]") { it.toJsonString() }
    is ResultTree.StringNode -> value.jsonQuoted()
    is ResultTree.NumberNode -> {
        if (value.isFinite()) {
            val long = value.toLong()
            if (value == long.toDouble()) long.toString() else value.toString()
        } else {
            "null"
        }
    }
    is ResultTree.BooleanNode -> value.toString()
    ResultTree.NullNode -> "null"
}

internal fun String.jsonQuoted(): String = buildString {
    append('"')
    this@jsonQuoted.forEach { char ->
        when (char) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\b' -> append("\\b")
            '\u000C' -> append("\\f")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> {
                if (char.code < 0x20) {
                    append("\\u")
                    append(char.code.toString(16).padStart(4, '0'))
                } else {
                    append(char)
                }
            }
        }
    }
    append('"')
}

