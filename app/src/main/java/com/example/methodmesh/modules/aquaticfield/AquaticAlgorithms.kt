package com.example.methodmesh.modules.aquaticfield

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure scientific/field algorithms for the aquaticfield module.
 *
 * These functions deliberately expose the algorithm name and warnings.  A value
 * is not promoted to a more sophisticated thermodynamic quantity merely because
 * a convenient approximation exists.
 */
object AquaticAlgorithms {

    data class Outcome(
        val status: String = "succeeded",
        val result: String,
        val values: Map<String, String>,
        val algorithm: String,
        val warnings: List<String> = emptyList(),
        val error: String = ""
    )

    fun pressureDepth(settings: Map<String, String>): Outcome {
        val mode = settings.value("mode") ?: "pressure_to_depth"
        val latitude = settings.double("latitude_deg") ?: 0.0
        val pressureOffset = settings.double("pressure_offset_dbar") ?: 0.0
        val verticalOffset = settings.double("vertical_offset_m") ?: 0.0
        val correctionFactor = settings.double("correction_factor") ?: 1.0
        val warnings = mutableListOf<String>()

        return when (mode) {
            "pressure_to_depth" -> {
                val pRaw = settings.double("pressure_dbar")
                    ?: return failed("Pressure is required.", "UNESCO_1983_pressure_depth")
                val p = pRaw - pressureOffset
                if (p < 0) return failed("Corrected sea pressure cannot be negative.", "UNESCO_1983_pressure_depth")
                val depth = unescoDepthFromPressure(p, latitude) * correctionFactor + verticalOffset
                Outcome(
                    result = "${fmt(depth, 3)} m",
                    values = linkedMapOf(
                        "aquatic_depth_m" to fmt(depth, 6),
                        "aquatic_pressure_dbar" to fmt(p, 6),
                        "aquatic_depth_correction_m" to fmt(verticalOffset, 6),
                        "aquatic_depth_is_estimated" to "false"
                    ),
                    algorithm = "UNESCO_1983_pressure_depth",
                    warnings = warnings
                )
            }

            "depth_to_pressure" -> {
                val depth = settings.double("depth_m")
                    ?: return failed("Depth is required.", "UNESCO_1983_pressure_depth_inverse")
                if (depth < 0) return failed("Depth cannot be negative.", "UNESCO_1983_pressure_depth_inverse")
                val adjusted = (depth - verticalOffset) / correctionFactor
                val p = pressureFromDepthIterative(adjusted, latitude) + pressureOffset
                Outcome(
                    result = "${fmt(p, 3)} dbar",
                    values = linkedMapOf(
                        "aquatic_depth_m" to fmt(depth, 6),
                        "aquatic_pressure_dbar" to fmt(p, 6),
                        "aquatic_depth_correction_m" to fmt(verticalOffset, 6),
                        "aquatic_depth_is_estimated" to "false"
                    ),
                    algorithm = "UNESCO_1983_pressure_depth_inverse",
                    warnings = warnings
                )
            }

            "wire_out" -> {
                val wire = settings.double("wire_length_m")
                    ?: return failed("Wire length is required.", "simple_wire_geometry")
                val angle = settings.double("cable_angle_deg")
                    ?: return failed("Cable angle is required.", "simple_wire_geometry")
                val convention = settings.value("angle_convention") ?: "from_vertical"
                if (wire < 0) return failed("Wire length cannot be negative.", "simple_wire_geometry")
                if (angle !in 0.0..90.0) return failed("Cable angle must be between 0 and 90 degrees.", "simple_wire_geometry")
                val radians = angle * PI / 180.0
                val vertical = if (convention == "from_horizontal") wire * sin(radians) else wire * cos(radians)
                val depth = vertical * correctionFactor + verticalOffset
                warnings += "Wire-out depth is a simple geometric estimate; cable stretch and catenary are not modelled."
                Outcome(
                    result = "Estimated depth ${fmt(depth, 2)} m",
                    values = linkedMapOf(
                        "aquatic_depth_m" to fmt(depth, 6),
                        "aquatic_pressure_dbar" to "",
                        "aquatic_depth_correction_m" to fmt(verticalOffset, 6),
                        "aquatic_depth_is_estimated" to "true"
                    ),
                    algorithm = "simple_wire_geometry",
                    warnings = warnings
                )
            }

            else -> failed("Unsupported pressure/depth mode: $mode", "none")
        }
    }

