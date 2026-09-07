package com.example.methodmesh.ui.odkcentral

data class OdkCentralProfile(
    val serverUrl: String = "",
    val email: String = "",
    val projectId: Long? = null,
    val projectName: String = "",
    val appUserId: Long? = null,
    val appUserName: String = ""
) {
    val isConfigured: Boolean
        get() = serverUrl.isNotBlank() && email.isNotBlank() && projectId != null && appUserId != null
}

data class OdkCentralSession(
    val token: String,
    val expiresAt: String
)

data class OdkCentralProject(
    val id: Long,
    val name: String
)

data class OdkCentralAppUser(
    val id: Long,
    val displayName: String,
    val tokenActive: Boolean
)

data class OdkCentralForm(
    val xmlFormId: String,
    val name: String,
    val publishedAt: String?,
    val submissions: Int = 0
)

data class OdkTemplateDeployment(
    val serverUrl: String,
    val projectId: Long,
    val templateId: String,
    val xmlFormId: String,
    val localSha256: String,
    val assignedAppUserId: Long? = null,
    val lastSyncedAt: String = ""
)
