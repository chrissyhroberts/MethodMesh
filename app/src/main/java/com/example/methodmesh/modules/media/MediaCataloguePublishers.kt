package com.example.methodmesh.modules.media

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneOffset
import java.util.Locale

/**
 * Publisher adapters deliberately use service credentials, never end-user media accounts.
 * TMDB availability is powered by JustWatch and requires visible JustWatch attribution.
 */
object MediaPublisherCredentials {
    private const val PREFS = "methodmesh_media_private"
    private const val KEY_TMDB = "tmdb_api_key"
    private const val KEY_WATCHMODE = "watchmode_api_key"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun tmdbKey(context: Context): String = prefs(context).getString(KEY_TMDB, "").orEmpty().trim()
    fun watchmodeKey(context: Context): String = prefs(context).getString(KEY_WATCHMODE, "").orEmpty().trim()
    fun saveTmdbKey(context: Context, value: String) = prefs(context).edit().putString(KEY_TMDB, value.trim()).apply()
    fun saveWatchmodeKey(context: Context, value: String) = prefs(context).edit().putString(KEY_WATCHMODE, value.trim()).apply()
    fun clearTmdbKey(context: Context) = prefs(context).edit().remove(KEY_TMDB).apply()
    fun clearWatchmodeKey(context: Context) = prefs(context).edit().remove(KEY_WATCHMODE).apply()
}

object MediaPublisherUsage {
    private const val PREFS = "methodmesh_media_private"
    private const val WATCHMODE_MONTHLY_FREE = 2500
    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun cycleKey(): String = "watchmode_calls_" + YearMonth.now(ZoneOffset.UTC).toString()
    fun recordWatchmodeCall(context: Context) {
        val p = prefs(context); val key = cycleKey(); p.edit().putInt(key, p.getInt(key, 0) + 1).apply()
    }
    fun watchmodeCallsThisMonth(context: Context): Int = prefs(context).getInt(cycleKey(), 0)
    fun watchmodeEstimatedRemaining(context: Context): Int = (WATCHMODE_MONTHLY_FREE - watchmodeCallsThisMonth(context)).coerceAtLeast(0)
    fun watchmodeMonthlyAllowance(): Int = WATCHMODE_MONTHLY_FREE
}

data class MediaEnrichmentResult(
    val workId: String,
    val status: String,
    val fromCache: Boolean,
    val genres: String = "",
    val language: String = "",
    val runtimeMinutes: Int? = null,
    val message: String = ""
)

data class MediaPublisherService(val id: String, val name: String, val source: String)
data class MediaPublisherSyncSummary(
    val source: String,
    val services: Int,
    val inserted: Int,
    val total: Int,
    val verifiedAt: String,
    val publisherResults: Int = inserted,
    val pagesFetched: Int = 0
)

object MediaCataloguePublishers {
    const val SOURCE_TMDB = "tmdb_justwatch"
    const val SOURCE_WATCHMODE = "watchmode"

    val popularGbServices = listOf(
        "Netflix", "Prime Video", "Disney+", "AppleTV+", "BBC iPlayer",
        "ITV Player", "All 4", "My5", "Now TV", "Paramount Plus", "Discovery+",
        "Crunchyroll Premium", "MUBI", "Britbox UK", "BFI Player", "Sky Go"
    )

