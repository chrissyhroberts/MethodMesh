package com.example.methodmesh.transport

enum class ReturnMode(
    val id: String,
    val label: String
) {
    Single("single", "Single"),
    Fields("fields", "Fields"),
    Json("json", "JSON"),
    Datapoints("datapoints", "Datapoints");

    companion object {
        fun fromId(id: String): ReturnMode =
            entries.firstOrNull { it.id == id } ?: Json
    }
}
