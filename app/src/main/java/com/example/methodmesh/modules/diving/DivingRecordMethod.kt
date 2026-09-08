package com.example.methodmesh.modules.diving

import com.example.methodmesh.settings.MethodSetting
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

object DivingRecordSettings {
    val all: Map<String, List<MethodSetting>> = mapOf(
        DivingIds.LOG_RECORD to listOf(
            MethodSetting.TextSetting("log_id", "Dive ID", "Leave blank to create a new local record; supply an existing ID to update it.", "Identity", ""),
            MethodSetting.TextSetting("date_time_iso", "Dive date/time", "ISO-8601 preferred. Blank uses the current time.", "Dive", ""),
            MethodSetting.TextSetting("site", "Dive site", group = "Dive", defaultValue = ""),
            MethodSetting.TextSetting("buddy", "Buddy / team", group = "Dive", defaultValue = ""),
            MethodSetting.FloatSetting("max_depth_m", "Maximum depth", group = "Dive", defaultValue = 20f, minimum = 0f, maximum = 300f, step = 0.5f, unit = "m", decimals = 1),
            MethodSetting.FloatSetting("average_depth_m", "Average depth", "Used for RMV when cylinder pressure data are supplied. Set 0 if unknown.", "Dive", 0f, 0f, 300f, 0.5f, "m", 1),
            MethodSetting.FloatSetting("duration_min", "Duration", group = "Dive", defaultValue = 40f, minimum = 0f, maximum = 1440f, step = 1f, unit = "min", decimals = 1),
            MethodSetting.FloatSetting("cylinder_volume_l", "Cylinder water volume", "Set 0 if not recording cylinder consumption.", "Gas", 0f, 0f, 100f, 0.5f, "L", 1),
            MethodSetting.FloatSetting("start_pressure_bar", "Start pressure", "Set 0 if unknown.", "Gas", 0f, 0f, 400f, 1f, "bar", 0),
            MethodSetting.FloatSetting("end_pressure_bar", "End pressure", "Set 0 if unknown.", "Gas", 0f, 0f, 400f, 1f, "bar", 0),
            MethodSetting.FloatSetting("fo2_percent", "Analysed oxygen", "Set 0 if gas composition was not recorded.", "Gas", 0f, 0f, 100f, 0.1f, "% O₂", 1),
            MethodSetting.FloatSetting("fhe_percent", "Analysed helium", group = "Gas", defaultValue = 0f, minimum = 0f, maximum = 99f, step = 0.1f, unit = "% He", decimals = 1),
            MethodSetting.FloatSetting("water_temperature_c", "Water temperature", "Use a realistic value if known; blank is not representable in FloatSetting, so -99 means unknown.", "Conditions", -99f, -99f, 50f, 0.5f, "°C", 1),
            MethodSetting.FloatSetting("visibility_m", "Visibility", "Set 0 if unknown.", "Conditions", 0f, 0f, 1000f, 0.5f, "m", 1),
            MethodSetting.TextSetting("suit", "Exposure suit", group = "Configuration", defaultValue = ""),
            MethodSetting.FloatSetting("weight_kg", "Weight carried", "Set 0 if unknown.", "Configuration", 0f, 0f, 100f, 0.5f, "kg", 1),
            MethodSetting.TextSetting("notes", "Notes", group = "Dive", defaultValue = ""),
            MethodSetting.ChoiceSetting("water_type", "Water type", group = "Environment", defaultValue = "seawater", choices = listOf("seawater", "freshwater", "custom")),
            MethodSetting.FloatSetting("water_density_kg_m3", "Water density", group = "Environment", defaultValue = 1025f, minimum = 900f, maximum = 1100f, step = 1f, unit = "kg/m³", decimals = 0),
            MethodSetting.FloatSetting("surface_pressure_bar", "Surface pressure", group = "Environment", defaultValue = 1.01325f, minimum = 0.5f, maximum = 1.2f, step = 0.001f, unit = "bar abs", decimals = 3)
        ),
        DivingIds.LOG_DASHBOARD to listOf(
            MethodSetting.IntSetting("recent_limit", "Recent dives shown", group = "Display", defaultValue = 10, minimum = 1, maximum = 50)
        ),
        DivingIds.GAS_ANALYSIS_RECORD to listOf(
            MethodSetting.TextSetting("analysis_id", "Analysis ID", "Leave blank to create a new record.", "Identity", ""),
            MethodSetting.TextSetting("cylinder_id", "Cylinder ID", "Use the same ID as the local cylinder inventory when possible.", "Cylinder", ""),
            MethodSetting.FloatSetting("fo2_percent", "Analysed oxygen", group = "Analysis", defaultValue = 21f, minimum = 1f, maximum = 100f, step = 0.1f, unit = "% O₂", decimals = 1),
            MethodSetting.FloatSetting("fhe_percent", "Analysed helium", group = "Analysis", defaultValue = 0f, minimum = 0f, maximum = 99f, step = 0.1f, unit = "% He", decimals = 1),
            MethodSetting.FloatSetting("pressure_bar", "Cylinder pressure", "Set 0 if not recorded at analysis.", "Cylinder", 0f, 0f, 400f, 1f, "bar", 0),
            MethodSetting.TextSetting("analyser", "Analyser / calibration reference", group = "Analysis", defaultValue = ""),
            MethodSetting.TextSetting("analysed_by", "Analysed by", group = "Analysis", defaultValue = ""),
            MethodSetting.TextSetting("analysed_time_iso", "Analysis time", "ISO-8601 preferred. Blank uses current time.", "Analysis", ""),
            MethodSetting.TextSetting("note", "Note", group = "Analysis", defaultValue = "")
        ),
        DivingIds.CYLINDER_RECORD to listOf(
            MethodSetting.TextSetting("cylinder_id", "Cylinder ID", "Leave blank to create a new local cylinder record.", "Identity", ""),
            MethodSetting.TextSetting("label", "Cylinder label", group = "Cylinder", defaultValue = ""),
            MethodSetting.FloatSetting("water_volume_l", "Water volume", group = "Cylinder", defaultValue = 12f, minimum = 0.5f, maximum = 100f, step = 0.5f, unit = "L", decimals = 1),
            MethodSetting.FloatSetting("working_pressure_bar", "Working pressure", group = "Cylinder", defaultValue = 232f, minimum = 1f, maximum = 400f, step = 1f, unit = "bar", decimals = 0),
            MethodSetting.FloatSetting("current_pressure_bar", "Current pressure", group = "Cylinder", defaultValue = 0f, minimum = 0f, maximum = 400f, step = 1f, unit = "bar", decimals = 0),
            MethodSetting.ChoiceSetting("material", "Cylinder material", group = "Cylinder", defaultValue = "steel", choices = listOf("steel", "aluminium", "composite", "other")),
            MethodSetting.TextSetting("test_due_date", "Test / inspection due date", "Free text/ISO date because requirements vary by jurisdiction and cylinder service.", "Service", ""),
            MethodSetting.TextSetting("service_note", "Service note", group = "Service", defaultValue = ""),
            MethodSetting.BooleanSetting("oxygen_clean", "Oxygen-clean status recorded", "This is only a recorded operator assertion; MethodMesh does not certify oxygen cleanliness.", "Service", false)
        ),
        DivingIds.GAS_DASHBOARD to emptyList()
    )

