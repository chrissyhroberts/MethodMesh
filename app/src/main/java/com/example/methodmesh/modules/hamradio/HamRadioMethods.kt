package com.example.methodmesh.modules.hamradio

import com.example.methodmesh.core.methodmesh.ArchitectureId
import com.example.methodmesh.core.methodmesh.ArchitectureRef
import com.example.methodmesh.core.methodmesh.Entity
import com.example.methodmesh.core.methodmesh.ExecutionRequest
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.core.methodmesh.InvocationContext
import com.example.methodmesh.core.methodmesh.KnowledgeObjectType
import com.example.methodmesh.core.methodmesh.MethodContract
import com.example.methodmesh.core.methodmesh.MethodDescriptor
import com.example.methodmesh.core.methodmesh.MethodObjectType
import com.example.methodmesh.core.methodmesh.Observation
import com.example.methodmesh.core.methodmesh.ProvenanceContext
import com.example.methodmesh.core.methodmesh.Signal
import com.example.methodmesh.core.methodmesh.Transformation
import com.example.methodmesh.core.methodmesh.TransformationStatus
import com.example.methodmesh.core.methodmesh.runtime.As100ExecutionEngine
import com.example.methodmesh.core.methodmesh.runtime.As100Method
import com.example.methodmesh.core.methodmesh.withInvocationContext
import com.example.methodmesh.settings.SettingsState
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import kotlin.math.roundToInt

internal const val HAM_VERSION = "0.4.0"

internal object HamMethodSupport {
    fun request(method: ArchitectureRef, action: String, context: Map<String, String>, signals: List<Signal>, inputs: List<ArchitectureRef>) =
        As100ExecutionEngine.request(action = action, method = method, context = context, signals = signals, inputs = inputs)

    fun complete(
        request: ExecutionRequest,
        methodId: String,
        methodRef: ArchitectureRef,
        phenomenon: String,
        entityType: String,
        statusField: String,
        errorField: String,
        values: Map<String, String>,
        invocation: InvocationContext?
    ): ExecutionResult {
        val ok = values[statusField] == "succeeded"
        val entity = Entity(
            ArchitectureId("hamradio:${methodId.substringAfterLast('.')}:${System.currentTimeMillis()}"),
            entityType,
            temporalContext = request.temporalContext
        )
        val provenance = ProvenanceContext("methodmesh.hamradio", methodId, HAM_VERSION)
        val observation = Observation(
            phenomenon = phenomenon,
            subject = null,
            values = values,
            temporalContext = request.temporalContext,
            provenance = provenance
        )
        val transformation = Transformation(
            action = methodId,
            method = methodRef,
            outputs = listOf(ArchitectureRef(observation.id, observation.objectType, observation.phenomenon)),
            status = if (ok) TransformationStatus.Succeeded else TransformationStatus.Failed,
            temporalContext = request.temporalContext,
            provenance = provenance
        )
        return As100ExecutionEngine.complete(
            request,
            if (ok) TransformationStatus.Succeeded else TransformationStatus.Failed,
            entities = listOf(entity),
            observations = listOf(observation),
            transformations = listOf(transformation),
            diagnostics = if (ok) emptyMap() else mapOf(errorField to values[errorField].orEmpty())
        ).withInvocationContext(invocation)
    }
}

internal fun Map<String, String>.hamValue(key: String): String? =
    (this[key] ?: this["input_$key"])?.takeIf { it.isNotBlank() }

internal fun fmt(value: Double?, decimals: Int = 2): String = when (value) {
    null -> ""
    else -> "%.${decimals}f".format(value).trimEnd('0').trimEnd('.')
}

internal fun bool(value: Boolean) = value.toString()

object HamSpaceWeatherFields {
    const val RESULT = "ham_space_weather_result"
    const val KP = "ham_space_weather_kp"
    const val F107 = "ham_space_weather_f107"
    const val WIND = "ham_space_weather_wind_speed_kms"
    const val BT = "ham_space_weather_bt_nt"
    const val BZ = "ham_space_weather_bz_nt"
    const val G = "ham_space_weather_g_scale"
    const val R = "ham_space_weather_r_scale"
    const val S = "ham_space_weather_s_scale"
    const val PROVIDER_TIME = "ham_space_weather_provider_time"
    const val RETRIEVED = "ham_space_weather_retrieved_time_iso"
    const val FROM_CACHE = "ham_space_weather_from_cache"
    const val STALE = "ham_space_weather_stale"
    const val PROVIDER = "ham_space_weather_provider"
    const val SOURCE_URLS = "ham_space_weather_source_urls"
    const val WARNINGS = "ham_space_weather_warnings"
    const val STATUS = "ham_space_weather_status"
    const val ERROR = "ham_space_weather_error"
    val outputs = listOf(RESULT, KP, F107, WIND, BT, BZ, G, R, S, PROVIDER_TIME, RETRIEVED, FROM_CACHE, STALE, PROVIDER, SOURCE_URLS, WARNINGS, STATUS, ERROR)
}

