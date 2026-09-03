package com.example.methodmesh.core.onlinedata

import org.json.JSONArray
import org.json.JSONObject

/**
 * Tree-native value model for online data, preset step outputs and later
 * capability-to-capability data flow.
 *
 * The important rule is that objects and arrays are values, not intermediate
 * things that must be flattened before they can move through the runtime.
 */
sealed interface ResultTree {
    data class ObjectNode(val values: Map<String, ResultTree>) : ResultTree
    data class ArrayNode(val values: List<ResultTree>) : ResultTree
    data class StringNode(val value: String) : ResultTree
    data class NumberNode(val value: Double) : ResultTree
    data class BooleanNode(val value: Boolean) : ResultTree
    data object NullNode : ResultTree

    companion object {
        fun objectNode(vararg pairs: Pair<String, ResultTree>): ObjectNode =
            ObjectNode(linkedMapOf(*pairs))

        fun arrayNode(vararg values: ResultTree): ArrayNode =
            ArrayNode(values.toList())

        fun string(value: String): StringNode = StringNode(value)

        fun number(value: Number): NumberNode = NumberNode(value.toDouble())

        fun bool(value: Boolean): BooleanNode = BooleanNode(value)

        fun fromJsonObject(json: JSONObject): ObjectNode {
            val values = linkedMapOf<String, ResultTree>()
            json.keys().forEach { key ->
                values[key] = fromJsonValue(json.opt(key))
            }
            return ObjectNode(values)
        }

        fun fromJsonArray(json: JSONArray): ArrayNode =
            ArrayNode((0 until json.length()).map { index -> fromJsonValue(json.opt(index)) })

        fun fromJsonValue(value: Any?): ResultTree = when (value) {
            null, JSONObject.NULL -> NullNode
            is JSONObject -> fromJsonObject(value)
            is JSONArray -> fromJsonArray(value)
            is Boolean -> BooleanNode(value)
            is Number -> NumberNode(value.toDouble())
            is String -> StringNode(value)
            else -> StringNode(value.toString())
        }
    }
}

sealed interface ResultPathSegment {
    data class Field(val name: String) : ResultPathSegment
    data class Index(val index: Int) : ResultPathSegment
}

data class ResultPath(val segments: List<ResultPathSegment>) {
    fun resolve(root: ResultTree): ResultLookup {
        var current = root
        for (segment in segments) {
            when (segment) {
                is ResultPathSegment.Field -> {
                    val objectNode = current as? ResultTree.ObjectNode
                        ?: return ResultLookup.TypeError("Expected object before field '${segment.name}'.")
                    current = objectNode.values[segment.name] ?: return ResultLookup.Missing(segment.name)
                }

                is ResultPathSegment.Index -> {
                    val arrayNode = current as? ResultTree.ArrayNode
                        ?: return ResultLookup.TypeError("Expected array before index ${segment.index}.")
                    current = arrayNode.values.getOrNull(segment.index)
                        ?: return ResultLookup.Missing("[${segment.index}]")
                }
            }
        }
        return if (current is ResultTree.NullNode) ResultLookup.NullValue else ResultLookup.Value(current)
    }

    override fun toString(): String = buildString {
        segments.forEachIndexed { index, segment ->
            when (segment) {
                is ResultPathSegment.Field -> {
                    if (index > 0) append(".")
                    append(segment.name)
                }

                is ResultPathSegment.Index -> append("[${segment.index}]")
            }
        }
    }

    companion object {
        fun parse(raw: String): ResultPath {
            val text = raw.trim()
                .removePrefix("\${")
                .removeSuffix("}")
                .trim()
            require(text.isNotBlank()) { "Result path cannot be blank." }

            val segments = mutableListOf<ResultPathSegment>()
            var token = StringBuilder()
            var index = 0
            while (index < text.length) {
                when (val char = text[index]) {
                    '.' -> {
                        flushField(token, segments)
                        index += 1
                    }

                    '[' -> {
                        flushField(token, segments)
                        val close = text.indexOf(']', startIndex = index + 1)
                        require(close > index + 1) { "Array index is missing in '$raw'." }
                        val number = text.substring(index + 1, close).toIntOrNull()
                        require(number != null && number >= 0) { "Array index must be a non-negative integer in '$raw'." }
                        segments += ResultPathSegment.Index(number)
                        index = close + 1
                    }

                    else -> {
                        token.append(char)
                        index += 1
                    }
                }
            }
            flushField(token, segments)
            require(segments.isNotEmpty()) { "Result path cannot be blank." }
            return ResultPath(segments)
        }

        private fun flushField(
            token: StringBuilder,
            segments: MutableList<ResultPathSegment>
        ) {
            if (token.isEmpty()) return
            val field = token.toString().trim()
            require(field.isNotBlank()) { "Result path contains a blank field." }
            require(!field.contains("[")) { "Malformed result path field '$field'." }
            segments += ResultPathSegment.Field(field)
            token.clear()
        }
    }
}

sealed interface ResultLookup {
    data class Value(val value: ResultTree) : ResultLookup
    data object NullValue : ResultLookup
    data class Missing(val pathPart: String) : ResultLookup
    data class TypeError(val message: String) : ResultLookup
}
