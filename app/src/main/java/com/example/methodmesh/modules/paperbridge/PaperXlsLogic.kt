package com.example.methodmesh.modules.paperbridge

import org.json.JSONArray
import org.json.JSONObject

internal enum class PaperLogicCheckState { PASS, VIOLATION, NOT_APPLICABLE, UNKNOWN }

internal data class PaperLogicCheck(
    val kind: String,
    val expression: String,
    val state: PaperLogicCheckState,
    val message: String
)

/**
 * Small, fail-closed XLSForm expression evaluator for Paper Bridge review logic.
 *
 * This intentionally implements only the common, deterministic subset useful
 * for paper transcription. Unsupported JavaRosa/XPath expressions are reported
 * as UNKNOWN and are never silently treated as true.
 */
internal object PaperXlsLogic {
    private class Unsupported(message: String) : IllegalArgumentException(message)

    private enum class Type { REF, CURRENT, STRING, NUMBER, IDENT, OP, LPAREN, RPAREN, COMMA, EOF }
    private data class Token(val type: Type, val text: String)

    fun apply(results: List<PaperFieldResult>): List<PaperFieldResult> {
        val values = results.associate { it.spec.name to it.finalValue }
        return results.map { result -> applyOne(result, values) }
    }

    fun checksJson(results: List<PaperFieldResult>): String = JSONArray().apply {
        results.forEach { result ->
            put(JSONObject().apply {
                put("field", result.spec.name)
                put("relevance_expression", result.spec.relevanceExpression ?: JSONObject.NULL)
                put("relevant", result.relevant ?: JSONObject.NULL)
                put("required_expression", result.spec.requiredExpression ?: JSONObject.NULL)
                put("required_now", result.requiredNow ?: JSONObject.NULL)
                put("constraint_expression", result.spec.constraintExpression ?: JSONObject.NULL)
                put("constraint_satisfied", result.constraintSatisfied ?: JSONObject.NULL)
                put("logic_unknown", result.logicUnknown)
                put("logic_violation", result.logicViolation)
                put("message", result.logicMessage)
                put("na_override", result.naOverride)
            })
        }
    }.toString()

