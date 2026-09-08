package com.example.methodmesh.modules.emergency

import java.time.Instant

/**
 * Small development core. Production promotion must replace strategic POI and
 * emergency-number fixtures with reproducibly generated, reviewed assets.
 */
object EmergencyCoreData {
    const val CORE_SCHEMA_VERSION = 1

    data class EmergencyNumber(
        val jurisdictionCode: String,
        val serviceType: String,
        val number: String,
        val notes: String,
        val sourceId: String,
        val sourceDatasetVersion: String
    )

    // Deliberately tiny fixture. This is enough to exercise offline behaviour
    // without pretending the global emergency-number table has been validated.
    val emergencyNumbers: List<EmergencyNumber> = listOf(
        EmergencyNumber("GB", "general_emergency", "999", "Primary UK emergency number.", "uk_emergency_services", "development_fixture_2026-09"),
        EmergencyNumber("GB", "general_emergency", "112", "European emergency number also supported in the UK.", "uk_emergency_services", "development_fixture_2026-09")
    )

    // Strategic POIs are intentionally not hand-maintained here. Release builds
    // should ingest the generated asset described by data/build_emergency_core.py.
    val strategicPoiFixture: List<EmergencyStrategicPoi> = emptyList()

    val referenceItems: List<EmergencyContentItem> = listOf(
        EmergencyContentItem(
            id = "adult_cpr_aed",
            title = "Adult CPR / AED",
            category = "First aid",
            nowText = "If the person is unresponsive, call the local emergency number immediately. If breathing is abnormal, start chest compressions and use an AED as soon as one is available.",
            thirtySecondText = "Chest compressions: centre of the chest, 100-120/min, 5-6 cm deep. If trained to give rescue breaths, use 30 compressions to 2 breaths. Follow AED prompts.",
            fullGuideText = "This short offline card is a MethodMesh development adaptation of Resuscitation Council UK 2025 adult basic life support guidance. It must be clinically/content reviewed before Production promotion. The emergency dispatcher can guide CPR if you are uncertain.",
            sourceOrganisation = "Resuscitation Council UK",
            sourceTitle = "Adult basic life support Guidelines 2025",
            sourceUrl = "https://www.resus.org.uk/professional-library/2025-resuscitation-guidelines/adult-basic-life-support-guidelines",
            sourcePublicationVersion = "2025-10-27",
            methodMeshAdaptationVersion = "0.2.0-development",
            validatedAt = null,
            reviewDueAt = Instant.parse("2026-10-01T00:00:00Z")
        ),
        developmentReference("choking", "Choking", "First aid"),
        developmentReference("severe_bleeding", "Severe bleeding", "First aid"),
        developmentReference("burns", "Burns", "First aid"),
        developmentReference("seizure", "Seizure", "First aid"),
        developmentReference("stroke", "Stroke", "First aid"),
        developmentReference("heart_attack", "Heart attack", "First aid"),
        developmentReference("anaphylaxis", "Anaphylaxis", "First aid"),
        developmentReference("drowning", "Drowning", "First aid"),
        developmentReference("heat_illness", "Heat illness", "First aid"),
        developmentReference("hypothermia", "Hypothermia", "First aid"),
        developmentReference("flood", "Flood - immediate actions", "Hazard"),
        developmentReference("earthquake", "Earthquake - immediate actions", "Hazard"),
        developmentReference("wildfire", "Wildfire - immediate actions", "Hazard"),
        developmentReference("cyclone", "Cyclone - immediate actions", "Hazard")
    )

    private fun developmentReference(id: String, title: String, category: String) = EmergencyContentItem(
        id = id,
        title = title,
        category = category,
        nowText = "Development content slot. Use authoritative local emergency guidance until this offline card has completed MethodMesh content validation.",
        thirtySecondText = "This card is intentionally not populated with unreviewed emergency instructions.",
        fullGuideText = "Source selection, adaptation and clinical/safety review are required before this item may be promoted from Development.",
        sourceOrganisation = "Pending authoritative source selection",
        sourceTitle = "Pending review",
        sourceUrl = "",
        sourcePublicationVersion = "unvalidated",
        methodMeshAdaptationVersion = "0.2.0-development",
        validatedAt = null,
        reviewDueAt = Instant.EPOCH
    )
}