object As100HamSpaceWeatherMethod : As100Method {
    const val ID = "ham.spaceweather.snapshot"
    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "Amateur-radio space weather snapshot")
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID), methodType = MethodObjectType.Calculation, name = "Space weather", version = HAM_VERSION,
        description = "Retrieve radio-relevant current conditions from NOAA SWPC.", outputs = HamSpaceWeatherFields.outputs,
        graphOutputs = listOf(ID), parameters = mapOf("category" to "Amateur radio", "status" to "Development")
    )
    override val contract = MethodContract(
        method = ref,
        producedKnowledgeTypes = listOf(KnowledgeObjectType.Observation),
        producedFields = descriptor.outputs,
        producedGraphOutputs = descriptor.graphOutputs
    )
    override fun request(action: String, context: Map<String, String>, signals: List<Signal>, inputs: List<ArchitectureRef>) = HamMethodSupport.request(ref, action, context, signals, inputs)
    override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?): ExecutionResult =
        result(request, read(request.context), InvocationContext.from(request.context))

    fun read(settings: Map<String, String>, repository: HamRadioRepository = HamRadioRepository.shared): Map<String, String> {
        val snapshot = repository.spaceWeather(settings.hamValue("refresh_mode") ?: "cache_preferred")
        val summary = buildList {
            snapshot.kp?.let { add("Kp ${fmt(it, 1)}") }
            snapshot.f107?.let { add("SFI ${fmt(it, 0)}") }
            if (snapshot.gScale != null || snapshot.rScale != null || snapshot.sScale != null) add("G${snapshot.gScale ?: 0} R${snapshot.rScale ?: 0} S${snapshot.sScale ?: 0}")
            snapshot.windSpeedKmS?.let { add("wind ${fmt(it, 0)} km/s") }
            snapshot.bzNt?.let { add("Bz ${fmt(it, 1)} nT") }
        }.joinToString(" · ").ifBlank { if (snapshot.status == "succeeded") "Space weather retrieved." else "Space weather unavailable." }
        return linkedMapOf(
            HamSpaceWeatherFields.RESULT to summary,
            HamSpaceWeatherFields.KP to fmt(snapshot.kp, 2),
            HamSpaceWeatherFields.F107 to fmt(snapshot.f107, 2),
            HamSpaceWeatherFields.WIND to fmt(snapshot.windSpeedKmS, 2),
            HamSpaceWeatherFields.BT to fmt(snapshot.btNt, 2),
            HamSpaceWeatherFields.BZ to fmt(snapshot.bzNt, 2),
            HamSpaceWeatherFields.G to snapshot.gScale?.toString().orEmpty(),
            HamSpaceWeatherFields.R to snapshot.rScale?.toString().orEmpty(),
            HamSpaceWeatherFields.S to snapshot.sScale?.toString().orEmpty(),
            HamSpaceWeatherFields.PROVIDER_TIME to snapshot.providerTime,
            HamSpaceWeatherFields.RETRIEVED to snapshot.retrievedAt.toString(),
            HamSpaceWeatherFields.FROM_CACHE to bool(snapshot.fromCache),
            HamSpaceWeatherFields.STALE to bool(snapshot.stale),
            HamSpaceWeatherFields.PROVIDER to "NOAA Space Weather Prediction Center (SWPC)",
            HamSpaceWeatherFields.SOURCE_URLS to listOf(
                "https://services.swpc.noaa.gov/products/noaa-planetary-k-index.json",
                "https://services.swpc.noaa.gov/products/summary/10cm-flux.json",
                "https://services.swpc.noaa.gov/products/summary/solar-wind-speed.json",
                "https://services.swpc.noaa.gov/products/summary/solar-wind-mag-field.json",
                "https://services.swpc.noaa.gov/products/noaa-scales.json"
            ).joinToString("|"),
            HamSpaceWeatherFields.WARNINGS to snapshot.warnings.joinToString(" | "),
            HamSpaceWeatherFields.STATUS to snapshot.status,
            HamSpaceWeatherFields.ERROR to snapshot.error
        )
    }

    fun result(request: ExecutionRequest, values: Map<String, String>, invocation: InvocationContext?) =
        HamMethodSupport.complete(request, ID, ref, ID, "SpaceWeatherSnapshot", HamSpaceWeatherFields.STATUS, HamSpaceWeatherFields.ERROR, values, invocation)
}

object HamPskReporterFields {
    const val RESULT = "ham_psk_result"
    const val REPORT_COUNT = "ham_psk_report_count"
    const val UNIQUE_CALLSIGNS = "ham_psk_unique_counterpart_callsigns"
    const val UNIQUE_GRIDS = "ham_psk_unique_grids"
    const val LATEST_TIME = "ham_psk_latest_time_iso"
    const val BAND_COUNTS_JSON = "ham_psk_band_counts_json"
    const val MODE_COUNTS_JSON = "ham_psk_mode_counts_json"
    const val REPORTS_JSON = "ham_psk_reports_json"
    const val SUBJECT = "ham_psk_subject"
    const val SUBJECT_TYPE = "ham_psk_subject_type"
    const val DIRECTION = "ham_psk_direction"
    const val LOOKBACK_MINUTES = "ham_psk_lookback_minutes"
    const val PROVIDER = "ham_psk_provider"
    const val SOURCE_URL = "ham_psk_source_url"
    const val REQUEST_URL = "ham_psk_request_url"
    const val RETRIEVED = "ham_psk_retrieved_time_iso"
    const val PROVIDER_TIME = "ham_psk_provider_time"
    const val FROM_CACHE = "ham_psk_from_cache"
    const val CACHE_AGE_SECONDS = "ham_psk_cache_age_seconds"
    const val RATE_LIMIT_SECONDS = "ham_psk_rate_limit_seconds"
    const val RETRY_AFTER_SECONDS = "ham_psk_retry_after_seconds"
    const val WARNING = "ham_psk_warning"
    const val STATUS = "ham_psk_status"
    const val ERROR = "ham_psk_error"
    val outputs = listOf(
        RESULT, REPORT_COUNT, UNIQUE_CALLSIGNS, UNIQUE_GRIDS, LATEST_TIME, BAND_COUNTS_JSON, MODE_COUNTS_JSON,
        REPORTS_JSON, SUBJECT, SUBJECT_TYPE, DIRECTION, LOOKBACK_MINUTES, PROVIDER, SOURCE_URL, REQUEST_URL,
        RETRIEVED, PROVIDER_TIME, FROM_CACHE, CACHE_AGE_SECONDS, RATE_LIMIT_SECONDS, RETRY_AFTER_SECONDS,
        WARNING, STATUS, ERROR
    )
}

