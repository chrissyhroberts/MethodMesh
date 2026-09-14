package com.example.methodmesh.settings

sealed class MethodSetting {

    abstract val id: String
    abstract val label: String
    abstract val description: String?
    abstract val group: String?

    /** Whether preset authoring may expose this setting as Ask when run. */
    abstract val runtimeInputAllowed: Boolean

    data class BooleanSetting(
        override val id: String,
        override val label: String,
        override val description: String? = null,
        override val group: String? = null,
        val defaultValue: Boolean,
        override val runtimeInputAllowed: Boolean = true
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
        val unit: String? = null,
        override val runtimeInputAllowed: Boolean = true
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
        val decimals: Int = 1,
        override val runtimeInputAllowed: Boolean = true
    ) : MethodSetting()

    data class TextSetting(
        override val id: String,
        override val label: String,
        override val description: String? = null,
        override val group: String? = null,
        val defaultValue: String,
        override val runtimeInputAllowed: Boolean = true
    ) : MethodSetting()

    data class ChoiceSetting(
        override val id: String,
        override val label: String,
        override val description: String? = null,
        override val group: String? = null,
        val defaultValue: String,
        val choices: List<String>,
        override val runtimeInputAllowed: Boolean = true
    ) : MethodSetting()

    data class MultiChoiceSetting(
        override val id: String,
        override val label: String,
        override val description: String? = null,
        override val group: String? = null,
        val defaultValue: String,
        val choices: List<String>,
        val delimiter: String = "|",
        val emptyMeansAll: Boolean = false,
        override val runtimeInputAllowed: Boolean = true
    ) : MethodSetting()
}