    private fun applyOne(result: PaperFieldResult, values: Map<String, String>): PaperFieldResult {
        if (result.naOverride) {
            return result.copy(
                relevant = true,
                requiredNow = false,
                constraintSatisfied = true,
                logicUnknown = false,
                logicViolation = false,
                logicMessage = "NA override confirmed by operator"
            )
        }

        val relevance = evaluateBoolean(result.spec.relevanceExpression, values, result.finalValue)
        val relevant = when (relevance.state) {
            PaperLogicCheckState.PASS -> true
            PaperLogicCheckState.VIOLATION -> false
            PaperLogicCheckState.NOT_APPLICABLE -> true
            PaperLogicCheckState.UNKNOWN -> null
        }

        val requiredEval = when {
            relevant == false -> PaperLogicCheck("required", result.spec.requiredExpression.orEmpty(), PaperLogicCheckState.NOT_APPLICABLE, "Question is not relevant")
            result.spec.requiredExpression.isNullOrBlank() -> PaperLogicCheck(
                "required", "", PaperLogicCheckState.PASS,
                if (result.spec.required) "Required" else "Optional"
            )
            else -> evaluateBoolean(result.spec.requiredExpression, values, result.finalValue, kind = "required")
        }
        val requiredNow = when {
            relevant == false -> false
            result.spec.requiredExpression.isNullOrBlank() -> result.spec.required
            requiredEval.state == PaperLogicCheckState.PASS -> true
            requiredEval.state == PaperLogicCheckState.VIOLATION -> false
            else -> null
        }

        val constraint = when {
            relevant == false || result.finalValue.isBlank() -> PaperLogicCheck("constraint", result.spec.constraintExpression.orEmpty(), PaperLogicCheckState.NOT_APPLICABLE, "Constraint not applicable")
            result.spec.constraintExpression.isNullOrBlank() -> PaperLogicCheck("constraint", "", PaperLogicCheckState.PASS, "No additional XLSForm constraint")
            else -> evaluateBoolean(result.spec.constraintExpression, values, result.finalValue, kind = "constraint")
        }

        val requiredViolation = relevant != false && requiredNow == true && result.finalValue.isBlank()
        val relevanceViolation = relevant == false && result.finalValue.isNotBlank()
        val constraintViolation = constraint.state == PaperLogicCheckState.VIOLATION
        val unknown = relevance.state == PaperLogicCheckState.UNKNOWN ||
            requiredEval.state == PaperLogicCheckState.UNKNOWN ||
            constraint.state == PaperLogicCheckState.UNKNOWN
        val violation = requiredViolation || relevanceViolation || constraintViolation

        val messages = mutableListOf<String>()
        when {
            relevance.state == PaperLogicCheckState.UNKNOWN -> messages += "Relevance could not be evaluated: ${relevance.message}"
            relevant == false && result.finalValue.isBlank() -> messages += "Not relevant under XLSForm logic"
            relevanceViolation -> messages += "Value is present although XLSForm relevance is false"
        }
        if (requiredViolation) messages += "Required by XLSForm logic but blank"
        if (requiredEval.state == PaperLogicCheckState.UNKNOWN) messages += "Dynamic required expression could not be evaluated: ${requiredEval.message}"
        if (constraintViolation) messages += (result.spec.constraintMessage?.takeIf(String::isNotBlank) ?: "XLSForm constraint is not satisfied")
        if (constraint.state == PaperLogicCheckState.UNKNOWN) messages += "Constraint could not be evaluated: ${constraint.message}"

        return result.copy(
            reason = when {
                relevant == false && result.finalValue.isBlank() -> "Not relevant under XLSForm logic"
                relevanceViolation -> "Value is present although XLSForm relevance is false"
                requiredViolation -> "Required by XLSForm logic but blank"
                constraintViolation -> result.spec.constraintMessage?.takeIf(String::isNotBlank) ?: "XLSForm constraint is not satisfied"
                else -> result.reason
            },
            relevant = relevant,
            requiredNow = requiredNow,
            constraintSatisfied = when (constraint.state) {
                PaperLogicCheckState.PASS -> true
                PaperLogicCheckState.VIOLATION -> false
                PaperLogicCheckState.NOT_APPLICABLE -> true
                PaperLogicCheckState.UNKNOWN -> null
            },
            logicUnknown = unknown,
            logicViolation = violation,
            logicMessage = messages.joinToString(" · ")
        )
    }

    private fun evaluateBoolean(
        expression: String?,
        values: Map<String, String>,
        current: String,
        kind: String = "relevance"
    ): PaperLogicCheck {
        if (expression.isNullOrBlank()) {
            return PaperLogicCheck(kind, "", PaperLogicCheckState.PASS, "No expression")
        }
        return runCatching {
            val value = Parser(tokenize(expression), values, current).parse()
            val bool = asBoolean(value)
            PaperLogicCheck(
                kind,
                expression,
                if (bool) PaperLogicCheckState.PASS else PaperLogicCheckState.VIOLATION,
                if (bool) "Expression is true" else "Expression is false"
            )
        }.getOrElse { error ->
            PaperLogicCheck(kind, expression, PaperLogicCheckState.UNKNOWN, error.message ?: "unsupported expression")
        }
    }