object As100HamPskReporterMethod : As100Method {
    const val ID = "ham.activity.pskreporter"
    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "PSK Reporter reception activity")
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID), methodType = MethodObjectType.Calculation, name = "PSK Reporter activity", version = HAM_VERSION,
        description = "Retrieve recent observed amateur-radio reception reports with a hard seven-minute upstream request floor.",
        outputs = HamPskReporterFields.outputs, graphOutputs = listOf(ID),
        parameters = mapOf("category" to "Amateur radio", "status" to "Development", "provider" to "PSK Reporter", "minimum_request_interval_seconds" to "420")
    )
    override val contract = MethodContract(
        method = ref,
        producedKnowledgeTypes = listOf(KnowledgeObjectType.Observation),
        producedFields = descriptor.outputs,
        producedGraphOutputs = descriptor.graphOutputs
    )
    override fun request(action: String, context: Map<String, String>, signals: List<Signal>, inputs: List<ArchitectureRef>) = HamMethodSupport.request(ref, action, context, signals, inputs)
    override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?): ExecutionResult =
        result(request, read(request.context), InvocationContext.from(request.context))

    fun read(settings: Map<String, String>, repository: HamRadioRepository = HamRadioRepository.shared): Map<String, String> {
        return try {
            val subject = settings.hamValue("subject").orEmpty()
            val query = HamRadioRepository.PskReporterQuery(
                subject = subject,
                subjectType = settings.hamValue("subject_type") ?: "callsign",
                direction = settings.hamValue("direction") ?: "sent",
                lookbackMinutes = settings.hamValue("lookback_minutes")?.toIntOrNull() ?: 30,
                mode = settings.hamValue("mode").orEmpty(),
                minFrequencyMhz = settings.hamValue("min_frequency_mhz")?.toDoubleOrNull()?.takeIf { it > 0 },
                maxFrequencyMhz = settings.hamValue("max_frequency_mhz")?.toDoubleOrNull()?.takeIf { it > 0 },
                recordLimit = settings.hamValue("record_limit")?.toIntOrNull() ?: 100,
                includeNoLocator = settings.hamValue("include_no_locator")?.lowercase() in setOf("true", "yes", "1")
            )
            val snapshot = repository.pskReporter(query)
            val reports = snapshot.reports
            val normalizedSubject = snapshot.query.subject.uppercase()
            fun counterpartCall(report: HamRadioRepository.PskReceptionReport): String = when (snapshot.query.direction) {
                "sent" -> report.receiverCallsign
                "received" -> report.senderCallsign
                else -> if (report.senderCallsign.equals(normalizedSubject, true)) report.receiverCallsign else report.senderCallsign
            }
            fun counterpartGrid(report: HamRadioRepository.PskReceptionReport): String = when (snapshot.query.direction) {
                "sent" -> report.receiverLocator
                "received" -> report.senderLocator
                else -> if (snapshot.query.subjectType == "grid") {
                    if (report.senderLocator.equals(normalizedSubject, true)) report.receiverLocator else report.senderLocator
                } else if (report.senderCallsign.equals(normalizedSubject, true)) report.receiverLocator else report.senderLocator
            }
            val uniqueCalls = reports.map(::counterpartCall).filter { it.isNotBlank() }.map { it.uppercase() }.toSet().size
            val uniqueGrids = reports.map(::counterpartGrid).filter { it.isNotBlank() }.map { it.uppercase() }.toSet().size
            val latest = reports.mapNotNull { it.flowStartSeconds }.maxOrNull()?.let { Instant.ofEpochSecond(it).toString() }.orEmpty()
            val bandCounts = reports.groupingBy { it.band }.eachCount().toList().sortedByDescending { it.second }.toMap()
            val modeCounts = reports.map { it.mode.ifBlank { "unknown" } }.groupingBy { it }.eachCount().toList().sortedByDescending { it.second }.toMap()
            val topBand = bandCounts.maxByOrNull { it.value }?.key.orEmpty()
            val topMode = modeCounts.maxByOrNull { it.value }?.key.orEmpty()
            val main = when (snapshot.status) {
                "succeeded" -> "${reports.size} reports · $uniqueCalls stations · $uniqueGrids grids${if (topBand.isNotBlank()) " · $topBand" else ""}${if (topMode.isNotBlank()) "/$topMode" else ""}${if (snapshot.fromCache) " · cached" else ""}"
                "rate_limited" -> "PSK Reporter request held by 7-minute gate · retry in ${snapshot.retryAfterSeconds}s"
                else -> "PSK Reporter unavailable"
            }
            linkedMapOf(
                HamPskReporterFields.RESULT to main,
                HamPskReporterFields.REPORT_COUNT to reports.size.toString(),
                HamPskReporterFields.UNIQUE_CALLSIGNS to uniqueCalls.toString(),
                HamPskReporterFields.UNIQUE_GRIDS to uniqueGrids.toString(),
                HamPskReporterFields.LATEST_TIME to latest,
                HamPskReporterFields.BAND_COUNTS_JSON to JSONObject(bandCounts).toString(),
                HamPskReporterFields.MODE_COUNTS_JSON to JSONObject(modeCounts).toString(),
                HamPskReporterFields.REPORTS_JSON to repository.pskReportsJson(reports),
                HamPskReporterFields.SUBJECT to snapshot.query.subject,
                HamPskReporterFields.SUBJECT_TYPE to snapshot.query.subjectType,
                HamPskReporterFields.DIRECTION to snapshot.query.direction,
                HamPskReporterFields.LOOKBACK_MINUTES to snapshot.query.lookbackMinutes.toString(),
                HamPskReporterFields.PROVIDER to HamRadioRepository.PSK_REPORTER_PROVIDER,
                HamPskReporterFields.SOURCE_URL to HamRadioRepository.PSK_REPORTER_ENDPOINT,
                HamPskReporterFields.REQUEST_URL to snapshot.requestUrl,
                HamPskReporterFields.RETRIEVED to snapshot.retrievedAt.toString(),
                HamPskReporterFields.PROVIDER_TIME to snapshot.providerTime,
                HamPskReporterFields.FROM_CACHE to bool(snapshot.fromCache),
                HamPskReporterFields.CACHE_AGE_SECONDS to snapshot.cacheAgeSeconds.toString(),
                HamPskReporterFields.RATE_LIMIT_SECONDS to HamRadioRepository.PSK_REPORTER_MIN_REQUEST_INTERVAL_SECONDS.toString(),
                HamPskReporterFields.RETRY_AFTER_SECONDS to snapshot.retryAfterSeconds.toString(),
                HamPskReporterFields.WARNING to snapshot.warning,
                HamPskReporterFields.STATUS to snapshot.status,
                HamPskReporterFields.ERROR to snapshot.error
            )
        } catch (e: Exception) {
            linkedMapOf(
                HamPskReporterFields.RESULT to "PSK Reporter query failed",
                HamPskReporterFields.REPORT_COUNT to "0",
                HamPskReporterFields.UNIQUE_CALLSIGNS to "0",
                HamPskReporterFields.UNIQUE_GRIDS to "0",
                HamPskReporterFields.LATEST_TIME to "",
                HamPskReporterFields.BAND_COUNTS_JSON to "{}",
                HamPskReporterFields.MODE_COUNTS_JSON to "{}",
                HamPskReporterFields.REPORTS_JSON to "[]",
                HamPskReporterFields.SUBJECT to settings.hamValue("subject").orEmpty(),
                HamPskReporterFields.SUBJECT_TYPE to settings.hamValue("subject_type").orEmpty(),
                HamPskReporterFields.DIRECTION to settings.hamValue("direction").orEmpty(),
                HamPskReporterFields.LOOKBACK_MINUTES to settings.hamValue("lookback_minutes").orEmpty(),
                HamPskReporterFields.PROVIDER to HamRadioRepository.PSK_REPORTER_PROVIDER,
                HamPskReporterFields.SOURCE_URL to HamRadioRepository.PSK_REPORTER_ENDPOINT,
                HamPskReporterFields.REQUEST_URL to "",
                HamPskReporterFields.RETRIEVED to Instant.now().toString(),
                HamPskReporterFields.PROVIDER_TIME to "",
                HamPskReporterFields.FROM_CACHE to "false",
                HamPskReporterFields.CACHE_AGE_SECONDS to "0",
                HamPskReporterFields.RATE_LIMIT_SECONDS to HamRadioRepository.PSK_REPORTER_MIN_REQUEST_INTERVAL_SECONDS.toString(),
                HamPskReporterFields.RETRY_AFTER_SECONDS to "0",
                HamPskReporterFields.WARNING to "",
                HamPskReporterFields.STATUS to "failed",
                HamPskReporterFields.ERROR to (e.message ?: "PSK Reporter query failed.")
            )
        }
    }

    fun result(request: ExecutionRequest, values: Map<String, String>, invocation: InvocationContext?) =
        HamMethodSupport.complete(request, ID, ref, ID, "PskReceptionActivity", HamPskReporterFields.STATUS, HamPskReporterFields.ERROR, values, invocation)
}

