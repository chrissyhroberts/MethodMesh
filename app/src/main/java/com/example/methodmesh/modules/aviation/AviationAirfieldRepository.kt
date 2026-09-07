package com.example.methodmesh.modules.aviation

import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.time.Instant

class AviationAirfieldRepository(
    private val storageDirectory: File,
    private val sourceUrl: String = DEFAULT_SOURCE_URL,
    private val runwaySourceUrl: String = DEFAULT_RUNWAY_SOURCE_URL,
    private val nowMillis: () -> Long = System::currentTimeMillis
) {
    companion object {
        const val DEFAULT_SOURCE_URL = "https://davidmegginson.github.io/ourairports-data/airports.csv"
        const val DEFAULT_RUNWAY_SOURCE_URL = "https://davidmegginson.github.io/ourairports-data/runways.csv"
        private const val CATALOG_NAME = "airports.csv"
        private const val RUNWAY_CATALOG_NAME = "runways.csv"
        private const val STALE_AFTER_MILLIS = 24L * 60L * 60L * 1000L
    }

    data class Airfield(
        val ident: String,
        val type: String,
        val name: String,
        val latitude: Double,
        val longitude: Double,
        val elevationFt: Double?,
        val isoCountry: String,
        val municipality: String,
        val scheduledService: Boolean,
        val gpsCode: String,
        val iataCode: String,
        val distanceNm: Double,
        val bearingDeg: Double
    )

    data class SearchResult(
        val airfields: List<Airfield>,
        val sourceUrl: String,
        val catalogUpdatedIso: String,
        val refreshed: Boolean,
        val staleCacheFallback: Boolean,
        val warning: String = ""
    )

    data class RunwayEnd(
        val airportIdent: String,
        val ident: String,
        val reciprocalIdent: String,
        val headingDeg: Double,
        val headingSource: String,
        val lengthFt: Double?,
        val widthFt: Double?,
        val surface: String,
        val lighted: Boolean
    )

    data class RunwayResult(
        val runwayEnds: List<RunwayEnd>,
        val sourceUrl: String,
        val catalogUpdatedIso: String,
        val refreshed: Boolean,
        val staleCacheFallback: Boolean,
        val warning: String = ""
    )

    fun searchNearest(
        latitude: Double,
        longitude: Double,
        radiusNm: Double,
        allowedTypes: Set<String>,
        scheduledOnly: Boolean,
        maximumResults: Int,
        refreshPolicy: String
    ): SearchResult {
        require(latitude in -90.0..90.0) { "Latitude must be between -90 and 90." }
        require(longitude in -180.0..180.0) { "Longitude must be between -180 and 180." }
        require(radiusNm > 0.0) { "Search radius must be greater than zero." }
        require(maximumResults in 1..100) { "Maximum results must be between 1 and 100." }
        require(allowedTypes.isNotEmpty()) { "Select at least one airfield type." }

        storageDirectory.mkdirs()
        val catalog = File(storageDirectory, CATALOG_NAME)
        val refresh = ensureCatalog(catalog, refreshPolicy, sourceUrl)

        val rows = mutableListOf<Airfield>()
        catalog.bufferedReader(Charsets.UTF_8).use { reader ->
            val headerLine = reader.readLine() ?: error("Airfield catalogue is empty.")
            val header = AviationCsv.parseLine(headerLine)
            val indexes = header.withIndex().associate { it.value to it.index }
            fun idx(name: String): Int = indexes[name] ?: error("OurAirports catalogue is missing '$name'.")

            val identIndex = idx("ident")
            val typeIndex = idx("type")
            val nameIndex = idx("name")
            val latitudeIndex = idx("latitude_deg")
            val longitudeIndex = idx("longitude_deg")
            val elevationIndex = idx("elevation_ft")
            val countryIndex = idx("iso_country")
            val municipalityIndex = idx("municipality")
            val scheduledIndex = idx("scheduled_service")
            val gpsIndex = idx("gps_code")
            val iataIndex = idx("iata_code")

            reader.forEachLine { line ->
                if (line.isBlank()) return@forEachLine
                val values = AviationCsv.parseLine(line)
                if (values.size < header.size) return@forEachLine
                val type = values[typeIndex]
                if (type == "closed") return@forEachLine
                if (type !in allowedTypes) return@forEachLine
                val scheduled = values[scheduledIndex].equals("yes", ignoreCase = true)
                if (scheduledOnly && !scheduled) return@forEachLine
                val airfieldLat = values[latitudeIndex].toDoubleOrNull() ?: return@forEachLine
                val airfieldLon = values[longitudeIndex].toDoubleOrNull() ?: return@forEachLine
                val navigation = AviationCalculations.distanceAndBearing(latitude, longitude, airfieldLat, airfieldLon)
                if (navigation.distanceNm > radiusNm) return@forEachLine

                rows += Airfield(
                    ident = values[identIndex],
                    type = type,
                    name = values[nameIndex],
                    latitude = airfieldLat,
                    longitude = airfieldLon,
                    elevationFt = values[elevationIndex].toDoubleOrNull(),
                    isoCountry = values[countryIndex],
                    municipality = values[municipalityIndex],
                    scheduledService = scheduled,
                    gpsCode = values[gpsIndex],
                    iataCode = values[iataIndex],
                    distanceNm = navigation.distanceNm,
                    bearingDeg = navigation.initialBearingDeg
                )
            }
        }

        return SearchResult(
            airfields = rows.sortedBy { it.distanceNm }.take(maximumResults),
            sourceUrl = sourceUrl,
            catalogUpdatedIso = Instant.ofEpochMilli(catalog.lastModified()).toString(),
            refreshed = refresh.refreshed,
            staleCacheFallback = refresh.staleCacheFallback,
            warning = refresh.warning
        )
    }

    fun runwaysFor(airportIdent: String, refreshPolicy: String): RunwayResult {
        val ident = airportIdent.trim()
        require(ident.isNotBlank()) { "Airfield identifier is required for runway lookup." }

        storageDirectory.mkdirs()
        val catalog = File(storageDirectory, RUNWAY_CATALOG_NAME)
        val refresh = ensureCatalog(catalog, refreshPolicy, runwaySourceUrl)
        val ends = mutableListOf<RunwayEnd>()

        catalog.bufferedReader(Charsets.UTF_8).use { reader ->
            val headerLine = reader.readLine() ?: error("Runway catalogue is empty.")
            val header = AviationCsv.parseLine(headerLine)
            val indexes = header.withIndex().associate { it.value to it.index }
            fun idx(name: String): Int = indexes[name] ?: error("OurAirports runway catalogue is missing '$name'.")

            val airportIndex = idx("airport_ident")
            val lengthIndex = idx("length_ft")
            val widthIndex = idx("width_ft")
            val surfaceIndex = idx("surface")
            val lightedIndex = idx("lighted")
            val closedIndex = idx("closed")
            val leIdentIndex = idx("le_ident")
            val leHeadingIndex = idx("le_heading_degT")
            val heIdentIndex = idx("he_ident")
            val heHeadingIndex = idx("he_heading_degT")

            reader.forEachLine { line ->
                if (line.isBlank()) return@forEachLine
                val values = AviationCsv.parseLine(line)
                if (values.size < header.size) return@forEachLine
                if (!values[airportIndex].equals(ident, ignoreCase = true)) return@forEachLine
                if (values[closedIndex] == "1") return@forEachLine

                val lengthFt = values[lengthIndex].toDoubleOrNull()
                val widthFt = values[widthIndex].toDoubleOrNull()
                val surface = values[surfaceIndex]
                val lighted = values[lightedIndex] == "1"
                val leIdent = values[leIdentIndex].trim()
                val heIdent = values[heIdentIndex].trim()
                val leTrue = values[leHeadingIndex].toDoubleOrNull()
                val heTrue = values[heHeadingIndex].toDoubleOrNull()

                runwayEnd(ident, leIdent, heIdent, leTrue, lengthFt, widthFt, surface, lighted)?.let(ends::add)
                runwayEnd(ident, heIdent, leIdent, heTrue, lengthFt, widthFt, surface, lighted)?.let(ends::add)
            }
        }

        return RunwayResult(
            runwayEnds = ends.sortedWith(compareBy<RunwayEnd> { runwaySortKey(it.ident) }.thenBy { it.ident }),
            sourceUrl = runwaySourceUrl,
            catalogUpdatedIso = Instant.ofEpochMilli(catalog.lastModified()).toString(),
            refreshed = refresh.refreshed,
            staleCacheFallback = refresh.staleCacheFallback,
            warning = refresh.warning
        )
    }

    fun hasCachedCatalog(): Boolean = File(storageDirectory, CATALOG_NAME).exists()
    fun hasCachedRunwayCatalog(): Boolean = File(storageDirectory, RUNWAY_CATALOG_NAME).exists()

    private fun runwayEnd(
        airportIdent: String,
        endIdent: String,
        reciprocalIdent: String,
        trueHeading: Double?,
        lengthFt: Double?,
        widthFt: Double?,
        surface: String,
        lighted: Boolean
    ): RunwayEnd? {
        if (endIdent.isBlank()) return null
        val runwayHeading = runwayHeadingFromIdent(endIdent)
        val heading = runwayHeading ?: trueHeading ?: return null
        return RunwayEnd(
            airportIdent = airportIdent,
            ident = endIdent,
            reciprocalIdent = reciprocalIdent,
            headingDeg = heading,
            headingSource = if (runwayHeading != null) "runway_designator" else "true_heading_fallback",
            lengthFt = lengthFt,
            widthFt = widthFt,
            surface = surface,
            lighted = lighted
        )
    }

    internal fun runwayHeadingFromIdent(runwayIdent: String): Double? {
        val digits = runwayIdent.trim().takeWhile(Char::isDigit)
        val number = digits.toIntOrNull() ?: return null
        if (number !in 1..36) return null
        return if (number == 36) 360.0 else number * 10.0
    }

    private fun runwaySortKey(value: String): Int =
        value.takeWhile(Char::isDigit).toIntOrNull() ?: Int.MAX_VALUE

    private data class RefreshOutcome(
        val refreshed: Boolean,
        val staleCacheFallback: Boolean,
        val warning: String
    )

    private fun ensureCatalog(catalog: File, policy: String, url: String): RefreshOutcome {
        val exists = catalog.exists() && catalog.length() > 0L
        val isStale = !exists || nowMillis() - catalog.lastModified() > STALE_AFTER_MILLIS
        val shouldRefresh = when (policy) {
            "cache_only" -> false
            "force_refresh" -> true
            else -> isStale
        }

        if (!shouldRefresh) {
            if (!exists) error("No cached aviation catalogue is available. Choose refresh-if-stale or force refresh while online.")
            return RefreshOutcome(false, false, "")
        }

        return try {
            download(catalog, url)
            RefreshOutcome(true, false, "")
        } catch (error: Exception) {
            if (exists) {
                RefreshOutcome(
                    refreshed = false,
                    staleCacheFallback = true,
                    warning = "Catalogue refresh failed; using cached OurAirports data. ${error.message.orEmpty()}".trim()
                )
            } else {
                throw IllegalStateException("Could not download the OurAirports catalogue and no cache is available: ${error.message}", error)
            }
        }
    }

    private fun download(destination: File, url: String) {
        val temporary = File(destination.parentFile, "${destination.name}.download")
        if (temporary.exists()) temporary.delete()
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 60_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", "MethodMesh-Aviation/0.3")
            connection.setRequestProperty("Accept", "text/csv,*/*;q=0.8")
            val code = connection.responseCode
            if (code !in 200..299) error("HTTP $code from aviation data source")
            connection.inputStream.use { input ->
                temporary.outputStream().buffered().use { output -> input.copyTo(output) }
            }
            if (temporary.length() < 1000L) error("Downloaded aviation catalogue is unexpectedly small.")
            if (destination.exists() && !destination.delete()) error("Could not replace cached aviation catalogue.")
            if (!temporary.renameTo(destination)) {
                temporary.copyTo(destination, overwrite = true)
                temporary.delete()
            }
            destination.setLastModified(nowMillis())
        } finally {
            connection.disconnect()
            if (temporary.exists() && temporary.length() == 0L) temporary.delete()
        }
    }
}
