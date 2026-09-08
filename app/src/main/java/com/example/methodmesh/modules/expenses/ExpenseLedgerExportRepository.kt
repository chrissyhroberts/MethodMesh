package com.example.methodmesh.modules.expenses

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ExpenseLedgerExportRepository(private val context: Context) {

    fun exportCsv(ledger: ExpenseLedger): File {
        val file = exportFile(ledger, "csv")
        file.writeText(buildCsv(ledger))
        return file
    }

    fun exportJson(ledger: ExpenseLedger): File {
        val file = exportFile(ledger, "json")
        file.writeText(ledger.toJson().toString(2))
        return file
    }

    fun exportSummary(ledger: ExpenseLedger): File {
        val file = exportFile(ledger, "txt")
        file.writeText(buildSummary(ledger))
        return file
    }

    fun exportZip(ledger: ExpenseLedger): File {
        val file = exportFile(ledger, "zip")
        val ledgerFolder = File(context.filesDir, "methodmesh/expenses/${ledger.ledgerId}")
        ZipOutputStream(file.outputStream().buffered()).use { zip ->
            addText(zip, "expenses.csv", buildCsv(ledger))
            addText(zip, "ledger.json", ledger.toJson().toString(2))
            addText(zip, "summary.txt", buildSummary(ledger))

            val events = File(ledgerFolder, "events.jsonl")
            if (events.exists()) addFile(zip, events, "events.jsonl")

            val receipts = File(ledgerFolder, "receipts")
            receipts.listFiles().orEmpty()
                .filter { it.isFile }
                .sortedBy { it.name }
                .forEach { addFile(zip, it, "receipts/${it.name}") }
        }
        return file
    }

    fun shareUri(file: File): Uri =
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

    fun buildSummary(ledger: ExpenseLedger): String {
        val summary = ExpenseLedgerCalculator.summarize(ledger)
        val symbol = ExpenseLedgerCalculator.symbol(ledger.homeCurrency)
        val lines = mutableListOf<String>()
        lines += ledger.name
        lines += ""
        lines += "Total: $symbol${ExpenseLedgerCalculator.display(summary.totalHomeExact, ledger.homeCurrency)} ${ledger.homeCurrency}"
        lines += "Expenses: ${summary.expenseCount}"
        lines += "Without receipt: ${summary.noReceiptCount}"
        lines += ""
        lines += "By category"
        ExpenseCategories.all.forEach { category ->
            val value = summary.byCategory[category] ?: return@forEach
            lines += "${ExpenseCategories.label(category)}: $symbol${ExpenseLedgerCalculator.display(value, ledger.homeCurrency)}"
        }
        lines += ""
        lines += "Transactions"
        ledger.expenses
            .filter { it.status == "active" }
            .sortedWith(compareByDescending<ExpenseEntry> { it.expenseDate }.thenByDescending { it.createdAtIso })
            .forEach { e ->
                val home = ExpenseLedgerCalculator.display(e.amountHomeExact.toBigDecimal(), ledger.homeCurrency)
                val note = e.note.ifBlank { ExpenseCategories.label(e.category) }
                lines += "${e.expenseDate} | $note | ${e.originalCurrency} ${e.amountOriginal} | ${ledger.homeCurrency} $home | ${e.evidenceStatus}"
            }
        return lines.joinToString("\n") + "\n"
    }

    private fun buildCsv(ledger: ExpenseLedger): String {
        val rows = mutableListOf<List<String>>()
        rows += listOf(
            "expense_id",
            "row_number",
            "date",
            "category",
            "note",
            "original_amount",
            "original_currency",
            "foreign_units_per_home_unit",
            "home_amount_exact",
            "home_currency",
            "evidence_status",
            "attachment_files",
            "status"
        )
        ledger.expenses
            .sortedWith(compareBy<ExpenseEntry> { it.expenseDate }.thenBy { it.createdAtIso })
            .forEach { e ->
                rows += listOf(
                    e.expenseId,
                    e.rowNumber.toString(),
                    e.expenseDate,
                    ExpenseCategories.label(e.category),
                    e.note,
                    e.amountOriginal,
                    e.originalCurrency,
                    e.appliedForeignUnitsPerHomeUnit,
                    e.amountHomeExact,
                    e.homeCurrency,
                    e.evidenceStatus,
                    e.attachmentPaths.joinToString("|"),
                    e.status
                )
            }
        return rows.joinToString("\n") { row -> row.joinToString(",") { csvEscape(it) } } + "\n"
    }

    private fun csvEscape(value: String): String =
        "\"" + value.replace("\"", "\"\"") + "\""

    private fun exportFile(ledger: ExpenseLedger, extension: String): File {
        val dir = File(context.cacheDir, "expense_exports").apply { mkdirs() }
        val safe = ledger.name
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .ifBlank { "expenses" }
        return File(dir, "${safe}_${LocalDate.now()}.$extension")
    }

    private fun addText(zip: ZipOutputStream, path: String, text: String) {
        zip.putNextEntry(ZipEntry(path))
        zip.write(text.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun addFile(zip: ZipOutputStream, file: File, path: String) {
        zip.putNextEntry(ZipEntry(path))
        file.inputStream().use { it.copyTo(zip) }
        zip.closeEntry()
    }
}
