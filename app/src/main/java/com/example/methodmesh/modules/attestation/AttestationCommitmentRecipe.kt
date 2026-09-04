package com.example.methodmesh.modules.attestation

object AttestationCommitmentRecipe {
    const val SCHEMA = "methodmesh.commitment_recipe.v1"
    const val CANONICALIZATION = "ordered-kv-v1"
    const val HASH_ALGORITHM = "SHA-256"

    private const val MAX_RECIPE_BYTES = 64 * 1024
    private val supportedCommitments = setOf(
        "value",
        "artifact-bytes-sha256",
        "text-utf8-sha256",
        "json-utf8-sha256"
    )

    fun validate(rawRecipe: String?): String {
        val recipe = rawRecipe.orEmpty()
        require(recipe.isNotBlank()) {
            "attestation.create requires commitment_recipe describing how event_payload_hash can be reconstructed"
        }
        require(recipe.toByteArray(Charsets.UTF_8).size <= MAX_RECIPE_BYTES) {
            "commitment_recipe is too large; keep recipe metadata compact and commit large objects by SHA-256 digest"
        }
        val json = try {
            JsonObjectParser(recipe).parseObject()
        } catch (error: Exception) {
            throw IllegalArgumentException("commitment_recipe must be valid JSON object", error)
        }
        require(json["schema"] == SCHEMA) {
            "commitment_recipe schema must be $SCHEMA"
        }
        require(json["canonicalization"] == CANONICALIZATION) {
            "commitment_recipe canonicalization must be $CANONICALIZATION"
        }
        require(json["hash_algorithm"] == HASH_ALGORITHM) {
            "commitment_recipe hash_algorithm must be $HASH_ALGORITHM"
        }
        val members = json["members"] as? List<*>
            ?: throw IllegalArgumentException("commitment_recipe members must be an array")
        validateMembers(members)
        return recipe
    }

    fun sha256(rawRecipe: String): String =
        AttestationCrypto.sha256Hex(rawRecipe)

    private fun validateMembers(members: List<*>) {
        members.forEachIndexed { index, item ->
            val member = item as? Map<*, *>
                ?: throw IllegalArgumentException("commitment_recipe members[$index] must be an object")
            val path = member["path"] as? String
            val type = member["type"] as? String
            val commitment = member["commitment"] as? String
            require(path.orEmpty().isNotBlank()) { "commitment_recipe members[$index].path is required" }
            require(type.orEmpty().isNotBlank()) { "commitment_recipe members[$index].type is required" }
            require(commitment in supportedCommitments) {
                "commitment_recipe members[$index].commitment must be one of ${supportedCommitments.joinToString()}"
            }
        }
    }
}

private class JsonObjectParser(private val text: String) {
    private var index = 0

    fun parseObject(): Map<String, Any?> {
        val value = parseValue()
        skipWhitespace()
        require(index == text.length) { "Unexpected trailing JSON content" }
        @Suppress("UNCHECKED_CAST")
        return value as? Map<String, Any?> ?: throw IllegalArgumentException("JSON root must be an object")
    }

    private fun parseValue(): Any? {
        skipWhitespace()
        require(index < text.length) { "Unexpected end of JSON" }
        return when (text[index]) {
            '{' -> parseMap()
            '[' -> parseArray()
            '"' -> parseString()
            't' -> parseLiteral("true", true)
            'f' -> parseLiteral("false", false)
            'n' -> parseLiteral("null", null)
            else -> parseNumber()
        }
    }

    private fun parseMap(): Map<String, Any?> {
        expect('{')
        val map = linkedMapOf<String, Any?>()
        skipWhitespace()
        if (peek('}')) {
            index++
            return map
        }
        while (true) {
            skipWhitespace()
            val key = parseString()
            skipWhitespace()
            expect(':')
            map[key] = parseValue()
            skipWhitespace()
            when {
                peek(',') -> index++
                peek('}') -> {
                    index++
                    return map
                }
                else -> throw IllegalArgumentException("Expected ',' or '}'")
            }
        }
    }

    private fun parseArray(): List<Any?> {
        expect('[')
        val list = mutableListOf<Any?>()
        skipWhitespace()
        if (peek(']')) {
            index++
            return list
        }
        while (true) {
            list += parseValue()
            skipWhitespace()
            when {
                peek(',') -> index++
                peek(']') -> {
                    index++
                    return list
                }
                else -> throw IllegalArgumentException("Expected ',' or ']'")
            }
        }
    }

    private fun parseString(): String {
        expect('"')
        val out = StringBuilder()
        while (index < text.length) {
            val c = text[index++]
            when (c) {
                '"' -> return out.toString()
                '\\' -> {
                    require(index < text.length) { "Unterminated JSON escape" }
                    out.append(
                        when (val escaped = text[index++]) {
                            '"', '\\', '/' -> escaped
                            'b' -> '\b'
                            'f' -> '\u000C'
                            'n' -> '\n'
                            'r' -> '\r'
                            't' -> '\t'
                            'u' -> parseUnicodeEscape()
                            else -> throw IllegalArgumentException("Unsupported JSON escape \\$escaped")
                        }
                    )
                }
                else -> out.append(c)
            }
        }
        throw IllegalArgumentException("Unterminated JSON string")
    }

    private fun parseUnicodeEscape(): Char {
        require(index + 4 <= text.length) { "Incomplete unicode escape" }
        val code = text.substring(index, index + 4).toInt(16)
        index += 4
        return code.toChar()
    }

    private fun parseLiteral(literal: String, value: Any?): Any? {
        require(text.startsWith(literal, index)) { "Expected $literal" }
        index += literal.length
        return value
    }

    private fun parseNumber(): Number {
        val start = index
        if (peek('-')) index++
        while (index < text.length && text[index].isDigit()) index++
        if (peek('.')) {
            index++
            while (index < text.length && text[index].isDigit()) index++
        }
        if (index < text.length && text[index] in setOf('e', 'E')) {
            index++
            if (index < text.length && text[index] in setOf('+', '-')) index++
            while (index < text.length && text[index].isDigit()) index++
        }
        require(index > start) { "Expected JSON value" }
        val raw = text.substring(start, index)
        return raw.toDoubleOrNull() ?: throw IllegalArgumentException("Invalid JSON number")
    }

    private fun expect(char: Char) {
        skipWhitespace()
        require(peek(char)) { "Expected '$char'" }
        index++
    }

    private fun peek(char: Char): Boolean =
        index < text.length && text[index] == char

    private fun skipWhitespace() {
        while (index < text.length && text[index].isWhitespace()) index++
    }
}