    fun forMethod(methodId: String): List<MethodSetting> = all[methodId].orEmpty()
}

object DivingRecordMethods {
    val logRecord = DivingCalculationMethod(
        DivingMethodSpec(
            DivingIds.LOG_RECORD,
            "Dive log record",
            "Prepare a traceable local dive-log record; native/intent capability execution persists it after confirmation of valid inputs.",
            listOf(
                DivingFields.LOG_ID, DivingFields.LOG_SITE, DivingFields.LOG_DATE_TIME_ISO,
                DivingFields.LOG_MAX_DEPTH_M, DivingFields.LOG_DURATION_MIN, DivingFields.LOG_RMV_L_MIN,
                DivingFields.LOG_RECORD_JSON
            ),
            "record",
            ::calculateLogRecord
        )
    )

    val logDashboard = DivingCalculationMethod(
        DivingMethodSpec(
            DivingIds.LOG_DASHBOARD,
            "Dive log dashboard",
            "Summarise locally recorded dives; a supplied dive_entries_json can also be used by machine callers.",
            listOf(
                DivingFields.LOG_COUNT, DivingFields.LOG_TOTAL_TIME_MIN, DivingFields.LOG_DEEPEST_M,
                DivingFields.LOG_MEAN_RMV_L_MIN, DivingFields.LOG_RECENT_JSON
            ),
            "record_dashboard",
            ::calculateLogDashboard
        )
    )

