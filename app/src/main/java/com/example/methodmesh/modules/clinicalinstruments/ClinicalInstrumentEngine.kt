package com.example.methodmesh.modules.clinicalinstruments

import org.json.JSONObject
import kotlin.math.abs

/**
 * Linear checklist/scoring engine for Clinical Instruments v0.1.
 *
 * v0.1 intentionally has no relevance/skip/branching semantics. Every question
 * in a definition is part of the instrument in the declared order. This keeps
 * execution, Back/Forward navigation, resumability and result provenance simple
 * and auditable while the richer protocol/relevance language is designed later.
 */
object ClinicalInstrumentEngine {
    fun validateResponse(question: ClinicalQuestion, raw: String): String? {
        if (raw.isBlank()) return if (question.required) "A response is required." else null
        return when (question.type) {
            QuestionType.INTEGER -> {
                val value = raw.toIntOrNull() ?: return "Enter a whole number."
                rangeError(question, value.toDouble())
            }
            QuestionType.DECIMAL -> {
                val value = raw.toDoubleOrNull() ?: return "Enter a number."
                rangeError(question, value)
            }
            QuestionType.BOOLEAN -> if (raw.lowercase() !in setOf("true", "false")) "Choose Yes or No." else null
            QuestionType.SELECT_ONE -> if (question.choices.none { it.value == raw }) "Choose one of the available options." else null
            QuestionType.TEXT -> null
        }
    }

    fun run(definition: ClinicalInstrumentDefinition, responses: Map<String, String>): ClinicalRunResult {
        val questionIds = definition.questions.map { it.id }.toSet()
        val canonical = responses.filterKeys { it in questionIds }
        val missing = definition.questions.firstOrNull { it.required && canonical[it.id].isNullOrBlank() }
        if (missing != null) return ClinicalRunResult(definition, canonical, emptyMap(), emptyMap(), null, false, "Required response '${missing.id}' is missing.")
        definition.questions.forEach { q ->
            val raw = canonical[q.id].orEmpty()
            if (raw.isNotBlank() || q.required) {
                validateResponse(q, raw)?.let { message ->
                    return ClinicalRunResult(definition, canonical, emptyMap(), emptyMap(), null, false, "${q.label}: $message")
                }
            }
        }

        val context = responseContext(definition, canonical).toMutableMap()
        val derived = linkedMapOf<String, ClinicalValue>()
        definition.derived.forEach { item ->
            val value = evaluateExpression(definition, canonical, item, context)
            derived[item.id] = value
            context[item.id] = value
        }
        val scores = linkedMapOf<String, ClinicalValue>()
        definition.scores.forEach { item ->
            val value = evaluateExpression(definition, canonical, item, context)
            scores[item.id] = value
            context[item.id] = value
        }
        val classification = definition.classifications.firstOrNull { ExpressionEvaluator.evaluate(it.whenExpression, context).asBoolean() }
        return ClinicalRunResult(definition, canonical, derived, scores, classification, true)
    }


    private fun evaluateExpression(
        definition: ClinicalInstrumentDefinition,
        responses: Map<String, String>,
        item: ClinicalExpression,
        context: Map<String, ClinicalValue>
    ): ClinicalValue {
        val blocked = item.requiresTestable.any { questionId ->
            val question = definition.questions.firstOrNull { it.id == questionId } ?: return@any true
            val notTestable = question.notTestableValue ?: return@any false
            responses[questionId] == notTestable
        }
        return if (blocked) ClinicalValue.Null else ExpressionEvaluator.evaluate(item.expression, context)
    }

    fun runDefinitionTests(definition: ClinicalInstrumentDefinition): List<String> = definition.tests.mapNotNull { test ->
        runCatching {
            val input = JSONObject(test.inputJson)
            val responses = input.keys().asSequence().associateWith { input.get(it).toString() }
            val result = run(definition, responses)
            val expected = JSONObject(test.expectJson)
            expected.keys().asSequence().forEach { key ->
                val actual: String? = when {
                    key == "classification" -> result.classification?.value
                    key in result.scores -> result.scores[key]?.plain()
                    key in result.derived -> result.derived[key]?.plain()
                    else -> result.responses[key]
                }
                val expectedValue = expected.get(key).toString()
                require(equivalent(actual, expectedValue)) { "expected $key=$expectedValue, got $actual" }
            }
        }.exceptionOrNull()?.let { "${test.name}: ${it.message}" }
    }

    private fun responseContext(definition: ClinicalInstrumentDefinition, responses: Map<String, String>): Map<String, ClinicalValue> =
        definition.questions.associate { q -> q.id to parseResponse(q, responses[q.id]) }

