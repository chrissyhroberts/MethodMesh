package com.example.methodmesh.modules.expenses

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Currency

object ExpenseLedgerCalculator {
    fun normalizeCurrency(value: String): String {
        val code = value.trim().uppercase()
        require(code.matches(Regex("[A-Z]{3}"))) { "Currency must be a three-letter ISO code." }
        Currency.getInstance(code)
        return code
    }

    fun parseMoney(value: String): BigDecimal =
        value.trim().toBigDecimalOrNull()?.also {
            require(it > BigDecimal.ZERO) { "Amount must be greater than zero." }
        } ?: throw IllegalArgumentException("Amount must be numeric.")

    fun parseRate(value: String): BigDecimal =
        value.trim().toBigDecimalOrNull()?.also {
            require(it > BigDecimal.ZERO) { "Exchange rate must be greater than zero." }
        } ?: throw IllegalArgumentException("Exchange rate must be numeric.")

    fun convertToHome(
        amountOriginal: BigDecimal,
        originalCurrency: String,
        homeCurrency: String,
        foreignUnitsPerHomeUnit: BigDecimal
    ): BigDecimal =
        if (originalCurrency == homeCurrency) amountOriginal
        else amountOriginal.divide(foreignUnitsPerHomeUnit, 16, RoundingMode.HALF_UP)

    fun summarize(ledger: ExpenseLedger): LedgerSummary {
        val active = ledger.expenses.filter { it.status == "active" }
        val total = active.fold(BigDecimal.ZERO) { acc, e -> acc + e.amountHomeExact.toBigDecimal() }
        val grouped = active
            .groupBy { it.category }
            .mapValues { (_, entries) ->
                entries.fold(BigDecimal.ZERO) { acc, e -> acc + e.amountHomeExact.toBigDecimal() }
            }
        return LedgerSummary(
            totalHomeExact = total,
            expenseCount = active.size,
            noReceiptCount = active.count { it.evidenceStatus == "no_receipt" },
            byCategory = grouped
        )
    }

    fun display(value: BigDecimal, currencyCode: String): String {
        val scale = Currency.getInstance(currencyCode).defaultFractionDigits.coerceAtLeast(0)
        return value.setScale(scale, RoundingMode.HALF_UP).toPlainString()
    }

    fun symbol(currencyCode: String): String =
        runCatching { Currency.getInstance(currencyCode).symbol }.getOrDefault(currencyCode)
}
