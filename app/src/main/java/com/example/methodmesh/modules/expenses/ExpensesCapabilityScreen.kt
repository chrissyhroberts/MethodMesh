package com.example.methodmesh.modules.expenses

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.methodmesh.transport.ResultShare
import com.example.methodmesh.transport.workflow.ui.CanonicalCommittedResultActions
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import java.io.File
import java.time.LocalDate

object ExpensesManagerCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100ExpensesManageMethod.ID
    override val title = "Expenses"
    override val description = "Browse and manage persistent expense ledgers."

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (com.example.methodmesh.core.methodmesh.ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        val appContext = LocalContext.current
        val repository = remember { ExpenseLedgerRepository(appContext.applicationContext) }
        val exporter = remember { ExpenseLedgerExportRepository(appContext.applicationContext) }

        var ledgers by remember { mutableStateOf(repository.listLedgers()) }
        var ledgerId by rememberSaveable { mutableStateOf(ledgers.firstOrNull()?.ledgerId.orEmpty()) }
        var showAdd by rememberSaveable { mutableStateOf(false) }
        var showCreate by rememberSaveable { mutableStateOf(ledgers.isEmpty()) }
        var showSettings by rememberSaveable { mutableStateOf(false) }
        var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }
        var selectedExpenseId by rememberSaveable { mutableStateOf("") }
        var refreshToken by remember { mutableStateOf(0) }
        var exportMenuOpen by rememberSaveable { mutableStateOf(false) }
        var shareMenuOpen by rememberSaveable { mutableStateOf(false) }
        var pendingSaveFile by remember { mutableStateOf<File?>(null) }
        var committedResult by remember { mutableStateOf<com.example.methodmesh.core.methodmesh.ExecutionResult?>(null) }

        fun refresh(selectLedgerId: String? = null) {
            ledgers = repository.listLedgers()
            ledgerId = selectLedgerId
                ?: ledgerId.takeIf { id -> ledgers.any { it.ledgerId == id } }
                ?: ledgers.firstOrNull()?.ledgerId.orEmpty()
            refreshToken++
        }

        val saveDocumentLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("*/*")
        ) { uri: Uri? ->
            val file = pendingSaveFile
            if (uri != null && file != null) {
                appContext.contentResolver.openOutputStream(uri)?.use { output ->
                    file.inputStream().use { input -> input.copyTo(output) }
                }
            }
            pendingSaveFile = null
        }

        val ledger = remember(ledgerId, refreshToken, ledgers) {
            ledgers.firstOrNull { it.ledgerId == ledgerId }
                ?.let { runCatching { repository.getLedger(it.ledgerId) }.getOrNull() }
        }

        fun shareFile(file: File) {
            val uri = exporter.shareUri(file)
            // Domain export: the file itself is the useful artefact. Use the shared
            // typed-stream transport rather than reconstructing Android sharing here.
            ResultShare.share(
                context = appContext,
                chooserTitle = "Share expenses",
                text = "",
                attachments = listOf(ResultShare.Attachment(file.name, uri))
            )
        }

        fun saveCopy(file: File) {
            pendingSaveFile = file
            saveDocumentLauncher.launch(file.name)
        }

        fun completionResult(): com.example.methodmesh.core.methodmesh.ExecutionResult? {
            val current = ledger ?: return null
            val summary = ExpenseLedgerCalculator.summarize(current)
            val request = As100ExpensesManageMethod.request(
                action = As100ExpensesManageMethod.ID,
                context = context.request.invocationContext.asMap(As100ExpensesManageMethod.ID) + context.action.settings,
                signals = emptyList(),
                inputs = emptyList()
            )
            val values = mapOf(
                ExpensesManageFields.STATUS to "succeeded",
                ExpensesManageFields.LEDGER_ID to current.ledgerId,
                ExpensesManageFields.LEDGER_NAME to current.name,
                ExpensesManageFields.TOTAL_HOME to summary.totalHomeExact.toPlainString(),
                ExpensesManageFields.HOME_CURRENCY to current.homeCurrency,
                ExpensesManageFields.EXPENSE_COUNT to summary.expenseCount.toString(),
                ExpensesManageFields.ERROR to ""
            )
            return As100ExpensesManageMethod.result(
                request = request,
                values = values,
                invocation = context.request.invocationContext
            )
        }

        fun completeAndClose() {
            val result = completionResult()
            if (result == null) {
                onCancel()
            } else if (context.submitsImmediately) {
                onConfirmed(result)
            } else {
                committedResult = result
            }
        }

        val frozenResult = committedResult
        if (frozenResult != null && !context.submitsImmediately) {
            val currentFields = com.example.methodmesh.transport.OutputFormatter.fields(frozenResult, includeProvenance = false)
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Expenses committed", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    listOfNotNull(
                        currentFields[ExpensesManageFields.LEDGER_NAME]?.toString()?.takeIf { it.isNotBlank() },
                        currentFields[ExpensesManageFields.TOTAL_HOME]?.toString()?.takeIf { it.isNotBlank() }?.let { total ->
                            val currency = currentFields[ExpensesManageFields.HOME_CURRENCY]?.toString().orEmpty()
                            if (currency.isBlank()) total else "$total $currency"
                        },
                        currentFields[ExpensesManageFields.EXPENSE_COUNT]?.toString()?.takeIf { it.isNotBlank() }?.let { "$it expenses" }
                    ).joinToString(" · "),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "CSV, JSON and ZIP remain domain exports. The controls below share or save the committed capability result.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                CanonicalCommittedResultActions(
                    result = frozenResult,
                    label = "expenses result",
                    onDone = { onConfirmed(frozenResult) },
                    onEdit = { committedResult = null }
                )
            }
            return
        }

        if (showDeleteConfirm && ledger != null) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text("Delete ledger?") },
                text = {
                    Text(
                        "Delete “${ledger.name}” and all of its expenses, audit history, and attached files? " +
                            "This cannot be undone."
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val deletedId = ledger.ledgerId
                            repository.deleteLedger(deletedId)
                            showDeleteConfirm = false
                            showSettings = false
                            showAdd = false
                            selectedExpenseId = ""
                            refresh()
                            if (ledgers.isEmpty()) showCreate = true
                        }
                    ) { Text("Delete") }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
                }
            )
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Text("Persistent expense ledgers", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(16.dp))

                if (showCreate) {
                    InlineCreateLedger(repository = repository) { created ->
                        refresh(created.ledgerId)
                        showCreate = false
                    }
                    Spacer(Modifier.height(14.dp))
                    if (ledgers.isNotEmpty()) {
                        OutlinedButton(
                            onClick = { showCreate = false },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Cancel new ledger") }
                    }
                    return@Column
                }

                if (ledgers.isEmpty()) {
                    Text("No expense ledgers yet.")
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { showCreate = true },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Create first ledger") }
                    return@Column
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(Modifier.weight(1f)) {
                        SimpleDropdown(
                            label = "Ledger",
                            value = ledgerId,
                            choices = ledgers.map { it.ledgerId to it.name },
                            onSelected = {
                                ledgerId = it
                                selectedExpenseId = ""
                                showAdd = false
                                showSettings = false
                            }
                        )
                    }
                    OutlinedButton(onClick = { showCreate = true }) { Text("New") }
                }

                if (ledger != null) {
                    val summary = ExpenseLedgerCalculator.summarize(ledger)
                    val symbol = ExpenseLedgerCalculator.symbol(ledger.homeCurrency)

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showSettings = !showSettings },
                            modifier = Modifier.weight(1f)
                        ) { Text(if (showSettings) "Close settings" else "Ledger settings") }

                        OutlinedButton(
                            onClick = { showDeleteConfirm = true },
                            modifier = Modifier.weight(1f)
                        ) { Text("Delete ledger") }
                    }

                    if (showSettings) {
                        InlineEditLedger(
                            ledger = ledger,
                            repository = repository,
                            onUpdated = {
                                refresh(ledger.ledgerId)
                                showSettings = false
                            }
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    Text(
                        "$symbol${ExpenseLedgerCalculator.display(summary.totalHomeExact, ledger.homeCurrency)}",
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${summary.expenseCount} expenses · ${summary.noReceiptCount} without receipt",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    if (summary.byCategory.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        summary.byCategory.entries
                            .sortedByDescending { it.value }
                            .forEach { (category, value) ->
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(ExpenseCategories.label(category))
                                    Text("$symbol${ExpenseLedgerCalculator.display(value, ledger.homeCurrency)}")
                                }
                            }
                    }

                    Spacer(Modifier.height(18.dp))
                    Text("Expenses", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

                    val activeExpenses = ledger.expenses
                        .filter { it.status == "active" }
                        .sortedWith(
                            compareByDescending<ExpenseEntry> { it.expenseDate }
                                .thenByDescending { it.createdAtIso }
                        )

                    if (activeExpenses.isEmpty()) {
                        Text("No expenses recorded yet.", modifier = Modifier.padding(vertical = 10.dp))
                    }

                    activeExpenses.forEach { expense ->
                        val expanded = expense.expenseId == selectedExpenseId
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    selectedExpenseId = if (expanded) "" else expense.expenseId
                                }
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            "#${expense.rowNumber} · " +
                                                expense.note.ifBlank { ExpenseCategories.label(expense.category) },
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            "${expense.expenseDate} · ${ExpenseCategories.label(expense.category)}",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    Column {
                                        Text("${expense.originalCurrency} ${expense.amountOriginal}")
                                        Text(
                                            "$symbol${ExpenseLedgerCalculator.display(expense.amountHomeExact.toPlainBigDecimal(), ledger.homeCurrency)}",
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                if (expanded) {
                                    Spacer(Modifier.height(8.dp))
                                    if (expense.originalCurrency == ledger.homeCurrency) {
                                        Text("Home-currency transaction · no FX conversion")
                                    } else {
                                        Text(
                                            "Applied rate: 1 ${ledger.homeCurrency} = " +
                                                "${expense.appliedForeignUnitsPerHomeUnit} ${expense.originalCurrency}"
                                        )
                                        Text(
                                            "Calculation: ${expense.originalCurrency} ${expense.amountOriginal} ÷ " +
                                                "${expense.appliedForeignUnitsPerHomeUnit} = " +
                                                "${ledger.homeCurrency} ${expense.amountHomeExact}"
                                        )
                                    }

                                    if (expense.attachmentPaths.isEmpty()) {
                                        Text("No receipt")
                                    } else {
                                        Text(
                                            "${expense.attachmentPaths.size} attached file(s)",
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        expense.attachmentPaths.forEach { path ->
                                            Text(
                                                path.substringAfterLast('/'),
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    }

                                    Spacer(Modifier.height(8.dp))
                                    OutlinedButton(
                                        onClick = {
                                            repository.voidExpense(ledger.ledgerId, expense.expenseId)
                                            selectedExpenseId = ""
                                            refresh()
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text("Void expense") }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = { showAdd = !showAdd },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (showAdd) "Close add form" else "+ Add expense") }

                    if (showAdd) {
                        InlineAddExpense(
                            ledger = ledger,
                            repository = repository,
                            onAdded = {
                                refresh()
                                showAdd = false
                            }
                        )
                    }

                    Spacer(Modifier.height(18.dp))
                    Text(
                        "Export & share",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(Modifier.weight(1f)) {
                            OutlinedButton(
                                onClick = { exportMenuOpen = true },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Save / download") }
                            DropdownMenu(
                                expanded = exportMenuOpen,
                                onDismissRequest = { exportMenuOpen = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("CSV") },
                                    onClick = {
                                        exportMenuOpen = false
                                        saveCopy(exporter.exportCsv(ledger))
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("JSON") },
                                    onClick = {
                                        exportMenuOpen = false
                                        saveCopy(exporter.exportJson(ledger))
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Summary text") },
                                    onClick = {
                                        exportMenuOpen = false
                                        saveCopy(exporter.exportSummary(ledger))
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Complete ZIP") },
                                    onClick = {
                                        exportMenuOpen = false
                                        saveCopy(exporter.exportZip(ledger))
                                    }
                                )
                            }
                        }

                        Column(Modifier.weight(1f)) {
                            OutlinedButton(
                                onClick = { shareMenuOpen = true },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Share") }
                            DropdownMenu(
                                expanded = shareMenuOpen,
                                onDismissRequest = { shareMenuOpen = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Summary") },
                                    onClick = {
                                        shareMenuOpen = false
                                        shareFile(exporter.exportSummary(ledger))
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("CSV") },
                                    onClick = {
                                        shareMenuOpen = false
                                        shareFile(exporter.exportCsv(ledger))
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Complete ZIP") },
                                    onClick = {
                                        shareMenuOpen = false
                                        shareFile(exporter.exportZip(ledger))
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick = { if (context.stepNumber > 1) onBack() else completeAndClose() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (context.stepNumber > 1) "Back" else "Done")
                }
            }
        }
    }
}

@Composable
private fun InlineCreateLedger(
    repository: ExpenseLedgerRepository,
    onCreated: (ExpenseLedger) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var home by rememberSaveable { mutableStateOf("GBP") }
    var rates by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf("") }

    Text("New ledger", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    LedgerSettingsFields(
        name = name,
        onNameChange = { name = it },
        home = home,
        onHomeChange = { home = it },
        rates = rates,
        onRatesChange = { rates = it }
    )
    if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)

    Button(
        onClick = {
            runCatching {
                repository.createLedger(name, home, parseRates(rates))
            }.onSuccess {
                error = ""
                onCreated(it)
            }.onFailure {
                error = it.message ?: "Could not create ledger."
            }
        },
        modifier = Modifier.fillMaxWidth()
    ) { Text("Create ledger") }
}

@Composable
private fun InlineEditLedger(
    ledger: ExpenseLedger,
    repository: ExpenseLedgerRepository,
    onUpdated: (ExpenseLedger) -> Unit
) {
    var name by rememberSaveable(ledger.ledgerId) { mutableStateOf(ledger.name) }
    var home by rememberSaveable(ledger.ledgerId) { mutableStateOf(ledger.homeCurrency) }
    var rates by rememberSaveable(ledger.ledgerId) {
        mutableStateOf(
            ledger.exchangeRates.values
                .filter { it.currency != ledger.homeCurrency }
                .joinToString("|") { "${it.currency}=${it.foreignUnitsPerHomeUnit}" }
        )
    }
    var error by rememberSaveable { mutableStateOf("") }

    Spacer(Modifier.height(12.dp))
    Text(
        "Edit ledger settings",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
    )
    Text(
        "Changing currencies or exchange rates immediately recalculates every expense and the running totals.",
        style = MaterialTheme.typography.bodySmall
    )

    LedgerSettingsFields(
        name = name,
        onNameChange = { name = it },
        home = home,
        onHomeChange = { home = it },
        rates = rates,
        onRatesChange = { rates = it }
    )

    if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)

    Button(
        onClick = {
            runCatching {
                repository.updateLedgerSettings(
                    ledgerId = ledger.ledgerId,
                    nameRaw = name,
                    homeCurrencyRaw = home,
                    rates = parseRates(rates)
                )
            }.onSuccess {
                error = ""
                onUpdated(it)
            }.onFailure {
                error = it.message ?: "Could not update ledger settings."
            }
        },
        modifier = Modifier.fillMaxWidth()
    ) { Text("Apply settings & recalculate") }
}

@Composable
private fun LedgerSettingsFields(
    name: String,
    onNameChange: (String) -> Unit,
    home: String,
    onHomeChange: (String) -> Unit,
    rates: String,
    onRatesChange: (String) -> Unit
) {
    OutlinedTextField(
        value = name,
        onValueChange = onNameChange,
        label = { Text("Trip / activity") },
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = home,
        onValueChange = { onHomeChange(it.uppercase().take(3)) },
        label = { Text("Home currency") },
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = rates,
        onValueChange = { onRatesChange(it.uppercase()) },
        label = { Text("Foreign currencies") },
        supportingText = { Text("USD=1.32 means 1 $home = 1.32 USD") },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun InlineAddExpense(
    ledger: ExpenseLedger,
    repository: ExpenseLedgerRepository,
    onAdded: () -> Unit
) {
    val appContext = LocalContext.current
    var category by rememberSaveable { mutableStateOf("subsistence") }
    var amount by rememberSaveable { mutableStateOf("") }
    var currency by rememberSaveable { mutableStateOf(ledger.homeCurrency) }
    var note by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var noReceipt by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf("") }
    var pendingCameraUri by rememberSaveable { mutableStateOf("") }
    val attachmentUris = remember { mutableStateListOf<String>() }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { ok ->
        if (ok && pendingCameraUri.isNotBlank()) {
            attachmentUris += pendingCameraUri
            noReceipt = false
        }
        pendingCameraUri = ""
    }

    val pickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        uris.forEach { uri ->
            runCatching {
                appContext.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            if (uri.toString() !in attachmentUris) attachmentUris += uri.toString()
        }
        if (uris.isNotEmpty()) noReceipt = false
    }

    val scannerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val scan = result.data?.let { GmsDocumentScanningResult.fromActivityResultIntent(it) }
        val pdfUri = scan?.pdf?.uri
        if (pdfUri != null) {
            val copied = copyUriToExpenseCache(
                appContext,
                pdfUri,
                "expense-scan-${System.currentTimeMillis()}.pdf"
            )
            if (copied != null) {
                attachmentUris += copied
                noReceipt = false
            }
        }
    }

    fun startCamera() {
        val tmp = File(appContext.cacheDir, "expense-photo-${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.fileprovider",
            tmp
        )
        pendingCameraUri = uri.toString()
        cameraLauncher.launch(uri)
    }

    fun startDocumentScanner() {
        val activity = appContext.findActivity()
        if (activity == null) {
            error = "No Android activity was available to launch the document scanner."
            return
        }
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(30)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_PDF)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
        GmsDocumentScanning.getClient(options)
            .getStartScanIntent(activity)
            .addOnSuccessListener { sender ->
                scannerLauncher.launch(IntentSenderRequest.Builder(sender).build())
            }
            .addOnFailureListener {
                error = "Could not open document scanner: ${it.message.orEmpty()}"
            }
    }

    Spacer(Modifier.height(14.dp))
    Text("Add expense", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

    SimpleDropdown(
        "Category",
        category,
        ExpenseCategories.all.map { it to ExpenseCategories.label(it) }
    ) { category = it }

    OutlinedTextField(
        amount,
        { amount = it.filter { ch -> ch.isDigit() || ch == '.' } },
        label = { Text("Amount") },
        modifier = Modifier.fillMaxWidth()
    )

    SimpleDropdown(
        "Currency",
        currency,
        ledger.exchangeRates.keys.map { it to it }
    ) { currency = it }

    OutlinedTextField(
        note,
        { note = it },
        label = { Text("Note") },
        modifier = Modifier.fillMaxWidth()
    )

    OutlinedTextField(
        date,
        { date = it },
        label = { Text("Date") },
        modifier = Modifier.fillMaxWidth()
    )

    Text(
        "Receipt / invoice",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold
    )

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(
            onClick = { startCamera() },
            modifier = Modifier.weight(1f)
        ) { Text("Camera") }

        OutlinedButton(
            onClick = { startDocumentScanner() },
            modifier = Modifier.weight(1f)
        ) { Text("Scan document") }
    }

    OutlinedButton(
        onClick = {
            pickerLauncher.launch(arrayOf("image/*", "application/pdf"))
        },
        modifier = Modifier.fillMaxWidth()
    ) { Text("Pick file(s): images or PDF") }

    if (attachmentUris.isNotEmpty()) {
        Text("${attachmentUris.size} file(s) attached")
        attachmentUris.forEachIndexed { index, uri ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "${index + 1}. ${displayAttachmentName(uri)}",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { attachmentUris.remove(uri) }) { Text("Remove") }
            }
        }
    }

    Row(Modifier.fillMaxWidth()) {
        Checkbox(
            checked = noReceipt,
            onCheckedChange = {
                noReceipt = it
                if (it) attachmentUris.clear()
            }
        )
        Text("No receipt", modifier = Modifier.padding(top = 12.dp))
    }

    if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)

    Button(
        onClick = {
            runCatching {
                repository.addExpense(
                    ledgerId = ledger.ledgerId,
                    category = category,
                    amountRaw = amount,
                    currencyRaw = currency,
                    note = note,
                    expenseDateRaw = date,
                    attachmentUris = attachmentUris.toList(),
                    noReceipt = noReceipt
                )
            }.onSuccess {
                error = ""
                onAdded()
            }.onFailure {
                error = it.message ?: "Could not add expense."
            }
        },
        modifier = Modifier.fillMaxWidth()
    ) { Text("Record expense") }
}

@Composable
private fun SimpleDropdown(
    label: String,
    value: String,
    choices: List<Pair<String, String>>,
    onSelected: (String) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    val display = choices.firstOrNull { it.first == value }?.second
        ?: value.ifBlank { "Select" }

    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        OutlinedButton(
            onClick = { open = true },
            modifier = Modifier.fillMaxWidth()
        ) { Text(display) }

        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false }
        ) {
            choices.forEach { (id, text) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        onSelected(id)
                        open = false
                    }
                )
            }
        }
    }
}

private fun parseRates(raw: String): Map<String, String> {
    val parsed = linkedMapOf<String, String>()
    raw.split('|', ';', '\n')
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .forEach { token ->
            val parts = token.split('=', limit = 2)
            require(parts.size == 2) { "Rates must use USD=1.32|EUR=1.16." }
            parsed[parts[0].trim()] = parts[1].trim()
        }
    return parsed
}

private fun copyUriToExpenseCache(
    context: Context,
    uri: Uri,
    fileName: String
): String? = runCatching {
    val out = File(context.cacheDir, fileName)
    context.contentResolver.openInputStream(uri)?.use { input ->
        out.outputStream().use { output -> input.copyTo(output) }
    } ?: return@runCatching null
    FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        out
    ).toString()
}.getOrNull()

private fun displayAttachmentName(rawUri: String): String {
    val uri = Uri.parse(rawUri)
    return uri.lastPathSegment
        ?.substringAfterLast('/')
        ?.takeIf { it.isNotBlank() }
        ?: "attachment"
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun String.toPlainBigDecimal() = this.toBigDecimal()