object HamBandFields {
    const val RESULT = "ham_band_result"
    const val PRIMARY = "ham_band_primary"
    const val PRIMARY_MHZ = "ham_band_primary_mhz"
    const val PRIMARY_SCORE = "ham_band_primary_score"
    const val RANKED_JSON = "ham_band_ranked_json"
    const val SOLAR_ELEVATION = "ham_band_solar_elevation_deg"
    const val SOLAR_HOUR = "ham_band_local_solar_hour"
    const val DISTANCE = "ham_band_target_distance_km"
    const val ORIGIN = "ham_band_origin_locator"
    const val TARGET = "ham_band_target_locator"
    const val KP = "ham_band_kp"
    const val F107 = "ham_band_f107"
    const val R_SCALE = "ham_band_r_scale"
    const val CONDITIONS_SOURCE = "ham_band_conditions_source"
    const val MODE = "ham_band_mode"
    const val HEURISTIC_VERSION = "ham_band_heuristic_version"
    const val ADVISORY = "ham_band_advisory"
    const val STATUS = "ham_band_status"
    const val ERROR = "ham_band_error"
    val outputs = listOf(RESULT, PRIMARY, PRIMARY_MHZ, PRIMARY_SCORE, RANKED_JSON, SOLAR_ELEVATION, SOLAR_HOUR, DISTANCE, ORIGIN, TARGET, KP, F107, R_SCALE, CONDITIONS_SOURCE, MODE, HEURISTIC_VERSION, ADVISORY, STATUS, ERROR)
}