    /**
     * UNESCO 1983 / Saunders pressure-to-depth polynomial used by classic
     * seawater toolkits.  p is sea pressure in dbar, latitude in degrees.
     *
     * This is intentionally labelled UNESCO rather than GSW z_from_p: the latter
     * is a TEOS-10 thermodynamic height calculation and should only be exposed
     * when a validated GSW implementation is bundled.
     */
    fun unescoDepthFromPressure(pDbar: Double, latitudeDeg: Double): Double {
        val x = sin(latitudeDeg * PI / 180.0)
        val x2 = x * x
        val gravity = 9.780318 * (1.0 + (5.2788e-3 + 2.36e-5 * x2) * x2) + 1.092e-6 * pDbar
        val numerator =
            (((-1.82e-15 * pDbar + 2.279e-10) * pDbar - 2.2512e-5) * pDbar + 9.72659) * pDbar
        return numerator / gravity
    }

    fun pressureFromDepthIterative(depthM: Double, latitudeDeg: Double): Double {
        if (depthM <= 0.0) return 0.0
        var lo = 0.0
        var hi = max(20.0, depthM * 1.2 + 20.0)
        while (unescoDepthFromPressure(hi, latitudeDeg) < depthM && hi < 120000.0) hi *= 2.0
        repeat(80) {
            val mid = (lo + hi) / 2.0
            if (unescoDepthFromPressure(mid, latitudeDeg) < depthM) lo = mid else hi = mid
        }
        return (lo + hi) / 2.0
    }

    fun salinity(settings: Map<String, String>): Outcome {
        val mode = settings.value("mode") ?: "practical_salinity_from_conductivity"
        val warnings = mutableListOf<String>()

        return when (mode) {
            "conductivity_units" -> {
                val input = settings.double("conductivity")
                    ?: return failed("Conductivity is required.", "unit_conversion")
                val unit = settings.value("conductivity_unit") ?: "mS/cm"
                val msCm = conductivityToMilliSiemensPerCm(input, unit)
                    ?: return failed("Unsupported conductivity unit: $unit", "unit_conversion")
                Outcome(
                    result = "${fmt(msCm, 6)} mS/cm",
                    values = salinityValueMap(
                        conductivityMsCm = msCm,
                        practicalSalinity = null,
                        specificConductance = null,
                        tds = null,
                        referenceSalinity = null,
                        absoluteSalinity = null
                    ),
                    algorithm = "exact_unit_conversion"
                )
            }

            "practical_salinity_from_conductivity" -> {
                val input = settings.double("conductivity")
                    ?: return failed("Conductivity is required.", "PSS-78")
                val unit = settings.value("conductivity_unit") ?: "mS/cm"
                val c = conductivityToMilliSiemensPerCm(input, unit)
                    ?: return failed("Unsupported conductivity unit: $unit", "PSS-78")
                val t = settings.double("temperature_c")
                    ?: return failed("Temperature is required for Practical Salinity.", "PSS-78")
                val p = settings.double("pressure_dbar") ?: 0.0
                if (c < 0 || p < 0) return failed("Conductivity and pressure cannot be negative.", "PSS-78")
                val sp = practicalSalinityPss78(c, t, p)
                if (!sp.isFinite()) return failed("Practical Salinity could not be calculated from these inputs.", "PSS-78")
                if (sp < 2.0) warnings += "Practical Salinity is below 2; low-salinity behaviour needs particular care and should be checked against a validated GSW implementation."
                val sr = sp * (35.16504 / 35.0)
                warnings += "Reference Salinity is returned for convenience; it is not Absolute Salinity. Absolute Salinity requires a validated TEOS-10 SAAR data implementation."
                Outcome(
                    result = "Practical Salinity ${fmt(sp, 4)}",
                    values = salinityValueMap(
                        conductivityMsCm = c,
                        practicalSalinity = sp,
                        specificConductance = null,
                        tds = null,
                        referenceSalinity = sr,
                        absoluteSalinity = null
                    ),
                    algorithm = "PSS-78 (TEOS-10 compatible Practical Salinity definition)",
                    warnings = warnings
                )
            }

            "specific_conductance_25" -> {
                val input = settings.double("conductivity")
                    ?: return failed("Conductivity is required.", "linear_temperature_compensation")
                val unit = settings.value("conductivity_unit") ?: "uS/cm"
                val cUs = conductivityToMicroSiemensPerCm(input, unit)
                    ?: return failed("Unsupported conductivity unit: $unit", "linear_temperature_compensation")
                val t = settings.double("temperature_c")
                    ?: return failed("Measured temperature is required.", "linear_temperature_compensation")
                val alpha = settings.double("temperature_coefficient_per_c") ?: 0.02
                val denom = 1.0 + alpha * (t - 25.0)
                if (denom <= 0.0) return failed("Temperature compensation denominator is not physically usable.", "linear_temperature_compensation")
                val sc25 = cUs / denom
                warnings += "Specific-conductance temperature compensation uses the operator-supplied linear coefficient."
                Outcome(
                    result = "${fmt(sc25, 2)} µS/cm at 25 °C",
                    values = salinityValueMap(
                        conductivityMsCm = cUs / 1000.0,
                        practicalSalinity = null,
                        specificConductance = sc25,
                        tds = null,
                        referenceSalinity = null,
                        absoluteSalinity = null
                    ),
                    algorithm = "linear_temperature_compensation",
                    warnings = warnings
                )
            }

            "tds_estimate" -> {
                val input = settings.double("conductivity")
                    ?: return failed("Conductivity is required.", "empirical_conductivity_to_tds")
                val unit = settings.value("conductivity_unit") ?: "uS/cm"
                val cUs = conductivityToMicroSiemensPerCm(input, unit)
                    ?: return failed("Unsupported conductivity unit: $unit", "empirical_conductivity_to_tds")
                val coefficient = settings.double("tds_coefficient") ?: 0.65
                if (coefficient <= 0) return failed("TDS coefficient must be greater than zero.", "empirical_conductivity_to_tds")
                val tds = cUs * coefficient
                warnings += "TDS is an empirical estimate using the supplied coefficient; it is not a universal conductivity conversion."
                Outcome(
                    result = "Estimated TDS ${fmt(tds, 2)} mg/L",
                    values = salinityValueMap(
                        conductivityMsCm = cUs / 1000.0,
                        practicalSalinity = null,
                        specificConductance = null,
                        tds = tds,
                        referenceSalinity = null,
                        absoluteSalinity = null
                    ),
                    algorithm = "empirical_conductivity_to_tds",
                    warnings = warnings
                )
            }

            "absolute_salinity" -> {
                failed(
                    "Absolute Salinity is deliberately unavailable until a validated TEOS-10 SAAR atlas implementation is bundled. Practical or Reference Salinity must not be relabelled as Absolute Salinity.",
                    "TEOS-10_SAAR_required"
                )
            }

            else -> failed("Unsupported salinity mode: $mode", "none")
        }
    }