    val gasAnalysisRecord = DivingCalculationMethod(
        DivingMethodSpec(
            DivingIds.GAS_ANALYSIS_RECORD,
            "Gas analysis record",
            "Prepare a local analysed-gas record for a cylinder, including measured oxygen/helium and analyser reference.",
            listOf(
                DivingFields.GAS_ANALYSIS_ID, DivingFields.GAS_ANALYSIS_CYLINDER_ID,
                DivingFields.GAS_ANALYSIS_FO2_PERCENT, DivingFields.GAS_ANALYSIS_FHE_PERCENT,
                DivingFields.GAS_ANALYSIS_TIME_ISO, DivingFields.GAS_ANALYSIS_RECORD_JSON
            ),
            "record",
            ::calculateGasAnalysisRecord
        )
    )

    val cylinderRecord = DivingCalculationMethod(
        DivingMethodSpec(
            DivingIds.CYLINDER_RECORD,
            "Cylinder record",
            "Create or update a local cylinder inventory record with nominal capacity and service metadata.",
            listOf(
                DivingFields.CYLINDER_ID, DivingFields.CYLINDER_LABEL, DivingFields.CYLINDER_WATER_VOLUME_L,
                DivingFields.CYLINDER_WORKING_PRESSURE_BAR, DivingFields.CYLINDER_CURRENT_PRESSURE_BAR,
                DivingFields.CYLINDER_TOTAL_GAS_L, DivingFields.CYLINDER_RECORD_JSON
            ),
            "record",
            ::calculateCylinderRecord
        )
    )

    val gasDashboard = DivingCalculationMethod(
        DivingMethodSpec(
            DivingIds.GAS_DASHBOARD,
            "Cylinder / gas dashboard",
            "Summarise local cylinder inventory and gas analyses; machine callers may supply cylinders_json and analyses_json.",
            listOf(
                DivingFields.GAS_CYLINDER_COUNT, DivingFields.GAS_ANALYSIS_COUNT,
                DivingFields.GAS_TOTAL_NOMINAL_L, DivingFields.GAS_INVENTORY_JSON
            ),
            "record_dashboard",
            ::calculateGasDashboard
        )
    )

    val all = listOf(logRecord, logDashboard, gasAnalysisRecord, cylinderRecord, gasDashboard)
    fun byId(id: String): DivingCalculationMethod? = all.firstOrNull { it.id == id }
}

private fun calculateLogRecord(s: Map<String, String>): Map<String, String> {
    val id = s.v("log_id") ?: DivingRepository.newId("dive")
    val dateTime = s.v("date_time_iso") ?: Instant.now().toString()
    val maxDepth = s.d("max_depth_m", 20.0)
    val avgDepth = s.d("average_depth_m", 0.0).takeIf { it > 0.0 }
    val duration = s.d("duration_min", 40.0)
    require(duration > 0.0) { "Dive duration must be greater than zero." }
    val cylinder = s.d("cylinder_volume_l", 0.0).takeIf { it > 0.0 }
    val start = s.d("start_pressure_bar", 0.0).takeIf { it > 0.0 }
    val end = s.d("end_pressure_bar", 0.0).takeIf { it > 0.0 }
    val rmv = if (cylinder != null && start != null && end != null && avgDepth != null && start >= end) {
        DivingAlgorithms.sacRmv(cylinder, start, end, avgDepth, duration, s.waterDensity(), s.surfacePressure()).rmvLMin
    } else null
    val entry = DivingRepository.DiveLogEntry(
        id = id,
        dateTimeIso = dateTime,
        site = s.v("site").orEmpty(),
        buddy = s.v("buddy").orEmpty(),
        maxDepthM = maxDepth,
        averageDepthM = avgDepth,
        durationMin = duration,
        cylinderVolumeL = cylinder,
        startPressureBar = start,
        endPressureBar = end,
        fo2Percent = s.d("fo2_percent", 0.0).takeIf { it > 0.0 },
        fhePercent = s.d("fhe_percent", 0.0).takeIf { it > 0.0 },
        waterTemperatureC = s.d("water_temperature_c", -99.0).takeIf { it > -90.0 },
        visibilityM = s.d("visibility_m", 0.0).takeIf { it > 0.0 },
        suit = s.v("suit").orEmpty(),
        weightKg = s.d("weight_kg", 0.0).takeIf { it > 0.0 },
        notes = s.v("notes").orEmpty(),
        rmvLMin = rmv,
        recordedTimeIso = Instant.now().toString()
    )
    val outputs = linkedMapOf(
        DivingFields.LOG_ID to entry.id,
        DivingFields.LOG_SITE to entry.site,
        DivingFields.LOG_DATE_TIME_ISO to entry.dateTimeIso,
        DivingFields.LOG_MAX_DEPTH_M to f(entry.maxDepthM, 1),
        DivingFields.LOG_DURATION_MIN to f(entry.durationMin, 1),
        DivingFields.LOG_RMV_L_MIN to (entry.rmvLMin?.let { f(it, 1) } ?: ""),
        DivingFields.LOG_RECORD_JSON to DivingRepository.diveToJson(entry).toString()
    )
    return success(
        DivingIds.LOG_RECORD, "record", s, outputs,
        warnings = if (rmv == null) listOf("RMV was not calculated because complete cylinder-consumption and average-depth inputs were not supplied.") else emptyList(),
        assumptions = listOf("Record values are operator-supplied; MethodMesh does not verify dive-computer data or buddy identity.")
    )
}

