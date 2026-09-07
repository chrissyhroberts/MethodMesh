package com.example.methodmesh.modules.expenses

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class ExpenseLedgerRepository(private val context: Context) {
    private val root = File(context.filesDir, "methodmesh/expenses").apply { mkdirs() }

    fun listLedgers(): List<ExpenseLedger> =
        root.listFiles().orEmpty()
            .filter { it.isDirectory && File(it, "ledger.json").exists() }
            .mapNotNull { runCatching { readLedger(File(it, "ledger.json")) }.getOrNull() }
            .sortedByDescending { it.updatedAtIso }

    fun createLedger(
        nameRaw: String,
        homeCurrencyRaw: String,
        rates: Map<String, String>
    ): ExpenseLedger {
        val name = nameRaw.trim()
        require(name.isNotBlank()) { "Ledger name is required." }
        val home = ExpenseLedgerCalculator.normalizeCurrency(homeCurrencyRaw)
        val normalizedRates = normalizeRates(home, rates)

        val ledger = ExpenseLedger.new(name, home, normalizedRates)
        val folder = folderFor(ledger.ledgerId)
        require(folder.mkdirs()) { "Could not create ledger folder." }
        File(folder, "receipts").mkdirs()
        atomicWrite(File(folder, "ledger.json"), ledger.toJson().toString(2))
        appendEvent(folder, JSONObject().apply {
            put("event", "ledger_created")
            put("event_time_iso", Instant.now().toString())
            put("ledger_id", ledger.ledgerId)
            put("name", ledger.name)
            put("home_currency", ledger.homeCurrency)
            put("exchange_rates", JSONObject().apply {
                ledger.exchangeRates.forEach { (currency, rate) ->
                    put(currency, rate.foreignUnitsPerHomeUnit)
                }
            })
        })
        return ledger
    }

    fun getLedger(ledgerId: String): ExpenseLedger =
        readLedger(File(folderFor(ledgerId), "ledger.json"))

    fun updateLedgerSettings(
        ledgerId: String,
        nameRaw: String,
        homeCurrencyRaw: String,
        rates: Map<String, String>
    ): ExpenseLedger {
        val before = getLedger(ledgerId)
        val name = nameRaw.trim()
        require(name.isNotBlank()) { "Ledger name is required." }
        val home = ExpenseLedgerCalculator.normalizeCurrency(homeCurrencyRaw)
        val normalizedRates = normalizeRates(home, rates)

        val missingCurrencies = before.expenses
            .map { it.originalCurrency }
            .distinct()
            .filter { it != home && it !in normalizedRates.keys }
        require(missingCurrencies.isEmpty()) {
            "Add exchange rates for existing expense currencies: ${missingCurrencies.joinToString(", ")}"
        }

        val now = Instant.now().toString()
        val recalculations = JSONArray()
        val recalculated = before.expenses.map { expense ->
            val originalAmount = ExpenseLedgerCalculator.parseMoney(expense.amountOriginal)
            val appliedRate = normalizedRates[expense.originalCurrency]
                ?: ExchangeRate(expense.originalCurrency, "1")
            val rateValue = ExpenseLedgerCalculator.parseRate(appliedRate.foreignUnitsPerHomeUnit)
            val newHome = ExpenseLedgerCalculator.convertToHome(
                originalAmount,
                expense.originalCurrency,
                home,
                rateValue
            )
            recalculations.put(JSONObject().apply {
                put("expense_id", expense.expenseId)
                put("row_number", expense.rowNumber)
                put("old_home_currency", expense.homeCurrency)
                put("old_rate", expense.appliedForeignUnitsPerHomeUnit)
                put("old_amount_home_exact", expense.amountHomeExact)
                put("new_home_currency", home)
                put("new_rate", rateValue.stripTrailingZeros().toPlainString())
                put("new_amount_home_exact", newHome.toPlainString())
            })
            val renamedAttachments = renameExistingAttachments(
                folder = folderFor(ledgerId),
                currentPaths = expense.attachmentPaths,
                rowNumber = expense.rowNumber,
                category = expense.category,
                amountHomeExact = newHome.toPlainString(),
                homeCurrency = home
            )
            expense.copy(
                updatedAtIso = now,
                appliedForeignUnitsPerHomeUnit = rateValue.stripTrailingZeros().toPlainString(),
                homeCurrency = home,
                amountHomeExact = newHome.toPlainString(),
                attachmentPaths = renamedAttachments
            )
        }

        val updated = before.copy(
            name = name,
            homeCurrency = home,
            exchangeRates = normalizedRates,
            updatedAtIso = now,
            expenses = recalculated
        )
        saveLedger(updated)

        appendEvent(folderFor(ledgerId), JSONObject().apply {
            put("event", "ledger_settings_updated")
            put("event_time_iso", now)
            put("ledger_id", ledgerId)
            put("old_name", before.name)
            put("new_name", updated.name)
            put("old_home_currency", before.homeCurrency)
            put("new_home_currency", updated.homeCurrency)
            put("old_exchange_rates", JSONObject().apply {
                before.exchangeRates.forEach { (currency, rate) ->
                    put(currency, rate.foreignUnitsPerHomeUnit)
                }
            })
            put("new_exchange_rates", JSONObject().apply {
                updated.exchangeRates.forEach { (currency, rate) ->
                    put(currency, rate.foreignUnitsPerHomeUnit)
                }
            })
            put("recalculations", recalculations)
        })
        return updated
    }

    fun deleteLedger(ledgerId: String) {
        val folder = folderFor(ledgerId)
        require(folder.exists()) { "Ledger not found." }
        require(folder.deleteRecursively()) { "Could not delete ledger." }
    }

    fun addExpense(
        ledgerId: String,
        category: String,
        amountRaw: String,
        currencyRaw: String,
        note: String,
        expenseDateRaw: String,
        attachmentUris: List<String>,
        noReceipt: Boolean
    ): ExpenseLedger {
        val ledger = getLedger(ledgerId)
        require(category in ExpenseCategories.all) { "Unknown category." }
        val amount = ExpenseLedgerCalculator.parseMoney(amountRaw)
        val currency = ExpenseLedgerCalculator.normalizeCurrency(currencyRaw)
        val rate = ledger.exchangeRates[currency]
            ?: throw IllegalArgumentException("Currency $currency is not configured for this ledger.")
        val rateValue = ExpenseLedgerCalculator.parseRate(rate.foreignUnitsPerHomeUnit)
        val amountHome = ExpenseLedgerCalculator.convertToHome(
            amount,
            currency,
            ledger.homeCurrency,
            rateValue
        )
        val date = expenseDateRaw.ifBlank { LocalDate.now().toString() }
        LocalDate.parse(date)

        val cleanedUris = attachmentUris.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        require(cleanedUris.isNotEmpty() || noReceipt) {
            "Attach at least one file or explicitly select No receipt."
        }
        require(!(cleanedUris.isNotEmpty() && noReceipt)) {
            "Attachments and No receipt cannot both be selected."
        }

        val now = Instant.now().toString()
        val expenseId = UUID.randomUUID().toString()
        val rowNumber = (ledger.expenses.maxOfOrNull { it.rowNumber } ?: 0) + 1
        val folder = folderFor(ledgerId)

        val attachmentPaths = storeAttachments(
            folder = folder,
            rowNumber = rowNumber,
            category = category,
            amountHomeExact = amountHome.toPlainString(),
            homeCurrency = ledger.homeCurrency,
            rawUris = cleanedUris
        )

        val entry = ExpenseEntry(
            expenseId = expenseId,
            rowNumber = rowNumber,
            expenseDate = date,
            createdAtIso = now,
            updatedAtIso = now,
            category = category,
            amountOriginal = amount.stripTrailingZeros().toPlainString(),
            originalCurrency = currency,
            appliedForeignUnitsPerHomeUnit = rateValue.stripTrailingZeros().toPlainString(),
            homeCurrency = ledger.homeCurrency,
            amountHomeExact = amountHome.toPlainString(),
            note = note.trim(),
            evidenceStatus = if (attachmentPaths.isNotEmpty()) "attached" else "no_receipt",
            attachmentPaths = attachmentPaths
        )

        val updated = ledger.copy(
            updatedAtIso = now,
            expenses = ledger.expenses + entry
        )
        saveLedger(updated)
        appendEvent(folder, JSONObject().apply {
            put("event", "expense_added")
            put("event_time_iso", now)
            put("ledger_id", ledgerId)
            put("expense", entry.toJson())
        })
        return updated
    }

    fun updateExpense(
        ledgerId: String,
        expenseId: String,
        category: String,
        amountRaw: String,
        currencyRaw: String,
        note: String,
        expenseDateRaw: String
    ): ExpenseLedger {
        val ledger = getLedger(ledgerId)
        val current = ledger.expenses.firstOrNull { it.expenseId == expenseId }
            ?: throw IllegalArgumentException("Expense not found.")
        require(current.status == "active") { "Voided expenses cannot be edited." }
        require(category in ExpenseCategories.all) { "Unknown category." }

        val amount = ExpenseLedgerCalculator.parseMoney(amountRaw)
        val currency = ExpenseLedgerCalculator.normalizeCurrency(currencyRaw)
        val rate = ledger.exchangeRates[currency]
            ?: throw IllegalArgumentException("Currency $currency is not configured for this ledger.")
        val rateValue = ExpenseLedgerCalculator.parseRate(rate.foreignUnitsPerHomeUnit)
        val home = ExpenseLedgerCalculator.convertToHome(
            amount,
            currency,
            ledger.homeCurrency,
            rateValue
        )
        val date = expenseDateRaw.ifBlank { current.expenseDate }
        LocalDate.parse(date)
        val now = Instant.now().toString()

        val renamedAttachments = renameExistingAttachments(
            folder = folderFor(ledgerId),
            currentPaths = current.attachmentPaths,
            rowNumber = current.rowNumber,
            category = category,
            amountHomeExact = home.toPlainString(),
            homeCurrency = ledger.homeCurrency
        )
        val changed = current.copy(
            expenseDate = date,
            updatedAtIso = now,
            category = category,
            amountOriginal = amount.stripTrailingZeros().toPlainString(),
            originalCurrency = currency,
            appliedForeignUnitsPerHomeUnit = rateValue.stripTrailingZeros().toPlainString(),
            homeCurrency = ledger.homeCurrency,
            amountHomeExact = home.toPlainString(),
            note = note.trim(),
            attachmentPaths = renamedAttachments
        )
        val updated = ledger.copy(
            updatedAtIso = now,
            expenses = ledger.expenses.map { if (it.expenseId == expenseId) changed else it }
        )
        saveLedger(updated)
        appendEvent(folderFor(ledgerId), JSONObject().apply {
            put("event", "expense_updated")
            put("event_time_iso", now)
            put("ledger_id", ledgerId)
            put("expense_id", expenseId)
            put("before", current.toJson())
            put("after", changed.toJson())
        })
        return updated
    }

    fun voidExpense(ledgerId: String, expenseId: String): ExpenseLedger {
        val ledger = getLedger(ledgerId)
        val current = ledger.expenses.firstOrNull { it.expenseId == expenseId }
            ?: throw IllegalArgumentException("Expense not found.")
        val now = Instant.now().toString()
        val updated = ledger.copy(
            updatedAtIso = now,
            expenses = ledger.expenses.map {
                if (it.expenseId == expenseId) it.copy(status = "void", updatedAtIso = now) else it
            }
        )
        saveLedger(updated)
        appendEvent(folderFor(ledgerId), JSONObject().apply {
            put("event", "expense_voided")
            put("event_time_iso", now)
            put("ledger_id", ledgerId)
            put("expense_id", expenseId)
            put("before", current.toJson())
        })
        return updated
    }

    fun attachmentUri(ledgerId: String, relativePath: String): String {
        val file = File(folderFor(ledgerId), relativePath)
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        ).toString()
    }

    private fun normalizeRates(
        home: String,
        rates: Map<String, String>
    ): LinkedHashMap<String, ExchangeRate> {
        val normalizedRates = linkedMapOf<String, ExchangeRate>()
        normalizedRates[home] = ExchangeRate(home, "1")
        rates.forEach { (currencyRaw, valueRaw) ->
            val currency = ExpenseLedgerCalculator.normalizeCurrency(currencyRaw)
            if (currency == home) return@forEach
            val value = ExpenseLedgerCalculator.parseRate(valueRaw)
                .stripTrailingZeros()
                .toPlainString()
            normalizedRates[currency] = ExchangeRate(currency, value)
        }
        return normalizedRates
    }

    private fun saveLedger(ledger: ExpenseLedger) =
        atomicWrite(
            File(folderFor(ledger.ledgerId), "ledger.json"),
            ledger.toJson().toString(2)
        )

    private fun readLedger(file: File): ExpenseLedger {
        require(file.exists()) { "Ledger not found." }
        return ExpenseLedger.fromJson(JSONObject(file.readText()))
    }

    private fun folderFor(ledgerId: String): File = File(root, ledgerId)

    private fun storeAttachments(
        folder: File,
        rowNumber: Int,
        category: String,
        amountHomeExact: String,
        homeCurrency: String,
        rawUris: List<String>
    ): List<String> {
        if (rawUris.isEmpty()) return emptyList()

        val receiptDir = File(folder, "receipts").apply { mkdirs() }
        return rawUris.mapIndexed { index, rawUri ->
            val uri = Uri.parse(rawUri)
            val extension = extensionFor(uri, rawUri)
            val out = File(
                receiptDir,
                attachmentFileName(
                    rowNumber = rowNumber,
                    category = category,
                    amountHomeExact = amountHomeExact,
                    homeCurrency = homeCurrency,
                    extension = extension,
                    index = index,
                    total = rawUris.size
                )
            )
            openInput(rawUri, uri).use { source ->
                out.outputStream().use { target -> source.copyTo(target) }
            }
            require(out.length() > 0L) { "Attachment was empty." }
            "receipts/${out.name}"
        }
    }

    private fun renameExistingAttachments(
        folder: File,
        currentPaths: List<String>,
        rowNumber: Int,
        category: String,
        amountHomeExact: String,
        homeCurrency: String
    ): List<String> {
        if (currentPaths.isEmpty()) return emptyList()
        return currentPaths.mapIndexed { index, relativePath ->
            val current = File(folder, relativePath)
            if (!current.exists()) return@mapIndexed relativePath
            val extension = current.extension.ifBlank { "bin" }.lowercase()
            val desired = File(
                current.parentFile,
                attachmentFileName(
                    rowNumber = rowNumber,
                    category = category,
                    amountHomeExact = amountHomeExact,
                    homeCurrency = homeCurrency,
                    extension = extension,
                    index = index,
                    total = currentPaths.size
                )
            )
            if (current.absolutePath == desired.absolutePath) {
                relativePath
            } else {
                if (desired.exists() && !desired.delete()) {
                    throw IllegalStateException("Could not replace attachment ${desired.name}.")
                }
                if (!current.renameTo(desired)) {
                    throw IllegalStateException("Could not rename attachment ${current.name}.")
                }
                "receipts/${desired.name}"
            }
        }
    }

    private fun attachmentFileName(
        rowNumber: Int,
        category: String,
        amountHomeExact: String,
        homeCurrency: String,
        extension: String,
        index: Int,
        total: Int
    ): String {
        val displayAmount = ExpenseLedgerCalculator.display(
            amountHomeExact.toBigDecimal(),
            homeCurrency
        ).replace(Regex("[^0-9.]"), "")
        val safeCategory = category
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
        val base = "${rowNumber.toString().padStart(3, '0')}_${safeCategory}_${displayAmount}${homeCurrency}"
        val suffix = if (total == 1) "" else "_${(index + 1).toString().padStart(2, '0')}"
        return "$base$suffix.${extension.lowercase()}"
    }

    private fun openInput(rawUri: String, uri: Uri) =
        when (uri.scheme?.lowercase()) {
            "content" -> context.contentResolver.openInputStream(uri)
            "file" -> FileInputStream(File(requireNotNull(uri.path)))
            null, "" -> FileInputStream(File(rawUri))
            else -> context.contentResolver.openInputStream(uri)
        } ?: throw IllegalArgumentException("Could not read attachment.")

    private fun extensionFor(uri: Uri, rawUri: String): String {
        val mime = runCatching { context.contentResolver.getType(uri) }.getOrNull()
        val fromMime = mime?.let {
            MimeTypeMap.getSingleton().getExtensionFromMimeType(it)
        }
        val fromPath = (uri.lastPathSegment ?: rawUri)
            .substringAfterLast('.', "")
            .takeIf { it.matches(Regex("[A-Za-z0-9]{1,8}")) }
        return (fromMime ?: fromPath ?: "bin").lowercase()
    }

    private fun atomicWrite(target: File, text: String) {
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, "${target.name}.tmp")
        tmp.writeText(text)
        if (target.exists() && !target.delete()) error("Could not replace ${target.name}.")
        if (!tmp.renameTo(target)) error("Could not commit ${target.name}.")
    }

    private fun appendEvent(folder: File, event: JSONObject) {
        File(folder, "events.jsonl").appendText(event.toString() + "\n")
    }
}
