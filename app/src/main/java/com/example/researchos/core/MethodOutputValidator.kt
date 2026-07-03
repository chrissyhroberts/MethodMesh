package com.example.researchos.core

data class MethodOutputValidation(
    val valid: Boolean,
    val messages: List<String> = emptyList()
)

object MethodOutputValidator {

    fun validate(
        schema: MethodOutputSchema,
        output: MethodOutput
    ): MethodOutputValidation {
        val messages = mutableListOf<String>()

        schema.fields
            .filter { it.required }
            .forEach { field ->
                if (!output.fields.containsKey(field.id)) {
                    messages.add("Missing required output: ${field.id}")
                }
            }

        output.fields.keys
            .filter { key -> schema.fields.isNotEmpty() && key !in schema.fieldIds() }
            .forEach { key ->
                messages.add("Output not declared in schema: $key")
            }

        schema.fields.forEach { field ->
            val value = output.fields[field.id]
            if (value != null && !matchesType(value, field.type)) {
                messages.add("Output ${field.id} has wrong type. Expected ${field.type}.")
            }
        }

        return MethodOutputValidation(
            valid = messages.isEmpty(),
            messages = messages
        )
    }

    private fun matchesType(
        value: Any,
        type: MethodFieldType
    ): Boolean {
        return when (type) {
            MethodFieldType.Text -> value is String
            MethodFieldType.Integer -> value is Int || value is Long
            MethodFieldType.Float -> value is Float || value is Double || value is Int || value is Long
            MethodFieldType.Boolean -> value is Boolean
            MethodFieldType.Json -> true
        }
    }
}