    private fun salinityValueMap(
        conductivityMsCm: Double?,
        practicalSalinity: Double?,
        specificConductance: Double?,
        tds: Double?,
        referenceSalinity: Double?,
        absoluteSalinity: Double?
    ) = linkedMapOf(
        "aquatic_conductivity_ms_cm" to conductivityMsCm?.let { fmt(it, 8) }.orEmpty(),
        "aquatic_practical_salinity" to practicalSalinity?.let { fmt(it, 8) }.orEmpty(),
        "aquatic_absolute_salinity_g_kg" to absoluteSalinity?.let { fmt(it, 8) }.orEmpty(),
        "aquatic_reference_salinity_g_kg" to referenceSalinity?.let { fmt(it, 8) }.orEmpty(),
        "aquatic_specific_conductance_25_us_cm" to specificConductance?.let { fmt(it, 8) }.orEmpty(),
        "aquatic_estimated_tds_mg_l" to tds?.let { fmt(it, 8) }.orEmpty()
    )

    /**
     * Practical Salinity Scale 1978 calculation for conductivity C in mS/cm,
     * ITS-90 temperature and sea pressure in dbar.
     *
     * The main PSS-78 polynomial is implemented here.  Low-salinity results
     * (<2) are flagged for validation rather than silently presented as high-
     * confidence TEOS-10 output.
     */
    fun practicalSalinityPss78(conductivityMsCm: Double, temperatureC90: Double, pressureDbar: Double): Double {
        if (conductivityMsCm < 0.0) return Double.NaN
        val t68 = temperatureC90 * 1.00024
        val r = conductivityMsCm / 42.9140

        val c0 = 0.6766097
        val c1 = 2.00564e-2
        val c2 = 1.104259e-4
        val c3 = -6.9698e-7
        val c4 = 1.0031e-9
        val rt = c0 + t68 * (c1 + t68 * (c2 + t68 * (c3 + c4 * t68)))

        val d1 = 3.426e-2
        val d2 = 4.464e-4
        val d3 = 4.215e-1
        val d4 = -3.107e-3
        val e1 = 2.070e-5
        val e2 = -6.370e-10
        val e3 = 3.989e-15
        val rp = 1.0 + pressureDbar * (e1 + pressureDbar * (e2 + e3 * pressureDbar)) /
            (1.0 + d1 * t68 + d2 * t68 * t68 + (d3 + d4 * t68) * r)

        val rtRatio = r / (rp * rt)
        if (rtRatio < 0.0) return Double.NaN
        val x = sqrt(rtRatio)

        val a0 = 0.0080
        val a1 = -0.1692
        val a2 = 25.3851
        val a3 = 14.0941
        val a4 = -7.0261
        val a5 = 2.7081

        val b0 = 0.0005
        val b1 = -0.0056
        val b2 = -0.0066
        val b3 = -0.0375
        val b4 = 0.0636
        val b5 = -0.0144
        val k = 0.0162

        val base = a0 + x * (a1 + x * (a2 + x * (a3 + x * (a4 + a5 * x))))
        val delta = ((t68 - 15.0) / (1.0 + k * (t68 - 15.0))) *
            (b0 + x * (b1 + x * (b2 + x * (b3 + x * (b4 + b5 * x)))))
        return max(0.0, base + delta)
    }