    private fun parseResponse(question: ClinicalQuestion, raw: String?): ClinicalValue {
        if (raw.isNullOrBlank()) return ClinicalValue.Null
        return when (question.type) {
            QuestionType.INTEGER, QuestionType.DECIMAL -> raw.toDoubleOrNull()?.let(ClinicalValue::Number) ?: ClinicalValue.Null
            QuestionType.BOOLEAN -> raw.toBooleanStrictOrNull()?.let(ClinicalValue::Bool) ?: ClinicalValue.Null
            QuestionType.SELECT_ONE, QuestionType.TEXT -> ClinicalValue.Text(raw)
        }
    }

    private fun rangeError(question: ClinicalQuestion, value: Double): String? = when {
        question.minimum != null && value < question.minimum -> "Minimum is ${question.minimum.clean()}."
        question.maximum != null && value > question.maximum -> "Maximum is ${question.maximum.clean()}."
        else -> null
    }

    private fun equivalent(actual: String?, expected: String): Boolean {
        if (actual == expected) return true
        val a = actual?.toDoubleOrNull()
        val e = expected.toDoubleOrNull()
        return a != null && e != null && abs(a - e) < 1e-9
    }

    private fun Double.clean(): String = if (this % 1.0 == 0.0) toLong().toString() else toString()
}

internal object ExpressionEvaluator {
    fun evaluate(expression: String, context: Map<String, ClinicalValue>): ClinicalValue {
        val parser = Parser(tokenize(expression), context)
        val value = parser.parseExpression()
        parser.expectEnd()
        return value
    }

    private enum class Kind { NUMBER, IDENT, STRING, OP, LPAREN, RPAREN, END }
    private data class Token(val kind: Kind, val text: String)

    private fun tokenize(expression: String): List<Token> {
        val tokens = mutableListOf<Token>()
        var i = 0
        while (i < expression.length) {
            val c = expression[i]
            when {
                c.isWhitespace() -> i++
                c.isDigit() || (c == '.' && i + 1 < expression.length && expression[i + 1].isDigit()) -> {
                    val start = i++
                    while (i < expression.length && (expression[i].isDigit() || expression[i] == '.')) i++
                    tokens += Token(Kind.NUMBER, expression.substring(start, i))
                }
                c.isLetter() || c == '_' -> {
                    val start = i++
                    while (i < expression.length && (expression[i].isLetterOrDigit() || expression[i] in setOf('_', '.', '-'))) i++
                    val text = expression.substring(start, i)
                    tokens += Token(if (text in setOf("and", "or", "not")) Kind.OP else Kind.IDENT, text)
                }
                c == '\'' || c == '"' -> {
                    val quote = c
                    i++
                    val out = StringBuilder()
                    while (i < expression.length && expression[i] != quote) {
                        if (expression[i] == '\\' && i + 1 < expression.length) i++
                        out.append(expression[i++])
                    }
                    require(i < expression.length) { "Unterminated string literal." }
                    i++
                    tokens += Token(Kind.STRING, out.toString())
                }
                c == '(' -> { tokens += Token(Kind.LPAREN, "("); i++ }
                c == ')' -> { tokens += Token(Kind.RPAREN, ")"); i++ }
                else -> {
                    val two = expression.substring(i, (i + 2).coerceAtMost(expression.length))
                    if (two in setOf(">=", "<=", "==", "!=", "&&", "||")) {
                        tokens += Token(Kind.OP, two); i += 2
                    } else if (c in listOf('+', '-', '*', '/', '>', '<', '!')) {
                        tokens += Token(Kind.OP, c.toString()); i++
                    } else error("Unsupported character '$c' in expression '$expression'.")
                }
            }
        }
        tokens += Token(Kind.END, "")
        return tokens
    }

    private class Parser(private val tokens: List<Token>, private val context: Map<String, ClinicalValue>) {
        private var pos = 0
        fun parseExpression(): ClinicalValue = parseOr()
        fun expectEnd() { require(peek().kind == Kind.END) { "Unexpected token '${peek().text}'." } }

        private fun parseOr(): ClinicalValue {
            var left = parseAnd()
            while (matchOp("or", "||")) {
                val right = parseAnd()
                left = ClinicalValue.Bool(left.asBoolean() || right.asBoolean())
            }
            return left
        }

        private fun parseAnd(): ClinicalValue {
            var left = parseComparison()
            while (matchOp("and", "&&")) {
                val right = parseComparison()
                left = ClinicalValue.Bool(left.asBoolean() && right.asBoolean())
            }
            return left
        }

