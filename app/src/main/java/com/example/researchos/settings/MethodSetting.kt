package com.example.researchos.settings

sealed class MethodSetting {

    abstract val id: String
    abstract val label: String
    abstract val description: String?
    abstract val group: String?

    data class BooleanSetting(
        override val id: String,
        override val label: String,
        override val description: String? = null,
        override val group: String? = null,
        val defaultValue: Boolean
    ) : MethodSetting()

    data class IntSetting(
        override val id: String,
        override val label: String,
        override val description: String? = null,
        override val group: String? = null,
        val defaultValue: Int,
        val minimum: Int? = null,
        val maximum: Int? = null,
        val step: Int = 1,
        val unit: String? = null
    ) : MethodSetting()

    data class FloatSetting(
        override val id: String,
        override val label: String,
        override val description: String? = null,
        override val group: String? = null,
        val defaultValue: Float,
        val minimum: Float? = null,
        val maximum: Float? = null,
        val step: Float = 1f,
        val unit: String? = null,
        val decimals: Int = 1
    ) : MethodSetting()

    data class TextSetting(
        override val id: String,
        override val label: String,
        override val description: String? = null,
        override val group: String? = null,
        val defaultValue: String
    ) : MethodSetting()

    data class ChoiceSetting(
        override val id: String,
        override val label: String,
        override val description: String? = null,
        override val group: String? = null,
        val defaultValue: String,
        val choices: List<String>
    ) : MethodSetting()
}