    fun secchi(settings: Map<String, String>): Outcome {
        val down = settings.double("disappearance_depth_m")
        val up = settings.double("reappearance_depth_m")
        val waterDepth = settings.double("water_depth_m")
        val bottomReached = settings.bool("bottom_reached_before_disappearance") ?: false
        if (down == null && up == null) return failed("At least one Secchi observation is required.", "secchi_mean_directional")
        if ((down != null && down <= 0.0) || (up != null && up <= 0.0)) return failed("Secchi depths must be greater than zero.", "secchi_mean_directional")
        val depth = when {
            down != null && up != null -> (down + up) / 2.0
            down != null -> down
            else -> up!!
        }
        val warnings = mutableListOf<String>()
        if (down == null || up == null) warnings += "Only one directional observation was recorded."
        if (down != null && up != null) {
            val diff = abs(down - up)
            val threshold = settings.double("direction_difference_warning_m") ?: 1.0
            if (diff > threshold) warnings += "Disappearance and reappearance depths differ by ${fmt(diff, 2)} m."
        }
        if (waterDepth != null && depth > waterDepth) warnings += "Secchi depth exceeds recorded water depth."
        if (bottomReached) warnings += "Disk reached bottom before disappearance; the observation is bottom-limited."

        val tsiEnabled = settings.bool("calculate_carlson_tsi") ?: true
        val tsi = if (tsiEnabled && depth > 0.0 && !bottomReached) 60.0 - 14.41 * ln(depth) else null

        val euphoticEnabled = settings.bool("estimate_euphotic_depth") ?: false
        val multiplier = settings.double("euphotic_multiplier") ?: 2.7
        val eup = if (euphoticEnabled && !bottomReached) depth * multiplier else null
        if (euphoticEnabled) warnings += "Euphotic depth is an empirical estimate using the configured Secchi multiplier."

        return Outcome(
            result = "Secchi depth ${fmt(depth, 2)} m",
            values = linkedMapOf(
                "aquatic_secchi_depth_m" to fmt(depth, 6),
                "aquatic_secchi_disappearance_m" to down?.let { fmt(it, 6) }.orEmpty(),
                "aquatic_secchi_reappearance_m" to up?.let { fmt(it, 6) }.orEmpty(),
                "aquatic_secchi_tsi_sd" to tsi?.let { fmt(it, 3) }.orEmpty(),
                "aquatic_secchi_estimated_euphotic_depth_m" to eup?.let { fmt(it, 6) }.orEmpty(),
                "aquatic_secchi_bottom_limited" to bottomReached.toString()
            ),
            algorithm = "secchi_mean_directional; Carlson_TSI_SD_when_enabled",
            warnings = warnings
        )
    }