object As100HamBandAdviceMethod : As100Method {
    const val ID = "ham.band.recommend"
    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "HF band recommendation")
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID), methodType = MethodObjectType.Calculation, name = "Best HF bands now", version = HAM_VERSION,
        description = "Rank amateur HF bands using path, solar geometry and live or manual conditions.", outputs = HamBandFields.outputs,
        graphOutputs = listOf(ID), parameters = mapOf("category" to "Amateur radio", "status" to "Development", "heuristic_version" to "0.1")
    )
    override val contract = MethodContract(
        method = ref,
        producedKnowledgeTypes = listOf(KnowledgeObjectType.Observation),
        producedFields = descriptor.outputs,
        producedGraphOutputs = descriptor.graphOutputs
    )
    override fun request(action: String, context: Map<String, String>, signals: List<Signal>, inputs: List<ArchitectureRef>) = HamMethodSupport.request(ref, action, context, signals, inputs)
    override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?): ExecutionResult = result(request, calculate(request.context), InvocationContext.from(request.context))

    fun calculate(settings: Map<String, String>, repository: HamRadioRepository = HamRadioRepository.shared): Map<String, String> = try {
        val originLocatorRaw = settings.hamValue("origin_locator").orEmpty()
        val locationMode = settings.hamValue("location_mode") ?: if (originLocatorRaw.isNotBlank()) "locator" else "coordinates"
        val originCoordinate: HamRadioCalculations.Coordinate
        val originLocator: String
        if (locationMode == "locator") {
            require(originLocatorRaw.isNotBlank()) { "Origin Maidenhead locator is required when location_mode=locator." }
            val cell = HamRadioCalculations.decodeMaidenhead(originLocatorRaw)
            originCoordinate = HamRadioCalculations.Coordinate(cell.centreLatitude, cell.centreLongitude)
            originLocator = cell.locator
        } else {
            val lat = settings.hamValue("latitude")?.toDoubleOrNull() ?: error("Latitude is required when location_mode=coordinates.")
            val lon = settings.hamValue("longitude")?.toDoubleOrNull() ?: error("Longitude is required when location_mode=coordinates.")
            require(lat in -90.0..90.0 && lon in -180.0..180.0) { "Origin coordinates are outside WGS84 bounds." }
            originCoordinate = HamRadioCalculations.Coordinate(lat, lon)
            originLocator = HamRadioCalculations.encodeMaidenhead(lat, lon, 6)
        }
        val targetRaw = settings.hamValue("target_locator").orEmpty()
        val targetLocator = if (targetRaw.isBlank()) "" else HamRadioCalculations.normalizeLocator(targetRaw)
        val distance = if (targetRaw.isNotBlank()) {
            val targetCell = HamRadioCalculations.decodeMaidenhead(targetRaw)
            HamRadioCalculations.path(originCoordinate, HamRadioCalculations.Coordinate(targetCell.centreLatitude, targetCell.centreLongitude)).distanceKm
        } else settings.hamValue("target_distance_km")?.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 2000.0
        val source = settings.hamValue("conditions_source") ?: "live_noaa"
        var kp = settings.hamValue("kp")?.toDoubleOrNull() ?: 2.0
        var f107 = settings.hamValue("f107")?.toDoubleOrNull() ?: 100.0
        var rScale = settings.hamValue("r_scale")?.toIntOrNull() ?: 0
        if (source == "live_noaa") {
            val live = repository.spaceWeather("cache_preferred")
            live.kp?.let { kp = it }
            live.f107?.let { f107 = it }
            live.rScale?.let { rScale = it }
        }
        val mode = settings.hamValue("mode") ?: "mixed"
        val whenInstant = settings.hamValue("when_iso")?.let { Instant.parse(it) } ?: Instant.now()
        val advice = HamRadioCalculations.recommendBands(originCoordinate, distance, whenInstant, kp, f107, rScale, mode)
        val rankedJson = JSONArray().apply {
            advice.ranked.forEach { candidate ->
                put(JSONObject().apply {
                    put("band", candidate.band)
                    put("representative_mhz", candidate.representativeMhz)
                    put("score", candidate.score)
                    put("reasons", JSONArray(candidate.reasons))
                })
            }
        }.toString()
        val top = advice.ranked.first()
        val next = advice.ranked.drop(1).take(2).joinToString(" / ") { it.band }
        linkedMapOf(
            HamBandFields.RESULT to "Best now: ${top.band} (${fmt(top.representativeMhz, 2)} MHz)${if (next.isNotBlank()) ", then $next" else ""} · score ${top.score}/100",
            HamBandFields.PRIMARY to top.band,
            HamBandFields.PRIMARY_MHZ to fmt(top.representativeMhz, 3),
            HamBandFields.PRIMARY_SCORE to top.score.toString(),
            HamBandFields.RANKED_JSON to rankedJson,
            HamBandFields.SOLAR_ELEVATION to fmt(advice.solarElevationDeg, 2),
            HamBandFields.SOLAR_HOUR to fmt(advice.localSolarHour, 2),
            HamBandFields.DISTANCE to fmt(advice.pathDistanceKm, 1),
            HamBandFields.ORIGIN to originLocator,
            HamBandFields.TARGET to targetLocator,
            HamBandFields.KP to fmt(kp, 2),
            HamBandFields.F107 to fmt(f107, 2),
            HamBandFields.R_SCALE to rScale.toString(),
            HamBandFields.CONDITIONS_SOURCE to source,
            HamBandFields.MODE to mode,
            HamBandFields.HEURISTIC_VERSION to "0.1",
            HamBandFields.ADVISORY to "Heuristic ordering only; not MUF/VOACAP/reliability or regulatory advice. Check current national allocations and band plan before transmitting.",
            HamBandFields.STATUS to "succeeded",
            HamBandFields.ERROR to ""
        )
    } catch (e: Exception) {
        HamBandFields.outputs.associateWith { "" }.toMutableMap().apply {
            this[HamBandFields.RESULT] = "Band recommendation failed"
            this[HamBandFields.STATUS] = "failed"
            this[HamBandFields.ERROR] = e.message ?: "Band recommendation failed."
            this[HamBandFields.HEURISTIC_VERSION] = "0.1"
        }
    }

    fun result(request: ExecutionRequest, values: Map<String, String>, invocation: InvocationContext?) =
        HamMethodSupport.complete(request, ID, ref, ID, "HamBandAdvice", HamBandFields.STATUS, HamBandFields.ERROR, values, invocation)
}

