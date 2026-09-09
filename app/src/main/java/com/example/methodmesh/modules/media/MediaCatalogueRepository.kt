package com.example.methodmesh.modules.media

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.security.MessageDigest
import java.time.Instant
import java.util.Locale

class MediaCatalogueRepository(context: Context) : SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE catalogue (
              row_id INTEGER PRIMARY KEY AUTOINCREMENT,
              work_id TEXT NOT NULL, title TEXT NOT NULL, media_type TEXT NOT NULL, year INTEGER,
              creator TEXT NOT NULL DEFAULT '', genres TEXT NOT NULL DEFAULT '', language TEXT NOT NULL DEFAULT '',
              runtime_minutes INTEGER, provider TEXT NOT NULL DEFAULT '', region TEXT NOT NULL DEFAULT '',
              access_type TEXT NOT NULL DEFAULT 'unknown', addon_required INTEGER NOT NULL DEFAULT 0,
              price REAL, currency TEXT NOT NULL DEFAULT '', external_id TEXT NOT NULL DEFAULT '', external_uri TEXT NOT NULL DEFAULT '',
              available_from TEXT NOT NULL DEFAULT '', available_until TEXT NOT NULL DEFAULT '', last_verified TEXT NOT NULL DEFAULT '',
              source_fingerprint TEXT NOT NULL DEFAULT ''
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX idx_catalogue_title ON catalogue(title COLLATE NOCASE)")
        db.execSQL("CREATE INDEX idx_catalogue_provider ON catalogue(provider COLLATE NOCASE, region COLLATE NOCASE)")
        db.execSQL("CREATE INDEX idx_catalogue_access ON catalogue(access_type, addon_required)")
        createPersonalState(db)
        createEnrichmentCache(db)
    }

    private fun createPersonalState(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS personal_state (
              work_id TEXT PRIMARY KEY,
              media_type TEXT NOT NULL DEFAULT 'other',
              title TEXT NOT NULL,
              creator TEXT NOT NULL DEFAULT '',
              year INTEGER,
              favourite INTEGER NOT NULL DEFAULT 0,
              watchlist INTEGER NOT NULL DEFAULT 0,
              watched INTEGER NOT NULL DEFAULT 0,
              class_name TEXT NOT NULL DEFAULT 'inbox',
              tags TEXT NOT NULL DEFAULT '',
              notes TEXT NOT NULL DEFAULT '',
              observed_text TEXT NOT NULL DEFAULT '',
              capture_method TEXT NOT NULL DEFAULT 'manual',
              external_id TEXT NOT NULL DEFAULT '',
              external_uri TEXT NOT NULL DEFAULT '',
              captured_at TEXT NOT NULL DEFAULT '',
              updated_at TEXT NOT NULL
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_media_state_flags ON personal_state(favourite, watchlist, watched)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_media_state_title ON personal_state(title COLLATE NOCASE)")
    }

    private fun createEnrichmentCache(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS enrichment_cache (
              work_id TEXT PRIMARY KEY,
              source TEXT NOT NULL DEFAULT '',
              status TEXT NOT NULL DEFAULT 'missing',
              fetched_at TEXT NOT NULL DEFAULT '',
              expires_at TEXT NOT NULL DEFAULT '',
              error TEXT NOT NULL DEFAULT '',
              genres TEXT NOT NULL DEFAULT '',
              language TEXT NOT NULL DEFAULT '',
              runtime_minutes INTEGER,
              creator TEXT NOT NULL DEFAULT ''
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_media_enrichment_status ON enrichment_cache(status, expires_at)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) db.execSQL("CREATE INDEX IF NOT EXISTS idx_catalogue_access ON catalogue(access_type, addon_required)")
        if (oldVersion < 3) {
            createPersonalState(db)
            // Best-effort migration from the v0.1 personal_library table if present.
            runCatching {
                db.execSQL("""
                    INSERT OR IGNORE INTO personal_state(
                      work_id,media_type,title,creator,year,class_name,tags,notes,observed_text,capture_method,
                      external_id,external_uri,captured_at,updated_at,favourite,watchlist,watched
                    )
                    SELECT
                      'legacy-' || library_id,media_type,title,creator,year,class_name,tags,notes,observed_text,capture_method,
                      external_id,external_uri,captured_at,captured_at,
                      CASE WHEN class_name='favourite' THEN 1 ELSE 0 END,
                      CASE WHEN class_name='want' THEN 1 ELSE 0 END,
                      CASE WHEN class_name='done' THEN 1 ELSE 0 END
                    FROM personal_library
                """.trimIndent())
            }
        }
        if (oldVersion < 4) createEnrichmentCache(db)
    }

    fun countCatalogue(): Int = readableDatabase.rawQuery("SELECT COUNT(*) FROM catalogue", null).use { if (it.moveToFirst()) it.getInt(0) else 0 }

    private fun reapplyCachedEnrichment(db: SQLiteDatabase) {
        db.execSQL("""
            UPDATE catalogue
            SET genres = CASE WHEN COALESCE((SELECT genres FROM enrichment_cache e WHERE e.work_id=catalogue.work_id AND e.status='ok'),'')!=''
                              THEN (SELECT genres FROM enrichment_cache e WHERE e.work_id=catalogue.work_id AND e.status='ok') ELSE genres END,
                language = CASE WHEN COALESCE((SELECT language FROM enrichment_cache e WHERE e.work_id=catalogue.work_id AND e.status='ok'),'')!=''
                                THEN (SELECT language FROM enrichment_cache e WHERE e.work_id=catalogue.work_id AND e.status='ok') ELSE language END,
                runtime_minutes = COALESCE((SELECT runtime_minutes FROM enrichment_cache e WHERE e.work_id=catalogue.work_id AND e.status='ok'), runtime_minutes),
                creator = CASE WHEN COALESCE((SELECT creator FROM enrichment_cache e WHERE e.work_id=catalogue.work_id AND e.status='ok'),'')!=''
                               THEN (SELECT creator FROM enrichment_cache e WHERE e.work_id=catalogue.work_id AND e.status='ok') ELSE creator END
            WHERE work_id IN (SELECT work_id FROM enrichment_cache WHERE status='ok')
        """.trimIndent())
    }

    fun importJson(input: InputStream, replaceProviderRegion: Boolean): ImportSummary {
        val text = input.bufferedReader().use { it.readText() }
        val root = text.trim()
        val array = when {
            root.startsWith("[") -> JSONArray(root)
            root.startsWith("{") -> JSONObject(root).optJSONArray("items") ?: JSONArray().put(JSONObject(root))
            else -> error("JSON catalogue must be an array, an object, or an object containing items[].")
        }
        val items = buildList {
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                add(itemFromMap(o.keys().asSequence().associateWith { o.opt(it)?.toString().orEmpty() }))
            }
        }
        return importItems(items, replaceProviderRegion, fingerprint(root.toByteArray()))
    }

    fun importCsv(input: InputStream, replaceProviderRegion: Boolean): ImportSummary {
        val reader = BufferedReader(InputStreamReader(input))
        val headerLine = reader.readLine() ?: error("CSV catalogue is empty.")
        val headers = parseCsvLine(headerLine).map { canonicalHeader(it) }
        val rows = mutableListOf<MediaCatalogueItem>()
        val digest = MessageDigest.getInstance("SHA-256").also { it.update(headerLine.toByteArray()) }
        reader.forEachLine { line ->
            digest.update(line.toByteArray())
            val cells = parseCsvLine(line)
            val map = headers.mapIndexed { i, h -> h to cells.getOrElse(i) { "" } }.toMap()
            if (map["title"].orEmpty().isNotBlank()) rows += itemFromMap(map)
        }
        return importItems(rows, replaceProviderRegion, digest.digest().joinToString("") { "%02x".format(it) })
    }

    private fun importItems(items: List<MediaCatalogueItem>, replaceProviderRegion: Boolean, fingerprint: String): ImportSummary {
        if (items.isEmpty()) return ImportSummary(0, 0, countCatalogue(), fingerprint)
        val db = writableDatabase; var deleted = 0; var inserted = 0
        db.beginTransaction()
        try {
            if (replaceProviderRegion) {
                items.map { it.provider.trim().lowercase(Locale.ROOT) to it.region.trim().lowercase(Locale.ROOT) }
                    .distinct().filter { (provider, region) -> provider.isNotBlank() && region.isNotBlank() }
                    .forEach { (provider, region) -> deleted += db.delete("catalogue", "lower(provider)=? AND lower(region)=?", arrayOf(provider, region)) }
            }
            items.forEach { db.insertOrThrow("catalogue", null, it.toContentValues(fingerprint)); inserted++ }
            reapplyCachedEnrichment(db)
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        return ImportSummary(inserted, deleted, countCatalogue(), fingerprint)
    }

    fun replacePublisherItems(provider: String, region: String, items: List<MediaCatalogueItem>, sourceFingerprint: String): ImportSummary {
        val cleanProvider = provider.trim()
        val cleanRegion = region.trim().uppercase(Locale.ROOT)
        require(cleanProvider.isNotBlank()) { "Provider is required." }
        require(cleanRegion.isNotBlank()) { "Region is required." }
        val db = writableDatabase
        var deleted = 0
        var inserted = 0
        db.beginTransaction()
        try {
            deleted = db.delete("catalogue", "lower(provider)=? AND upper(region)=?", arrayOf(cleanProvider.lowercase(Locale.ROOT), cleanRegion))
            items.forEach { item ->
                db.insertOrThrow("catalogue", null, item.toContentValues(sourceFingerprint))
                inserted++
            }
            reapplyCachedEnrichment(db)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return ImportSummary(inserted, deleted, countCatalogue(), sourceFingerprint)
    }

    fun countDistinctWorks(): Int = readableDatabase.rawQuery(
        "SELECT COUNT(DISTINCT work_id) FROM catalogue WHERE work_id!=''",
        emptyArray()
    ).use { c -> if (c.moveToFirst()) c.getInt(0) else 0 }

    fun hasWatchmodeData(): Boolean = readableDatabase.rawQuery(
        "SELECT 1 FROM catalogue WHERE source_fingerprint LIKE 'watchmode:%' LIMIT 1",
        emptyArray()
    ).use { it.moveToFirst() }

    fun catalogueProviders(region: String = ""): List<String> {
        val sql = if (region.isBlank()) {
            "SELECT DISTINCT provider FROM catalogue WHERE provider!='' ORDER BY provider COLLATE NOCASE"
        } else {
            "SELECT DISTINCT provider FROM catalogue WHERE provider!='' AND upper(region)=? ORDER BY provider COLLATE NOCASE"
        }
        val args: Array<String> = if (region.isBlank()) emptyArray() else arrayOf(region.trim().uppercase(Locale.ROOT))
        return readableDatabase.rawQuery(sql, args).use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }
    }

    data class CatalogueFacets(
        val providers: List<String>,
        val genres: List<String>,
        val languages: List<String>,
        val mediaTypes: List<String>,
        val accessTypes: List<String>
    )

    fun catalogueFacets(region: String = ""): CatalogueFacets {
        val cleanRegion = region.trim().uppercase(Locale.ROOT)
        val regionWhere = if (cleanRegion.isBlank()) "" else " WHERE upper(region)=?"
        val args: Array<String> = if (cleanRegion.isBlank()) emptyArray() else arrayOf(cleanRegion)

        fun distinct(column: String): List<String> = readableDatabase.rawQuery(
            "SELECT DISTINCT $column FROM catalogue$regionWhere ORDER BY $column COLLATE NOCASE",
            args
        ).use { c -> buildList { while (c.moveToNext()) c.getString(0)?.trim()?.takeIf { it.isNotBlank() }?.let(::add) } }

        val genres = distinct("genres")
            .flatMap { it.split(Regex("[,;|]")) }
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase(Locale.ROOT) }
            .sortedBy { it.lowercase(Locale.ROOT) }

        return CatalogueFacets(
            providers = distinct("provider"),
            genres = genres,
            languages = distinct("language"),
            mediaTypes = distinct("media_type"),
            accessTypes = distinct("access_type")
        )
    }

    fun browseAlphabetically(spec: MediaSearchSpec, initial: String, limit: Int = 2000): List<MediaCatalogueItem> {
        val cleanInitial = initial.trim().uppercase(Locale.ROOT)
        return searchSql(
            spec.copy(query = ""),
            includeTextQuery = false,
            limit = limit.coerceIn(1, 2000),
            titleInitial = cleanInitial
        )
    }

    fun search(spec: MediaSearchSpec): List<MediaCatalogueItem> {
        val direct = searchSql(spec, includeTextQuery = true, limit = spec.limit)
        if (spec.query.isBlank() || direct.isNotEmpty()) return direct

        // Fuzzy fallback: keep all structural filters (provider, type, entitlement, year, etc.)
        // but rank local candidates in Kotlin so punctuation, word order and minor typos do not
        // turn an otherwise valid downloaded title into "No matches".
        val candidates = searchSql(spec, includeTextQuery = false, limit = 2000)
        if (candidates.isEmpty()) return emptyList()
        val needle = normalizeSearch(spec.query)
        if (needle.isBlank()) return candidates.take(spec.limit)
        val needleTokens = needle.split(' ').filter { it.length > 1 }

        return candidates.mapNotNull { item ->
            val title = normalizeSearch(item.title)
            val creator = normalizeSearch(item.creator)
            val genres = normalizeSearch(item.genres)
            val haystack = listOf(title, creator, genres).filter { it.isNotBlank() }.joinToString(" ")
            val tokenHits = needleTokens.count { token -> haystack.contains(token) }
            val allTokens = needleTokens.isNotEmpty() && tokenHits == needleTokens.size
            val contains = haystack.contains(needle) || title.contains(needle)
            val distance = levenshtein(needle, title)
            val threshold = when {
                needle.length <= 4 -> 1
                needle.length <= 8 -> 2
                needle.length <= 16 -> 3
                else -> 4
            }
            val close = distance <= threshold
            if (!contains && !allTokens && !close && tokenHits == 0) null else {
                val score = when {
                    title == needle -> 0
                    title.startsWith(needle) -> 5
                    contains -> 10
                    allTokens -> 20
                    close -> 30 + distance
                    else -> 50 + (needleTokens.size - tokenHits) * 5
                }
                score to item
            }
        }.sortedWith(compareBy<Pair<Int, MediaCatalogueItem>> { it.first }
            .thenBy { it.second.title.lowercase(Locale.ROOT) })
            .map { it.second }
            .take(spec.limit)
    }

    private fun searchSql(spec: MediaSearchSpec, includeTextQuery: Boolean, limit: Int, titleInitial: String = ""): List<MediaCatalogueItem> {
        val where = mutableListOf<String>(); val args = mutableListOf<String>()
        when {
            titleInitial == "#" -> where += "(upper(substr(ltrim(title),1,1))<'A' OR upper(substr(ltrim(title),1,1))>'Z')"
            titleInitial.length == 1 && titleInitial[0] in 'A'..'Z' -> {
                where += "upper(substr(ltrim(title),1,1))=?"
                args += titleInitial
            }
        }
        if (spec.workId.isNotBlank()) { where += "work_id=?"; args += spec.workId }
        if (includeTextQuery && spec.query.isNotBlank()) {
            val tokens = normalizeSearch(spec.query).split(' ').filter { it.isNotBlank() }
            if (tokens.isNotEmpty()) {
                tokens.forEach { token ->
                    where += "(lower(title) LIKE ? OR lower(creator) LIKE ? OR lower(genres) LIKE ?)"
                    repeat(3) { args += "%$token%" }
                }
            }
        }
        if (spec.mediaType != "any") { where += "lower(media_type)=?"; args += spec.mediaType }
        if (spec.providers.isNotEmpty()) {
            where += spec.providers.joinToString(prefix = "(", postfix = ")", separator = " OR ") { "lower(provider)=?" }
            args += spec.providers.map { it.lowercase(Locale.ROOT) }
        }
        if (spec.includedOnly) {
            val allowed = if (spec.allowFreeWithAds) listOf("subscription_included", "free", "free_with_ads") else listOf("subscription_included", "free")
            where += "access_type IN (${allowed.joinToString { "?" }})"; args += allowed
        }
        if (spec.excludeAddons) where += "addon_required=0 AND access_type!='addon_subscription'"
        spec.yearFrom?.let { where += "year>=?"; args += it.toString() }; spec.yearTo?.let { where += "year<=?"; args += it.toString() }
        spec.maxRuntimeMinutes?.let { where += "runtime_minutes<=?"; args += it.toString() }
        if (spec.genre.isNotBlank()) { where += "lower(genres) LIKE ?"; args += "%${normalizeSearch(spec.genre)}%" }
        if (spec.language.isNotBlank()) { where += "lower(language) LIKE ?"; args += "%${normalizeSearch(spec.language)}%" }
        val sql = buildString {
            append("SELECT work_id,title,media_type,year,creator,genres,language,runtime_minutes,provider,region,access_type,addon_required,price,currency,external_id,external_uri,available_from,available_until,last_verified FROM catalogue")
            if (where.isNotEmpty()) append(" WHERE ").append(where.joinToString(" AND "))
            append(" ORDER BY title COLLATE NOCASE ASC, year DESC LIMIT ?")
        }
        args += limit.coerceIn(1, 2000).toString()
        return readableDatabase.rawQuery(sql, args.toTypedArray()).use { c -> buildList { while (c.moveToNext()) add(catalogueFromCursor(c)) } }
    }

    private fun normalizeSearch(value: String): String = value
        .lowercase(Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")

    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in a.indices) {
            current[0] = i + 1
            for (j in b.indices) {
                val cost = if (a[i] == b[j]) 0 else 1
                current[j + 1] = minOf(current[j] + 1, previous[j + 1] + 1, previous[j] + cost)
            }
            val swap = previous; previous = current; current = swap
        }
        return previous[b.length]
    }

    data class EnrichmentState(
        val workId: String,
        val source: String,
        val status: String,
        val fetchedAt: String,
        val expiresAt: String,
        val error: String
    )

    data class EnrichmentStats(val enriched: Int, val failed: Int, val pendingOrMissing: Int)

    fun enrichmentState(workId: String): EnrichmentState? = readableDatabase.rawQuery(
        "SELECT work_id,source,status,fetched_at,expires_at,error FROM enrichment_cache WHERE work_id=?",
        arrayOf(workId)
    ).use { c ->
        if (!c.moveToFirst()) null else EnrichmentState(c.getString(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4), c.getString(5))
    }

    fun enrichmentStats(): EnrichmentStats {
        val totalWorks = readableDatabase.rawQuery("SELECT COUNT(DISTINCT work_id) FROM catalogue", null).use { if (it.moveToFirst()) it.getInt(0) else 0 }
        var enriched = 0
        var failed = 0
        readableDatabase.rawQuery("SELECT status,COUNT(*) FROM enrichment_cache GROUP BY status", null).use { c ->
            while (c.moveToNext()) {
                when (c.getString(0)) {
                    "ok" -> enriched = c.getInt(1)
                    "failed" -> failed = c.getInt(1)
                }
            }
        }
        return EnrichmentStats(enriched, failed, (totalWorks - enriched - failed).coerceAtLeast(0))
    }

    fun needsEnrichment(workId: String, now: Instant = Instant.now()): Boolean {
        val state = enrichmentState(workId) ?: return true
        val expiry = runCatching { Instant.parse(state.expiresAt) }.getOrNull() ?: return true
        return expiry.isBefore(now) || expiry == now
    }

    fun applyEnrichment(
        workId: String,
        source: String,
        genres: String = "",
        language: String = "",
        runtimeMinutes: Int? = null,
        creator: String = "",
        fetchedAt: Instant = Instant.now(),
        validDays: Long = 30
    ) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val cv = ContentValues().apply {
                if (genres.isNotBlank()) put("genres", genres)
                if (language.isNotBlank()) put("language", language)
                runtimeMinutes?.takeIf { it > 0 }?.let { put("runtime_minutes", it) }
                if (creator.isNotBlank()) put("creator", creator)
            }
            if (cv.size() > 0) db.update("catalogue", cv, "work_id=?", arrayOf(workId))
            db.insertWithOnConflict("enrichment_cache", null, ContentValues().apply {
                put("work_id", workId); put("source", source); put("status", "ok")
                put("fetched_at", fetchedAt.toString()); put("expires_at", fetchedAt.plusSeconds(validDays * 86_400L).toString()); put("error", "")
                put("genres", genres); put("language", language); runtimeMinutes?.takeIf { it > 0 }?.let { put("runtime_minutes", it) }; put("creator", creator)
            }, SQLiteDatabase.CONFLICT_REPLACE)
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun markEnrichmentFailure(
        workId: String,
        source: String,
        message: String,
        fetchedAt: Instant = Instant.now(),
        retryAfterHours: Long = 12
    ) {
        val db = writableDatabase
        val existing = enrichmentState(workId)
        val values = ContentValues().apply {
            put("source", source); put("status", "failed")
            put("fetched_at", fetchedAt.toString()); put("expires_at", fetchedAt.plusSeconds(retryAfterHours * 3_600L).toString())
            put("error", message.take(300))
        }
        if (existing == null) { values.put("work_id", workId); db.insert("enrichment_cache", null, values) }
        else db.update("enrichment_cache", values, "work_id=?", arrayOf(workId))
    }

    fun catalogueItem(workId: String): MediaCatalogueItem? = search(
        MediaSearchSpec(workId = workId, includedOnly = false, excludeAddons = false, limit = 1)
    ).firstOrNull()

    fun setState(
        item: MediaCatalogueItem? = null,
        workId: String,
        title: String,
        mediaType: String = "other",
        creator: String = "",
        year: Int? = null,
        favourite: Boolean? = null,
        watchlist: Boolean? = null,
        watched: Boolean? = null,
        className: String? = null,
        tags: String? = null,
        notes: String? = null,
        observedText: String? = null,
        captureMethod: String? = null,
        externalId: String? = null,
        externalUri: String? = null
    ): MediaPersonalItem {
        val id = workId.ifBlank { MediaIds.stableWorkId(title, year, externalId.orEmpty()) }
        val existing = getState(id)
        val extras = existingExtras(id)
        val now = Instant.now().toString()
        val cv = ContentValues().apply {
            put("work_id", id); put("media_type", item?.mediaType ?: mediaType); put("title", item?.title ?: title)
            put("creator", item?.creator ?: creator); (item?.year ?: year)?.let { put("year", it) }
            put("favourite", if (favourite ?: existing?.favourite ?: false) 1 else 0)
            put("watchlist", if (watchlist ?: existing?.watchlist ?: false) 1 else 0)
            put("watched", if (watched ?: existing?.watched ?: false) 1 else 0)
            put("class_name", className ?: existing?.className ?: "inbox")
            put("tags", tags ?: existing?.tags ?: ""); put("notes", notes ?: existing?.notes ?: "")
            put("observed_text", observedText ?: extras["observed_text"].orEmpty()); put("capture_method", captureMethod ?: extras["capture_method"].orEmpty().ifBlank { "manual" })
            put("external_id", item?.externalId?.takeIf { it.isNotBlank() } ?: externalId?.takeIf { it.isNotBlank() } ?: extras["external_id"].orEmpty())
            put("external_uri", item?.externalUri?.takeIf { it.isNotBlank() } ?: externalUri?.takeIf { it.isNotBlank() } ?: extras["external_uri"].orEmpty())
            put("captured_at", extras["captured_at"].orEmpty().ifBlank { now }); put("updated_at", now)
        }
        writableDatabase.insertWithOnConflict("personal_state", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        return getState(id) ?: error("Could not save media state.")
    }

    fun savePersonal(values: Map<String, String>): String {
        val title = values["title"].orEmpty().trim(); require(title.isNotBlank()) { "Title is required." }
        val year = values["year"]?.toIntOrNull(); val ext = values["external_id"].orEmpty()
        val workId = values["work_id"].orEmpty().ifBlank { MediaIds.stableWorkId(title, year, ext) }
        val className = values["class"].orEmpty().ifBlank { "inbox" }
        setState(
            workId = workId, title = title, mediaType = values["media_type"].orEmpty().ifBlank { "other" }, creator = values["creator"].orEmpty(), year = year,
            favourite = values["favourite"]?.toBooleanStrictOrNull() ?: (className == "favourite"),
            watchlist = values["watchlist"]?.toBooleanStrictOrNull() ?: (className == "want"),
            watched = values["watched"]?.toBooleanStrictOrNull() ?: (className == "done"),
            className = className, tags = values["tags"].orEmpty(), notes = values["notes"].orEmpty(), observedText = values["observed_text"].orEmpty(),
            captureMethod = values["capture_method"].orEmpty().ifBlank { "manual" }, externalId = ext, externalUri = values["external_uri"].orEmpty()
        )
        return workId
    }

    private fun existingExtras(workId: String): Map<String, String> = readableDatabase.rawQuery(
        "SELECT observed_text,capture_method,external_id,external_uri,captured_at FROM personal_state WHERE work_id=?",
        arrayOf(workId)
    ).use { c ->
        if (!c.moveToFirst()) emptyMap() else mapOf(
            "observed_text" to c.getString(0), "capture_method" to c.getString(1), "external_id" to c.getString(2),
            "external_uri" to c.getString(3), "captured_at" to c.getString(4)
        )
    }

    fun getState(workId: String): MediaPersonalItem? = readableDatabase.rawQuery(
        "SELECT work_id,title,media_type,creator,year,favourite,watchlist,watched,class_name,tags,notes,updated_at FROM personal_state WHERE work_id=?",
        arrayOf(workId)
    ).use { c -> if (c.moveToFirst()) personalFromCursor(c) else null }

    fun listPersonal(mode: String, provider: String = "", includedOnly: Boolean = false, limit: Int = 200): List<MediaPersonalItem> {
        val flag = when (mode.lowercase(Locale.ROOT)) { "favourites", "favourite" -> "favourite"; "watchlist", "want" -> "watchlist"; "watched", "done" -> "watched"; else -> "" }
        val where = mutableListOf<String>(); val args = mutableListOf<String>()
        if (flag.isNotBlank()) where += "s.$flag=1"
        if (provider.isNotBlank()) { where += "lower(c.provider)=?"; args += provider.lowercase(Locale.ROOT) }
        if (includedOnly) where += "c.access_type IN ('subscription_included','free') AND c.addon_required=0"
        val sql = buildString {
            append("SELECT s.work_id,s.title,s.media_type,s.creator,s.year,s.favourite,s.watchlist,s.watched,s.class_name,s.tags,s.notes,s.updated_at,")
            append("COALESCE(c.provider,''),COALESCE(c.region,''),COALESCE(c.access_type,''),CASE WHEN c.access_type IN ('subscription_included','free') AND c.addon_required=0 THEN 1 ELSE 0 END ")
            append("FROM personal_state s LEFT JOIN catalogue c ON c.work_id=s.work_id ")
            if (where.isNotEmpty()) append("WHERE ").append(where.joinToString(" AND ")).append(' ')
            append("ORDER BY s.updated_at DESC, s.title COLLATE NOCASE ASC LIMIT ?")
        }
        args += limit.coerceIn(1, 500).toString()
        return readableDatabase.rawQuery(sql, args.toTypedArray()).use { c ->
            val seen = mutableSetOf<String>(); buildList {
                while (c.moveToNext()) {
                    val key = c.getString(0) + "|" + c.getString(12)
                    if (seen.add(key)) add(personalFromCursor(c, withAvailability = true))
                }
            }
        }
    }

    data class ImportSummary(val inserted: Int, val deleted: Int, val total: Int, val fingerprint: String)

    companion object {
        private const val DB_NAME = "methodmesh_media.db"
        private const val DB_VERSION = 4

        private fun canonicalHeader(raw: String): String = raw.trim().lowercase(Locale.ROOT).replace(' ', '_').let { h -> when (h) {
            "type" -> "media_type"; "runtime", "runtime_mins", "runtime_min" -> "runtime_minutes"; "service", "platform" -> "provider";
            "country" -> "region"; "monetization_type", "offer_type" -> "access_type"; "url", "deeplink", "deep_link" -> "external_uri";
            "id", "tmdb_id", "imdb_id" -> "external_id"; else -> h
        } }

        private fun itemFromMap(raw: Map<String, String>): MediaCatalogueItem {
            val m = raw.mapKeys { canonicalHeader(it.key) }; val title = m["title"].orEmpty().trim(); require(title.isNotBlank()) { "Catalogue row has no title." }
            val provider = m["provider"].orEmpty().trim(); val region = m["region"].orEmpty().trim().uppercase(Locale.ROOT); val externalId = m["external_id"].orEmpty().trim(); val year = m["year"]?.toIntOrNull()
            val workId = m["work_id"].orEmpty().ifBlank { MediaIds.stableWorkId(title, year, externalId) }; val access = MediaAccessType.normalise(m["access_type"])
            return MediaCatalogueItem(workId,title,m["media_type"].orEmpty().ifBlank { "other" }.lowercase(Locale.ROOT),year,m["creator"].orEmpty(),m["genres"].orEmpty(),m["language"].orEmpty(),m["runtime_minutes"]?.toIntOrNull(),provider,region,access,m["addon_required"]?.toBooleanStrictOrNull() ?: (access==MediaAccessType.AddonSubscription),m["price"]?.toDoubleOrNull(),m["currency"].orEmpty(),externalId,m["external_uri"].orEmpty(),m["available_from"].orEmpty(),m["available_until"].orEmpty(),m["last_verified"].orEmpty())
        }

        private fun fingerprint(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        private fun MediaCatalogueItem.toContentValues(fingerprint: String) = ContentValues().apply {
            put("work_id",workId);put("title",title);put("media_type",mediaType);year?.let{put("year",it)};put("creator",creator);put("genres",genres);put("language",language);runtimeMinutes?.let{put("runtime_minutes",it)};put("provider",provider);put("region",region);put("access_type",accessType.wire);put("addon_required",if(addonRequired)1 else 0);price?.let{put("price",it)};put("currency",currency);put("external_id",externalId);put("external_uri",externalUri);put("available_from",availableFrom);put("available_until",availableUntil);put("last_verified",lastVerified);put("source_fingerprint",fingerprint)
        }

        private fun catalogueFromCursor(c: android.database.Cursor) = MediaCatalogueItem(c.getString(0),c.getString(1),c.getString(2),if(c.isNull(3))null else c.getInt(3),c.getString(4),c.getString(5),c.getString(6),if(c.isNull(7))null else c.getInt(7),c.getString(8),c.getString(9),MediaAccessType.normalise(c.getString(10)),c.getInt(11)!=0,if(c.isNull(12))null else c.getDouble(12),c.getString(13),c.getString(14),c.getString(15),c.getString(16),c.getString(17),c.getString(18))
        private fun personalFromCursor(c: android.database.Cursor, withAvailability: Boolean = false) = MediaPersonalItem(c.getString(0),c.getString(1),c.getString(2),c.getString(3),if(c.isNull(4))null else c.getInt(4),c.getInt(5)!=0,c.getInt(6)!=0,c.getInt(7)!=0,c.getString(8),c.getString(9),c.getString(10),c.getString(11),if(withAvailability)c.getString(12) else "",if(withAvailability)c.getString(13) else "",if(withAvailability)c.getString(14) else "",withAvailability && c.getInt(15)!=0)

        internal fun parseCsvLine(line: String): List<String> {
            val out=mutableListOf<String>();val sb=StringBuilder();var quoted=false;var i=0
            while(i<line.length){val ch=line[i];when{ch=='"'&&quoted&&i+1<line.length&&line[i+1]=='"'->{sb.append('"');i++};ch=='"'->quoted=!quoted;ch==','&&!quoted->{out+=sb.toString();sb.clear()};else->sb.append(ch)};i++};out+=sb.toString();return out
        }
    }
}