    fun depthPlan(settings: Map<String, String>): Outcome {
        val strategy = settings.value("strategy") ?: "fixed_interval"
        val waterDepth = settings.double("water_depth_m")
        val clearance = (settings.double("bottom_clearance_m") ?: 1.0).coerceAtLeast(0.0)
        val warnings = mutableListOf<String>()
        val raw = when (strategy) {
            "explicit" -> parseNumberList(settings.value("explicit_depths_m").orEmpty())
            "fixed_interval" -> {
                val maxDepth = waterDepth ?: settings.double("maximum_depth_m")
                    ?: return failed("Water depth or maximum depth is required.", "depth_plan_fixed_interval")
                val interval = settings.double("interval_m") ?: 5.0
                if (interval <= 0) return failed("Interval must be greater than zero.", "depth_plan_fixed_interval")
                buildList {
                    var d = 0.0
                    while (d <= maxDepth + 1e-9) {
                        add(d)
                        d += interval
                    }
                }
            }
            "surface_mid_bottom" -> {
                val wd = waterDepth ?: return failed("Water depth is required.", "depth_plan_surface_mid_bottom")
                listOf(0.0, wd / 2.0, max(0.0, wd - clearance))
            }
            "proportional" -> {
                val wd = waterDepth ?: return failed("Water depth is required.", "depth_plan_proportional")
                val fractions = parseNumberList(settings.value("depth_fractions").orEmpty()).ifEmpty { listOf(0.0, 0.25, 0.5, 0.75, 1.0) }
                fractions.map { it.coerceIn(0.0, 1.0) * max(0.0, wd - clearance) }
            }
            "stratification_targeted" -> {
                val wd = waterDepth ?: return failed("Water depth is required.", "depth_plan_stratification_targeted")
                val thermo = settings.double("thermocline_depth_m")
                    ?: return failed("Thermocline depth is required for stratification-targeted planning.", "depth_plan_stratification_targeted")
                val offset = settings.double("stratification_offset_m") ?: 2.0
                listOf(0.0, max(0.0, thermo - offset), thermo, min(wd - clearance, thermo + offset), max(0.0, wd - clearance))
            }
            else -> return failed("Unsupported depth-plan strategy: $strategy", "none")
        }

        if (raw.isEmpty()) return failed("Depth plan contains no valid depths.", "depth_plan")
        val clipped = raw.mapNotNull { d ->
            if (d < 0.0) {
                warnings += "Negative planned depth $d m was removed."
                null
            } else if (waterDepth != null && d > waterDepth - clearance) {
                val replacement = max(0.0, waterDepth - clearance)
                warnings += "Planned depth ${fmt(d, 2)} m was clipped to ${fmt(replacement, 2)} m using the bottom-clearance rule."
                replacement
            } else d
        }.distinct().sorted()
        if (clipped.isEmpty()) return failed("No valid depths remain after applying limits.", "depth_plan")
        return Outcome(
            result = clipped.joinToString(", ") { "${fmt(it, 2)} m" },
            values = linkedMapOf(
                "aquatic_depth_plan_count" to clipped.size.toString(),
                "aquatic_depth_plan_text" to clipped.joinToString(",") { fmt(it, 6) },
                "aquatic_depth_plan_json" to clipped.joinToString(prefix = "[", postfix = "]") { fmt(it, 6) }
            ),
            algorithm = "depth_plan_$strategy",
            warnings = warnings
        )
    }

    data class ProfileRow(
        val depth: Double,
        val temperature: Double? = null,
        val salinity: Double? = null,
        val oxygen: Double? = null,
        val density: Double? = null
    )

    data class GradientFeature(val depth: Double, val gradient: Double)