object HamMaidenheadFields {
    const val RESULT = "ham_maidenhead_result"
    const val LOCATOR = "ham_maidenhead_locator"
    const val LATITUDE = "ham_maidenhead_latitude"
    const val LONGITUDE = "ham_maidenhead_longitude"
    const val LAT_SPAN = "ham_maidenhead_latitude_span_deg"
    const val LON_SPAN = "ham_maidenhead_longitude_span_deg"
    const val STATUS = "ham_maidenhead_status"
    const val ERROR = "ham_maidenhead_error"
    val outputs = listOf(RESULT, LOCATOR, LATITUDE, LONGITUDE, LAT_SPAN, LON_SPAN, STATUS, ERROR)
}

object As100HamMaidenheadMethod : As100Method {
    const val ID = "ham.maidenhead.convert"
    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "Maidenhead conversion")
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID), methodType = MethodObjectType.Calculation, name = "Maidenhead converter", version = HAM_VERSION,
        description = "Encode WGS84 coordinates or decode Maidenhead locators.", outputs = HamMaidenheadFields.outputs,
        graphOutputs = listOf(ID), parameters = mapOf("category" to "Amateur radio", "status" to "Development")
    )
    override val contract = MethodContract(
        method = ref,
        producedKnowledgeTypes = listOf(KnowledgeObjectType.Observation),
        producedFields = descriptor.outputs,
        producedGraphOutputs = descriptor.graphOutputs
    )
    override fun request(action: String, context: Map<String, String>, signals: List<Signal>, inputs: List<ArchitectureRef>) = HamMethodSupport.request(ref, action, context, signals, inputs)
    override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?) = result(request, calculate(request.context), InvocationContext.from(request.context))
    fun calculate(settings: Map<String, String>): Map<String, String> = try {
        val operation = settings.hamValue("operation") ?: "encode"
        val cell = if (operation == "decode") {
            HamRadioCalculations.decodeMaidenhead(settings.hamValue("locator") ?: error("Locator is required."))
        } else {
            val lat = settings.hamValue("latitude")?.toDoubleOrNull() ?: 0.0
            val lon = settings.hamValue("longitude")?.toDoubleOrNull() ?: 0.0
            val precision = settings.hamValue("precision")?.toIntOrNull() ?: 6
            HamRadioCalculations.decodeMaidenhead(HamRadioCalculations.encodeMaidenhead(lat, lon, precision))
        }
        linkedMapOf(
            HamMaidenheadFields.RESULT to "${cell.locator} · ${fmt(cell.centreLatitude, 4)}, ${fmt(cell.centreLongitude, 4)}",
            HamMaidenheadFields.LOCATOR to cell.locator,
            HamMaidenheadFields.LATITUDE to fmt(cell.centreLatitude, 6),
            HamMaidenheadFields.LONGITUDE to fmt(cell.centreLongitude, 6),
            HamMaidenheadFields.LAT_SPAN to fmt(cell.latitudeSpanDeg, 8),
            HamMaidenheadFields.LON_SPAN to fmt(cell.longitudeSpanDeg, 8),
            HamMaidenheadFields.STATUS to "succeeded", HamMaidenheadFields.ERROR to ""
        )
    } catch (e: Exception) { failure(HamMaidenheadFields.outputs, HamMaidenheadFields.RESULT, HamMaidenheadFields.STATUS, HamMaidenheadFields.ERROR, "Maidenhead conversion failed", e) }
    fun result(request: ExecutionRequest, values: Map<String, String>, invocation: InvocationContext?) = HamMethodSupport.complete(request, ID, ref, ID, "MaidenheadLocation", HamMaidenheadFields.STATUS, HamMaidenheadFields.ERROR, values, invocation)
}

object HamPathFields {
    const val RESULT = "ham_path_result"
    const val DISTANCE_KM = "ham_path_distance_km"
    const val DISTANCE_MI = "ham_path_distance_mi"
    const val BEARING = "ham_path_initial_bearing_deg"
    const val REVERSE = "ham_path_reverse_bearing_deg"
    const val LONG_PATH = "ham_path_long_path_bearing_deg"
    const val ORIGIN = "ham_path_origin_locator"
    const val DESTINATION = "ham_path_destination_locator"
    const val STATUS = "ham_path_status"
    const val ERROR = "ham_path_error"
    val outputs = listOf(RESULT, DISTANCE_KM, DISTANCE_MI, BEARING, REVERSE, LONG_PATH, ORIGIN, DESTINATION, STATUS, ERROR)
}

object As100HamPathMethod : As100Method {
    const val ID = "ham.path.calculate"
    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "Great-circle radio path")
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID), methodType = MethodObjectType.Calculation, name = "Radio path", version = HAM_VERSION,
        description = "Calculate locator-to-locator distance and bearings.", outputs = HamPathFields.outputs,
        graphOutputs = listOf(ID), parameters = mapOf("category" to "Amateur radio", "status" to "Development")
    )
    override val contract = MethodContract(
        method = ref,
        producedKnowledgeTypes = listOf(KnowledgeObjectType.Observation),
        producedFields = descriptor.outputs,
        producedGraphOutputs = descriptor.graphOutputs
    )
    override fun request(action: String, context: Map<String, String>, signals: List<Signal>, inputs: List<ArchitectureRef>) = HamMethodSupport.request(ref, action, context, signals, inputs)
    override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?) = result(request, calculate(request.context), InvocationContext.from(request.context))
    fun calculate(settings: Map<String, String>): Map<String, String> = try {
        val origin = settings.hamValue("origin_locator") ?: error("Origin locator is required.")
        val destination = settings.hamValue("destination_locator") ?: error("Destination locator is required.")
        val path = HamRadioCalculations.pathFromLocators(origin, destination)
        linkedMapOf(
            HamPathFields.RESULT to "${fmt(path.distanceKm, 1)} km · ${fmt(path.initialBearingDeg, 1)}° short path",
            HamPathFields.DISTANCE_KM to fmt(path.distanceKm, 3), HamPathFields.DISTANCE_MI to fmt(path.distanceKm * 0.621371192, 3),
            HamPathFields.BEARING to fmt(path.initialBearingDeg, 2), HamPathFields.REVERSE to fmt(path.reverseBearingDeg, 2),
            HamPathFields.LONG_PATH to fmt(path.longPathBearingDeg, 2), HamPathFields.ORIGIN to HamRadioCalculations.normalizeLocator(origin),
            HamPathFields.DESTINATION to HamRadioCalculations.normalizeLocator(destination), HamPathFields.STATUS to "succeeded", HamPathFields.ERROR to ""
        )
    } catch (e: Exception) { failure(HamPathFields.outputs, HamPathFields.RESULT, HamPathFields.STATUS, HamPathFields.ERROR, "Radio path calculation failed", e) }
    fun result(request: ExecutionRequest, values: Map<String, String>, invocation: InvocationContext?) = HamMethodSupport.complete(request, ID, ref, ID, "RadioPath", HamPathFields.STATUS, HamPathFields.ERROR, values, invocation)
}

