package com.example.methodmesh.modules.media

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.workflow.ui.CanonicalCommittedResultActions
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private fun copy(context: Context, label: String, value: String) {
    if (value.isBlank()) return
    context.getSystemService(ClipboardManager::class.java)
        .setPrimaryClip(ClipData.newPlainText(label, value))
    Toast.makeText(context, "Copied $label", Toast.LENGTH_SHORT).show()
}


private fun openWeb(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure { Toast.makeText(context, "Could not open link", Toast.LENGTH_SHORT).show() }
}

@Composable
private fun MediaWebLink(label: String, url: String, context: Context) {
    Text(
        label,
        modifier = Modifier.fillMaxWidth().clickable { openWeb(context, url) }.padding(vertical = 5.dp),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun WatchmodeAttribution(context: Context) {
    Text(
        "Streaming data powered by Watchmode.com",
        modifier = Modifier.fillMaxWidth().clickable { openWeb(context, "https://www.watchmode.com/") }.padding(vertical = 6.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun MediaHero(
    eyebrow: String,
    title: String,
    subtitle: String,
    accent: String = ""
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            eyebrow.uppercase(Locale.ROOT),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (accent.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(accent, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String = "") {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (subtitle.isNotBlank()) {
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MediaDataDropdown(
    label: String,
    value: String,
    options: List<String>,
    allLabel: String,
    onSelected: (String) -> Unit,
    display: (String) -> String = { it }
) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { if (options.isNotEmpty()) expanded = true },
            modifier = Modifier.fillMaxWidth(),
            enabled = options.isNotEmpty()
        ) {
            Text(
                if (value.isBlank() || value == "any") "$label · $allLabel" else "$label · ${display(value)}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(allLabel) },
                onClick = { onSelected(""); expanded = false }
            )
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(display(option)) },
                    onClick = { onSelected(option); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun MediaToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }.padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.fillMaxWidth(0.82f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun MediaCheckboxRow(
    title: String,
    checked: Boolean,
    subtitle: String = "",
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MediaResultLine(
    title: String,
    meta: String,
    selected: Boolean,
    badge: String = "",
    onClick: () -> Unit
) {
    val bg = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = bg,
        tonalElevation = if (selected) 2.dp else 0.dp
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier.size(42.dp).background(
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    CircleShape
                ),
                contentAlignment = Alignment.Center
            ) {
                Text(if (selected) "✓" else "•", color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(Modifier.fillMaxWidth(0.78f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (badge.isNotBlank()) {
                Surface(shape = RoundedCornerShape(999.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Text(badge, Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun InlineSuccess(
    eyebrow: String,
    title: String,
    subtitle: String,
    onCopy: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {}
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(eyebrow.uppercase(Locale.ROOT), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(
            title,
            modifier = if (onCopy != null) Modifier.fillMaxWidth().clickable(onClick = onCopy) else Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        actions()
    }
}

object MediaCatalogueSearchCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100MediaCatalogueSearchMethod.ID
    override val title = "Browse media"
    override val description = "Browse the local catalogue A–Z; use search when looking for something specific."

    @Composable
    override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
        val androidContext = LocalContext.current
        val scope = rememberCoroutineScope()
        val initial = context.action.settings
        var catalogueMode by rememberSaveable { mutableStateOf(if (initial["query"].orEmpty().isNotBlank()) "search" else "browse") }
        var browseLetter by rememberSaveable { mutableStateOf("A") }
        var query by rememberSaveable { mutableStateOf(initial["query"].orEmpty()) }
        var providers by rememberSaveable { mutableStateOf(initial["providers"].orEmpty()) }
        var providerQuery by rememberSaveable { mutableStateOf("") }
        var mediaType by rememberSaveable { mutableStateOf(initial["media_type"].orEmpty().ifBlank { "any" }) }
        var includedOnly by rememberSaveable { mutableStateOf(initial["included_only"]?.toBooleanStrictOrNull() ?: true) }
        var allowAds by rememberSaveable { mutableStateOf(initial["allow_free_with_ads"]?.toBooleanStrictOrNull() ?: false) }
        var excludeAddons by rememberSaveable { mutableStateOf(initial["exclude_addons"]?.toBooleanStrictOrNull() ?: true) }
        var genre by rememberSaveable { mutableStateOf(initial["genre"].orEmpty()) }
        var language by rememberSaveable { mutableStateOf(initial["language"].orEmpty()) }
        var maxRuntime by rememberSaveable { mutableStateOf(initial["max_runtime_minutes"].orEmpty()) }
        var results by remember { mutableStateOf<List<MediaCatalogueItem>>(emptyList()) }
        var selectedId by rememberSaveable { mutableStateOf("") }
        var searched by rememberSaveable { mutableStateOf(false) }
        var error by rememberSaveable { mutableStateOf("") }
        var enrichmentBusyId by remember { mutableStateOf("") }
        var enrichmentMessage by remember { mutableStateOf("") }
        var enrichmentRevision by remember { mutableStateOf(0) }
        var committedResult by remember { mutableStateOf<ExecutionResult?>(null) }

        val frozenResult = committedResult
        if (frozenResult != null && !context.submitsImmediately) {
            val fields = OutputFormatter.fields(frozenResult, includeProvenance = false)
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                MediaHero(
                    "Committed",
                    fields[MediaFields.TITLE]?.toString().orEmpty().ifBlank { "Media selection" },
                    "Result frozen for this capability run."
                )
                CanonicalCommittedResultActions(
                    result = frozenResult,
                    label = "media selection",
                    onDone = { onConfirmed(frozenResult) },
                    onEdit = { committedResult = null }
                )
            }
            return
        }

        fun settings() = initial + mapOf(
            "query" to query, "providers" to providers, "media_type" to mediaType,
            "included_only" to includedOnly.toString(), "allow_free_with_ads" to allowAds.toString(),
            "exclude_addons" to excludeAddons.toString(), "genre" to genre, "language" to language,
            "max_runtime_minutes" to maxRuntime, "limit" to "100"
        )

        LaunchedEffect(query, providers, mediaType, includedOnly, allowAds, excludeAddons, genre, language, maxRuntime) {
            context.onSettingsChanged(settings())
        }

        fun runSearch() {
            error = ""
            searched = true
            results = runCatching { MediaCatalogueRepository(androidContext).search(MediaSearchSpec.from(settings())) }
                .onFailure { error = it.message ?: "Search failed." }
                .getOrDefault(emptyList())
            if (selectedId !in results.map { it.workId }) selectedId = ""
        }

        fun runBrowse() {
            error = ""
            searched = true
            val spec = MediaSearchSpec.from(settings() + ("query" to ""))
            results = runCatching { MediaCatalogueRepository(androidContext).browseAlphabetically(spec, browseLetter) }
                .onFailure { error = it.message ?: "Browse failed." }
                .getOrDefault(emptyList())
            if (selectedId !in results.map { it.workId }) selectedId = ""
        }

        LaunchedEffect(catalogueMode, browseLetter, providers, mediaType, includedOnly, allowAds, excludeAddons, genre, language, maxRuntime, enrichmentRevision) {
            if (catalogueMode == "browse") runBrowse()
        }

        fun enrichIfUseful(item: MediaCatalogueItem) {
            if (!item.externalId.startsWith("watchmode:") || enrichmentBusyId == item.workId) return
            val key = MediaPublisherCredentials.watchmodeKey(androidContext)
            if (key.isBlank()) return
            val repo = MediaCatalogueRepository(androidContext)
            if (!repo.needsEnrichment(item.workId)) return
            enrichmentBusyId = item.workId
            enrichmentMessage = "Fetching full details for ${item.title}…"
            scope.launch {
                val enriched = withContext(Dispatchers.IO) {
                    MediaCataloguePublishers.enrichWatchmodeTitle(androidContext, key, item.workId)
                }
                enrichmentBusyId = ""
                enrichmentMessage = if (enriched.status == "ok") {
                    if (enriched.fromCache) "Using cached details" else "Details cached · genres, language and runtime updated where available"
                } else enriched.message
                enrichmentRevision++
                if (catalogueMode == "search") runSearch()
            }
        }

        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val repo = remember { MediaCatalogueRepository(androidContext) }
            val favouritesCount = remember(enrichmentRevision) { repo.listPersonal("favourites", limit = 500).size }
            val watchlistCount = remember(enrichmentRevision) { repo.listPersonal("watchlist", limit = 500).size }
            val catalogueCount = remember(enrichmentRevision) { repo.countDistinctWorks() }
            val selectedProviders = providers.split(',', ';', '|').map { it.trim() }.filter { it.isNotBlank() }.toSet()
            val facets = remember(enrichmentRevision) { repo.catalogueFacets() }
            val downloadedProviders = facets.providers
            val allProviders = downloadedProviders.ifEmpty { MediaCataloguePublishers.popularGbServices }.distinct().sorted()
            val visibleProviders = allProviders.filter { providerQuery.isBlank() || it.contains(providerQuery, ignoreCase = true) }.take(18)
            val groupedResults = results.groupBy { it.workId }.values.map { rows ->
                rows.first() to rows.map { it.provider }.filter { it.isNotBlank() }.distinct().sorted()
            }

            MediaHero(
                eyebrow = "TV & film",
                title = "Browse what you can watch",
                subtitle = "Start with the catalogue, not a search box. Browse A–Z across your selected services; search when you already know what you want.",
                accent = if (includedOnly) "Included with what you already pay for" else "All availability layers"
            )

            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column { Text(catalogueCount.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text("catalogue titles", style = MaterialTheme.typography.labelMedium) }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(favouritesCount.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text("favourites", style = MaterialTheme.typography.labelMedium) }
                    Column(horizontalAlignment = Alignment.End) { Text(watchlistCount.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text("watchlist", style = MaterialTheme.typography.labelMedium) }
                }
            }

            val detailsStats = remember(enrichmentRevision) { repo.enrichmentStats() }
            val watchmodeCalls = remember(enrichmentRevision) { MediaPublisherUsage.watchmodeCallsThisMonth(androidContext) }
            val watchmodeRemaining = remember(enrichmentRevision) { MediaPublisherUsage.watchmodeEstimatedRemaining(androidContext) }
            if (repo.hasWatchmodeData()) {
                Text(
                    "${detailsStats.enriched} titles enriched · $watchmodeCalls Watchmode calls tracked this month · ≈$watchmodeRemaining remaining",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(catalogueMode == "browse", { catalogueMode = "browse" }, { Text("Browse A–Z") })
                FilterChip(catalogueMode == "search", { catalogueMode = "search" }, { Text("Search") })
            }

            if (catalogueMode == "browse") {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    (listOf("#") + ('A'..'Z').map { it.toString() }).forEach { letter ->
                        FilterChip(browseLetter == letter, { browseLetter = letter }, { Text(letter) })
                    }
                }
            } else {
                OutlinedTextField(
                    query,
                    { query = it },
                    label = { Text("Search catalogue") },
                    placeholder = { Text("Title, creator, genre…") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Services & filters", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        FilterChip(selectedProviders.isEmpty(), { providers = "" }, { Text("All services") })
                        visibleProviders.forEach { service ->
                            FilterChip(service in selectedProviders, {
                                val next = selectedProviders.toMutableSet().apply {
                                    if (service in this) remove(service) else add(service)
                                }
                                providers = next.sorted().joinToString(",")
                            }, { Text(service, maxLines = 1) })
                        }
                    }
                    if (allProviders.size > 8) {
                        OutlinedTextField(providerQuery, { providerQuery = it }, label = { Text("Find service") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(includedOnly, { includedOnly = !includedOnly }, { Text("Included") })
                        FilterChip(excludeAddons, { excludeAddons = !excludeAddons }, { Text("No add-ons") })
                        FilterChip(allowAds, { allowAds = !allowAds }, { Text("Ads okay") })
                    }
                    MediaDataDropdown(
                        label = "Type",
                        value = mediaType,
                        options = facets.mediaTypes,
                        allLabel = "All types",
                        onSelected = { mediaType = it.ifBlank { "any" } },
                        display = { it.replace('_', ' ').replaceFirstChar { c -> c.uppercase() } }
                    )
                    if (facets.genres.isNotEmpty()) MediaDataDropdown("Genre", genre, facets.genres, "All genres", { genre = it })
                    if (facets.languages.isNotEmpty()) MediaDataDropdown(
                        "Language", language, facets.languages, "All languages", { language = it },
                        display = { code -> code.uppercase(Locale.ROOT) }
                    )
                    OutlinedTextField(maxRuntime, { maxRuntime = it.filter(Char::isDigit) }, label = { Text("Maximum runtime") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                }
            }

            if (catalogueMode == "search") {
                Button(onClick = ::runSearch, enabled = query.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Find title") }
            }

            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            if (searched && error.isBlank()) {
                Text(
                    when {
                        groupedResults.isEmpty() -> if (catalogueMode == "browse") "Nothing under $browseLetter" else "No matches"
                        catalogueMode == "browse" -> "$browseLetter · ${groupedResults.size} titles"
                        else -> "${groupedResults.size} titles"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                if (groupedResults.isEmpty()) Text(
                    if (catalogueMode == "browse") "Try another letter or widen the service and availability filters."
                    else "Try a shorter title or widen the filters.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            groupedResults.take(80).forEach { (item, itemProviders) ->
                val selected = item.workId == selectedId
                val rowsForWork = results.filter { it.workId == item.workId }
                val includedSomewhere = rowsForWork.any { it.accessType == MediaAccessType.SubscriptionIncluded || it.accessType == MediaAccessType.Free }
                val serviceLabel = when {
                    itemProviders.isEmpty() -> ""
                    itemProviders.size <= 3 -> itemProviders.joinToString(" · ")
                    else -> itemProviders.take(3).joinToString(" · ") + " +${itemProviders.size - 3}"
                }
                val meta = listOfNotNull(
                    item.year?.toString(),
                    item.mediaType.replace('_', ' ').takeIf { it.isNotBlank() },
                    serviceLabel.takeIf { it.isNotBlank() },
                    item.runtimeMinutes?.let { "$it min" },
                    item.genres.takeIf { it.isNotBlank() },
                    item.language.takeIf { it.isNotBlank() }?.uppercase(Locale.ROOT)
                ).joinToString(" · ")
                MediaResultLine(
                    title = item.title,
                    meta = meta,
                    selected = selected,
                    badge = if (includedSomewhere) "Included" else "",
                    onClick = {
                        selectedId = if (selected) "" else item.workId
                        if (!selected) enrichIfUseful(item)
                    }
                )
                if (selected) {
                    val enrichmentState = repo.enrichmentState(item.workId)
                    if (itemProviders.isNotEmpty()) {
                        Text("Available on ${itemProviders.joinToString(", ")}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    }
                    Text(
                        when {
                            enrichmentBusyId == item.workId -> "Fetching full Watchmode details…"
                            enrichmentState?.status == "ok" -> "Full details cached locally for 30 days"
                            item.externalId.startsWith("watchmode:") -> "Catalogue listing · full details are fetched once when needed"
                            else -> "Local catalogue metadata"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (enrichmentMessage.isNotBlank() && (enrichmentBusyId == item.workId || enrichmentState?.status == "ok")) {
                        Text(enrichmentMessage, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = { copy(androidContext, "media title", item.title) }) { Text("Copy") }
                        OutlinedButton(onClick = {
                            MediaCatalogueRepository(androidContext).setState(
                                workId = item.workId, title = item.title, mediaType = item.mediaType,
                                creator = item.creator, year = item.year, favourite = true,
                                externalUri = item.externalUri
                            )
                            Toast.makeText(androidContext, "Added to favourites", Toast.LENGTH_SHORT).show()
                            enrichmentRevision++
                            enrichIfUseful(item)
                        }) { Text("☆ Favourite") }
                    }
                    OutlinedButton(onClick = {
                        MediaCatalogueRepository(androidContext).setState(
                            workId = item.workId, title = item.title, mediaType = item.mediaType,
                            creator = item.creator, year = item.year, watchlist = true,
                            externalUri = item.externalUri
                        )
                        Toast.makeText(androidContext, "Added to watchlist", Toast.LENGTH_SHORT).show()
                        enrichmentRevision++
                        enrichIfUseful(item)
                    }, modifier = Modifier.fillMaxWidth()) { Text("+ Watchlist") }
                    Button(onClick = {
                        val selectedSettings = settings() + ("work_id" to item.workId)
                        val req = As100MediaCatalogueSearchMethod.request(
                            action = context.action.canonicalId,
                            context = context.request.invocationContext.asMap(context.action.canonicalId) + selectedSettings,
                            signals = emptyList(), inputs = emptyList()
                        )
                        val result = As100MediaCatalogueSearchMethod.executeWithAndroidContext(req, androidContext, selectedSettings)
                        if (context.submitsImmediately) onConfirmed(result) else committedResult = result
                    }, modifier = Modifier.fillMaxWidth()) { Text("Use ${item.title}") }
                }
            }
            if (groupedResults.size > 80) {
                Text(
                    if (catalogueMode == "browse") "Showing the first 80 titles under $browseLetter. Refine services, type or genre to narrow the shelf."
                    else "Showing the first 80 matches. Add a little more detail to narrow the result.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (repo.hasWatchmodeData()) {
                HorizontalDivider()
                WatchmodeAttribution(androidContext)
            }
        }
    }
}

object MediaCatalogueImportCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100MediaCatalogueImportMethod.ID
    override val title = "Catalogue sources"
    override val description = "Refresh TV and film availability from maintained publishers; manual file import remains available as a fallback."

    @Composable
    override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
        val androidContext = LocalContext.current
        val scope = rememberCoroutineScope()
        var source by rememberSaveable { mutableStateOf(MediaCataloguePublishers.SOURCE_WATCHMODE) }
        var region by rememberSaveable { mutableStateOf("GB") }
        var serviceSearch by rememberSaveable { mutableStateOf("") }
        var selectedServicesWire by rememberSaveable { mutableStateOf("Netflix,Amazon Prime Video,Disney Plus,BBC iPlayer,ITVX,Channel 4") }
        var includeMovies by rememberSaveable { mutableStateOf(true) }
        var includeSeries by rememberSaveable { mutableStateOf(true) }
        var includeAds by rememberSaveable { mutableStateOf(false) }
        var depth by rememberSaveable { mutableStateOf("complete") }
        var keyDraft by rememberSaveable { mutableStateOf("") }
        var keyVisible by rememberSaveable { mutableStateOf(false) }
        var publisherServices by remember { mutableStateOf(MediaCataloguePublishers.popularGbServices) }
        var refreshing by rememberSaveable { mutableStateOf(false) }
        var status by rememberSaveable { mutableStateOf("Ready") }
        var showManual by rememberSaveable { mutableStateOf(false) }
        var replace by rememberSaveable { mutableStateOf(context.action.settings["import_mode"] != "merge") }
        var pendingResult by remember { mutableStateOf<ExecutionResult?>(null) }
        var committedResult by remember { mutableStateOf<ExecutionResult?>(null) }

        val frozenResult = committedResult
        if (frozenResult != null && !context.submitsImmediately) {
            val fields = OutputFormatter.fields(frozenResult, includeProvenance = false)
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                MediaHero(
                    "Committed",
                    "Catalogue import",
                    fields.values.firstOrNull { it?.toString()?.isNotBlank() == true }?.toString().orEmpty().ifBlank { "Catalogue result frozen." }
                )
                CanonicalCommittedResultActions(
                    result = frozenResult,
                    label = "media catalogue import",
                    onDone = { onConfirmed(frozenResult) },
                    onEdit = { committedResult = null }
                )
            }
            return
        }

        val selectedServices = selectedServicesWire.split(',').map { it.trim() }.filter { it.isNotBlank() }.toSet()
        val visibleServices = publisherServices.filter { serviceSearch.isBlank() || it.contains(serviceSearch, ignoreCase = true) }.take(24)
        val configured = if (source == MediaCataloguePublishers.SOURCE_TMDB) MediaPublisherCredentials.tmdbKey(androidContext).isNotBlank() else MediaPublisherCredentials.watchmodeKey(androidContext).isNotBlank()

        fun storedKey(): String = if (source == MediaCataloguePublishers.SOURCE_TMDB) MediaPublisherCredentials.tmdbKey(androidContext) else MediaPublisherCredentials.watchmodeKey(androidContext)
        fun saveKey() {
            if (source == MediaCataloguePublishers.SOURCE_TMDB) MediaPublisherCredentials.saveTmdbKey(androidContext, keyDraft)
            else MediaPublisherCredentials.saveWatchmodeKey(androidContext, keyDraft)
            keyDraft = ""; keyVisible = false; status = "Publisher credential saved locally"
        }
        fun loadServices() {
            val key = storedKey()
            if (key.isBlank()) { keyVisible = true; status = "Add the publisher API key first"; return }
            refreshing = true; status = "Loading services…"
            scope.launch {
                val attempt = runCatching {
                    withContext(Dispatchers.IO) {
                        if (source == MediaCataloguePublishers.SOURCE_TMDB) MediaCataloguePublishers.tmdbServices(key, region).map { it.name }
                        else MediaCataloguePublishers.watchmodeServices(androidContext, key, region).map { it.name }
                    }
                }
                refreshing = false
                attempt.onSuccess { publisherServices = it; status = "${it.size} services available in $region" }
                    .onFailure { status = it.message ?: "Could not load services" }
            }
        }
        fun refreshCatalogue() {
            val key = storedKey()
            if (key.isBlank()) { keyVisible = true; status = "Add the publisher API key first"; return }
            if (selectedServices.isEmpty()) { status = "Select at least one TV service"; return }
            if (!includeMovies && !includeSeries) { status = "Select movies, series, or both"; return }
            refreshing = true; status = "Refreshing ${selectedServices.size} services…"
            scope.launch {
                val attempt = runCatching {
                    withContext(Dispatchers.IO) {
                        if (source == MediaCataloguePublishers.SOURCE_TMDB) {
                            val pages = if (depth == "quick") 1 else 5
                            MediaCataloguePublishers.syncTmdb(androidContext, key, selectedServices, region, includeMovies, includeSeries, includeAds, pages)
                        } else {
                            val maxPages = if (depth == "quick") 2 else 0
                            MediaCataloguePublishers.syncWatchmode(androidContext, key, selectedServices, region, includeMovies, includeSeries, maxPages)
                        }
                    }
                }
                refreshing = false
                attempt.onSuccess { summary ->
                    status = if (summary.source == "Watchmode") {
                        "${summary.inserted} rows stored · ${summary.publisherResults} provider results reported · ${summary.pagesFetched} pages fetched across ${summary.services} services · ${summary.total} local rows"
                    } else {
                        "${summary.inserted} availability rows refreshed across ${summary.services} services · ${summary.total} local rows · ${summary.source}"
                    }
                }
                    .onFailure { status = it.message ?: "Catalogue refresh failed" }
            }
        }

        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri == null) return@rememberLauncherForActivityResult
            status = "Importing file…"
            runCatching {
                val name = uri.lastPathSegment.orEmpty().lowercase(Locale.ROOT)
                val repo = MediaCatalogueRepository(androidContext)
                androidContext.contentResolver.openInputStream(uri)?.use { input ->
                    if (name.endsWith(".csv")) repo.importCsv(input, replace) else repo.importJson(input, replace)
                } ?: error("Could not open selected file.")
            }.onSuccess { summary ->
                val req = As100MediaCatalogueImportMethod.request(
                    action = context.action.canonicalId,
                    context = context.request.invocationContext.asMap(context.action.canonicalId) + context.action.settings,
                    signals = emptyList(), inputs = emptyList()
                )
                pendingResult = As100MediaCatalogueImportMethod.resultForSummary(req, summary)
                status = "${summary.inserted} imported · ${summary.total} catalogue rows"
                if (context.submitsImmediately) pendingResult?.let(onConfirmed)
            }.onFailure { status = "Import failed · ${it.message ?: "invalid catalogue"}" }
        }

        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            MediaHero("Catalogue", "Keep the streaming world locally searchable", "Refresh maintained availability data, then browse MethodMesh offline.", accent = "$region · ${selectedServices.size} services selected")

            SectionTitle("Publisher", "No Netflix, Amazon or Disney account sign-in. These are catalogue-service credentials only.")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(source == MediaCataloguePublishers.SOURCE_TMDB, { source = MediaCataloguePublishers.SOURCE_TMDB; publisherServices = MediaCataloguePublishers.popularGbServices; status = "TMDB availability is powered by JustWatch" }, { Text("TMDB · JustWatch") })
                FilterChip(source == MediaCataloguePublishers.SOURCE_WATCHMODE, { source = MediaCataloguePublishers.SOURCE_WATCHMODE; publisherServices = MediaCataloguePublishers.popularGbServices; status = "Watchmode selected" }, { Text("Watchmode") })
            }
            Text(
                if (source == MediaCataloguePublishers.SOURCE_TMDB) "Free for non-commercial use with attribution. Availability data powered by JustWatch."
                else "Free developer tier: 2,500 requests/month for non-commercial use; attribution required.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (source == MediaCataloguePublishers.SOURCE_WATCHMODE) {
                val used = MediaPublisherUsage.watchmodeCallsThisMonth(androidContext)
                val remaining = MediaPublisherUsage.watchmodeEstimatedRemaining(androidContext)
                Text("MethodMesh has tracked $used Watchmode calls this month · ≈$remaining of 2,500 remaining", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }

            SectionTitle("Country")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("GB", "IE", "US", "CA", "AU").forEach { code -> FilterChip(region == code, { region = code; publisherServices = MediaCataloguePublishers.popularGbServices }, { Text(code) }) }
            }

            Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (configured) "Publisher access configured" else "One-time publisher setup", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(if (configured) "The key stays in private module storage and is never exported to ODK, presets or results." else "Add a free developer API key. This is not a streaming-service login.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { keyVisible = !keyVisible }) { Text(if (keyVisible) "Hide key setup" else if (configured) "Replace key" else "Add key") }
                        OutlinedButton(onClick = ::loadServices, enabled = !refreshing && configured) { Text("Load services") }
                    }
                    if (keyVisible) {
                        if (source == MediaCataloguePublishers.SOURCE_WATCHMODE) {
                            MediaWebLink("Get a free Watchmode API key ↗", "https://api.watchmode.com/requestApiKey", androidContext)
                            MediaWebLink("Watchmode API documentation ↗", "https://api.watchmode.com/docs", androidContext)
                        } else {
                            MediaWebLink("TMDB API setup instructions ↗", "https://developer.themoviedb.org/docs/getting-started", androidContext)
                        }
                        OutlinedTextField(keyDraft, { keyDraft = it }, label = { Text(if (source == MediaCataloguePublishers.SOURCE_TMDB) "TMDB API key" else "Watchmode API key") }, modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation())
                        Button(onClick = ::saveKey, enabled = keyDraft.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Save locally") }
                    }
                }
            }

            HorizontalDivider()
            SectionTitle("Services", "Compact selection from the publisher's live service list.")
            OutlinedTextField(serviceSearch, { serviceSearch = it }, label = { Text("Find service") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Text(if (selectedServices.isEmpty()) "No services selected" else "${selectedServices.size} selected", style = MaterialTheme.typography.bodySmall, color = if (selectedServices.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary)
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                visibleServices.forEach { service ->
                    FilterChip(service in selectedServices, {
                        val next = selectedServices.toMutableSet().apply {
                            if (service in this) remove(service) else add(service)
                        }
                        selectedServicesWire = next.sorted().joinToString(",")
                    }, { Text(service, maxLines = 1) })
                }
            }
            if (serviceSearch.isBlank() && publisherServices.size > visibleServices.size) Text("Search to reveal ${publisherServices.size - visibleServices.size} more providers.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Surface(shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Catalogue scope", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    MediaCheckboxRow("Movies", includeMovies, "Films, TV movies and shorts") { includeMovies = it }
                    MediaCheckboxRow("TV", includeSeries, "Series, miniseries and specials") { includeSeries = it }
                    if (source == MediaCataloguePublishers.SOURCE_TMDB) MediaCheckboxRow("Free with ads", includeAds, "Include ad-supported availability") { includeAds = it }
                    if (source == MediaCataloguePublishers.SOURCE_WATCHMODE) {
                        Text("Complete follows Watchmode's reported total_pages for each selected service. Quick fetches only the first 500 results/service.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(depth == "complete", { depth = "complete" }, { Text("Complete") })
                            FilterChip(depth == "quick", { depth = "quick" }, { Text("Quick sample") })
                        }
                    } else {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(depth != "quick", { depth = "complete" }, { Text("Standard") })
                            FilterChip(depth == "quick", { depth = "quick" }, { Text("Quick") })
                        }
                    }
                }
            }
            Button(onClick = ::refreshCatalogue, enabled = !refreshing && selectedServices.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text(if (refreshing) "Refreshing…" else "Refresh catalogue") }
            Text(status, style = MaterialTheme.typography.bodyLarge, color = if (status.contains("failed", true) || status.contains("HTTP", true)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)

            HorizontalDivider()
            OutlinedButton(onClick = { showManual = !showManual }, modifier = Modifier.fillMaxWidth()) { Text(if (showManual) "Hide manual import" else "Advanced · import CSV / JSON") }
            if (showManual) {
                MediaToggle("Replace matching service + region", "Replace rows for service/region pairs contained in the file.", replace) { replace = it }
                OutlinedButton(onClick = { launcher.launch(arrayOf("text/csv", "application/json", "text/plain", "application/octet-stream")) }, modifier = Modifier.fillMaxWidth()) { Text("Choose catalogue file") }
                pendingResult?.takeIf { !context.submitsImmediately }?.let { result ->
                    Button(onClick = { committedResult = result }, modifier = Modifier.fillMaxWidth()) { Text("Commit file import") }
                }
            }

            if (source == MediaCataloguePublishers.SOURCE_WATCHMODE) {
                WatchmodeAttribution(androidContext)
            } else {
                Text("TMDB availability is powered by JustWatch. This product uses the TMDB API but is not endorsed or certified by TMDB.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SectionTitle("API keys & sources", "Direct links to the services used by this module.")
            MediaWebLink("Watchmode · get API key ↗", "https://api.watchmode.com/requestApiKey", androidContext)
            MediaWebLink("TMDB · API setup ↗", "https://developer.themoviedb.org/docs/getting-started", androidContext)
            MediaWebLink("AudD · API token ↗", "https://docs.audd.io/", androidContext)
        }
    }
}

object MediaCaptureCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100MediaCaptureMethod.ID
    override val title = "Capture media"
    override val description = "Capture a work now; classify it now or leave it in the inbox."

    @Composable
    override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
        val androidContext = LocalContext.current
        val initial = context.action.settings
        var type by rememberSaveable { mutableStateOf(initial["media_type"].orEmpty().ifBlank { "other" }) }
        var title by rememberSaveable { mutableStateOf(initial["title"].orEmpty()) }
        var creator by rememberSaveable { mutableStateOf(initial["creator"].orEmpty()) }
        var year by rememberSaveable { mutableStateOf(initial["year"].orEmpty()) }
        var className by rememberSaveable { mutableStateOf(initial["class"].orEmpty().ifBlank { "inbox" }) }
        var tags by rememberSaveable { mutableStateOf(initial["tags"].orEmpty()) }
        var notes by rememberSaveable { mutableStateOf(initial["notes"].orEmpty()) }
        var observedText by rememberSaveable { mutableStateOf(initial["observed_text"].orEmpty()) }
        var committed by remember { mutableStateOf<ExecutionResult?>(null) }

        fun values() = initial + mapOf(
            "media_type" to type, "title" to title, "creator" to creator, "year" to year,
            "class" to className, "tags" to tags, "notes" to notes, "observed_text" to observedText,
            "capture_method" to initial["capture_method"].orEmpty().ifBlank { "manual" }
        )

        LaunchedEffect(type, title, creator, year, className, tags, notes, observedText) { context.onSettingsChanged(values()) }

        val frozen = committed
        if (frozen != null && !context.submitsImmediately) {
            val fields = OutputFormatter.fields(frozen, includeProvenance = false)
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                MediaHero("Saved", fields[MediaFields.TITLE]?.toString().orEmpty(), "Now part of your MethodMesh media library.")
                val detail = listOf(fields[MediaFields.CREATOR], fields[MediaFields.CLASS]).map { it?.toString().orEmpty() }.filter { it.isNotBlank() }.joinToString(" · ")
                if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.titleMedium)
                Text("Tap the title to copy it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.clickable { copy(androidContext, "media title", fields[MediaFields.TITLE]?.toString().orEmpty()) })
                CanonicalCommittedResultActions(
                    result = frozen,
                    label = "captured media",
                    onDone = { onConfirmed(frozen) },
                    onEdit = { committed = null }
                )
            }
            return
        }

        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            MediaHero("Capture", "Keep the thing you just found", "Book, film, programme, song, album or anything else worth remembering.")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("book", "movie", "series", "music", "album", "other").forEach { v -> FilterChip(type == v, { type = v }, { Text(v.replaceFirstChar { it.uppercase() }) }) }
            }
            OutlinedTextField(title, { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(creator, { creator = it }, label = { Text("Author / artist / creator") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(year, { year = it.filter(Char::isDigit).take(4) }, label = { Text("Year") }, modifier = Modifier.fillMaxWidth(), singleLine = true)

            SectionTitle("Put it somewhere")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("inbox", "want", "current", "done", "favourite").forEach { v -> FilterChip(className == v, { className = v }, { Text(v.replaceFirstChar { it.uppercase() }) }) }
            }

            SectionTitle("Optional detail", "Raw observed/OCR text remains separate from resolved metadata.")
            OutlinedTextField(tags, { tags = it }, label = { Text("Tags") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            OutlinedTextField(observedText, { observedText = it }, label = { Text("Observed / OCR text") }, modifier = Modifier.fillMaxWidth(), minLines = 2)

            Button(onClick = {
                val req = As100MediaCaptureMethod.request(
                    action = context.action.canonicalId,
                    context = context.request.invocationContext.asMap(context.action.canonicalId) + values(), signals = emptyList(), inputs = emptyList()
                )
                val result = As100MediaCaptureMethod.executeWithAndroidContext(req, androidContext, values())
                val fields = OutputFormatter.fields(result, includeProvenance = false)
                if (fields[MediaFields.ERROR]?.toString().orEmpty().isBlank()) {
                    if (context.submitsImmediately) onConfirmed(result) else committed = result
                } else {
                    Toast.makeText(androidContext, fields[MediaFields.ERROR]?.toString().orEmpty(), Toast.LENGTH_LONG).show()
                }
            }, enabled = title.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Save to library") }
        }
    }
}

object MediaLibraryCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100MediaLibraryListMethod.ID
    override val title = "Media library"
    override val description = "Browse MethodMesh favourites, watchlist and watched titles by service and availability."

    @Composable
    override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
        val androidContext = LocalContext.current
        val scope = rememberCoroutineScope()
        var mode by rememberSaveable { mutableStateOf(context.action.settings["mode"].orEmpty().ifBlank { "favourites" }) }
        var provider by rememberSaveable { mutableStateOf(context.action.settings["provider"].orEmpty()) }
        var providerSearch by rememberSaveable { mutableStateOf("") }
        var includedOnly by rememberSaveable { mutableStateOf(context.action.settings["included_only"]?.toBooleanStrictOrNull() ?: false) }
        var items by remember { mutableStateOf<List<MediaPersonalItem>>(emptyList()) }
        var selectedWorkId by rememberSaveable { mutableStateOf("") }
        var enrichmentBusyId by remember { mutableStateOf("") }
        var committedResult by remember { mutableStateOf<ExecutionResult?>(null) }
        var enrichmentRevision by remember { mutableStateOf(0) }

        fun refresh() {
            items = MediaCatalogueRepository(androidContext).listPersonal(mode, provider, includedOnly, 250)
            if (selectedWorkId !in items.map { it.workId }) selectedWorkId = ""
        }

        fun enrichLibraryItem(item: MediaPersonalItem) {
            if (enrichmentBusyId == item.workId) return
            val repo = MediaCatalogueRepository(androidContext)
            val catalogueItem = repo.catalogueItem(item.workId) ?: return
            if (!catalogueItem.externalId.startsWith("watchmode:") || !repo.needsEnrichment(item.workId)) return
            val key = MediaPublisherCredentials.watchmodeKey(androidContext)
            if (key.isBlank()) return
            enrichmentBusyId = item.workId
            scope.launch {
                withContext(Dispatchers.IO) { MediaCataloguePublishers.enrichWatchmodeTitle(androidContext, key, item.workId) }
                enrichmentBusyId = ""
                enrichmentRevision++
                refresh()
            }
        }

        LaunchedEffect(mode, provider, includedOnly) {
            context.onSettingsChanged(mapOf("mode" to mode, "provider" to provider, "included_only" to includedOnly.toString(), "limit" to "250"))
            refresh()
        }


        val frozenResult = committedResult
        if (frozenResult != null && !context.submitsImmediately) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                MediaHero("Committed", "Media shelf", "Result frozen for this capability run.")
                CanonicalCommittedResultActions(
                    result = frozenResult,
                    label = "media library result",
                    onDone = { onConfirmed(frozenResult) },
                    onEdit = { committedResult = null }
                )
            }
            return
        }

        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            val cacheStats = remember(enrichmentRevision) { MediaCatalogueRepository(androidContext).enrichmentStats() }
            MediaHero(
                "Your library",
                when (mode) { "watchlist" -> "What you want to watch"; "watched" -> "What you've watched"; else -> "Your favourites" },
                "Your list belongs to MethodMesh, not to any streaming account.",
                accent = if (provider.isBlank()) "All services" else provider
            )
            Text("${cacheStats.enriched} titles have full cached details", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("favourites", "watchlist", "watched").forEach { v ->
                    FilterChip(mode == v, { mode = v }, { Text(when(v) { "favourites" -> "Favourites"; "watchlist" -> "Watchlist"; else -> "Watched" }) })
                }
            }
            SectionTitle("Service", "Leave All services selected, or search and choose one provider.")
            OutlinedTextField(providerSearch, { providerSearch = it }, label = { Text("Find a service") }, placeholder = { Text("Netflix, NOW, MUBI…") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            FilterChip(provider.isBlank(), { provider = "" }, { Text("All services") })
            val downloadedLibraryProviders = MediaCatalogueRepository(androidContext).catalogueProviders("GB")
            val libraryProviders = downloadedLibraryProviders.ifEmpty { MediaCataloguePublishers.popularGbServices }.distinct().sorted()
                .filter { providerSearch.isBlank() || it.contains(providerSearch, ignoreCase = true) }.take(14)
            libraryProviders.forEach { service -> FilterChip(provider == service, { provider = service }, { Text(service) }) }
            MediaToggle("Available without paying extra", "Only show saved titles currently included in the selected service.", includedOnly) { includedOnly = it }

            HorizontalDivider()
            Text("${items.size} ${if (items.size == 1) "title" else "titles"}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (items.isEmpty()) {
                Text("Nothing here yet. Save something from catalogue search or capture.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            items.take(80).forEach { item ->
                val selected = selectedWorkId == item.workId
                val availability = listOf(item.provider, item.accessType.replace('_', ' '), item.year?.toString().orEmpty()).filter { it.isNotBlank() }.joinToString(" · ")
                val badge = when {
                    item.watched -> "Watched"
                    item.watchlist -> "Watchlist"
                    item.favourite -> "Favourite"
                    else -> ""
                }
                MediaResultLine(item.title, availability, selected, badge) {
                    selectedWorkId = if (selected) "" else item.workId
                    if (!selected) enrichLibraryItem(item)
                }
                if (selected) {
                    val state = MediaCatalogueRepository(androidContext).enrichmentState(item.workId)
                    Text(
                        if (enrichmentBusyId == item.workId) "Fetching full Watchmode details…" else if (state?.status == "ok") "Full details cached locally" else "Catalogue metadata",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            MediaCatalogueRepository(androidContext).setState(workId = item.workId, title = item.title, mediaType = item.mediaType, creator = item.creator, year = item.year, favourite = !item.favourite)
                            refresh()
                        }) { Text(if (item.favourite) "★ Favourite" else "☆ Favourite") }
                        OutlinedButton(onClick = {
                            MediaCatalogueRepository(androidContext).setState(workId = item.workId, title = item.title, mediaType = item.mediaType, creator = item.creator, year = item.year, watchlist = !item.watchlist)
                            refresh()
                        }) { Text(if (item.watchlist) "✓ Watchlist" else "+ Watchlist") }
                    }
                    Button(onClick = {
                        val reqSettings = mapOf("mode" to mode, "provider" to provider, "included_only" to includedOnly.toString())
                        val req = As100MediaLibraryListMethod.request(action = context.action.canonicalId, context = context.request.invocationContext.asMap(context.action.canonicalId) + reqSettings, signals = emptyList(), inputs = emptyList())
                        val result = As100MediaLibraryListMethod.executeWithAndroidContext(req, androidContext, reqSettings)
                        if (context.submitsImmediately) onConfirmed(result) else committedResult = result
                    }, modifier = Modifier.fillMaxWidth()) { Text("Use this shelf") }
                }
            }
        }
    }
}

object MediaLibraryStateCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100MediaLibraryStateMethod.ID
    override val title = "Media state"
    override val description = "Set MethodMesh favourite, watchlist and watched state for a title."

    @Composable
    override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
        val androidContext = LocalContext.current
        val initial = context.action.settings
        var title by rememberSaveable { mutableStateOf(initial["title"].orEmpty()) }
        var creator by rememberSaveable { mutableStateOf(initial["creator"].orEmpty()) }
        var workId by rememberSaveable { mutableStateOf(initial["work_id"].orEmpty()) }
        var favourite by rememberSaveable { mutableStateOf(initial["favourite"]?.toBooleanStrictOrNull() ?: false) }
        var watchlist by rememberSaveable { mutableStateOf(initial["watchlist"]?.toBooleanStrictOrNull() ?: false) }
        var watched by rememberSaveable { mutableStateOf(initial["watched"]?.toBooleanStrictOrNull() ?: false) }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }

        fun values() = initial + mapOf("title" to title, "creator" to creator, "work_id" to workId, "favourite" to favourite.toString(), "watchlist" to watchlist.toString(), "watched" to watched.toString())
        LaunchedEffect(title, creator, workId, favourite, watchlist, watched) { context.onSettingsChanged(values()) }

        val frozen = result
        if (frozen != null && !context.submitsImmediately) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                MediaHero("Updated", title, "Your MethodMesh media state has been saved.")
                Text(listOf(if (favourite) "★ Favourite" else "", if (watchlist) "✓ Watchlist" else "", if (watched) "✓ Watched" else "").filter { it.isNotBlank() }.joinToString("   "), style = MaterialTheme.typography.titleMedium)
                CanonicalCommittedResultActions(
                    result = frozen,
                    label = "media state",
                    onDone = { onConfirmed(frozen) },
                    onEdit = { result = null }
                )
            }
            return
        }

        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            MediaHero("Personal state", "Make it yours", "Favourite it, keep it for later, or mark it watched.")
            OutlinedTextField(title, { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(creator, { creator = it }, label = { Text("Creator") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            HorizontalDivider()
            MediaToggle("Favourite", "A title you particularly like or want to keep prominent.", favourite) { favourite = it }
            MediaToggle("Watchlist", "Something you intend to watch later.", watchlist) { watchlist = it }
            MediaToggle("Watched", "Keep it in history without removing other states.", watched) { watched = it }
            Button(onClick = {
                val req = As100MediaLibraryStateMethod.request(action = context.action.canonicalId, context = context.request.invocationContext.asMap(context.action.canonicalId) + values(), signals = emptyList(), inputs = emptyList())
                val r = As100MediaLibraryStateMethod.executeWithAndroidContext(req, androidContext, values())
                if (context.submitsImmediately) onConfirmed(r) else result = r
            }, enabled = title.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Save") }
        }
    }
}

object MediaAudioIdentifyCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100MediaAudioIdentifyMethod.ID
    override val title = "What's this song?"
    override val description = "Listen briefly through the microphone and identify ambient music. No Spotify or media-service sign-in."

    @Composable
    override fun Render(context: CapabilityScreenContext, onBack: () -> Unit, onConfirmed: (ExecutionResult) -> Unit, onCancel: () -> Unit) {
        val androidContext = LocalContext.current
        val scope = rememberCoroutineScope()
        var hasPermission by remember { mutableStateOf(ContextCompat.checkSelfPermission(androidContext, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
        var tokenDraft by rememberSaveable { mutableStateOf("") }
        var showTokenSetup by rememberSaveable { mutableStateOf(false) }
        var listening by rememberSaveable { mutableStateOf(false) }
        var identifying by rememberSaveable { mutableStateOf(false) }
        var status by rememberSaveable { mutableStateOf("Ready") }
        var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
        var clipPath by rememberSaveable { mutableStateOf("") }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }
        val sampleSeconds = (context.action.settings["sample_seconds"]?.toIntOrNull() ?: 10).coerceIn(5, 20)

        DisposableEffect(Unit) {
            onDispose {
                MediaAudioDiscovery.stopRecorder(recorder)
                clipPath.takeIf { it.isNotBlank() }?.let { java.io.File(it).delete() }
            }
        }

        val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            hasPermission = granted || ContextCompat.checkSelfPermission(androidContext, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            if (!hasPermission) status = "Microphone permission denied"
        }

        fun identifyClip(file: java.io.File) {
            identifying = true
            status = "Matching audio…"
            scope.launch {
                val attempt = runCatching { withContext(Dispatchers.IO) { MediaAudioDiscovery.identify(androidContext, file) } }
                file.delete(); clipPath = ""; identifying = false
                attempt.onSuccess { match ->
                    val req = As100MediaAudioIdentifyMethod.request(action = context.action.canonicalId, context = context.request.invocationContext.asMap(context.action.canonicalId) + mapOf("sample_seconds" to sampleSeconds.toString()), signals = emptyList(), inputs = emptyList())
                    result = As100MediaAudioIdentifyMethod.resultForMatch(req, match, sampleSeconds)
                    status = if (match.matched) "Matched" else "No match — try again closer to the source"
                    if (context.submitsImmediately) result?.let(onConfirmed)
                }.onFailure { status = it.message ?: "Audio identification failed" }
            }
        }

        fun startListen() {
            if (!hasPermission) { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO); return }
            result = null
            val file = MediaAudioDiscovery.newClipFile(androidContext); clipPath = file.absolutePath
            runCatching { MediaAudioDiscovery.startRecorder(file) }.onSuccess { r ->
                recorder = r; listening = true; status = "Listening…"
                scope.launch {
                    delay(sampleSeconds * 1000L)
                    if (listening) {
                        MediaAudioDiscovery.stopRecorder(recorder); recorder = null; listening = false
                        identifyClip(file)
                    }
                }
            }.onFailure { file.delete(); clipPath = ""; status = it.message ?: "Could not start microphone" }
        }

        val currentResult = result
        val currentFields = currentResult?.let { OutputFormatter.fields(it, includeProvenance = false) }.orEmpty()
        val matchedTitle = currentFields[MediaFields.TITLE]?.toString().orEmpty()
        val artist = currentFields[MediaFields.CREATOR]?.toString().orEmpty()
        val album = currentFields[MediaFields.ALBUM]?.toString().orEmpty()
        val copyValue = currentFields[MediaFields.VALUE]?.toString().orEmpty().ifBlank {
            listOf(matchedTitle, artist).filter { it.isNotBlank() }.joinToString(" — ")
        }

        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            MediaHero(
                "Listen",
                "What's this song?",
                "Hold the phone near whatever is playing. No media-account sign-in, ever.",
                accent = if (MediaAudioDiscovery.usingFreeTestService(androidContext)) "AudD free recognition · $sampleSeconds second sample" else "AudD custom service · $sampleSeconds second sample"
            )

            Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                Surface(
                    modifier = Modifier.size(if (listening) 150.dp else 132.dp).clickable(enabled = !identifying) {
                        if (listening) {
                            val file = clipPath.takeIf { it.isNotBlank() }?.let { java.io.File(it) }
                            MediaAudioDiscovery.stopRecorder(recorder); recorder = null; listening = false
                            if (file != null) identifyClip(file)
                        } else startListen()
                    },
                    shape = CircleShape,
                    color = if (listening) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
                    tonalElevation = 6.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(if (listening) "●" else "♪", style = MaterialTheme.typography.displayMedium, color = if (listening) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer)
                            Text(
                                if (listening) "Listening" else if (identifying) "Matching…" else if (matchedTitle.isNotBlank()) "Listen again" else "Tap to listen",
                                fontWeight = FontWeight.Bold,
                                color = if (listening) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
            Text(status, modifier = Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            if (currentResult != null && !context.submitsImmediately) {
                HorizontalDivider()
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        if (matchedTitle.isNotBlank()) "NOW PLAYING" else "NO MATCH",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (matchedTitle.isNotBlank()) {
                        Text(
                            matchedTitle,
                            modifier = Modifier.fillMaxWidth().clickable { copy(androidContext, "identified song", copyValue) }.padding(vertical = 2.dp),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (artist.isNotBlank()) {
                            Text(artist, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                        }
                        if (album.isNotBlank()) {
                            Text("Album · $album", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        OutlinedButton(onClick = {
                            val wid = currentFields[MediaFields.WORK_ID]?.toString().orEmpty()
                            MediaCatalogueRepository(androidContext).setState(workId = wid, title = matchedTitle, mediaType = "music", creator = artist, favourite = true, externalUri = currentFields[MediaFields.SONG_LINK]?.toString().orEmpty())
                            Toast.makeText(androidContext, "Added to favourites", Toast.LENGTH_SHORT).show()
                        }) { Text("☆ Favourite") }
                    } else {
                        Text("Nothing matched this sample. Tap the listen button and try again closer to the source.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    CanonicalCommittedResultActions(
                        result = currentResult,
                        label = "audio identification",
                        onDone = { onConfirmed(currentResult) },
                        onEdit = { result = null; status = "Ready" }
                    )
                }
            }

            HorizontalDivider()
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Audio service", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        if (MediaAudioDiscovery.usingFreeTestService(androidContext)) "AudD free test service is already selected · up to 10 recognitions/day on the public test token."
                        else "Using your private AudD API token.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(onClick = { showTokenSetup = !showTokenSetup }) { Text(if (showTokenSetup) "Hide advanced service setup" else "Advanced · use my own AudD key") }
                }
            }
            if (showTokenSetup) {
                SectionTitle("Optional custom recognition key", "Only needed if you outgrow the free public test allowance. This is a MethodMesh service credential, never a Spotify login.")
                MediaWebLink("Get an AudD API token ↗", "https://docs.audd.io/", androidContext)
                OutlinedTextField(tokenDraft, { tokenDraft = it }, label = { Text("AudD API token") }, modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation())
                OutlinedButton(onClick = { MediaAudioDiscovery.clearToken(androidContext); tokenDraft = ""; showTokenSetup = false; status = "Using free AudD test service" }, modifier = Modifier.fillMaxWidth()) { Text("Use free AudD service") }
                Button(onClick = { MediaAudioDiscovery.saveToken(androidContext, tokenDraft); tokenDraft = ""; showTokenSetup = false; status = "Using custom AudD service" }, enabled = tokenDraft.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Save custom key locally") }
                Text("Never exported to presets, protocols, ODK or result payloads.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