    fun profileSummary(settings: Map<String, String>): Outcome {
        val text = settings.value("profile_data").orEmpty()
        if (text.isBlank()) return failed("Processed profile data are required.", "processed_profile_summary")
        val depthCol = settings.value("depth_column") ?: "depth"
        val tempCol = settings.value("temperature_column") ?: "temperature"
        val salCol = settings.value("salinity_column") ?: "salinity"
        val oxyCol = settings.value("oxygen_column") ?: "oxygen"
        val rhoCol = settings.value("density_column") ?: "density"
        val rows = parseProfile(text, depthCol, tempCol, salCol, oxyCol, rhoCol)
        if (rows.size < 2) return failed("At least two valid profile rows are required.", "processed_profile_summary")
        val ordered = rows.sortedBy { it.depth }
        val warnings = mutableListOf<String>()
        if (rows.map { it.depth } != ordered.map { it.depth }) warnings += "Profile rows were sorted by depth before summarisation."

        val tempFeature = strongestGradient(ordered) { it.temperature }
        val salFeature = strongestGradient(ordered) { it.salinity }
        val oxyFeature = strongestGradient(ordered) { it.oxygen }
        val rhoFeature = strongestGradient(ordered) { it.density }

        val threshold = settings.double("metalimnion_gradient_threshold_c_per_m") ?: 0.5
        val metalimnion = contiguousTemperatureGradientBand(ordered, threshold)
        val mldMode = settings.value("mixed_layer_mode") ?: "temperature"
        val mldThreshold = settings.double("mixed_layer_threshold") ?: if (mldMode == "density") 0.03 else 0.2
        val referenceDepth = settings.double("mixed_layer_reference_depth_m") ?: ordered.first().depth
        val mld = mixedLayerDepth(ordered, mldMode, mldThreshold, referenceDepth)

        val maxN2 = maxBuoyancyFrequencySquared(ordered)
        if (maxN2 == null && ordered.any { it.density != null }) warnings += "Buoyancy frequency could not be calculated from the available density spacing."
        warnings += "Schmidt stability and whole-lake heat content are not calculated without an explicit hypsography/geometry integration implementation."

        val values = linkedMapOf(
            "aquatic_profile_n_rows" to ordered.size.toString(),
            "aquatic_profile_min_depth_m" to fmt(ordered.first().depth, 6),
            "aquatic_profile_max_depth_m" to fmt(ordered.last().depth, 6),
            "aquatic_profile_thermocline_depth_m" to tempFeature?.depth?.let { fmt(it, 6) }.orEmpty(),
            "aquatic_profile_halocline_depth_m" to salFeature?.depth?.let { fmt(it, 6) }.orEmpty(),
            "aquatic_profile_pycnocline_depth_m" to rhoFeature?.depth?.let { fmt(it, 6) }.orEmpty(),
            "aquatic_profile_oxycline_depth_m" to oxyFeature?.depth?.let { fmt(it, 6) }.orEmpty(),
            "aquatic_profile_mixed_layer_depth_m" to mld?.let { fmt(it, 6) }.orEmpty(),
            "aquatic_profile_metalimnion_top_m" to metalimnion?.first?.let { fmt(it, 6) }.orEmpty(),
            "aquatic_profile_metalimnion_bottom_m" to metalimnion?.second?.let { fmt(it, 6) }.orEmpty(),
            "aquatic_profile_max_buoyancy_frequency_s2" to maxN2?.let { fmt(it, 9) }.orEmpty(),
            "aquatic_profile_schmidt_stability_j_m2" to "",
            "aquatic_profile_heat_content" to ""
        )
        val main = when {
            tempFeature != null -> "Profile to ${fmt(ordered.last().depth, 1)} m; thermocline candidate ${fmt(tempFeature.depth, 1)} m"
            else -> "Profile to ${fmt(ordered.last().depth, 1)} m; no temperature-gradient feature available"
        }
        return Outcome(
            result = main,
            values = values,
            algorithm = "processed_profile_gradient_summary",
            warnings = warnings
        )
    }

