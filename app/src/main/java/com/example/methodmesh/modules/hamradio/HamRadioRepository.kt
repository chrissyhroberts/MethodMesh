package com.example.methodmesh.modules.hamradio

import com.example.methodmesh.core.onlinedata.HttpUrlConnectionOnlineHttpClient
import com.example.methodmesh.core.onlinedata.OnlineHttpClient
import com.example.methodmesh.core.onlinedata.OnlineHttpRequest
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.xml.parsers.DocumentBuilderFactory

class HamRadioRepository(
    private val httpClient: OnlineHttpClient = HttpUrlConnectionOnlineHttpClient(userAgent = "MethodMesh HamRadio/0.4"),
    private val clock: Clock = Clock.systemUTC()
) {
    companion object {
        const val PSK_REPORTER_MIN_REQUEST_INTERVAL_SECONDS = 420L
        const val PSK_REPORTER_PROVIDER = "PSK Reporter"
        const val PSK_REPORTER_ENDPOINT = "https://retrieve.pskreporter.info/query"
        val shared: HamRadioRepository by lazy { HamRadioRepository() }

        // Network-boundary gate is shared by every repository instance in this process.
        // This prevents callers from constructing another repository to bypass the floor.
        private val pskGateLock = Any()
        private var pskLastUpstreamAttemptAt: Instant? = null
    }

    data class SpaceWeatherSnapshot(
        val status: String,
        val kp: Double?,
        val f107: Double?,
        val windSpeedKmS: Double?,
        val btNt: Double?,
        val bzNt: Double?,
        val gScale: Int?,
        val rScale: Int?,
        val sScale: Int?,
        val providerTime: String,
        val retrievedAt: Instant,
        val fromCache: Boolean,
        val stale: Boolean,
        val warnings: List<String>,
        val error: String = ""
    )

    data class PskReporterQuery(
        val subject: String,
        val subjectType: String,
        val direction: String,
        val lookbackMinutes: Int,
        val mode: String,
        val minFrequencyMhz: Double?,
        val maxFrequencyMhz: Double?,
        val recordLimit: Int,
        val includeNoLocator: Boolean
    ) {
        fun normalized(): PskReporterQuery = copy(
            subject = subject.trim().uppercase(),
            subjectType = subjectType.trim().lowercase(),
            direction = direction.trim().lowercase(),
            lookbackMinutes = lookbackMinutes.coerceIn(5, 1440),
            mode = mode.trim().uppercase(),
            recordLimit = recordLimit.coerceIn(1, 100)
        )

        fun cacheKey(): String = normalized().let {
            listOf(
                it.subject, it.subjectType, it.direction, it.lookbackMinutes.toString(), it.mode,
                it.minFrequencyMhz?.toString().orEmpty(), it.maxFrequencyMhz?.toString().orEmpty(),
                it.recordLimit.toString(), it.includeNoLocator.toString()
            ).joinToString("|")
        }
    }

    data class PskReceptionReport(
        val senderCallsign: String,
        val senderLocator: String,
        val receiverCallsign: String,
        val receiverLocator: String,
        val frequencyHz: Long?,
        val mode: String,
        val snrDb: Int?,
        val flowStartSeconds: Long?,
        val band: String
    )

    data class PskReporterSnapshot(
        val status: String,
        val query: PskReporterQuery,
        val reports: List<PskReceptionReport>,
        val retrievedAt: Instant,
        val providerTime: String,
        val fromCache: Boolean,
        val cacheAgeSeconds: Long,
        val retryAfterSeconds: Long,
        val requestUrl: String,
        val warning: String = "",
        val error: String = ""
    )

    private data class SpaceCache(val snapshot: SpaceWeatherSnapshot, val fetchedAt: Instant)
    private data class PskCache(val snapshot: PskReporterSnapshot, val fetchedAt: Instant)

    @Volatile private var spaceCache: SpaceCache? = null
    private val pskCache = mutableMapOf<String, PskCache>()

    private val spaceWeatherUrls = listOf(
        "https://services.swpc.noaa.gov/products/noaa-planetary-k-index.json",
        "https://services.swpc.noaa.gov/products/summary/10cm-flux.json",
        "https://services.swpc.noaa.gov/products/summary/solar-wind-speed.json",
        "https://services.swpc.noaa.gov/products/summary/solar-wind-mag-field.json",
        "https://services.swpc.noaa.gov/products/noaa-scales.json"
    )

    fun spaceWeather(refreshMode: String = "cache_preferred"): SpaceWeatherSnapshot {
        val now = Instant.now(clock)
        val existing = spaceCache
        val age = existing?.let { Duration.between(it.fetchedAt, now).seconds.coerceAtLeast(0) }
        if (refreshMode != "fresh_required" && existing != null && age != null && age < 900) {
            return existing.snapshot.copy(retrievedAt = now, fromCache = true, stale = false)
        }

        val warnings = mutableListOf<String>()
        return try {
            val kpBody = get(spaceWeatherUrls[0])
            val fluxBody = get(spaceWeatherUrls[1])
            val speedBody = get(spaceWeatherUrls[2])
            val fieldBody = get(spaceWeatherUrls[3])
            val scalesBody = get(spaceWeatherUrls[4])

            val kp = latestNumeric(kpBody, listOf("kp", "Kp", "estimated_kp"))
            val f107 = latestNumeric(fluxBody, listOf("flux", "Flux", "f107", "10cm_flux", "value"))
            val wind = latestNumeric(speedBody, listOf("wind_speed", "speed", "Speed", "value"))
            val bt = latestNumeric(fieldBody, listOf("bt", "Bt", "total_field", "value"))
            val bz = latestNumeric(fieldBody, listOf("bz", "Bz", "bz_gsm"))
            val scales = parseNoaaScales(scalesBody)
            val providerTime = latestTime(kpBody)
                .ifBlank { latestTime(fluxBody) }
                .ifBlank { latestTime(speedBody) }

            if (kp == null) warnings += "Kp unavailable or unrecognised in provider response."
            if (f107 == null) warnings += "F10.7 unavailable or unrecognised in provider response."
            val snapshot = SpaceWeatherSnapshot(
                status = "succeeded",
                kp = kp,
                f107 = f107,
                windSpeedKmS = wind,
                btNt = bt,
                bzNt = bz,
                gScale = scales.first,
                rScale = scales.second,
                sScale = scales.third,
                providerTime = providerTime,
                retrievedAt = now,
                fromCache = false,
                stale = false,
                warnings = warnings
            )
            spaceCache = SpaceCache(snapshot, now)
            snapshot
        } catch (e: Exception) {
            if (existing != null) {
                val oldAge = Duration.between(existing.fetchedAt, now).seconds.coerceAtLeast(0)
                existing.snapshot.copy(
                    retrievedAt = now,
                    fromCache = true,
                    stale = oldAge >= 7200,
                    warnings = existing.snapshot.warnings + "Live refresh failed; returned cached data: ${e.message.orEmpty()}"
                )
            } else {
                SpaceWeatherSnapshot(
                    status = "failed", kp = null, f107 = null, windSpeedKmS = null, btNt = null, bzNt = null,
                    gScale = null, rScale = null, sScale = null, providerTime = "", retrievedAt = now,
                    fromCache = false, stale = false, warnings = emptyList(), error = e.message ?: "Space-weather request failed."
                )
            }
        }
    }

    /**
     * Query PSK Reporter with a process-wide hard minimum of seven minutes between upstream requests.
     *
     * The gate is reserved before network I/O. Therefore failures also consume the seven-minute slot,
     * and concurrent callers cannot race around the limit. A repeated identical query during the gate
     * receives its cached result. A different uncached query receives status=rate_limited and a retry-after.
     */
    fun pskReporter(queryRaw: PskReporterQuery): PskReporterSnapshot {
        val query = queryRaw.normalized()
        require(query.subject.isNotBlank()) { "PSK Reporter subject is required." }
        require(query.subjectType in setOf("callsign", "grid")) { "subject_type must be callsign or grid." }
        require(query.direction in setOf("sent", "received", "either")) { "direction must be sent, received or either." }
        if (query.minFrequencyMhz != null && query.maxFrequencyMhz != null) {
            require(query.minFrequencyMhz > 0 && query.maxFrequencyMhz > query.minFrequencyMhz) {
                "Maximum frequency must be greater than minimum frequency."
            }
        }

        val now = Instant.now(clock)
        val key = query.cacheKey()
        val cached: PskCache?
        val retryAfter: Long
        synchronized(pskGateLock) {
            cached = pskCache[key]
            val last = pskLastUpstreamAttemptAt
            val elapsed = last?.let { Duration.between(it, now).seconds.coerceAtLeast(0) }
            retryAfter = if (elapsed == null) 0 else (PSK_REPORTER_MIN_REQUEST_INTERVAL_SECONDS - elapsed).coerceAtLeast(0)
            if (retryAfter > 0) {
                if (cached != null) {
                    val cacheAge = Duration.between(cached.fetchedAt, now).seconds.coerceAtLeast(0)
                    return cached.snapshot.copy(
                        retrievedAt = now,
                        fromCache = true,
                        cacheAgeSeconds = cacheAge,
                        retryAfterSeconds = retryAfter,
                        warning = "Seven-minute PSK Reporter request gate active; returned cached result."
                    )
                }
                return PskReporterSnapshot(
                    status = "rate_limited",
                    query = query,
                    reports = emptyList(),
                    retrievedAt = now,
                    providerTime = "",
                    fromCache = false,
                    cacheAgeSeconds = 0,
                    retryAfterSeconds = retryAfter,
                    requestUrl = buildPskUrl(query),
                    warning = "Seven-minute PSK Reporter request gate active.",
                    error = "No matching cached result is available until the upstream request gate opens."
                )
            }
            // Reserve the slot before network I/O so no concurrent request can bypass the hard floor.
            pskLastUpstreamAttemptAt = now
        }

        val requestUrl = buildPskUrl(query)
        return try {
            val response = httpClient.get(
                OnlineHttpRequest(
                    url = requestUrl,
                    headers = mapOf("Accept" to "application/xml, text/xml;q=0.9, */*;q=0.1")
                )
            )
            if (response.statusCode !in 200..299) {
                error("PSK Reporter returned HTTP ${response.statusCode}.")
            }
            val reports = parsePskXml(response.body)
            val providerTime = reports.mapNotNull { it.flowStartSeconds }.maxOrNull()?.let { Instant.ofEpochSecond(it).toString() }.orEmpty()
            val snapshot = PskReporterSnapshot(
                status = "succeeded",
                query = query,
                reports = reports,
                retrievedAt = now,
                providerTime = providerTime,
                fromCache = false,
                cacheAgeSeconds = 0,
                retryAfterSeconds = PSK_REPORTER_MIN_REQUEST_INTERVAL_SECONDS,
                requestUrl = requestUrl
            )
            synchronized(pskGateLock) { pskCache[key] = PskCache(snapshot, now) }
            snapshot
        } catch (e: Exception) {
            val fallback = synchronized(pskGateLock) { pskCache[key] }
            if (fallback != null) {
                val cacheAge = Duration.between(fallback.fetchedAt, now).seconds.coerceAtLeast(0)
                fallback.snapshot.copy(
                    retrievedAt = now,
                    fromCache = true,
                    cacheAgeSeconds = cacheAge,
                    retryAfterSeconds = PSK_REPORTER_MIN_REQUEST_INTERVAL_SECONDS,
                    warning = "Live PSK Reporter request failed; returned cached result.",
                    error = e.message.orEmpty()
                )
            } else {
                PskReporterSnapshot(
                    status = "failed",
                    query = query,
                    reports = emptyList(),
                    retrievedAt = now,
                    providerTime = "",
                    fromCache = false,
                    cacheAgeSeconds = 0,
                    retryAfterSeconds = PSK_REPORTER_MIN_REQUEST_INTERVAL_SECONDS,
                    requestUrl = requestUrl,
                    error = e.message ?: "PSK Reporter request failed."
                )
            }
        }
    }

    fun pskReportsJson(reports: List<PskReceptionReport>): String {
        val arr = JSONArray()
        reports.forEach { report ->
            arr.put(JSONObject().apply {
                put("sender_callsign", report.senderCallsign)
                put("sender_locator", report.senderLocator)
                put("receiver_callsign", report.receiverCallsign)
                put("receiver_locator", report.receiverLocator)
                put("frequency_hz", report.frequencyHz ?: JSONObject.NULL)
                put("frequency_mhz", report.frequencyHz?.div(1_000_000.0) ?: JSONObject.NULL)
                put("band", report.band)
                put("mode", report.mode)
                put("snr_db", report.snrDb ?: JSONObject.NULL)
                put("flow_start_seconds", report.flowStartSeconds ?: JSONObject.NULL)
                put("time_iso", report.flowStartSeconds?.let { Instant.ofEpochSecond(it).toString() } ?: "")
            })
        }
        return arr.toString()
    }

    private fun get(url: String): String {
        val response = httpClient.get(OnlineHttpRequest(url = url, headers = mapOf("Accept" to "application/json")))
        if (response.statusCode !in 200..299) error("Provider returned HTTP ${response.statusCode} for $url")
        return response.body
    }

    private fun buildPskUrl(query: PskReporterQuery): String {
        val q = query.normalized()
        val params = mutableListOf<Pair<String, String>>()
        val key = when (q.direction) {
            "sent" -> "senderCallsign"
            "received" -> "receiverCallsign"
            else -> "callsign"
        }
        params += key to q.subject
        params += "flowStartSeconds" to (-q.lookbackMinutes * 60).toString()
        params += "rptlimit" to q.recordLimit.toString()
        params += "rronly" to "1"
        params += "noactive" to "1"
        if (q.mode.isNotBlank()) params += "mode" to q.mode
        if (q.subjectType == "grid") params += "modify" to "grid"
        if (q.includeNoLocator) params += "nolocator" to "1"
        val low = q.minFrequencyMhz
        val high = q.maxFrequencyMhz
        if (low != null && high != null && low > 0 && high > low) {
            params += "frange" to "${(low * 1_000_000).toLong()}-${(high * 1_000_000).toLong()}"
        }
        return PSK_REPORTER_ENDPOINT + "?" + params.joinToString("&") { (k, v) ->
            "${urlEncode(k)}=${urlEncode(v)}"
        }
    }

    private fun urlEncode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    private fun parsePskXml(xml: String): List<PskReceptionReport> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isExpandEntityReferences = false
        }
        listOf(
            "http://apache.org/xml/features/disallow-doctype-decl" to true,
            "http://xml.org/sax/features/external-general-entities" to false,
            "http://xml.org/sax/features/external-parameter-entities" to false
        ).forEach { (feature, value) -> runCatching { factory.setFeature(feature, value) } }
        val document = factory.newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))
        val nodes = document.getElementsByTagName("receptionReport")
        return buildList {
            for (i in 0 until nodes.length) {
                val attrs = nodes.item(i).attributes ?: continue
                fun attr(vararg names: String): String = names.firstNotNullOfOrNull { name ->
                    attrs.getNamedItem(name)?.nodeValue?.takeIf { it.isNotBlank() }
                }.orEmpty()
                val frequency = attr("frequency").toLongOrNull()
                add(
                    PskReceptionReport(
                        senderCallsign = attr("senderCallsign"),
                        senderLocator = attr("senderLocator"),
                        receiverCallsign = attr("receiverCallsign"),
                        receiverLocator = attr("receiverLocator"),
                        frequencyHz = frequency,
                        mode = attr("mode"),
                        snrDb = attr("sNR", "snr").toIntOrNull(),
                        flowStartSeconds = attr("flowStartSeconds").toLongOrNull(),
                        band = frequency?.let(HamRadioCalculations::bandForFrequencyHz) ?: "unknown"
                    )
                )
            }
        }
    }

    private fun latestNumeric(body: String, preferredKeys: List<String>): Double? {
        val text = body.trim()
        if (text.isBlank()) return null
        return runCatching {
            when {
                text.startsWith("[") -> scanArrayForNumeric(JSONArray(text), preferredKeys)
                text.startsWith("{") -> scanObjectForNumeric(JSONObject(text), preferredKeys)
                else -> null
            }
        }.getOrNull()
    }

    private fun scanArrayForNumeric(array: JSONArray, keys: List<String>): Double? {
        if (array.length() == 0) return null
        val first = array.opt(0)
        if (first is JSONArray && first.length() > 0) {
            val headers = (0 until first.length()).map { first.optString(it) }
            val index = headers.indexOfFirst { header -> keys.any { it.equals(header, ignoreCase = true) } }
            if (index >= 0) {
                for (i in array.length() - 1 downTo 1) {
                    val row = array.optJSONArray(i) ?: continue
                    row.optString(index).toDoubleOrNull()?.let { return it }
                }
            }
        }
        for (i in array.length() - 1 downTo 0) {
            val item = array.opt(i)
            when (item) {
                is JSONObject -> scanObjectForNumeric(item, keys)?.let { return it }
                is JSONArray -> scanArrayForNumeric(item, keys)?.let { return it }
            }
        }
        return null
    }

    private fun scanObjectForNumeric(obj: JSONObject, keys: List<String>): Double? {
        keys.forEach { wanted ->
            obj.keys().asSequence().firstOrNull { it.equals(wanted, ignoreCase = true) }?.let { actual ->
                val raw = obj.opt(actual)
                when (raw) {
                    is Number -> return raw.toDouble()
                    is String -> raw.toDoubleOrNull()?.let { return it }
                }
            }
        }
        val nestedKeys = obj.keys().asSequence().toList().asReversed()
        nestedKeys.forEach { key ->
            when (val value = obj.opt(key)) {
                is JSONObject -> scanObjectForNumeric(value, keys)?.let { return it }
                is JSONArray -> scanArrayForNumeric(value, keys)?.let { return it }
            }
        }
        return null
    }

    private fun latestTime(body: String): String {
        val keys = listOf("time_tag", "TimeStamp", "timestamp", "time", "DateStamp")
        val text = body.trim()
        return runCatching {
            when {
                text.startsWith("[") -> scanArrayForString(JSONArray(text), keys)
                text.startsWith("{") -> scanObjectForString(JSONObject(text), keys)
                else -> ""
            }
        }.getOrDefault("")
    }

    private fun scanArrayForString(array: JSONArray, keys: List<String>): String {
        if (array.length() == 0) return ""
        val first = array.opt(0)
        if (first is JSONArray) {
            val headers = (0 until first.length()).map { first.optString(it) }
            val index = headers.indexOfFirst { header -> keys.any { it.equals(header, ignoreCase = true) } }
            if (index >= 0) {
                for (i in array.length() - 1 downTo 1) {
                    val value = array.optJSONArray(i)?.optString(index).orEmpty()
                    if (value.isNotBlank()) return value
                }
            }
        }
        for (i in array.length() - 1 downTo 0) {
            when (val item = array.opt(i)) {
                is JSONObject -> scanObjectForString(item, keys).takeIf { it.isNotBlank() }?.let { return it }
                is JSONArray -> scanArrayForString(item, keys).takeIf { it.isNotBlank() }?.let { return it }
            }
        }
        return ""
    }

    private fun scanObjectForString(obj: JSONObject, keys: List<String>): String {
        keys.forEach { wanted ->
            obj.keys().asSequence().firstOrNull { it.equals(wanted, ignoreCase = true) }?.let { actual ->
                obj.optString(actual).takeIf { it.isNotBlank() }?.let { return it }
            }
        }
        obj.keys().asSequence().toList().asReversed().forEach { key ->
            when (val value = obj.opt(key)) {
                is JSONObject -> scanObjectForString(value, keys).takeIf { it.isNotBlank() }?.let { return it }
                is JSONArray -> scanArrayForString(value, keys).takeIf { it.isNotBlank() }?.let { return it }
            }
        }
        return ""
    }

    private fun parseNoaaScales(body: String): Triple<Int?, Int?, Int?> {
        return runCatching {
            val obj = JSONObject(body)
            val current = obj.optJSONObject("0") ?: obj.optJSONObject("-1") ?: obj
            fun scale(letter: String): Int? {
                val nested = current.optJSONObject(letter) ?: obj.optJSONObject(letter)
                return when {
                    nested != null -> nested.opt("Scale")?.toString()?.toIntOrNull()
                        ?: nested.opt("scale")?.toString()?.toIntOrNull()
                    else -> current.opt(letter)?.toString()?.filter(Char::isDigit)?.firstOrNull()?.digitToIntOrNull()
                }
            }
            Triple(scale("G"), scale("R"), scale("S"))
        }.getOrElse { Triple(null, null, null) }
    }
}
