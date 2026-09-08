package com.example.methodmesh.modules.referencelibrary

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec

/**
 * Deliberately does not use CapabilityScreenScaffold.
 *
 * Email handoff is an interactive transaction: the user selects human-readable
 * documents, reviews the populated message in a mail app, returns here, and
 * confirms whether they actually sent it. The same card owns the whole flow so
 * dashboard, preset and ODK launches never fall through to a generic result card.
 */
object ReferenceLibraryEmailCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100ReferenceLibraryEmailMethod.ID
    override val title = "Email library documents"
    override val description = "Select library documents, prepare an email, and confirm the handoff when you return."

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        val appContext = LocalContext.current
        val repository = remember { ReferenceLibraryRepository(appContext) }
        fun initial(key: String): String = context.action.settings[key] ?: context.action.settings["input_$key"] ?: ""

        var revision by rememberSaveable { mutableStateOf(0) }
        val documents = remember(revision) { repository.documents().filter(repository::isReadable).sortedBy { it.title.lowercase() } }
        val customShelves = remember(revision) { repository.customShelves() }
        val shelves = remember(customShelves) { BUILT_IN_EMAIL_SHELVES + customShelves }

        var selectedDocumentIdsRaw by rememberSaveable { mutableStateOf(initial("document_ids")) }
        var extraAttachmentUrisRaw by rememberSaveable { mutableStateOf(initial("attachment_uris")) }
        val selectedDocumentIds = remember(selectedDocumentIdsRaw) {
            As100ReferenceLibraryEmailMethod.parseIds(selectedDocumentIdsRaw).toSet()
        }
        val extraAttachmentUris = remember(extraAttachmentUrisRaw) {
            As100ReferenceLibraryEmailMethod.parseUris(extraAttachmentUrisRaw)
        }
        var selectedShelf by rememberSaveable { mutableStateOf("all") }
        var shelfMenuOpen by remember { mutableStateOf(false) }
        var documentMenuOpen by remember { mutableStateOf(false) }
        var optionalRecipientsOpen by rememberSaveable { mutableStateOf(initial("cc").isNotBlank() || initial("bcc").isNotBlank()) }
        var recipient by rememberSaveable { mutableStateOf(initial("recipient")) }
        var cc by rememberSaveable { mutableStateOf(initial("cc")) }
        var bcc by rememberSaveable { mutableStateOf(initial("bcc")) }
        var subject by rememberSaveable { mutableStateOf(initial("subject")) }
        var body by rememberSaveable { mutableStateOf(initial("body")) }
        var chooserTitle by rememberSaveable { mutableStateOf(initial("chooser_title").ifBlank { "Send document copies" }) }
        var pendingDocumentIdsRaw by rememberSaveable { mutableStateOf("") }
        var pendingUrisRaw by rememberSaveable { mutableStateOf("") }
        var pendingMissingRaw by rememberSaveable { mutableStateOf("") }
        val pendingDocumentIds = remember(pendingDocumentIdsRaw) { As100ReferenceLibraryEmailMethod.parseIds(pendingDocumentIdsRaw) }
        val pendingUris = remember(pendingUrisRaw) { As100ReferenceLibraryEmailMethod.parseUris(pendingUrisRaw) }
        val pendingMissing = remember(pendingMissingRaw) { As100ReferenceLibraryEmailMethod.parseIds(pendingMissingRaw) }
        var status by rememberSaveable { mutableStateOf("Choose documents and prepare the message.") }
        var awaitingSendConfirmation by rememberSaveable { mutableStateOf(false) }
        var emailLaunchInProgress by rememberSaveable { mutableStateOf(false) }

        val filteredDocuments = remember(documents, selectedShelf) {
            if (selectedShelf == "all") documents else documents.filter { it.shelf == selectedShelf }
        }
        val selectedDocuments = remember(documents, selectedDocumentIds) {
            documents.filter { it.id in selectedDocumentIds }
        }

        fun documentIdsText(): String = selectedDocumentIds.joinToString(",")
        fun attachmentUrisText(): String = extraAttachmentUris.joinToString("\n")
        fun settingsMap() = mapOf(
            "document_ids" to documentIdsText(),
            "attachment_uris" to attachmentUrisText(),
            "recipient" to recipient,
            "cc" to cc,
            "bcc" to bcc,
            "subject" to subject,
            "body" to body,
            "chooser_title" to chooserTitle
        )

        fun buildResult(
            statusValue: String,
            handoff: String,
            userConfirmedSent: Boolean,
            error: String = ""
        ): ExecutionResult {
            val request = As100ReferenceLibraryEmailMethod.request(
                action = As100ReferenceLibraryEmailMethod.ID,
                context = context.request.invocationContext.asMap(As100ReferenceLibraryEmailMethod.ID) + context.action.settings + settingsMap(),
                signals = emptyList(),
                inputs = emptyList()
            )
            return As100ReferenceLibraryEmailMethod.result(
                request,
                As100ReferenceLibraryEmailMethod.values(
                    status = statusValue,
                    recipient = recipient.trim(),
                    subject = subject,
                    documentIds = pendingDocumentIds,
                    attachmentUris = pendingUris,
                    missingDocumentIds = pendingMissing,
                    handoff = handoff,
                    userConfirmedSent = userConfirmedSent,
                    error = error
                ),
                context.request.invocationContext
            )
        }

        val attachmentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (uris.isNotEmpty()) {
                uris.forEach { uri ->
                    runCatching {
                        appContext.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                }
                extraAttachmentUrisRaw = (extraAttachmentUris + uris.map(Uri::toString)).distinct().joinToString("\n")
                status = "Added ${uris.size} attachment${if (uris.size == 1) "" else "s"}."
            }
        }

        val mailLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            emailLaunchInProgress = false
            awaitingSendConfirmation = true
            status = "Back from the mail app. Confirm below whether you sent the message."
        }

        fun launchEmail() {
            awaitingSendConfirmation = false
            val requestedIds = selectedDocumentIds.toList()
            val resolved = requestedIds.mapNotNull(repository::document).filter(repository::isReadable)
            val resolvedIds = resolved.map { it.id }
            val missing = requestedIds.filterNot { it in resolvedIds }
            val uris = (resolved.map { it.uri } + extraAttachmentUris).distinct()

            pendingDocumentIdsRaw = resolvedIds.joinToString(",")
            pendingMissingRaw = missing.joinToString(",")
            pendingUrisRaw = uris.joinToString("\n")

            val validationError = when {
                recipient.isBlank() -> "Recipient email is required."
                uris.isEmpty() -> "Choose at least one library document or attachment."
                missing.isNotEmpty() -> "One or more selected library documents are no longer readable."
                else -> ""
            }
            if (validationError.isNotBlank()) {
                status = validationError
                return
            }

            val parsedUris = uris.map(Uri::parse)
            val sendIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = if (resolved.isNotEmpty() && resolved.all { it.mimeType == "application/pdf" } && extraAttachmentUris.isEmpty()) {
                    "application/pdf"
                } else {
                    "*/*"
                }
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(parsedUris))
                putExtra(Intent.EXTRA_EMAIL, parseAddresses(recipient))
                if (cc.isNotBlank()) putExtra(Intent.EXTRA_CC, parseAddresses(cc))
                if (bcc.isNotBlank()) putExtra(Intent.EXTRA_BCC, parseAddresses(bcc))
                if (subject.isNotBlank()) putExtra(Intent.EXTRA_SUBJECT, subject)
                if (body.isNotBlank()) putExtra(Intent.EXTRA_TEXT, body)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                parsedUris.firstOrNull()?.let { first ->
                    clipData = ClipData.newUri(appContext.contentResolver, "MethodMesh document", first).also { clip ->
                        parsedUris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
                    }
                }
            }
            runCatching {
                emailLaunchInProgress = true
                status = "Opening mail app…"
                mailLauncher.launch(Intent.createChooser(sendIntent, chooserTitle.ifBlank { "Send document copies" }))
            }.onFailure { error ->
                emailLaunchInProgress = false
                status = error.message ?: "No compatible mail app is available."
            }
        }

        fun finish(userConfirmedSent: Boolean) {
            val execution = buildResult(
                statusValue = if (userConfirmedSent) "user_confirmed_sent" else "user_confirmed_not_sent",
                handoff = "returned_from_mail_app",
                userConfirmedSent = userConfirmedSent,
                error = if (userConfirmedSent) "" else "User indicated that the message was not sent."
            )
            onConfirmed(execution)
        }

        LaunchedEffect(
            selectedDocumentIds,
            extraAttachmentUris,
            recipient,
            cc,
            bcc,
            subject,
            body,
            chooserTitle
        ) {
            context.onSettingsChanged(settingsMap())
        }

        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(
                "DOCUMENT DELIVERY",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(3.dp))
            Text(
                "Send from your library",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "Choose the copies, compose the note, then hand off to your mail app.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(18.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Attachments", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (selectedDocumentIds.isEmpty() && extraAttachmentUris.isEmpty()) {
                                    "Nothing attached yet"
                                } else {
                                    "${selectedDocumentIds.size + extraAttachmentUris.size} item${if (selectedDocumentIds.size + extraAttachmentUris.size == 1) "" else "s"} ready"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (selectedDocumentIds.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.68f)
                            ) {
                                Text(
                                    selectedDocumentIds.size.toString(),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.weight(1f)) {
                            OutlinedButton(
                                onClick = { shelfMenuOpen = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text(
                                    shelves.firstOrNull { it.id == selectedShelf }?.label ?: "All shelves",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            DropdownMenu(expanded = shelfMenuOpen, onDismissRequest = { shelfMenuOpen = false }) {
                                shelves.forEach { shelf ->
                                    DropdownMenuItem(
                                        text = { Text(if (shelf.id == selectedShelf) "✓ ${shelf.label}" else shelf.label) },
                                        onClick = {
                                            selectedShelf = shelf.id
                                            shelfMenuOpen = false
                                        }
                                    )
                                }
                            }
                        }
                        Box(Modifier.weight(1f)) {
                            Button(
                                onClick = { documentMenuOpen = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text(if (selectedDocumentIds.isEmpty()) "Add document" else "Add / remove")
                            }
                            DropdownMenu(expanded = documentMenuOpen, onDismissRequest = { documentMenuOpen = false }) {
                                if (filteredDocuments.isEmpty()) {
                                    DropdownMenuItem(text = { Text("No documents on this shelf") }, onClick = {})
                                } else {
                                    filteredDocuments.forEach { document ->
                                        val selected = document.id in selectedDocumentIds
                                        DropdownMenuItem(
                                            text = {
                                                Column {
                                                    Text(
                                                        if (selected) "✓ ${document.title}" else document.title,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        shelfLabel(document.shelf, shelves),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            },
                                            onClick = {
                                                val updated = if (selected) {
                                                    selectedDocumentIds - document.id
                                                } else {
                                                    selectedDocumentIds + document.id
                                                }
                                                selectedDocumentIdsRaw = updated.joinToString(",")
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (selectedDocuments.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
                        ) {
                            Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                                selectedDocuments.forEachIndexed { index, document ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(9.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.58f)
                                        ) {
                                            Text(
                                                if (document.mimeType.contains("pdf", true)) "PDF" else "DOC",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp)
                                            )
                                        }
                                        Spacer(Modifier.width(10.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                document.title,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                shelfLabel(document.shelf, shelves),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        TextButton(onClick = {
                                            selectedDocumentIdsRaw = (selectedDocumentIds - document.id).joinToString(",")
                                        }) { Text("Remove") }
                                    }
                                    if (index < selectedDocuments.lastIndex) {
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.40f))
                                    }
                                }
                            }
                        }
                    }

                    if (!context.settingIsFixedInNativePreset("attachment_uris")) {
                        Spacer(Modifier.height(9.dp))
                        TextButton(onClick = { attachmentPicker.launch(arrayOf("application/pdf", "image/*", "text/plain")) }) {
                            Text(if (extraAttachmentUris.isEmpty()) "+ Add file from device" else "+ Add another file  ·  ${extraAttachmentUris.size} extra")
                        }
                        if (extraAttachmentUris.isNotEmpty()) {
                            TextButtonRow("Clear additional files") { extraAttachmentUrisRaw = "" }
                        }
                    } else if (extraAttachmentUris.isNotEmpty()) {
                        Text(
                            "${extraAttachmentUris.size} additional attachment${if (extraAttachmentUris.size == 1) "" else "s"} supplied by caller",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.16f)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Message", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "ODK and presets can pre-fill any of these fields.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))

                    if (!context.settingIsFixedInNativePreset("recipient")) {
                        OutlinedTextField(
                            recipient,
                            { recipient = it },
                            label = { Text("To") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp)
                        )
                    } else {
                        ReadOnlyValue("To", recipient)
                    }

                    if (!optionalRecipientsOpen && (cc.isBlank() && bcc.isBlank())) {
                        TextButtonRow("Add Cc / Bcc") { optionalRecipientsOpen = true }
                    } else if (optionalRecipientsOpen) {
                        if (!context.settingIsFixedInNativePreset("cc")) {
                            OutlinedTextField(
                                cc,
                                { cc = it },
                                label = { Text("Cc") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp)
                            )
                        } else if (cc.isNotBlank()) {
                            ReadOnlyValue("Cc", cc)
                        }
                        if (!context.settingIsFixedInNativePreset("bcc")) {
                            OutlinedTextField(
                                bcc,
                                { bcc = it },
                                label = { Text("Bcc") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp)
                            )
                        } else if (bcc.isNotBlank()) {
                            ReadOnlyValue("Bcc", bcc)
                        }
                    }

                    Spacer(Modifier.height(6.dp))
                    if (!context.settingIsFixedInNativePreset("subject")) {
                        OutlinedTextField(
                            subject,
                            { subject = it },
                            label = { Text("Subject") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp)
                        )
                    } else {
                        ReadOnlyValue("Subject", subject)
                    }

                    Spacer(Modifier.height(6.dp))
                    if (!context.settingIsFixedInNativePreset("body")) {
                        OutlinedTextField(
                            body,
                            { body = it },
                            label = { Text("Message") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 5,
                            shape = RoundedCornerShape(16.dp)
                        )
                    } else {
                        ReadOnlyValue("Message", body)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                color = if (awaitingSendConfirmation) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.34f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.20f)
                }
            ) {
                Column(Modifier.padding(14.dp)) {
                    if (!awaitingSendConfirmation) {
                        Button(
                            onClick = ::launchEmail,
                            enabled = !emailLaunchInProgress,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(if (emailLaunchInProgress) "Opening mail app…" else "Send email")
                        }
                        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
                    } else {
                        Text("Did it send?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Confirm what happened in the mail app. This records operator confirmation, not server delivery.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = { finish(true) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        ) { Text("Yes — sent") }
                        OutlinedButton(
                            onClick = { finish(false) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp)
                        ) { Text("No — not sent") }
                        TextButtonRow("Open mail app again") { launchEmail() }
                    }

                    if (status.isNotBlank() && status != "Choose documents and prepare the message.") {
                        Text(
                            status,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TextButtonRow(label: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        androidx.compose.material3.TextButton(onClick = onClick) { Text(label) }
    }
}

@Composable
private fun ReadOnlyValue(label: String, value: String) {
    if (value.isBlank()) return
    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun shelfLabel(id: String, shelves: List<LibraryShelf>): String =
    shelves.firstOrNull { it.id == id }?.label ?: id.replace('_', ' ').replaceFirstChar { it.uppercase() }

private val BUILT_IN_EMAIL_SHELVES = listOf(LibraryShelf("all", "All shelves")) + REFERENCE_LIBRARY_BUILT_IN_SHELVES

private fun parseAddresses(raw: String): Array<String> = raw
    .split(',', ';', '\n')
    .map { it.trim() }
    .filter { it.isNotBlank() }
    .toTypedArray()
