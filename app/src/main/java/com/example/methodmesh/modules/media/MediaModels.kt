package com.example.methodmesh.modules.media

import org.json.JSONObject
import java.security.MessageDigest
import java.util.Locale

object MediaFields {
    const val VALUE = "media_value"
    const val STATUS = "media_status"
    const val OPERATION = "media_operation"
    const val COUNT = "media_count"
    const val WORK_ID = "media_work_id"
    const val TITLE = "media_title"
    const val CREATOR = "media_creator"
    const val TYPE = "media_type"
    const val YEAR = "media_year"
    const val PROVIDER = "media_provider"
    const val REGION = "media_region"
    const val ACCESS_TYPE = "media_access_type"
    const val INCLUDED = "media_included"
    const val ADDON_REQUIRED = "media_addon_required"
    const val PRICE = "media_price"
    const val CURRENCY = "media_currency"
    const val GENRES = "media_genres"
    const val LANGUAGE = "media_language"
    const val RUNTIME_MINUTES = "media_runtime_minutes"
    const val EXTERNAL_ID = "media_external_id"
    const val EXTERNAL_URI = "media_external_uri"
    const val CLASS = "media_class"
    const val FAVOURITE = "media_favourite"
    const val WATCHLIST = "media_watchlist"
    const val WATCHED = "media_watched"
    const val TAGS = "media_tags"
    const val NOTES = "media_notes"
    const val OBSERVED_TEXT = "media_observed_text"
    const val CAPTURE_METHOD = "media_capture_method"
    const val CAPTURED_AT = "media_captured_at"
    const val ALBUM = "media_album"
    const val RELEASE_DATE = "media_release_date"
    const val SONG_LINK = "media_song_link"
    const val DISCOVERY_PROVIDER = "media_discovery_provider"
    const val MATCH_STATUS = "media_match_status"
    const val SAMPLE_SECONDS = "media_sample_seconds"
    const val NETWORK_USED = "media_network_used"
    const val RESULTS_JSON = "media_results_json"
    const val ERROR = "media_error"

    val outputs = listOf(
        VALUE, STATUS, OPERATION, COUNT, WORK_ID, TITLE, CREATOR, TYPE, YEAR, PROVIDER, REGION,
        ACCESS_TYPE, INCLUDED, ADDON_REQUIRED, PRICE, CURRENCY, GENRES, LANGUAGE,
        RUNTIME_MINUTES, EXTERNAL_ID, EXTERNAL_URI, CLASS, FAVOURITE, WATCHLIST, WATCHED,
        TAGS, NOTES, OBSERVED_TEXT, CAPTURE_METHOD, CAPTURED_AT, ALBUM, RELEASE_DATE,
        SONG_LINK, DISCOVERY_PROVIDER, MATCH_STATUS, SAMPLE_SECONDS, NETWORK_USED, RESULTS_JSON, ERROR
    )
}

enum class MediaAccessType(val wire: String) {
    SubscriptionIncluded("subscription_included"), FreeWithAds("free_with_ads"), Rent("rent"), Buy("buy"),
    AddonSubscription("addon_subscription"), Free("free"), Unavailable("unavailable"), Unknown("unknown");

    companion object {
        fun normalise(raw: String?): MediaAccessType {
            val v = raw.orEmpty().trim().lowercase(Locale.ROOT).replace('-', '_').replace(' ', '_')
            return when (v) {
                "included", "subscription", "subscription_included", "flatrate", "stream" -> SubscriptionIncluded
                "free_with_ads", "ads", "ad_supported" -> FreeWithAds
                "rent", "rental" -> Rent
                "buy", "purchase" -> Buy
                "addon", "add_on", "addon_subscription", "channel" -> AddonSubscription
                "free" -> Free
                "unavailable", "none" -> Unavailable
                else -> Unknown
            }
        }
    }
}