object HamAntennaFields {
    const val RESULT = "ham_antenna_result"
    const val WAVELENGTH_M = "ham_antenna_wavelength_m"
    const val TOTAL_M = "ham_antenna_total_length_m"
    const val TOTAL_FT = "ham_antenna_total_length_ft"
    const val ELEMENT_M = "ham_antenna_element_length_m"
    const val ELEMENT_FT = "ham_antenna_element_length_ft"
    const val DESIGN = "ham_antenna_design"
    const val FACTOR = "ham_antenna_factor"
    const val STATUS = "ham_antenna_status"
    const val ERROR = "ham_antenna_error"
    val outputs = listOf(RESULT, WAVELENGTH_M, TOTAL_M, TOTAL_FT, ELEMENT_M, ELEMENT_FT, DESIGN, FACTOR, STATUS, ERROR)
}

object As100HamAntennaMethod : As100Method {
    const val ID = "ham.antenna.calculate"
    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "Antenna dimension calculation")
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID), methodType = MethodObjectType.Calculation, name = "Antenna length", version = HAM_VERSION,
        description = "Calculate wavelength-derived antenna starting dimensions.", outputs = HamAntennaFields.outputs,
        graphOutputs = listOf(ID), parameters = mapOf("category" to "Amateur radio", "status" to "Development")
    )
    override val contract = MethodContract(
        method = ref,
        producedKnowledgeTypes = listOf(KnowledgeObjectType.Observation),
        producedFields = descriptor.outputs,
        producedGraphOutputs = descriptor.graphOutputs
    )
    override fun request(action: String, context: Map<String, String>, signals: List<Signal>, inputs: List<ArchitectureRef>) = HamMethodSupport.request(ref, action, context, signals, inputs)
    override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?) = result(request, calculate(request.context), InvocationContext.from(request.context))
    fun calculate(settings: Map<String, String>): Map<String, String> = try {
        val r = HamRadioCalculations.antenna(settings.hamValue("frequency_mhz")?.toDoubleOrNull() ?: 14.2, settings.hamValue("design") ?: "dipole", settings.hamValue("velocity_factor")?.toDoubleOrNull() ?: 0.95)
        linkedMapOf(
            HamAntennaFields.RESULT to "${r.design.replace('_', ' ')} · ${fmt(r.totalLengthM, 2)} m total${if (r.design == "dipole") " · ${fmt(r.elementLengthM, 2)} m/leg" else ""}",
            HamAntennaFields.WAVELENGTH_M to fmt(r.wavelengthM, 4), HamAntennaFields.TOTAL_M to fmt(r.totalLengthM, 4), HamAntennaFields.TOTAL_FT to fmt(r.totalLengthM * 3.280839895, 4),
            HamAntennaFields.ELEMENT_M to fmt(r.elementLengthM, 4), HamAntennaFields.ELEMENT_FT to fmt(r.elementLengthM * 3.280839895, 4), HamAntennaFields.DESIGN to r.design,
            HamAntennaFields.FACTOR to fmt(r.factor, 4), HamAntennaFields.STATUS to "succeeded", HamAntennaFields.ERROR to ""
        )
    } catch (e: Exception) { failure(HamAntennaFields.outputs, HamAntennaFields.RESULT, HamAntennaFields.STATUS, HamAntennaFields.ERROR, "Antenna calculation failed", e) }
    fun result(request: ExecutionRequest, values: Map<String, String>, invocation: InvocationContext?) = HamMethodSupport.complete(request, ID, ref, ID, "AntennaDimension", HamAntennaFields.STATUS, HamAntennaFields.ERROR, values, invocation)
}

object HamSwrFields {
    const val RESULT = "ham_swr_result"
    const val VALUE = "ham_swr_value"
    const val GAMMA = "ham_swr_reflection_coefficient"
    const val RETURN_LOSS = "ham_swr_return_loss_db"
    const val MISMATCH_LOSS = "ham_swr_mismatch_loss_db"
    const val STATUS = "ham_swr_status"
    const val ERROR = "ham_swr_error"
    val outputs = listOf(RESULT, VALUE, GAMMA, RETURN_LOSS, MISMATCH_LOSS, STATUS, ERROR)
}