    private class Parser(
        private val tokens: List<Token>,
        private val values: Map<String, String>,
        private val current: String
    ) {
        private var index = 0

        fun parse(): Any? {
            val value = parseOr()
            if (peek().type != Type.EOF) throw Unsupported("Unexpected token '${peek().text}'")
            return value
        }

        private fun parseOr(): Any? {
            var left = parseAnd()
            while (matchIdent("or")) {
                // Always parse the RHS. Using Kotlin's short-circuit operator directly
                // here would leave RHS tokens unconsumed when the LHS is already true.
                val right = parseAnd()
                left = asBoolean(left) || asBoolean(right)
            }
            return left
        }

        private fun parseAnd(): Any? {
            var left = parseComparison()
            while (matchIdent("and")) {
                // As above, parsing and boolean evaluation are separate concerns.
                val right = parseComparison()
                left = asBoolean(left) && asBoolean(right)
            }
            return left
        }

        private fun parseComparison(): Any? {
            var left = parseUnary()
            while (peek().type == Type.OP) {
                val op = advance().text
                val right = parseUnary()
                left = compare(left, right, op)
            }
            return left
        }

        private fun parseUnary(): Any? {
            if (matchIdent("not")) {
                if (match(Type.LPAREN)) {
                    val v = parseOr()
                    expect(Type.RPAREN)
                    return !asBoolean(v)
                }
                return !asBoolean(parseUnary())
            }
            return parsePrimary()
        }

        private fun parsePrimary(): Any? {
            val token = advance()
            return when (token.type) {
                Type.REF -> values[token.text] ?: throw Unsupported("Reference '${token.text}' is not a mapped paper field")
                Type.CURRENT -> current
                Type.STRING -> token.text
                Type.NUMBER -> token.text.toDouble()
                Type.LPAREN -> parseOr().also { expect(Type.RPAREN) }
                Type.IDENT -> {
                    if (peek().type == Type.LPAREN) {
                        advance()
                        val args = mutableListOf<Any?>()
                        if (peek().type != Type.RPAREN) {
                            do { args += parseOr() } while (match(Type.COMMA))
                        }
                        expect(Type.RPAREN)
                        function(token.text, args)
                    } else when (token.text.lowercase()) {
                        "true" -> true
                        "false" -> false
                        else -> throw Unsupported("Unsupported identifier '${token.text}'")
                    }
                }
                else -> throw Unsupported("Unexpected token '${token.text}'")
            }
        }

        private fun function(nameRaw: String, args: List<Any?>): Any? {
            val name = nameRaw.lowercase()
            return when (name) {
                "true" -> true
                "false" -> false
                "selected" -> {
                    requireArgs(name, args, 2)
                    val selected = asString(args[0]).split(Regex("\\s+")).filter(String::isNotBlank)
                    asString(args[1]) in selected
                }
                "count-selected" -> {
                    requireArgs(name, args, 1)
                    asString(args[0]).split(Regex("\\s+")).count(String::isNotBlank).toDouble()
                }
                "regex" -> {
                    requireArgs(name, args, 2)
                    runCatching { Regex(asString(args[1])).containsMatchIn(asString(args[0])) }
                        .getOrElse { throw Unsupported("Invalid regex in XLSForm expression") }
                }
                "string-length" -> {
                    requireArgs(name, args, 1)
                    asString(args[0]).length.toDouble()
                }
                "contains" -> {
                    requireArgs(name, args, 2)
                    asString(args[0]).contains(asString(args[1]))
                }
                "starts-with" -> {
                    requireArgs(name, args, 2)
                    asString(args[0]).startsWith(asString(args[1]))
                }
                "ends-with" -> {
                    requireArgs(name, args, 2)
                    asString(args[0]).endsWith(asString(args[1]))
                }
                "number" -> {
                    requireArgs(name, args, 1)
                    asString(args[0]).toDoubleOrNull() ?: Double.NaN
                }
                "boolean" -> {
                    requireArgs(name, args, 1)
                    asBoolean(args[0])
                }
                "not" -> {
                    requireArgs(name, args, 1)
                    !asBoolean(args[0])
                }
                else -> throw Unsupported("Unsupported XLSForm function '$nameRaw'")
            }
        }

        private fun requireArgs(name: String, args: List<Any?>, count: Int) {
            if (args.size != count) throw Unsupported("$name() expects $count argument${if (count == 1) "" else "s"}")
        }

        private fun match(type: Type): Boolean {
            if (peek().type != type) return false
            index++
            return true
        }

        private fun matchIdent(text: String): Boolean {
            val p = peek()
            if (p.type != Type.IDENT || !p.text.equals(text, true)) return false
            index++
            return true
        }

        private fun expect(type: Type) {
            val got = advance()
            if (got.type != type) throw Unsupported("Expected $type but found '${got.text}'")
        }

        private fun peek(): Token = tokens.getOrElse(index) { Token(Type.EOF, "") }
        private fun advance(): Token = peek().also { if (index < tokens.size) index++ }
    }

