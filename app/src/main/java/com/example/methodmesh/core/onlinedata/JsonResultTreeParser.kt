package com.example.methodmesh.core.onlinedata

/**
 * Small JSON parser for MethodMesh result trees.
 *
 * This intentionally covers standard JSON only. It keeps the online-data core
 * testable on the local JVM without relying on Android's org.json runtime.
 */
internal class JsonResultTreeParser(private val text: String) {
    private var index = 0

    fun parse(): ResultTree {
        skipWhitespace()
        val value = parseValue()
        skipWhitespace()
        require(index == text.length) { "Unexpected trailing JSON content at offset $index." }
        return value
    }

    private fun parseValue(): ResultTree {
        skipWhitespace()
        require(index < text.length) { "Unexpected end of JSON." }
        return when (text[index]) {
            '{' -> parseObject()
            '[' -> parseArray()
            '"' -> ResultTree.StringNode(parseString())
            't' -> {
                expect("true")
                ResultTree.BooleanNode(true)
            }
            'f' -> {
                expect("false")
                ResultTree.BooleanNode(false)
            }
            'n' -> {
                expect("null")
                ResultTree.NullNode
            }
            '-', in '0'..'9' -> parseNumber()
            else -> error("Unexpected JSON token '${text[index]}' at offset $index.")
        }
    }

    private fun parseObject(): ResultTree.ObjectNode {
        expect('{')
        val values = linkedMapOf<String, ResultTree>()
        skipWhitespace()
        if (peek('}')) {
            expect('}')
            return ResultTree.ObjectNode(values)
        }
        while (true) {
            skipWhitespace()
            val key = parseString()
            skipWhitespace()
            expect(':')
            values[key] = parseValue()
            skipWhitespace()
            when {
                peek(',') -> expect(',')
                peek('}') -> {
                    expect('}')
                    return ResultTree.ObjectNode(values)
                }
                else -> error("Expected ',' or '}' at offset $index.")
            }
        }
    }

    private fun parseArray(): ResultTree.ArrayNode {
        expect('[')
        val values = mutableListOf<ResultTree>()
        skipWhitespace()
        if (peek(']')) {
            expect(']')
            return ResultTree.ArrayNode(values)
        }
        while (true) {
            values += parseValue()
            skipWhitespace()
            when {
                peek(',') -> expect(',')
                peek(']') -> {
                    expect(']')
                    return ResultTree.ArrayNode(values)
                }
                else -> error("Expected ',' or ']' at offset $index.")
            }
        }
    }

    private fun parseString(): String {
        expect('"')
        val output = StringBuilder()
        while (index < text.length) {
            val char = text[index++]
            when (char) {
                '"' -> return output.toString()
                '\\' -> {
                    require(index < text.length) { "Unexpected end of JSON escape." }
                    output.append(
                        when (val escaped = text[index++]) {
                            '"' -> '"'
                            '\\' -> '\\'
                            '/' -> '/'
                            'b' -> '\b'
                            'f' -> '\u000C'
                            'n' -> '\n'
                            'r' -> '\r'
                            't' -> '\t'
                            'u' -> parseUnicodeEscape()
                            else -> error("Invalid JSON escape '\\$escaped' at offset ${index - 1}.")
                        }
                    )
                }
                else -> output.append(char)
            }
        }
        error("Unterminated JSON string.")
    }

    private fun parseUnicodeEscape(): Char {
        require(index + 4 <= text.length) { "Incomplete unicode escape at offset $index." }
        val code = text.substring(index, index + 4).toIntOrNull(16)
            ?: error("Invalid unicode escape at offset $index.")
        index += 4
        return code.toChar()
    }

    private fun parseNumber(): ResultTree.NumberNode {
        val start = index
        if (peek('-')) index += 1
        consumeDigits()
        if (peek('.')) {
            index += 1
            consumeDigits()
        }
        if (peek('e') || peek('E')) {
            index += 1
            if (peek('+') || peek('-')) index += 1
            consumeDigits()
        }
        val raw = text.substring(start, index)
        return ResultTree.NumberNode(raw.toDoubleOrNull() ?: error("Invalid number '$raw'."))
    }

    private fun consumeDigits() {
        val start = index
        while (index < text.length && text[index].isDigit()) index += 1
        require(index > start) { "Expected digit at offset $index." }
    }

    private fun skipWhitespace() {
        while (index < text.length && text[index].isWhitespace()) index += 1
    }

    private fun expect(value: String) {
        require(text.startsWith(value, index)) { "Expected '$value' at offset $index." }
        index += value.length
    }

    private fun expect(char: Char) {
        require(index < text.length && text[index] == char) { "Expected '$char' at offset $index." }
        index += 1
    }

    private fun peek(char: Char): Boolean =
        index < text.length && text[index] == char
}

