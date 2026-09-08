package com.example.methodmesh.modules.expenses

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

object ExpenseCategories {
    val all = listOf(
        "travel",
        "local_transport",
        "accommodation",
        "subsistence",
        "fees_registrations",
        "supplies_services",
        "communications",
        "miscellaneous"
    )

    fun label(value: String): String = when (value) {
        "travel" -> "Travel"
        "local_transport" -> "Local transport"
        "accommodation" -> "Accommodation"
        "subsistence" -> "Subsistence"
        "fees_registrations" -> "Fees / registrations"
        "supplies_services" -> "Supplies / services"
        "communications" -> "Communications"
        "miscellaneous" -> "Miscellaneous"
        else -> value.replace('_', ' ').replaceFirstChar { it.uppercase() }
    }
}

data class ExchangeRate(
    val currency: String,
    val foreignUnitsPerHomeUnit: String
) {
    fun toJson() = JSONObject().apply {
        put("currency", currency)
        put("foreign_units_per_home_unit", foreignUnitsPerHomeUnit)
    }

    companion object {
        fun fromJson(json: JSONObject) = ExchangeRate(
            currency = json.getString("currency"),
            foreignUnitsPerHomeUnit = json.getString("foreign_units_per_home_unit")
        )
    }
}

data class ExpenseEntry(
    val expenseId: String,
    val rowNumber: Int,
    val expenseDate: String,
    val createdAtIso: String,
    val updatedAtIso: String,
    val category: String,
    val amountOriginal: String,
    val originalCurrency: String,
    val appliedForeignUnitsPerHomeUnit: String,
    val homeCurrency: String,
    val amountHomeExact: String,
    val note: String,
    val evidenceStatus: String,
    val attachmentPaths: List<String>,
    val status: String = "active"
) {
    fun toJson() = JSONObject().apply {
        put("expense_id", expenseId)
        put("row_number", rowNumber)
        put("expense_date", expenseDate)
        put("created_at_iso", createdAtIso)
        put("updated_at_iso", updatedAtIso)
        put("category", category)
        put("amount_original", amountOriginal)
        put("original_currency", originalCurrency)
        put("applied_foreign_units_per_home_unit", appliedForeignUnitsPerHomeUnit)
        // Backward-readable alias for v0.3 exports.
        put("rate_snapshot_foreign_units_per_home_unit", appliedForeignUnitsPerHomeUnit)
        put("home_currency", homeCurrency)
        put("amount_home_exact", amountHomeExact)
        put("note", note)
        put("evidence_status", evidenceStatus)
        put("receipt_status", evidenceStatus)
        put("attachment_paths", JSONArray(attachmentPaths))
        put("receipt_paths", JSONArray(attachmentPaths))
        put("status", status)
    }

    companion object {
        fun fromJson(json: JSONObject): ExpenseEntry {
            val attachments = json.optJSONArray("attachment_paths")
                ?: json.optJSONArray("receipt_paths")
                ?: JSONArray()
            val appliedRate = json.optString(
                "applied_foreign_units_per_home_unit",
                json.optString("rate_snapshot_foreign_units_per_home_unit", "1")
            )
            return ExpenseEntry(
                expenseId = json.getString("expense_id"),
                rowNumber = json.optInt("row_number", 0),
                expenseDate = json.getString("expense_date"),
                createdAtIso = json.getString("created_at_iso"),
                updatedAtIso = json.optString("updated_at_iso", json.getString("created_at_iso")),
                category = json.getString("category"),
                amountOriginal = json.getString("amount_original"),
                originalCurrency = json.getString("original_currency"),
                appliedForeignUnitsPerHomeUnit = appliedRate,
                homeCurrency = json.getString("home_currency"),
                amountHomeExact = json.getString("amount_home_exact"),
                note = json.optString("note"),
                evidenceStatus = json.optString(
                    "evidence_status",
                    json.optString("receipt_status", "no_receipt")
                ),
                attachmentPaths = (0 until attachments.length()).map { attachments.getString(it) },
                status = json.optString("status", "active")
            )
        }
    }
}

data class ExpenseLedger(
    val ledgerId: String,
    val name: String,
    val homeCurrency: String,
    val exchangeRates: Map<String, ExchangeRate>,
    val createdAtIso: String,
    val updatedAtIso: String,
    val expenses: List<ExpenseEntry>
) {
    fun toJson() = JSONObject().apply {
        put("schema_version", 3)
        put("ledger_id", ledgerId)
        put("name", name)
        put("home_currency", homeCurrency)
        put("created_at_iso", createdAtIso)
        put("updated_at_iso", updatedAtIso)
        put("exchange_rates", JSONObject().apply {
            exchangeRates.forEach { (currency, rate) -> put(currency, rate.toJson()) }
        })
        put("expenses", JSONArray(expenses.map { it.toJson() }))
    }

    companion object {
        fun fromJson(json: JSONObject): ExpenseLedger {
            val ratesJson = json.optJSONObject("exchange_rates") ?: JSONObject()
            val rates = linkedMapOf<String, ExchangeRate>()
            ratesJson.keys().forEach { key ->
                rates[key] = ExchangeRate.fromJson(ratesJson.getJSONObject(key))
            }
            val expenseJson = json.optJSONArray("expenses") ?: JSONArray()
            val parsed = (0 until expenseJson.length()).mapIndexed { index, i ->
                val entry = ExpenseEntry.fromJson(expenseJson.getJSONObject(i))
                if (entry.rowNumber > 0) entry else entry.copy(rowNumber = index + 1)
            }
            return ExpenseLedger(
                ledgerId = json.getString("ledger_id"),
                name = json.getString("name"),
                homeCurrency = json.getString("home_currency"),
                exchangeRates = rates,
                createdAtIso = json.getString("created_at_iso"),
                updatedAtIso = json.getString("updated_at_iso"),
                expenses = parsed
            )
        }

        fun new(
            name: String,
            homeCurrency: String,
            exchangeRates: Map<String, ExchangeRate>
        ): ExpenseLedger {
            val now = Instant.now().toString()
            return ExpenseLedger(
                ledgerId = UUID.randomUUID().toString(),
                name = name,
                homeCurrency = homeCurrency,
                exchangeRates = exchangeRates,
                createdAtIso = now,
                updatedAtIso = now,
                expenses = emptyList()
            )
        }
    }
}

data class LedgerSummary(
    val totalHomeExact: java.math.BigDecimal,
    val expenseCount: Int,
    val noReceiptCount: Int,
    val byCategory: Map<String, java.math.BigDecimal>
)
