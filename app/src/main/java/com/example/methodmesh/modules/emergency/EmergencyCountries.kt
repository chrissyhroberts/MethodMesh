package com.example.methodmesh.modules.emergency

import java.util.Locale

data class EmergencyCountry(val code: String, val name: String) {
    val label: String get() = "$name — $code"
}

object EmergencyCountries {
    val all: List<EmergencyCountry> by lazy {
        Locale.getISOCountries()
            .map { code ->
                val normalized = code.uppercase(Locale.ROOT)
                val locale = Locale.Builder().setRegion(normalized).build()
                EmergencyCountry(normalized, locale.getDisplayCountry(Locale.getDefault()).ifBlank { normalized })
            }
            .sortedWith(compareBy<EmergencyCountry> { it.name.lowercase(Locale.getDefault()) }.thenBy { it.code })
    }

    fun normalizeCode(raw: String?): String {
        val value = raw.orEmpty().trim()
        if (value.isBlank()) return ""
        val direct = value.uppercase(Locale.ROOT).takeIf { candidate -> all.any { it.code == candidate } }
        if (direct != null) return direct
        return all.firstOrNull { country ->
            value.equals(country.label, ignoreCase = true) ||
                value.equals(country.name, ignoreCase = true) ||
                value.startsWith("${country.code} ", ignoreCase = true) ||
                value.endsWith(" ${country.code}", ignoreCase = true) ||
                value.endsWith("— ${country.code}", ignoreCase = true)
        }?.code.orEmpty()
    }

    fun labelFor(code: String?): String {
        val normalized = normalizeCode(code)
        return all.firstOrNull { it.code == normalized }?.label ?: code.orEmpty()
    }
}
