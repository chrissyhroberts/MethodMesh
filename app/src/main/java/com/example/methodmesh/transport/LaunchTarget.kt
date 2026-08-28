package com.example.methodmesh.transport

enum class LaunchTarget(
    val id: String,
    val label: String
) {
    Appearance("appearance", "Appearance"),
    IntentColumn("intent_column", "Intent column"),
    AndroidIntentUri("android_intent_uri", "Android URI"),
    KotlinIntent("kotlin_intent", "Kotlin")
}
