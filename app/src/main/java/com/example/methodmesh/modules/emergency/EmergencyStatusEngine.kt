package com.example.methodmesh.modules.emergency

import java.time.Instant

/**
 * Explainable aggregation only. Source adapters remain responsible for their
 * source-specific age, spatial uncertainty and corroboration policies.
 */
object EmergencyStatusEngine {
    fun evaluate(
        coverage: List<EmergencyDomainCoverage>,
        alerts: List<EmergencyAlert>,
        preparedness: EmergencyPreparednessState,
        now: Instant = Instant.now()
    ): EmergencyStatusSnapshot {
        val activeLocalAlerts = alerts
            .asSequence()
            .filter { it.locallyRelevant && it.activeAt(now) }
            .mapNotNull { alert -> acceptedRisk(alert)?.let { risk -> alert to risk } }
            .sortedWith(
                compareByDescending<Pair<EmergencyAlert, EmergencyRiskState>> { riskRank(it.second) }
                    .thenByDescending { it.first.issuedAt ?: it.first.retrievedAt }
            )
            .toList()

        val criticalCoverage = coverage.filter { it.critical }
        val criticalIncomplete = criticalCoverage.any { item ->
            item.state in setOf(EmergencyCoverageState.STALE, EmergencyCoverageState.UNAVAILABLE)
        }

        val primary = activeLocalAlerts.firstOrNull()
        val localRisk = when {
            primary != null -> primary.second
            criticalIncomplete -> EmergencyRiskState.GREY
            criticalCoverage.isNotEmpty() && criticalCoverage.all(::greenCompatible) -> EmergencyRiskState.GREEN
            else -> EmergencyRiskState.GREY
        }

        val explanation = buildList {
            when (localRisk) {
                EmergencyRiskState.RED,
                EmergencyRiskState.AMBER -> primary?.first?.let { alert ->
                    add(alert.title)
                    add("Source class: ${alert.authority.name}")
                    alert.relevanceReason?.let { add("Local relevance: ${it.name}") }
                    alert.distanceM?.let { add("Distance: ${it.toLong()} m") }
                    if (alert.detail.isNotBlank()) add(alert.detail)
                }
                EmergencyRiskState.GREEN -> add("Configured critical hazard domains have current-enough coverage and no locally relevant active warning was identified.")
                EmergencyRiskState.GREY -> {
                    val problemDomains = coverage.filter { it.critical && it.state in setOf(EmergencyCoverageState.STALE, EmergencyCoverageState.UNAVAILABLE) }
                    if (problemDomains.isEmpty()) {
                        add("Current local risk cannot be established with sufficient configured coverage.")
                    } else {
                        problemDomains.forEach { item ->
                            add("${item.domain.name}: ${item.state.name}${item.detail.takeIf(String::isNotBlank)?.let { " - $it" }.orEmpty()}")
                        }
                    }
                }
            }
            if (primary != null && criticalIncomplete) {
                add("Some critical hazard coverage is incomplete; the displayed alert outranks GREY but coverage is not complete.")
            }
        }

        return EmergencyStatusSnapshot(
            localRisk = localRisk,
            preparedness = preparedness,
            checkedAt = now,
            primaryAlert = primary?.first,
            coverage = coverage,
            incompleteCriticalCoverage = criticalIncomplete,
            explanation = explanation
        )
    }

    private fun acceptedRisk(alert: EmergencyAlert): EmergencyRiskState? = when (alert.candidateRisk) {
        EmergencyRiskState.RED -> when (alert.authority) {
            EmergencySourceAuthority.A_AUTHORITATIVE_INSTRUCTION -> EmergencyRiskState.RED
            EmergencySourceAuthority.B_AUTHORITATIVE_SITUATIONAL -> if (alert.adapterAllowsRed) EmergencyRiskState.RED else EmergencyRiskState.AMBER
            EmergencySourceAuthority.C_CURATED_EVENT_FEED -> if (alert.adapterAllowsRed && alert.corroborated) EmergencyRiskState.RED else EmergencyRiskState.AMBER
            EmergencySourceAuthority.D_OPEN_UNVERIFIED -> EmergencyRiskState.AMBER
        }
        EmergencyRiskState.AMBER -> EmergencyRiskState.AMBER
        EmergencyRiskState.GREEN,
        EmergencyRiskState.GREY -> null
    }

    private fun greenCompatible(item: EmergencyDomainCoverage): Boolean =
        item.state in setOf(
            EmergencyCoverageState.CURRENT_NO_ALERT,
            EmergencyCoverageState.NOT_APPLICABLE,
            EmergencyCoverageState.DISABLED_BY_USER
        )

    private fun riskRank(state: EmergencyRiskState): Int = when (state) {
        EmergencyRiskState.RED -> 4
        EmergencyRiskState.AMBER -> 3
        EmergencyRiskState.GREY -> 2
        EmergencyRiskState.GREEN -> 1
    }
}