object As100HamSwrMethod : As100Method {
    const val ID = "ham.swr.calculate"
    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "SWR calculation")
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID), methodType = MethodObjectType.Calculation, name = "SWR calculator", version = HAM_VERSION,
        description = "Calculate SWR and related mismatch metrics from forward/reflected power.", outputs = HamSwrFields.outputs,
        graphOutputs = listOf(ID), parameters = mapOf("category" to "Amateur radio", "status" to "Development")
    )
    override val contract = MethodContract(
        method = ref,
        producedKnowledgeTypes = listOf(KnowledgeObjectType.Observation),
        producedFields = descriptor.outputs,
        producedGraphOutputs = descriptor.graphOutputs
    )
    override fun request(action: String, context: Map<String, String>, signals: List<Signal>, inputs: List<ArchitectureRef>) = HamMethodSupport.request(ref, action, context, signals, inputs)
    override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?) = result(request, calculate(request.context), InvocationContext.from(request.context))
    fun calculate(settings: Map<String, String>): Map<String, String> = try {
        val r = HamRadioCalculations.swr(settings.hamValue("forward_power_w")?.toDoubleOrNull() ?: 100.0, settings.hamValue("reflected_power_w")?.toDoubleOrNull() ?: 4.0)
        linkedMapOf(
            HamSwrFields.RESULT to "SWR ${fmt(r.swr, 2)}:1 · mismatch loss ${fmt(r.mismatchLossDb, 2)} dB",
            HamSwrFields.VALUE to fmt(r.swr, 4), HamSwrFields.GAMMA to fmt(r.reflectionCoefficient, 6),
            HamSwrFields.RETURN_LOSS to (r.returnLossDb?.let { fmt(it, 4) } ?: "infinite"), HamSwrFields.MISMATCH_LOSS to fmt(r.mismatchLossDb, 4),
            HamSwrFields.STATUS to "succeeded", HamSwrFields.ERROR to ""
        )
    } catch (e: Exception) { failure(HamSwrFields.outputs, HamSwrFields.RESULT, HamSwrFields.STATUS, HamSwrFields.ERROR, "SWR calculation failed", e) }
    fun result(request: ExecutionRequest, values: Map<String, String>, invocation: InvocationContext?) = HamMethodSupport.complete(request, ID, ref, ID, "SWR", HamSwrFields.STATUS, HamSwrFields.ERROR, values, invocation)
}

object HamLinkFields {
    const val RESULT = "ham_link_result"
    const val FSPL = "ham_link_fspl_db"
    const val OPTICAL_HORIZON = "ham_link_optical_horizon_km"
    const val RADIO_HORIZON = "ham_link_radio_horizon_km"
    const val FRESNEL = "ham_link_fresnel_midpoint_m"
    const val EIRP = "ham_link_eirp_dbm"
    const val RECEIVED = "ham_link_received_power_dbm"
    const val STATUS = "ham_link_status"
    const val ERROR = "ham_link_error"
    val outputs = listOf(RESULT, FSPL, OPTICAL_HORIZON, RADIO_HORIZON, FRESNEL, EIRP, RECEIVED, STATUS, ERROR)
}

object As100HamLinkMethod : As100Method {
    const val ID = "ham.link.calculate"
    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "RF link calculation")
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID), methodType = MethodObjectType.Calculation, name = "RF link / Fresnel", version = HAM_VERSION,
        description = "Calculate FSPL, horizon, Fresnel radius and optional EIRP.", outputs = HamLinkFields.outputs,
        graphOutputs = listOf(ID), parameters = mapOf("category" to "Amateur radio", "status" to "Development")
    )
    override val contract = MethodContract(
        method = ref,
        producedKnowledgeTypes = listOf(KnowledgeObjectType.Observation),
        producedFields = descriptor.outputs,
        producedGraphOutputs = descriptor.graphOutputs
    )
    override fun request(action: String, context: Map<String, String>, signals: List<Signal>, inputs: List<ArchitectureRef>) = HamMethodSupport.request(ref, action, context, signals, inputs)
    override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?) = result(request, calculate(request.context), InvocationContext.from(request.context))
    fun calculate(settings: Map<String, String>): Map<String, String> = try {
        val r = HamRadioCalculations.link(
            settings.hamValue("frequency_mhz")?.toDoubleOrNull() ?: 145.0,
            settings.hamValue("distance_km")?.toDoubleOrNull() ?: 25.0,
            settings.hamValue("tx_height_m")?.toDoubleOrNull() ?: 10.0,
            settings.hamValue("rx_height_m")?.toDoubleOrNull() ?: 10.0,
            settings.hamValue("tx_power_w")?.toDoubleOrNull() ?: 0.0,
            settings.hamValue("antenna_gain_dbi")?.toDoubleOrNull() ?: 0.0,
            settings.hamValue("feedline_loss_db")?.toDoubleOrNull() ?: 0.0
        )
        linkedMapOf(
            HamLinkFields.RESULT to "FSPL ${fmt(r.fsplDb, 2)} dB · Fresnel midpoint ${fmt(r.fresnelMidpointM, 1)} m",
            HamLinkFields.FSPL to fmt(r.fsplDb, 4), HamLinkFields.OPTICAL_HORIZON to fmt(r.opticalHorizonKm, 3), HamLinkFields.RADIO_HORIZON to fmt(r.radioHorizonKm, 3),
            HamLinkFields.FRESNEL to fmt(r.fresnelMidpointM, 3), HamLinkFields.EIRP to fmt(r.eirpDbm, 3), HamLinkFields.RECEIVED to fmt(r.freeSpaceReceivedPowerDbm, 3),
            HamLinkFields.STATUS to "succeeded", HamLinkFields.ERROR to ""
        )
    } catch (e: Exception) { failure(HamLinkFields.outputs, HamLinkFields.RESULT, HamLinkFields.STATUS, HamLinkFields.ERROR, "RF link calculation failed", e) }
    fun result(request: ExecutionRequest, values: Map<String, String>, invocation: InvocationContext?) = HamMethodSupport.complete(request, ID, ref, ID, "RfLink", HamLinkFields.STATUS, HamLinkFields.ERROR, values, invocation)
}

private fun failure(outputs: List<String>, resultField: String, statusField: String, errorField: String, message: String, e: Exception): Map<String, String> =
    outputs.associateWith { "" }.toMutableMap().apply {
        this[resultField] = message
        this[statusField] = "failed"
        this[errorField] = e.message ?: message
    }