private fun calculateLogDashboard(s: Map<String, String>): Map<String, String> {
    val entries = DivingRepository.parseDivesJson(s.v("dive_entries_json").orEmpty())
    val limit = s.v("recent_limit")?.toIntOrNull()?.coerceIn(1, 50) ?: 10
    val summary = DivingRepository.logSummary(entries, limit)
    val recent = JSONArray().apply { summary.recent.forEach { put(DivingRepository.diveToJson(it)) } }
    return success(
        DivingIds.LOG_DASHBOARD,
        "record_dashboard",
        s.filterKeys { it != "dive_entries_json" },
        linkedMapOf(
            DivingFields.LOG_COUNT to summary.count.toString(),
            DivingFields.LOG_TOTAL_TIME_MIN to f(summary.totalTimeMin, 0),
            DivingFields.LOG_DEEPEST_M to f(summary.deepestM, 1),
            DivingFields.LOG_MEAN_RMV_L_MIN to (summary.meanRmvLMin?.let { f(it, 1) } ?: ""),
            DivingFields.LOG_RECENT_JSON to recent.toString()
        ),
        warnings = emptyList(),
        assumptions = listOf("Summary reflects only the supplied/local MethodMesh log records.")
    )
}

private fun calculateGasAnalysisRecord(s: Map<String, String>): Map<String, String> {
    val fo2 = s.d("fo2_percent", 21.0)
    val fhe = s.d("fhe_percent", 0.0)
    require(fo2 > 0.0 && fo2 <= 100.0) { "Analysed oxygen must be between 0 and 100%." }
    require(fhe >= 0.0 && fo2 + fhe <= 100.0 + 1e-9) { "Oxygen plus helium cannot exceed 100%." }
    val entry = DivingRepository.GasAnalysisEntry(
        id = s.v("analysis_id") ?: DivingRepository.newId("gas"),
        cylinderId = s.v("cylinder_id").orEmpty(),
        fo2Percent = fo2,
        fhePercent = fhe,
        pressureBar = s.d("pressure_bar", 0.0).takeIf { it > 0.0 },
        analyser = s.v("analyser").orEmpty(),
        analysedBy = s.v("analysed_by").orEmpty(),
        analysedTimeIso = s.v("analysed_time_iso") ?: Instant.now().toString(),
        note = s.v("note").orEmpty()
    )
    return success(
        DivingIds.GAS_ANALYSIS_RECORD, "record", s,
        linkedMapOf(
            DivingFields.GAS_ANALYSIS_ID to entry.id,
            DivingFields.GAS_ANALYSIS_CYLINDER_ID to entry.cylinderId,
            DivingFields.GAS_ANALYSIS_FO2_PERCENT to f(entry.fo2Percent, 1),
            DivingFields.GAS_ANALYSIS_FHE_PERCENT to f(entry.fhePercent, 1),
            DivingFields.GAS_ANALYSIS_TIME_ISO to entry.analysedTimeIso,
            DivingFields.GAS_ANALYSIS_RECORD_JSON to DivingRepository.analysisToJson(entry).toString()
        ),
        warnings = listOf("This records an operator-entered analyser reading; it does not perform or certify the gas analysis."),
        assumptions = emptyList()
    )
}