        private fun parseComparison(): ClinicalValue {
            var left = parseAdditive()
            while (peek().kind == Kind.OP && peek().text in setOf(">=", "<=", "==", "!=", ">", "<")) {
                val op = take().text
                val right = parseAdditive()
                left = ClinicalValue.Bool(compare(left, right, op))
            }
            return left
        }

        private fun parseAdditive(): ClinicalValue {
            var left = parseMultiplicative()
            while (peek().kind == Kind.OP && peek().text in setOf("+", "-")) {
                val op = take().text
                val right = parseMultiplicative()
                left = if (op == "+") add(left, right) else ClinicalValue.Number(left.asNumber() - right.asNumber())
            }
            return left
        }

        private fun parseMultiplicative(): ClinicalValue {
            var left = parseUnary()
            while (peek().kind == Kind.OP && peek().text in setOf("*", "/")) {
                val op = take().text
                val right = parseUnary()
                left = if (op == "*") ClinicalValue.Number(left.asNumber() * right.asNumber())
                else ClinicalValue.Number(left.asNumber() / right.asNumber())
            }
            return left
        }

        private fun parseUnary(): ClinicalValue {
            if (matchOp("not", "!")) return ClinicalValue.Bool(!parseUnary().asBoolean())
            if (matchOp("-")) return ClinicalValue.Number(-parseUnary().asNumber())
            return parsePrimary()
        }

        private fun parsePrimary(): ClinicalValue {
            val token = take()
            return when (token.kind) {
                Kind.NUMBER -> ClinicalValue.Number(token.text.toDouble())
                Kind.STRING -> ClinicalValue.Text(token.text)
                Kind.IDENT -> when (token.text.lowercase()) {
                    "true" -> ClinicalValue.Bool(true)
                    "false" -> ClinicalValue.Bool(false)
                    "null" -> ClinicalValue.Null
                    else -> context[token.text] ?: ClinicalValue.Null
                }
                Kind.LPAREN -> parseExpression().also { require(take().kind == Kind.RPAREN) { "Missing ')'." } }
                else -> error("Unexpected token '${token.text}'.")
            }
        }

        private fun compare(left: ClinicalValue, right: ClinicalValue, op: String): Boolean {
            if (left is ClinicalValue.Null || right is ClinicalValue.Null) return when (op) {
                "==" -> left is ClinicalValue.Null && right is ClinicalValue.Null
                "!=" -> !(left is ClinicalValue.Null && right is ClinicalValue.Null)
                else -> false
            }
            val ln = left.numberOrNull()
            val rn = right.numberOrNull()
            return if (ln != null && rn != null) when (op) {
                ">=" -> ln >= rn; "<=" -> ln <= rn; ">" -> ln > rn; "<" -> ln < rn; "==" -> ln == rn; "!=" -> ln != rn
                else -> false
            } else {
                val ls = left.plain(); val rs = right.plain()
                when (op) { "==" -> ls == rs; "!=" -> ls != rs; ">=" -> ls >= rs; "<=" -> ls <= rs; ">" -> ls > rs; "<" -> ls < rs; else -> false }
            }
        }

        private fun add(left: ClinicalValue, right: ClinicalValue): ClinicalValue {
            val ln = left.numberOrNull(); val rn = right.numberOrNull()
            return if (ln != null && rn != null) ClinicalValue.Number(ln + rn) else ClinicalValue.Text(left.plain() + right.plain())
        }

        private fun matchOp(vararg values: String): Boolean {
            if (peek().kind == Kind.OP && peek().text in values) { pos++; return true }
            return false
        }
        private fun peek(): Token = tokens[pos]
        private fun take(): Token = tokens[pos++]
    }
}

internal fun ClinicalValue.asBoolean(): Boolean = when (this) {
    is ClinicalValue.Bool -> value
    is ClinicalValue.Number -> value != 0.0
    is ClinicalValue.Text -> value.equals("true", true) || value == "1" || value.equals("yes", true)
    ClinicalValue.Null -> false
}

internal fun ClinicalValue.asNumber(): Double = numberOrNull() ?: error("Value '${plain()}' is not numeric.")
internal fun ClinicalValue.numberOrNull(): Double? = when (this) {
    is ClinicalValue.Number -> value
    is ClinicalValue.Bool -> if (value) 1.0 else 0.0
    is ClinicalValue.Text -> value.toDoubleOrNull()
    ClinicalValue.Null -> null
}
internal fun ClinicalValue.plain(): String = when (this) {
    is ClinicalValue.Number -> if (value % 1.0 == 0.0) value.toLong().toString() else "%.4f".format(java.util.Locale.US, value).trimEnd('0').trimEnd('.')
    is ClinicalValue.Bool -> value.toString()
    is ClinicalValue.Text -> value
    ClinicalValue.Null -> ""
}