data class MediaCatalogueItem(
    val workId: String, val title: String, val mediaType: String, val year: Int?, val creator: String,
    val genres: String, val language: String, val runtimeMinutes: Int?, val provider: String, val region: String,
    val accessType: MediaAccessType, val addonRequired: Boolean, val price: Double?, val currency: String,
    val externalId: String, val externalUri: String, val availableFrom: String, val availableUntil: String,
    val lastVerified: String
) {
    val includedWithoutExtraPayment: Boolean get() = accessType == MediaAccessType.SubscriptionIncluded || accessType == MediaAccessType.Free
    fun toJson() = JSONObject().apply {
        put("work_id", workId); put("title", title); put("media_type", mediaType); put("year", year ?: JSONObject.NULL)
        put("creator", creator); put("genres", genres); put("language", language); put("runtime_minutes", runtimeMinutes ?: JSONObject.NULL)
        put("provider", provider); put("region", region); put("access_type", accessType.wire); put("included", includedWithoutExtraPayment)
        put("addon_required", addonRequired); put("price", price ?: JSONObject.NULL); put("currency", currency)
        put("external_id", externalId); put("external_uri", externalUri); put("available_from", availableFrom)
        put("available_until", availableUntil); put("last_verified", lastVerified)
    }
}

data class MediaPersonalItem(
    val workId: String, val title: String, val mediaType: String, val creator: String, val year: Int?,
    val favourite: Boolean, val watchlist: Boolean, val watched: Boolean, val className: String,
    val tags: String, val notes: String, val updatedAt: String,
    val provider: String = "", val region: String = "", val accessType: String = "", val included: Boolean = false
) {
    fun toJson() = JSONObject().apply {
        put("work_id", workId); put("title", title); put("media_type", mediaType); put("creator", creator)
        put("year", year ?: JSONObject.NULL); put("favourite", favourite); put("watchlist", watchlist); put("watched", watched)
        put("class", className); put("tags", tags); put("notes", notes); put("updated_at", updatedAt)
        put("provider", provider); put("region", region); put("access_type", accessType); put("included", included)
    }
}

data class MediaSearchSpec(
    val workId: String = "", val query: String = "", val mediaType: String = "any", val providers: Set<String> = emptySet(),
    val includedOnly: Boolean = true, val allowFreeWithAds: Boolean = false, val excludeAddons: Boolean = true,
    val yearFrom: Int? = null, val yearTo: Int? = null, val maxRuntimeMinutes: Int? = null,
    val genre: String = "", val language: String = "", val limit: Int = 50
) {
    companion object {
        fun from(values: Map<String, String>) = MediaSearchSpec(
            workId = values["work_id"].orEmpty().trim(), query = values["query"].orEmpty().trim(),
            mediaType = values["media_type"].orEmpty().ifBlank { "any" }.lowercase(Locale.ROOT),
            providers = values["providers"].orEmpty().split(',', ';', '|').map { it.trim() }.filter { it.isNotBlank() }.toSet(),
            includedOnly = values["included_only"]?.toBooleanStrictOrNull() ?: true,
            allowFreeWithAds = values["allow_free_with_ads"]?.toBooleanStrictOrNull() ?: false,
            excludeAddons = values["exclude_addons"]?.toBooleanStrictOrNull() ?: true,
            yearFrom = values["year_from"]?.toIntOrNull(), yearTo = values["year_to"]?.toIntOrNull(),
            maxRuntimeMinutes = values["max_runtime_minutes"]?.toIntOrNull(), genre = values["genre"].orEmpty().trim(),
            language = values["language"].orEmpty().trim(), limit = values["limit"]?.toIntOrNull()?.coerceIn(1, 200) ?: 50
        )
    }
}

object MediaIds {
    fun stableWorkId(title: String, year: Int?, externalId: String = ""): String {
        val raw = externalId.ifBlank { "$title|${year ?: ""}" }.trim().lowercase(Locale.ROOT)
        return "work-" + MessageDigest.getInstance("SHA-256").digest(raw.toByteArray()).take(10).joinToString("") { "%02x".format(it) }
    }
}
