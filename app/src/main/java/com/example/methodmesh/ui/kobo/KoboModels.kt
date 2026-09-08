package com.example.methodmesh.ui.kobo

data class KoboProfile(
    val serverUrl: String = "https://kf.kobotoolbox.org",
    val username: String = ""
) {
    val isConfigured: Boolean get() = serverUrl.isNotBlank() && username.isNotBlank()
}

data class KoboSession(val token: String)

data class KoboTemplateDeployment(
    val serverUrl: String,
    val templateId: String,
    val assetUid: String,
    val localSha256: String,
    val active: Boolean,
    val namespacedCompatibilityCopy: Boolean = false,
    val lastSyncedAt: String = ""
)
