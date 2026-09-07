package com.example.methodmesh.modules.emergency

import java.time.Instant

enum class EmergencyRiskState { GREEN, AMBER, RED, GREY }
enum class EmergencyPreparednessState { READY, PARTIAL, MINIMAL }

enum class EmergencyHazardDomain {
    SEVERE_WEATHER,
    FLOOD,
    EARTHQUAKE_TSUNAMI,
    CYCLONE,
    WILDFIRE,
    VOLCANIC,
    CIVIL_SECURITY,
    EVACUATION_SHELTER
}

enum class EmergencyCoverageState {
    CURRENT_NO_ALERT,
    CURRENT_ALERT,
    STALE,
    UNAVAILABLE,
    NOT_APPLICABLE,
    DISABLED_BY_USER
}

enum class EmergencySourceAuthority {
    A_AUTHORITATIVE_INSTRUCTION,
    B_AUTHORITATIVE_SITUATIONAL,
    C_CURATED_EVENT_FEED,
    D_OPEN_UNVERIFIED
}

enum class EmergencyRelevanceReason {
    CURRENT_LOCATION_INTERSECTS_POLYGON,
    EVENT_WITHIN_RADIUS,
    SAVED_LOCATION_INTERSECTS_POLYGON,
    ROUTE_CORRIDOR_INTERSECTS_POLYGON,
    REGION_LEVEL_ALERT
}

data class EmergencyDomainCoverage(
    val domain: EmergencyHazardDomain,
    val state: EmergencyCoverageState,
    val critical: Boolean = true,
    val sourceId: String = "",
    val lastSuccessfulRefreshAt: Instant? = null,
    val checkedAt: Instant? = null,
    val detail: String = ""
)

data class EmergencyAlert(
    val eventId: String,
    val title: String,
    val domain: EmergencyHazardDomain,
    val sourceId: String,
    val authority: EmergencySourceAuthority,
    val candidateRisk: EmergencyRiskState,
    val issuedAt: Instant? = null,
    val retrievedAt: Instant,
    val expiresAt: Instant? = null,
    val locallyRelevant: Boolean,
    val relevanceReason: EmergencyRelevanceReason? = null,
    val distanceM: Double? = null,
    val corroborated: Boolean = false,
    val adapterAllowsRed: Boolean = false,
    val detail: String = ""
) {
    fun activeAt(now: Instant): Boolean = expiresAt?.isAfter(now) ?: true
}

data class EmergencyStatusSnapshot(
    val localRisk: EmergencyRiskState,
    val preparedness: EmergencyPreparednessState,
    val checkedAt: Instant,
    val primaryAlert: EmergencyAlert? = null,
    val coverage: List<EmergencyDomainCoverage> = emptyList(),
    val incompleteCriticalCoverage: Boolean = false,
    val explanation: List<String> = emptyList()
)

enum class EmergencyPoiCategory {
    AIRPORT,
    PORT_OR_FERRY,
    LAND_BORDER,
    EMBASSY,
    CONSULATE_GENERAL,
    CONSULATE,
    HONORARY_CONSULATE
}

data class EmergencyStrategicPoi(
    val id: String,
    val name: String,
    val category: EmergencyPoiCategory,
    val latitude: Double,
    val longitude: Double,
    val countryCode: String = "",
    val representedCountryCodes: Set<String> = emptySet(),
    val code: String = "",
    val sourceId: String,
    val sourceVersion: String,
    val knownLocationAt: Instant? = null,
    val operationalStatus: String = "unknown"
)

data class EmergencyRankedPoi(
    val poi: EmergencyStrategicPoi,
    val distanceM: Double,
    val bearingDeg: Double,
    val plusCode: String = ""
)

data class EmergencyPoiPackManifest(
    val packId: String,
    val schemaVersion: Int,
    val region: String,
    val createdAt: Instant,
    val sourceVersions: List<String>,
    val categoryCounts: Map<EmergencyPoiCategory, Int>,
    val licences: List<String>,
    val attribution: List<String>,
    val fileChecksums: Map<String, String>,
    val packChecksum: String
)

data class EmergencyContentItem(
    val id: String,
    val title: String,
    val category: String,
    val nowText: String,
    val thirtySecondText: String,
    val fullGuideText: String,
    val sourceOrganisation: String,
    val sourceTitle: String,
    val sourceUrl: String,
    val sourcePublicationVersion: String,
    val methodMeshAdaptationVersion: String,
    val validatedAt: Instant? = null,
    val reviewDueAt: Instant? = null
) {
    fun reviewDue(now: Instant): Boolean = reviewDueAt?.isBefore(now) == true
}

data class EmergencyContentPackManifest(
    val packId: String,
    val schemaVersion: Int,
    val jurisdictionOrScope: String,
    val sourceOrganisations: List<String>,
    val sourcePublicationVersions: List<String>,
    val methodMeshAdaptationVersion: String,
    val validatedAt: Instant? = null,
    val reviewDueAt: Instant? = null,
    val licences: List<String> = emptyList(),
    val attribution: List<String> = emptyList(),
    val fileChecksums: Map<String, String> = emptyMap(),
    val packChecksum: String = ""
)

enum class EmergencyMonitoringMode { OFF, TRAVEL, FIELD_DEPLOYMENT, ACTIVE_WARNING }

data class EmergencyMonitoringSession(
    val mode: EmergencyMonitoringMode,
    val startedAt: Instant? = null,
    val region: String = ""
)

data class EmergencyLocationFix(
    val latitude: Double,
    val longitude: Double,
    val accuracyM: Double? = null,
    val plusCode: String,
    val capturedAt: Instant
)