private fun calculateCylinderRecord(s: Map<String, String>): Map<String, String> {
    val entry = DivingRepository.CylinderEntry(
        id = s.v("cylinder_id") ?: DivingRepository.newId("cyl"),
        label = s.v("label").orEmpty(),
        waterVolumeL = s.d("water_volume_l", 12.0),
        workingPressureBar = s.d("working_pressure_bar", 232.0),
        currentPressureBar = s.d("current_pressure_bar", 0.0),
        material = s.v("material") ?: "steel",
        testDueDate = s.v("test_due_date").orEmpty(),
        serviceNote = s.v("service_note").orEmpty(),
        oxygenClean = (s.v("oxygen_clean") ?: "false").toBoolean(),
        updatedTimeIso = Instant.now().toString()
    )
    require(entry.waterVolumeL > 0.0) { "Cylinder water volume must be greater than zero." }
    require(entry.workingPressureBar > 0.0) { "Working pressure must be greater than zero." }
    require(entry.currentPressureBar >= 0.0) { "Current pressure cannot be negative." }
    val nominal = entry.waterVolumeL * entry.currentPressureBar
    return success(
        DivingIds.CYLINDER_RECORD, "record", s,
        linkedMapOf(
            DivingFields.CYLINDER_ID to entry.id,
            DivingFields.CYLINDER_LABEL to entry.label,
            DivingFields.CYLINDER_WATER_VOLUME_L to f(entry.waterVolumeL, 1),
            DivingFields.CYLINDER_WORKING_PRESSURE_BAR to f(entry.workingPressureBar, 0),
            DivingFields.CYLINDER_CURRENT_PRESSURE_BAR to f(entry.currentPressureBar, 0),
            DivingFields.CYLINDER_TOTAL_GAS_L to f(nominal, 0),
            DivingFields.CYLINDER_RECORD_JSON to DivingRepository.cylinderToJson(entry).toString()
        ),
        warnings = buildList {
            if (entry.currentPressureBar > entry.workingPressureBar) add("Current pressure exceeds the entered working pressure; verify the record.")
            if (entry.oxygenClean) add("Oxygen-clean is a recorded status only; MethodMesh does not certify cylinder cleanliness or serviceability.")
            add("Nominal gas volume uses pressure x water-volume and does not correct for real-gas compressibility.")
        },
        assumptions = emptyList()
    )
}

private fun calculateGasDashboard(s: Map<String, String>): Map<String, String> {
    val cylinders = DivingRepository.parseCylindersJson(s.v("cylinders_json").orEmpty())
    val analyses = DivingRepository.parseAnalysesJson(s.v("analyses_json").orEmpty())
    val summary = DivingRepository.gasSummary(cylinders, analyses)
    val latestByCylinder = analyses.groupBy { it.cylinderId }.mapValues { (_, values) -> values.maxByOrNull { it.analysedTimeIso } }
    val inventory = JSONArray().apply {
        cylinders.forEach { c ->
            val latest = latestByCylinder[c.id]
            put(JSONObject().apply {
                put("cylinder", DivingRepository.cylinderToJson(c))
                if (latest != null) put("latest_analysis", DivingRepository.analysisToJson(latest))
                put("nominal_current_gas_l", c.waterVolumeL * c.currentPressureBar)
            })
        }
    }
    return success(
        DivingIds.GAS_DASHBOARD,
        "record_dashboard",
        s.filterKeys { it !in setOf("cylinders_json", "analyses_json") },
        linkedMapOf(
            DivingFields.GAS_CYLINDER_COUNT to summary.cylinderCount.toString(),
            DivingFields.GAS_ANALYSIS_COUNT to summary.analysisCount.toString(),
            DivingFields.GAS_TOTAL_NOMINAL_L to f(summary.totalNominalGasL, 0),
            DivingFields.GAS_INVENTORY_JSON to inventory.toString()
        ),
        warnings = listOf("Inventory values are records, not proof that a cylinder is in test, correctly labelled, adequately analysed or fit for use."),
        assumptions = listOf("Nominal stored-gas total uses current pressure x water volume.")
    )
}