    fun tmdbServices(apiKey: String, region: String = "GB"): List<MediaPublisherService> {
        require(apiKey.isNotBlank()) { "TMDB API key is not configured." }
        val movie = tmdbGet("watch/providers/movie", apiKey, mapOf("watch_region" to region, "language" to "en-GB"))
        val tv = tmdbGet("watch/providers/tv", apiKey, mapOf("watch_region" to region, "language" to "en-GB"))
        val seen = linkedMapOf<String, MediaPublisherService>()
        listOf(movie, tv).forEach { root ->
            val arr = root.optJSONArray("results") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val name = o.optString("provider_name").trim()
                if (name.isNotBlank()) seen[name.lowercase(Locale.ROOT)] = MediaPublisherService(o.optInt("provider_id").toString(), name, SOURCE_TMDB)
            }
        }
        return seen.values.sortedBy { it.name.lowercase(Locale.ROOT) }
    }

    fun syncTmdb(
        context: Context,
        apiKey: String,
        selectedServices: Set<String>,
        region: String = "GB",
        includeMovies: Boolean = true,
        includeSeries: Boolean = true,
        includeAds: Boolean = false,
        pagesPerService: Int = 3
    ): MediaPublisherSyncSummary {
        require(selectedServices.isNotEmpty()) { "Select at least one TV service." }
        val services = tmdbServices(apiKey, region).associateBy { it.name.lowercase(Locale.ROOT) }
        val repo = MediaCatalogueRepository(context)
        val now = Instant.now().toString()
        var inserted = 0
        selectedServices.forEach { requested ->
            val service = services[requested.lowercase(Locale.ROOT)] ?: return@forEach
            val rows = linkedMapOf<String, MediaCatalogueItem>()
            if (includeMovies) {
                fetchTmdbDiscover(apiKey, service, region, "movie", "flatrate", pagesPerService, now).forEach { rows[it.workId + "|" + it.accessType.wire] = it }
                if (includeAds) fetchTmdbDiscover(apiKey, service, region, "movie", "ads", pagesPerService, now).forEach { rows[it.workId + "|" + it.accessType.wire] = it }
            }
            if (includeSeries) {
                fetchTmdbDiscover(apiKey, service, region, "tv", "flatrate", pagesPerService, now).forEach { rows[it.workId + "|" + it.accessType.wire] = it }
                if (includeAds) fetchTmdbDiscover(apiKey, service, region, "tv", "ads", pagesPerService, now).forEach { rows[it.workId + "|" + it.accessType.wire] = it }
            }
            val summary = repo.replacePublisherItems(service.name, region, rows.values.toList(), "tmdb-justwatch:$now")
            inserted += summary.inserted
        }
        return MediaPublisherSyncSummary("TMDB · JustWatch", selectedServices.size, inserted, repo.countCatalogue(), now, inserted, 0)
    }

    fun watchmodeServices(apiKey: String, region: String = "GB"): List<MediaPublisherService> = watchmodeServices(null, apiKey, region)

    fun watchmodeServices(context: Context?, apiKey: String, region: String = "GB"): List<MediaPublisherService> {
        require(apiKey.isNotBlank()) { "Watchmode API key is not configured." }
        val root = watchmodeGet("sources", apiKey, mapOf("regions" to region, "types" to "sub,free"), context)
        val arr = when (root) {
            is JSONArray -> root
            is JSONObject -> root.optJSONArray("sources") ?: JSONArray()
            else -> JSONArray()
        }
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val name = o.optString("name").trim()
                if (name.isNotBlank()) add(MediaPublisherService(o.optInt("id").toString(), name, SOURCE_WATCHMODE))
            }
        }.distinctBy { it.name.lowercase(Locale.ROOT) }.sortedBy { it.name.lowercase(Locale.ROOT) }
    }

    fun syncWatchmode(
        context: Context,
        apiKey: String,
        selectedServices: Set<String>,
        region: String = "GB",
        includeMovies: Boolean = true,
        includeSeries: Boolean = true,
        maxPagesPerService: Int = 0
    ): MediaPublisherSyncSummary {
        require(selectedServices.isNotEmpty()) { "Select at least one TV service." }
        val services = watchmodeServices(context, apiKey, region).associateBy { it.name.lowercase(Locale.ROOT) }
        val repo = MediaCatalogueRepository(context)
        val now = Instant.now().toString()
        var inserted = 0
        var publisherResults = 0
        var pagesFetched = 0
        var resolvedServices = 0
        selectedServices.forEach { requested ->
            val service = services[requested.lowercase(Locale.ROOT)] ?: return@forEach
            resolvedServices++
            val rows = linkedMapOf<String, MediaCatalogueItem>()
            val types = buildList {
                if (includeMovies) { add("movie"); add("short_film") }
                if (includeSeries) { add("tv_series"); add("tv_miniseries"); add("tv_special"); add("tv_movie") }
            }.joinToString(",")
            var page = 1
            var totalPages = 1
            var serviceTotal = 0
            do {
                val root = watchmodeGet("list-titles", apiKey, mapOf(
                    "source_ids" to service.id,
                    "source_types" to "sub,free",
                    "regions" to region,
                    "types" to types,
                    "sort_by" to "popularity_desc",
                    "page" to page.toString(),
                    "limit" to "250"
                ), context) as JSONObject
                pagesFetched++
                if (page == 1) {
                    serviceTotal = root.optInt("total_results", 0).coerceAtLeast(0)
                    publisherResults += serviceTotal
                    totalPages = root.optInt("total_pages", 1).coerceAtLeast(1)
                    if (maxPagesPerService > 0) totalPages = minOf(totalPages, maxPagesPerService)
                }
                val arr = root.optJSONArray("titles") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val title = o.optString("title").trim(); if (title.isBlank()) continue
                    val year = o.optInt("year").takeIf { it > 0 }
                    val wmId = o.optInt("id")
                    val rawType = o.optString("type")
                    val type = when {
                        rawType == "movie" || rawType == "short_film" || rawType == "tv_movie" -> "movie"
                        rawType.startsWith("tv_") -> "series"
                        else -> rawType.ifBlank { "other" }
                    }
                    val externalId = "watchmode:$wmId"
                    val workId = MediaIds.stableWorkId(title, year, externalId)
                    rows[workId] = MediaCatalogueItem(
                        workId, title, type, year, "", "", "", null, service.name, region,
                        MediaAccessType.SubscriptionIncluded, false, null, "", externalId,
                        "https://api.watchmode.com/v1/title/$wmId/details/", "", "", now
                    )
                }
                page++
            } while (page <= totalPages)
            val summary = repo.replacePublisherItems(service.name, region, rows.values.toList(), "watchmode:$now")
            inserted += summary.inserted
        }
        return MediaPublisherSyncSummary(
            source = "Watchmode",
            services = resolvedServices,
            inserted = inserted,
            total = repo.countCatalogue(),
            verifiedAt = now,
            publisherResults = publisherResults,
            pagesFetched = pagesFetched
        )
    }

    fun enrichWatchmodeTitle(
        context: Context,
        apiKey: String,
        workId: String,
        force: Boolean = false
    ): MediaEnrichmentResult {
        require(apiKey.isNotBlank()) { "Watchmode API key is not configured." }
        val repo = MediaCatalogueRepository(context)
        val item = repo.catalogueItem(workId) ?: return MediaEnrichmentResult(workId, "missing", false, message = "Title is not in the local catalogue.")
        if (!item.externalId.startsWith("watchmode:")) {
            return MediaEnrichmentResult(workId, "unsupported", false, message = "This title was not imported from Watchmode.")
        }
        if (!force && !repo.needsEnrichment(workId)) {
            return MediaEnrichmentResult(workId, "ok", true, item.genres, item.language, item.runtimeMinutes, "Using cached details.")
        }
        val wmId = item.externalId.removePrefix("watchmode:").toIntOrNull()
            ?: return MediaEnrichmentResult(workId, "invalid", false, message = "Watchmode title ID is invalid.")
        return try {
            val root = watchmodeGet("title/$wmId/details", apiKey, emptyMap(), context) as? JSONObject ?: JSONObject()
            val genres = parseNames(root.opt("genre_names") ?: root.opt("genres"))
            val language = listOf("original_language", "language", "original_language_code")
                .asSequence().map { root.optString(it).trim() }.firstOrNull { it.isNotBlank() }.orEmpty()
            val runtime = sequenceOf(root.optInt("runtime_minutes"), root.optInt("runtime"))
                .firstOrNull { it > 0 }
            val creators = parseNames(root.opt("director_names") ?: root.opt("directors"))
            repo.applyEnrichment(
                workId = workId, source = SOURCE_WATCHMODE, genres = genres, language = language,
                runtimeMinutes = runtime, creator = creators, fetchedAt = Instant.now(), validDays = 30
            )
            MediaEnrichmentResult(workId, "ok", false, genres, language, runtime, "Details cached for 30 days.")
        } catch (t: Throwable) {
            repo.markEnrichmentFailure(workId, SOURCE_WATCHMODE, t.message ?: "Watchmode enrichment failed")
            MediaEnrichmentResult(workId, "failed", false, message = t.message ?: "Watchmode enrichment failed")
        }
    }

    private fun parseNames(value: Any?): String {
        val names = mutableListOf<String>()
        when (value) {
            is JSONArray -> for (i in 0 until value.length()) {
                when (val v = value.opt(i)) {
                    is JSONObject -> v.optString("name").trim().takeIf { it.isNotBlank() }?.let(names::add)
                    else -> v?.toString()?.trim()?.takeIf { it.isNotBlank() && it != "null" }?.let(names::add)
                }
            }
            is String -> value.split(',', ';', '|').map { it.trim() }.filter { it.isNotBlank() }.forEach(names::add)
        }
        return names.distinctBy { it.lowercase(Locale.ROOT) }.joinToString(", ")
    }

    private fun fetchTmdbDiscover(
        apiKey: String, service: MediaPublisherService, region: String, kind: String,
        monetization: String, pages: Int, verifiedAt: String
    ): List<MediaCatalogueItem> = buildList {
        val access = if (monetization == "ads") MediaAccessType.FreeWithAds else MediaAccessType.SubscriptionIncluded
        for (page in 1..pages.coerceIn(1, 25)) {
            val root = tmdbGet("discover/$kind", apiKey, mapOf(
                "watch_region" to region, "with_watch_providers" to service.id,
                "with_watch_monetization_types" to monetization, "sort_by" to "popularity.desc",
                "include_adult" to "false", "language" to "en-GB", "page" to page.toString()
            ))
            val arr = root.optJSONArray("results") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val title = if (kind == "movie") o.optString("title") else o.optString("name")
                if (title.isBlank()) continue
                val date = if (kind == "movie") o.optString("release_date") else o.optString("first_air_date")
                val year = date.take(4).toIntOrNull()
                val tmdbId = o.optInt("id")
                val externalId = "tmdb:$kind:$tmdbId"
                val type = if (kind == "movie") "movie" else "series"
                val workId = MediaIds.stableWorkId(title, year, externalId)
                add(MediaCatalogueItem(
                    workId, title, type, year, "", "", o.optString("original_language"), null,
                    service.name, region, access, false, null, "", externalId,
                    "https://www.themoviedb.org/${if (kind == "tv") "tv" else "movie"}/$tmdbId/watch", "", "", verifiedAt
                ))
            }
            if (arr.length() < 20) break
        }
    }

    private fun tmdbGet(path: String, apiKey: String, params: Map<String, String>): JSONObject {
        val all = linkedMapOf("api_key" to apiKey).apply { putAll(params) }
        val body = httpGet("https://api.themoviedb.org/3/$path", all, emptyMap())
        return JSONObject(body)
    }

    private fun watchmodeGet(path: String, apiKey: String, params: Map<String, String>, context: Context? = null): Any {
        val body = try {
            httpGet("https://api.watchmode.com/v1/$path/", params, mapOf("X-API-Key" to apiKey))
        } finally {
            context?.let(MediaPublisherUsage::recordWatchmodeCall)
        }
        val trimmed = body.trim()
        return if (trimmed.startsWith("[")) JSONArray(trimmed) else JSONObject(trimmed)
    }

    private fun httpGet(base: String, params: Map<String, String>, headers: Map<String, String>): String {
        val query = params.filterValues { it.isNotBlank() }.entries.joinToString("&") {
            URLEncoder.encode(it.key, "UTF-8") + "=" + URLEncoder.encode(it.value, "UTF-8")
        }
        val connection = (URL(if (query.isBlank()) base else "$base?$query").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"; connectTimeout = 15_000; readTimeout = 30_000
            setRequestProperty("Accept", "application/json")
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        try {
            val code = connection.responseCode
            val body = (if (code in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) error("Catalogue publisher returned HTTP $code${body.take(180).takeIf { it.isNotBlank() }?.let { ": $it" }.orEmpty()}")
            return body
        } finally { connection.disconnect() }
    }
}