    private fun compare(left: Any?, right: Any?, op: String): Boolean {
        val ln = asNumber(left)
        val rn = asNumber(right)
        if (ln != null && rn != null && !ln.isNaN() && !rn.isNaN()) {
            return when (op) {
                "=" -> ln == rn
                "!=" -> ln != rn
                ">" -> ln > rn
                ">=" -> ln >= rn
                "<" -> ln < rn
                "<=" -> ln <= rn
                else -> throw Unsupported("Unsupported comparison '$op'")
            }
        }
        val ls = asString(left)
        val rs = asString(right)
        return when (op) {
            "=" -> ls == rs
            "!=" -> ls != rs
            ">" -> ls > rs
            ">=" -> ls >= rs
            "<" -> ls < rs
            "<=" -> ls <= rs
            else -> throw Unsupported("Unsupported comparison '$op'")
        }
    }

    private fun asBoolean(value: Any?): Boolean = when (value) {
        null -> false
        is Boolean -> value
        is Number -> value.toDouble() != 0.0 && !value.toDouble().isNaN()
        else -> value.toString().trim().let { it.isNotEmpty() && !it.equals("false", true) && it != "0" }
    }

    private fun asString(value: Any?): String = when (value) {
        null -> ""
        is Double -> if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
        else -> value.toString()
    }

    private fun asNumber(value: Any?): Double? = when (value) {
        is Number -> value.toDouble()
        else -> value?.toString()?.trim()?.toDoubleOrNull()
    }

    private fun tokenize(expression: String): List<Token> {
        val output = mutableListOf<Token>()
        var i = 0
        while (i < expression.length) {
            val ch = expression[i]
            when {
                ch.isWhitespace() -> i++
                ch == '$' && i + 1 < expression.length && expression[i + 1] == '{' -> {
                    val end = expression.indexOf('}', i + 2)
                    if (end < 0) throw Unsupported("Unclosed XLSForm field reference")
                    val name = expression.substring(i + 2, end).trim()
                    if (name.isBlank()) throw Unsupported("Blank XLSForm field reference")
                    output += Token(Type.REF, name)
                    i = end + 1
                }
                ch == '.' -> { output += Token(Type.CURRENT, "."); i++ }
                ch == '(' -> { output += Token(Type.LPAREN, "("); i++ }
                ch == ')' -> { output += Token(Type.RPAREN, ")"); i++ }
                ch == ',' -> { output += Token(Type.COMMA, ","); i++ }
                ch == '\'' || ch == '"' -> {
                    val quote = ch
                    val b = StringBuilder()
                    i++
                    var closed = false
                    while (i < expression.length) {
                        val c = expression[i]
                        if (c == quote) { closed = true; i++; break }
                        if (c == '\\' && i + 1 < expression.length) {
                            b.append(c)
                            i++
                            b.append(expression[i])
                            i++
                        } else {
                            b.append(c); i++
                        }
                    }
                    if (!closed) throw Unsupported("Unclosed quoted string")
                    output += Token(Type.STRING, b.toString())
                }
                ch in listOf('>', '<', '!', '=') -> {
                    val two = if (i + 1 < expression.length) expression.substring(i, i + 2) else ""
                    val op = if (two in setOf(">=", "<=", "!=")) two else ch.toString()
                    if (op == "!") throw Unsupported("Unsupported operator '!'")
                    output += Token(Type.OP, op)
                    i += op.length
                }
                ch.isDigit() || (ch == '-' && i + 1 < expression.length && expression[i + 1].isDigit()) -> {
                    val start = i
                    i++
                    while (i < expression.length && (expression[i].isDigit() || expression[i] == '.')) i++
                    output += Token(Type.NUMBER, expression.substring(start, i))
                }
                ch.isLetter() || ch == '_' -> {
                    val start = i
                    i++
                    while (i < expression.length && (expression[i].isLetterOrDigit() || expression[i] in setOf('_', '-'))) i++
                    output += Token(Type.IDENT, expression.substring(start, i))
                }
                else -> throw Unsupported("Unsupported token '$ch'")
            }
        }
        output += Token(Type.EOF, "")
        return output
    }
}