    fun fieldQc(settings: Map<String, String>): Outcome {
        val issues = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val stationDepth = settings.double("station_water_depth_m")
        val secchi = settings.double("secchi_depth_m")
        val sampleDepth = settings.double("sample_depth_m")
        val ctdDepth = settings.double("ctd_max_depth_m")
        val planned = settings.int("planned_sample_count")
        val completed = settings.int("completed_sample_count")
        val requiredBlank = settings.bool("blank_required") ?: false
        val blankRecorded = settings.bool("blank_recorded") ?: false
        val requiredDuplicate = settings.bool("duplicate_required") ?: false
        val duplicateRecorded = settings.bool("duplicate_recorded") ?: false
        val calibrationCurrent = settings.bool("calibration_current")
        val gpsPresent = settings.bool("gps_present")

        if (stationDepth != null && stationDepth <= 0) issues += "Station water depth must be greater than zero."
        if (secchi != null && stationDepth != null && secchi > stationDepth) issues += "Secchi depth exceeds recorded station water depth."
        if (sampleDepth != null && stationDepth != null && sampleDepth > stationDepth) issues += "Sample depth exceeds recorded station water depth."
        if (ctdDepth != null && stationDepth != null && ctdDepth > stationDepth + (settings.double("depth_tolerance_m") ?: 2.0)) warnings += "CTD maximum depth exceeds station water depth beyond the configured tolerance."
        if (planned != null && completed != null && completed < planned) issues += "${planned - completed} planned sample(s) are not recorded."
        if (requiredBlank && !blankRecorded) issues += "Required field blank is not recorded."
        if (requiredDuplicate && !duplicateRecorded) issues += "Required duplicate is not recorded."
        if (calibrationCurrent == false) warnings += "Instrument calibration/check is not current."
        if (gpsPresent == false) warnings += "Station visit has no GPS fix."

        val status = when {
            issues.isNotEmpty() -> "INCOMPLETE"
            warnings.isNotEmpty() -> "PASS_WITH_WARNINGS"
            else -> "PASS"
        }
        return Outcome(
            result = "$status — ${issues.size + warnings.size} QC finding(s)",
            values = linkedMapOf(
                "aquatic_qc_status" to status,
                "aquatic_qc_issue_count" to issues.size.toString(),
                "aquatic_qc_warning_count" to warnings.size.toString(),
                "aquatic_qc_error_count" to issues.size.toString(),
                "aquatic_qc_summary" to (issues + warnings).joinToString(" | "),
                "aquatic_qc_json" to jsonStringList((issues.map { "ERROR: $it" } + warnings.map { "WARNING: $it" }))
            ),
            algorithm = "aquatic_field_qc_rules_v0.1",
            warnings = emptyList()
        )
    }

