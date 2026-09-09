package com.example.methodmesh.modules.media

import com.example.methodmesh.modules.MethodMeshModule
import com.example.methodmesh.modules.ModuleDependency
import com.example.methodmesh.modules.RilBinding
import com.example.methodmesh.settings.MethodSetting

object MediaModule : MethodMeshModule {
    override val moduleId = "media"
    override val displayName = "Media"
    override val summary = "Beautiful local media dashboard with maintained streaming-catalogue publishers, no-sign-in favourites/watchlists, ambient song identification and personal capture."
    override val iconKey = "media"

    override fun as100Methods() = listOf(
        As100MediaCatalogueSearchMethod,
        As100MediaCatalogueImportMethod,
        As100MediaLibraryListMethod,
        As100MediaLibraryStateMethod,
        As100MediaCaptureMethod,
        As100MediaAudioIdentifyMethod
    )

    override fun capabilityScreens() = listOf(
        MediaCatalogueSearchCapabilityScreen,
        MediaCatalogueImportCapabilityScreen,
        MediaLibraryCapabilityScreen,
        MediaLibraryStateCapabilityScreen,
        MediaCaptureCapabilityScreen,
        MediaAudioIdentifyCapabilityScreen
    )

    override fun rilBindings() = listOf(
        RilBinding("browse media catalogue", As100MediaCatalogueSearchMethod.ID, "Browse the local media catalogue A–Z"),
        RilBinding("search media catalogue", As100MediaCatalogueSearchMethod.ID, "Search the local media catalogue"),
        RilBinding("find included media", As100MediaCatalogueSearchMethod.ID, "Find titles included without an extra paywall"),
        RilBinding("import media catalogue", As100MediaCatalogueImportMethod.ID, "Import a downloaded streaming catalogue"),
        RilBinding("browse media favourites", As100MediaLibraryListMethod.ID, "Browse MethodMesh-owned favourites by provider and availability"),
        RilBinding("browse media watchlist", As100MediaLibraryListMethod.ID, "Browse MethodMesh-owned watchlist"),
        RilBinding("set media favourite", As100MediaLibraryStateMethod.ID, "Set MethodMesh favourite/watchlist/watched state"),
        RilBinding("capture media", As100MediaCaptureMethod.ID, "Capture a book, film, programme, song, album, podcast or other work"),
        RilBinding("identify song", As100MediaAudioIdentifyMethod.ID, "Identify ambient music from a short microphone sample"),
        RilBinding("what is this song", As100MediaAudioIdentifyMethod.ID, "Identify ambient music without a media-account sign-in")
    )

    override fun dependencies() = listOf(
        ModuleDependency(
            moduleId = "mlkitvision",
            reason = "Optional composition target for book-cover/poster OCR; media capture remains usable without it."
        )
    )

    override fun capabilitySettings() = mapOf(
        As100MediaCatalogueSearchMethod.ID to listOf(
            MethodSetting.TextSetting("query", "Search", defaultValue = ""),
            MethodSetting.ChoiceSetting("media_type", "Type", defaultValue = "any", choices = listOf("any", "movie", "series", "book", "music", "podcast", "other")),
            MethodSetting.TextSetting("providers", "Providers", "Canonical wire setting; native UI exposes searchable multi-select services.", defaultValue = ""),
            MethodSetting.BooleanSetting("included_only", "Included with subscription only", defaultValue = true),
            MethodSetting.BooleanSetting("allow_free_with_ads", "Allow free with ads", defaultValue = false),
            MethodSetting.BooleanSetting("exclude_addons", "Exclude add-on channels", defaultValue = true),
            MethodSetting.TextSetting("year_from", "Year from", defaultValue = ""),
            MethodSetting.TextSetting("year_to", "Year to", defaultValue = ""),
            MethodSetting.TextSetting("max_runtime_minutes", "Maximum runtime (minutes)", defaultValue = ""),
            MethodSetting.TextSetting("genre", "Genre contains", defaultValue = ""),
            MethodSetting.TextSetting("language", "Language contains", defaultValue = ""),
            MethodSetting.TextSetting("limit", "Maximum results", defaultValue = "50")
        ),
        As100MediaCatalogueImportMethod.ID to listOf(
            MethodSetting.ChoiceSetting("import_mode", "Import mode", defaultValue = "replace_provider_region", choices = listOf("replace_provider_region", "merge"))
        ),
        As100MediaLibraryListMethod.ID to listOf(
            MethodSetting.ChoiceSetting("mode", "Shelf", defaultValue = "favourites", choices = listOf("favourites", "watchlist", "watched", "all")),
            MethodSetting.TextSetting("provider", "TV service", "Optional canonical provider filter; native UI exposes searchable provider selection.", defaultValue = ""),
            MethodSetting.BooleanSetting("included_only", "Included without extra payment only", defaultValue = false),
            MethodSetting.TextSetting("limit", "Maximum results", defaultValue = "200")
        ),
        As100MediaLibraryStateMethod.ID to listOf(
            MethodSetting.TextSetting("work_id", "Work ID", defaultValue = ""),
            MethodSetting.TextSetting("title", "Title", defaultValue = ""),
            MethodSetting.TextSetting("creator", "Creator", defaultValue = ""),
            MethodSetting.ChoiceSetting("media_type", "Type", defaultValue = "other", choices = listOf("movie", "series", "book", "music", "album", "podcast", "other")),
            MethodSetting.BooleanSetting("favourite", "Favourite", defaultValue = false),
            MethodSetting.BooleanSetting("watchlist", "Watchlist", defaultValue = false),
            MethodSetting.BooleanSetting("watched", "Watched", defaultValue = false)
        ),
        As100MediaCaptureMethod.ID to listOf(
            MethodSetting.ChoiceSetting("media_type", "Type", defaultValue = "other", choices = listOf("movie", "series", "book", "music", "album", "podcast", "other")),
            MethodSetting.TextSetting("title", "Title", defaultValue = ""),
            MethodSetting.TextSetting("creator", "Creator / artist / author", defaultValue = ""),
            MethodSetting.TextSetting("year", "Year", defaultValue = ""),
            MethodSetting.ChoiceSetting("class", "Class", defaultValue = "inbox", choices = listOf("inbox", "want", "current", "done", "favourite", "reference", "teaching", "ignore")),
            MethodSetting.TextSetting("tags", "Tags", "Comma-separated.", defaultValue = ""),
            MethodSetting.TextSetting("notes", "Notes", defaultValue = ""),
            MethodSetting.TextSetting("observed_text", "Observed text", "Raw OCR/observed text, kept distinct from resolved metadata.", defaultValue = ""),
            MethodSetting.TextSetting("external_id", "External identifier", defaultValue = ""),
            MethodSetting.TextSetting("external_uri", "External URI", defaultValue = "")
        ),
        As100MediaAudioIdentifyMethod.ID to listOf(
            MethodSetting.IntSetting("sample_seconds", "Listening time", defaultValue = 10, minimum = 5, maximum = 20, unit = "s")
        )
    )
}