    fun geodesicDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371008.8
        val p1 = lat1 * PI / 180.0
        val p2 = lat2 * PI / 180.0
        val dp = (lat2 - lat1) * PI / 180.0
        val dl = (lon2 - lon1) * PI / 180.0
        val a = sin(dp / 2).pow(2) + cos(p1) * cos(p2) * sin(dl / 2).pow(2)
        return 2 * r * kotlin.math.asin(sqrt(a.coerceIn(0.0, 1.0)))
    }

    private fun strongestGradient(rows: List<ProfileRow>, selector: (ProfileRow) -> Double?): GradientFeature? {
        var best: GradientFeature? = null
        for (i in 1 until rows.size) {
            val v0 = selector(rows[i - 1]) ?: continue
            val v1 = selector(rows[i]) ?: continue
            val dz = rows[i].depth - rows[i - 1].depth
            if (dz <= 0) continue
            val g = (v1 - v0) / dz
            val feature = GradientFeature((rows[i].depth + rows[i - 1].depth) / 2.0, g)
            if (best == null || abs(feature.gradient) > abs(best.gradient)) best = feature
        }
        return best
    }

    private fun contiguousTemperatureGradientBand(rows: List<ProfileRow>, threshold: Double): Pair<Double, Double>? {
        val bands = mutableListOf<Pair<Double, Double>>()
        var start: Double? = null
        var end: Double? = null
        for (i in 1 until rows.size) {
            val t0 = rows[i - 1].temperature
            val t1 = rows[i].temperature
            val dz = rows[i].depth - rows[i - 1].depth
            val strong = t0 != null && t1 != null && dz > 0 && abs((t1 - t0) / dz) >= threshold
            if (strong) {
                if (start == null) start = rows[i - 1].depth
                end = rows[i].depth
            } else if (start != null && end != null) {
                bands += start to end
                start = null
                end = null
            }
        }
        if (start != null && end != null) bands += start to end
        return bands.maxByOrNull { it.second - it.first }
    }

    private fun mixedLayerDepth(rows: List<ProfileRow>, mode: String, threshold: Double, referenceDepth: Double): Double? {
        val ref = rows.minByOrNull { abs(it.depth - referenceDepth) } ?: return null
        val refValue = if (mode == "density") ref.density else ref.temperature
        refValue ?: return null
        for (row in rows.filter { it.depth >= ref.depth }) {
            val value = if (mode == "density") row.density else row.temperature
            if (value != null && abs(value - refValue) >= threshold) return row.depth
        }
        return rows.lastOrNull()?.depth
    }

    private fun maxBuoyancyFrequencySquared(rows: List<ProfileRow>): Double? {
        var best: Double? = null
        val g = 9.80665
        for (i in 1 until rows.size) {
            val rho0 = rows[i - 1].density ?: continue
            val rho1 = rows[i].density ?: continue
            val dz = rows[i].depth - rows[i - 1].depth
            if (dz <= 0) continue
            val rhoMean = (rho0 + rho1) / 2.0
            if (rhoMean <= 0) continue
            // depth z is positive downward, so stable density increase yields positive N^2.
            val n2 = g / rhoMean * ((rho1 - rho0) / dz)
            if (best == null || n2 > best) best = n2
        }
        return best
    }

    private fun parseProfile(
        text: String,
        depthCol: String,
        tempCol: String,
        salCol: String,
        oxyCol: String,
        rhoCol: String
    ): List<ProfileRow> {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.size < 2) return emptyList()
        val delimiter = when {
            lines.first().contains('\t') -> '\t'
            lines.first().contains(';') && !lines.first().contains(',') -> ';'
            else -> ','
        }
        val header = splitDelimited(lines.first(), delimiter).map { it.trim().lowercase() }
        fun idx(name: String): Int = header.indexOf(name.trim().lowercase())
        val di = idx(depthCol)
        if (di < 0) return emptyList()
        val ti = idx(tempCol)
        val si = idx(salCol)
        val oi = idx(oxyCol)
        val ri = idx(rhoCol)
        return lines.drop(1).mapNotNull { line ->
            val cells = splitDelimited(line, delimiter)
            val d = cells.getOrNull(di)?.trim()?.toDoubleOrNull() ?: return@mapNotNull null
            ProfileRow(
                depth = d,
                temperature = cells.getOrNull(ti)?.trim()?.toDoubleOrNull(),
                salinity = cells.getOrNull(si)?.trim()?.toDoubleOrNull(),
                oxygen = cells.getOrNull(oi)?.trim()?.toDoubleOrNull(),
                density = cells.getOrNull(ri)?.trim()?.toDoubleOrNull()
            )
        }
    }

    private fun splitDelimited(line: String, delimiter: Char): List<String> {
        // Processed field exports for v0.1 are intentionally simple. Quoted
        // delimiters are not silently guessed: users should supply a clean
        // processed table.
        return line.split(delimiter)
    }

    private fun parseNumberList(text: String): List<Double> =
        text.split(',', ';', '|', '\n', '\t', ' ')
            .mapNotNull { it.trim().takeIf(String::isNotBlank)?.toDoubleOrNull() }

    private fun conductivityToMilliSiemensPerCm(value: Double, unit: String): Double? =
        when (unit.lowercase().replace("μ", "u").replace("µ", "u").replace(" ", "")) {
            "ms/cm", "mscm" -> value
            "us/cm", "uscm" -> value / 1000.0
            "s/m", "sm" -> value * 10.0
            else -> null
        }

    private fun conductivityToMicroSiemensPerCm(value: Double, unit: String): Double? =
        conductivityToMilliSiemensPerCm(value, unit)?.times(1000.0)

    private fun failed(message: String, algorithm: String) = Outcome(
        status = "failed",
        result = "",
        values = emptyMap(),
        algorithm = algorithm,
        error = message
    )

    fun fmt(value: Double, decimals: Int = 6): String =
        "%.${decimals}f".format(java.util.Locale.US, value).trimEnd('0').trimEnd('.')

    fun jsonStringList(values: List<String>): String =
        values.joinToString(prefix = "[", postfix = "]") { "\"${jsonEscape(it)}\"" }

    fun jsonObject(values: Map<String, String>): String =
        values.entries.joinToString(prefix = "{", postfix = "}") { (k, v) -> "\"${jsonEscape(k)}\":\"${jsonEscape(v)}\"" }

    private fun jsonEscape(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")

    fun Map<String, String>.value(key: String): String? =
        (this[key] ?: this["input_$key"])?.trim()?.takeIf { it.isNotBlank() }

    fun Map<String, String>.double(key: String): Double? = value(key)?.toDoubleOrNull()
    fun Map<String, String>.int(key: String): Int? = value(key)?.toIntOrNull()
    fun Map<String, String>.bool(key: String): Boolean? = value(key)?.let {
        when (it.lowercase()) {
            "true", "1", "yes", "y" -> true
            "false", "0", "no", "n" -> false
            else -> null
        }
    }
}
