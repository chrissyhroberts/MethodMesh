package com.example.methodmesh.modules.aquaticfield

import com.example.methodmesh.modules.MethodMeshModule
import com.example.methodmesh.modules.RilBinding
import com.example.methodmesh.settings.MethodSetting

object AquaticFieldModule : MethodMeshModule {
    override val moduleId = "aquaticfield"
    override val displayName = "Aquatic fieldwork"
    override val summary = "Offline oceanography and limnology calculations, sampling records, field QC and station workflow."
    override val iconKey = "water"

    override fun as100Methods() = listOf(
        As100AquaticSalinityMethod,
        As100AquaticPressureDepthMethod,
        As100AquaticSecchiMethod,
        As100AquaticStationVisitMethod,
        As100AquaticCtdCastMethod,
        As100AquaticSampleIdMethod,
        As100AquaticSampleRecordMethod,
        As100AquaticRosetteFireMethod,
        As100AquaticProfileSummaryMethod,
        As100AquaticDepthPlanMethod,
        As100AquaticInstrumentCheckMethod,
        As100AquaticFieldQcMethod,
        As100AquaticTransectMethod,
        As100AquaticMooringMethod,
        As100AquaticSedimentMethod,
        As100AquaticStationDashboardMethod
    )

    override fun rilBindings() = listOf(
        RilBinding("convert salinity", As100AquaticSalinityMethod.ID, "Convert conductivity or calculate Practical Salinity"),
        RilBinding("convert pressure depth", As100AquaticPressureDepthMethod.ID, "Convert aquatic pressure and depth"),
        RilBinding("measure secchi depth", As100AquaticSecchiMethod.ID, "Record a Secchi observation"),
        RilBinding("record aquatic station", As100AquaticStationVisitMethod.ID, "Record a station visit"),
        RilBinding("record ctd cast", As100AquaticCtdCastMethod.ID, "Record a CTD or sonde cast"),
        RilBinding("generate sample id", As100AquaticSampleIdMethod.ID, "Generate an aquatic sample identifier"),
        RilBinding("record water sample", As100AquaticSampleRecordMethod.ID, "Record a discrete water sample"),
        RilBinding("record rosette fire", As100AquaticRosetteFireMethod.ID, "Record a water-sampler bottle firing"),
        RilBinding("summarise aquatic profile", As100AquaticProfileSummaryMethod.ID, "Summarise a processed vertical profile"),
        RilBinding("generate aquatic depth plan", As100AquaticDepthPlanMethod.ID, "Generate planned sampling depths"),
        RilBinding("record aquatic instrument check", As100AquaticInstrumentCheckMethod.ID, "Record calibration or field verification"),
        RilBinding("run aquatic field qc", As100AquaticFieldQcMethod.ID, "Run field completeness and plausibility checks"),
        RilBinding("record aquatic transect", As100AquaticTransectMethod.ID, "Record a transect"),
        RilBinding("record aquatic mooring", As100AquaticMooringMethod.ID, "Record a mooring event"),
        RilBinding("record sediment sample", As100AquaticSedimentMethod.ID, "Record a sediment core or grab"),
        RilBinding("aquatic station dashboard", As100AquaticStationDashboardMethod.ID, "Open or return the current station snapshot")
    )

    override fun capabilityScreens() = listOf(
        AquaticSalinityCapabilityScreen,
        AquaticPressureDepthCapabilityScreen,
        AquaticSecchiCapabilityScreen,
        AquaticStationVisitCapabilityScreen,
        AquaticCtdCapabilityScreen,
        AquaticSampleIdCapabilityScreen,
        AquaticSampleCapabilityScreen,
        AquaticRosetteCapabilityScreen,
        AquaticProfileCapabilityScreen,
        AquaticDepthPlanCapabilityScreen,
        AquaticInstrumentCheckCapabilityScreen,
        AquaticFieldQcCapabilityScreen,
        AquaticTransectCapabilityScreen,
        AquaticMooringCapabilityScreen,
        AquaticSedimentCapabilityScreen,
        AquaticStationDashboardCapabilityScreen
    )

    fun settingsFor(capabilityId: String): List<MethodSetting> =
        capabilitySettings()[capabilityId].orEmpty()

    override fun capabilitySettings(): Map<String, List<MethodSetting>> = mapOf(
        As100AquaticSalinityMethod.ID to listOf(
            MethodSetting.ChoiceSetting("mode", "Calculation", defaultValue = "practical_salinity_from_conductivity", choices = listOf("practical_salinity_from_conductivity", "conductivity_units", "specific_conductance_25", "tds_estimate", "absolute_salinity")),
            MethodSetting.FloatSetting("conductivity", "Conductivity", defaultValue = 42.914f, minimum = 0f, decimals = 5),
            MethodSetting.ChoiceSetting("conductivity_unit", "Conductivity unit", defaultValue = "mS/cm", choices = listOf("mS/cm", "uS/cm", "S/m")),
            MethodSetting.FloatSetting("temperature_c", "Temperature", defaultValue = 15f, minimum = -5f, maximum = 50f, unit = "°C", decimals = 3),
            MethodSetting.FloatSetting("pressure_dbar", "Sea pressure", defaultValue = 0f, minimum = 0f, unit = "dbar", decimals = 2),
            MethodSetting.FloatSetting("latitude_deg", "Latitude", defaultValue = 0f, minimum = -90f, maximum = 90f, unit = "°", decimals = 5),
            MethodSetting.FloatSetting("longitude_deg", "Longitude", defaultValue = 0f, minimum = -180f, maximum = 180f, unit = "°", decimals = 5),
            MethodSetting.FloatSetting("temperature_coefficient_per_c", "Conductivity temperature coefficient", defaultValue = 0.02f, minimum = 0f, maximum = 0.1f, decimals = 4),
            MethodSetting.FloatSetting("tds_coefficient", "TDS coefficient", description = "Empirical mg/L per µS/cm coefficient.", defaultValue = 0.65f, minimum = 0.01f, maximum = 2f, decimals = 3)
        ),
        As100AquaticPressureDepthMethod.ID to listOf(
            MethodSetting.ChoiceSetting("mode", "Calculation", defaultValue = "pressure_to_depth", choices = listOf("pressure_to_depth", "depth_to_pressure", "wire_out")),
            MethodSetting.FloatSetting("pressure_dbar", "Sea pressure", defaultValue = 0f, minimum = 0f, unit = "dbar", decimals = 3),
            MethodSetting.FloatSetting("depth_m", "Depth", defaultValue = 0f, minimum = 0f, unit = "m", decimals = 3),
            MethodSetting.FloatSetting("latitude_deg", "Latitude", defaultValue = 0f, minimum = -90f, maximum = 90f, unit = "°", decimals = 4),
            MethodSetting.FloatSetting("pressure_offset_dbar", "Pressure-zero offset", defaultValue = 0f, unit = "dbar", decimals = 3),
            MethodSetting.FloatSetting("vertical_offset_m", "Sensor / bottle vertical offset", defaultValue = 0f, unit = "m", decimals = 3),
            MethodSetting.FloatSetting("correction_factor", "Mechanical correction factor", defaultValue = 1f, minimum = 0.01f, maximum = 5f, decimals = 4),
            MethodSetting.FloatSetting("wire_length_m", "Wire out", defaultValue = 10f, minimum = 0f, unit = "m", decimals = 2),
            MethodSetting.FloatSetting("cable_angle_deg", "Cable angle", defaultValue = 0f, minimum = 0f, maximum = 90f, unit = "°", decimals = 1),
            MethodSetting.ChoiceSetting("angle_convention", "Angle convention", defaultValue = "from_vertical", choices = listOf("from_vertical", "from_horizontal"))
        ),
        As100AquaticSecchiMethod.ID to listOf(
            MethodSetting.FloatSetting("disappearance_depth_m", "Disappearance depth", defaultValue = 1f, minimum = 0f, unit = "m", decimals = 2),
            MethodSetting.FloatSetting("reappearance_depth_m", "Reappearance depth", defaultValue = 1f, minimum = 0f, unit = "m", decimals = 2),
            MethodSetting.FloatSetting("water_depth_m", "Water depth", defaultValue = 10f, minimum = 0f, unit = "m", decimals = 2),
            MethodSetting.FloatSetting("disk_diameter_cm", "Disk diameter", defaultValue = 20f, minimum = 1f, unit = "cm", decimals = 1),
            MethodSetting.BooleanSetting("bottom_reached_before_disappearance", "Bottom reached before disappearance", defaultValue = false),
            MethodSetting.BooleanSetting("calculate_carlson_tsi", "Calculate Carlson TSI(SD)", defaultValue = true),
            MethodSetting.BooleanSetting("estimate_euphotic_depth", "Estimate euphotic depth", description = "Empirical and disabled by default.", defaultValue = false),
            MethodSetting.FloatSetting("euphotic_multiplier", "Euphotic-depth multiplier", defaultValue = 2.7f, minimum = 0.1f, maximum = 10f, decimals = 2),
            MethodSetting.FloatSetting("direction_difference_warning_m", "Directional difference warning", defaultValue = 1f, minimum = 0f, unit = "m", decimals = 2),
            MethodSetting.TextSetting("observer", "Observer", defaultValue = ""),
            MethodSetting.TextSetting("conditions", "Sun / glare / waves / notes", defaultValue = "")
        ),
        As100AquaticStationVisitMethod.ID to listOf(
            MethodSetting.TextSetting("station_id", "Station ID", defaultValue = ""),
            MethodSetting.TextSetting("visit_id", "Visit ID", defaultValue = ""),
            MethodSetting.TextSetting("station_name", "Station name", defaultValue = ""),
            MethodSetting.TextSetting("waterbody", "Waterbody", defaultValue = ""),
            MethodSetting.FloatSetting("latitude", "Latitude", defaultValue = 0f, minimum = -90f, maximum = 90f, decimals = 6),
            MethodSetting.FloatSetting("longitude", "Longitude", defaultValue = 0f, minimum = -180f, maximum = 180f, decimals = 6),
            MethodSetting.TextSetting("plus_code", "Plus Code", defaultValue = ""),
            MethodSetting.FloatSetting("water_depth_m", "Observed water depth", defaultValue = 0f, minimum = 0f, unit = "m", decimals = 2),
            MethodSetting.TextSetting("timestamp_utc", "Arrival timestamp (UTC)", defaultValue = ""),
            MethodSetting.TextSetting("operators", "Operators", defaultValue = ""),
            MethodSetting.TextSetting("platform", "Vessel / platform", defaultValue = ""),
            MethodSetting.TextSetting("weather", "Weather", defaultValue = ""),
            MethodSetting.TextSetting("wind", "Wind", defaultValue = ""),
            MethodSetting.TextSetting("surface_state", "Sea / lake state", defaultValue = ""),
            MethodSetting.TextSetting("tide", "Tidal state", defaultValue = ""),
            MethodSetting.TextSetting("ice", "Ice state", defaultValue = ""),
            MethodSetting.ChoiceSetting("visit_status", "Visit status", defaultValue = "open", choices = listOf("open", "completed", "abandoned")),
            MethodSetting.TextSetting("notes", "Notes", defaultValue = "")
        ),
        As100AquaticCtdCastMethod.ID to listOf(
            MethodSetting.TextSetting("cast_id", "Cast ID", defaultValue = ""),
            MethodSetting.TextSetting("station_id", "Station ID", defaultValue = ""),
            MethodSetting.TextSetting("visit_id", "Visit ID", defaultValue = ""),
            MethodSetting.TextSetting("instrument_id", "Instrument ID", defaultValue = ""),
            MethodSetting.TextSetting("manufacturer_model", "Make / model", defaultValue = ""),
            MethodSetting.TextSetting("serial_number", "Serial number", defaultValue = ""),
            MethodSetting.TextSetting("configuration_id", "Configuration ID", defaultValue = ""),
            MethodSetting.TextSetting("calibration_date", "Calibration date", defaultValue = ""),
            MethodSetting.TextSetting("start_utc", "Cast start UTC", defaultValue = ""),
            MethodSetting.TextSetting("end_utc", "Cast end UTC", defaultValue = ""),
            MethodSetting.ChoiceSetting("cast_direction", "Recorded direction", defaultValue = "downcast", choices = listOf("downcast", "upcast", "both")),
            MethodSetting.FloatSetting("max_pressure_dbar", "Maximum pressure", defaultValue = 0f, minimum = 0f, unit = "dbar", decimals = 2),
            MethodSetting.FloatSetting("max_depth_m", "Maximum depth", defaultValue = 0f, minimum = 0f, unit = "m", decimals = 2),
            MethodSetting.TextSetting("raw_file_uri", "Raw data file URI", defaultValue = ""),
            MethodSetting.TextSetting("processed_file_uri", "Processed data file URI", defaultValue = ""),
            MethodSetting.TextSetting("file_sha256", "File SHA-256", defaultValue = ""),
            MethodSetting.TextSetting("flags", "Flags / problems", defaultValue = ""),
            MethodSetting.TextSetting("notes", "Notes", defaultValue = "")
        ),
        As100AquaticSampleIdMethod.ID to listOf(
            MethodSetting.TextSetting("template", "ID template", defaultValue = "{project}-{station}-{cast}-{depth}M-{type}-{replicate}"),
            MethodSetting.TextSetting("project", "Project", defaultValue = ""),
            MethodSetting.TextSetting("campaign", "Campaign / cruise", defaultValue = ""),
            MethodSetting.TextSetting("station", "Station", defaultValue = ""),
            MethodSetting.TextSetting("visit", "Visit", defaultValue = ""),
            MethodSetting.TextSetting("cast", "Cast", defaultValue = ""),
            MethodSetting.TextSetting("depth", "Depth token", defaultValue = ""),
            MethodSetting.TextSetting("bottle", "Bottle", defaultValue = ""),
            MethodSetting.TextSetting("sample_type", "Sample type token", defaultValue = ""),
            MethodSetting.TextSetting("replicate", "Replicate", defaultValue = "01"),
            MethodSetting.TextSetting("sequence", "Sequence", defaultValue = "01"),
            MethodSetting.TextSetting("date", "Date token", defaultValue = "")
        ),
        As100AquaticSampleRecordMethod.ID to listOf(
            MethodSetting.TextSetting("sample_id", "Sample ID", defaultValue = ""),
            MethodSetting.TextSetting("station_id", "Station ID", defaultValue = ""),
            MethodSetting.TextSetting("visit_id", "Visit ID", defaultValue = ""),
            MethodSetting.TextSetting("cast_id", "Cast ID", defaultValue = ""),
            MethodSetting.TextSetting("bottle_number", "Bottle number", defaultValue = ""),
            MethodSetting.FloatSetting("target_depth_m", "Target depth", defaultValue = 0f, minimum = 0f, unit = "m", decimals = 2),
            MethodSetting.FloatSetting("actual_depth_m", "Actual depth", defaultValue = 0f, minimum = 0f, unit = "m", decimals = 2),
            MethodSetting.FloatSetting("pressure_dbar", "Pressure", defaultValue = 0f, minimum = 0f, unit = "dbar", decimals = 2),
            MethodSetting.TextSetting("timestamp_utc", "Sample time UTC", defaultValue = ""),
            MethodSetting.ChoiceSetting("sample_type", "Sample category", defaultValue = "chemistry", choices = listOf("chemistry", "nutrients", "chlorophyll", "microbiology", "eDNA", "isotope", "particulate", "phytoplankton", "zooplankton", "archive", "other")),
            MethodSetting.TextSetting("analyte_purpose", "Analyte / purpose", defaultValue = ""),
            MethodSetting.FloatSetting("volume_ml", "Volume", defaultValue = 0f, minimum = 0f, unit = "mL", decimals = 1),
            MethodSetting.TextSetting("container", "Container", defaultValue = ""),
            MethodSetting.BooleanSetting("filtered", "Filtered", defaultValue = false),
            MethodSetting.TextSetting("filter_id", "Filter ID", defaultValue = ""),
            MethodSetting.TextSetting("filter_spec", "Filter material / pore size", defaultValue = ""),
            MethodSetting.TextSetting("preservative", "Preservative", defaultValue = ""),
            MethodSetting.TextSetting("storage", "Storage condition", defaultValue = ""),
            MethodSetting.TextSetting("replicate_id", "Replicate", defaultValue = ""),
            MethodSetting.ChoiceSetting("blank_type", "Blank type", defaultValue = "none", choices = listOf("none", "field_blank", "equipment_blank", "trip_blank", "other")),
            MethodSetting.TextSetting("operator", "Operator", defaultValue = ""),
            MethodSetting.TextSetting("notes", "Notes", defaultValue = "")
        ),
        As100AquaticRosetteFireMethod.ID to listOf(
            MethodSetting.TextSetting("cast_id", "Cast ID", defaultValue = ""),
            MethodSetting.TextSetting("rosette_id", "Rosette / sampler ID", defaultValue = ""),
            MethodSetting.TextSetting("bottle_number", "Bottle number", defaultValue = ""),
            MethodSetting.IntSetting("firing_sequence", "Firing sequence", defaultValue = 1, minimum = 1),
            MethodSetting.TextSetting("fire_time_utc", "Firing time UTC", defaultValue = ""),
            MethodSetting.FloatSetting("pressure_dbar", "Pressure", defaultValue = 0f, minimum = 0f, unit = "dbar", decimals = 2),
            MethodSetting.FloatSetting("depth_m", "Depth", defaultValue = 0f, minimum = 0f, unit = "m", decimals = 2),
            MethodSetting.ChoiceSetting("bottle_status", "Bottle status", defaultValue = "successful", choices = listOf("successful", "misfire", "refire", "unknown")),
            MethodSetting.TextSetting("sample_ids", "Linked sample IDs", description = "Comma-separated.", defaultValue = ""),
            MethodSetting.TextSetting("flags", "Flags / notes", defaultValue = "")
        ),
        As100AquaticProfileSummaryMethod.ID to listOf(
            MethodSetting.TextSetting("profile_data", "Processed profile table", description = "Paste CSV/TSV with a header row.", defaultValue = ""),
            MethodSetting.TextSetting("depth_column", "Depth column", defaultValue = "depth"),
            MethodSetting.TextSetting("temperature_column", "Temperature column", defaultValue = "temperature"),
            MethodSetting.TextSetting("salinity_column", "Salinity column", defaultValue = "salinity"),
            MethodSetting.TextSetting("oxygen_column", "Oxygen column", defaultValue = "oxygen"),
            MethodSetting.TextSetting("density_column", "Density column", defaultValue = "density"),
            MethodSetting.FloatSetting("metalimnion_gradient_threshold_c_per_m", "Metalimnion temperature-gradient threshold", defaultValue = 0.5f, minimum = 0f, unit = "°C/m", decimals = 3),
            MethodSetting.ChoiceSetting("mixed_layer_mode", "Mixed-layer criterion", defaultValue = "temperature", choices = listOf("temperature", "density")),
            MethodSetting.FloatSetting("mixed_layer_threshold", "Mixed-layer threshold", defaultValue = 0.2f, minimum = 0f, decimals = 4),
            MethodSetting.FloatSetting("mixed_layer_reference_depth_m", "Mixed-layer reference depth", defaultValue = 0f, minimum = 0f, unit = "m", decimals = 2)
        ),
        As100AquaticDepthPlanMethod.ID to listOf(
            MethodSetting.ChoiceSetting("strategy", "Depth strategy", defaultValue = "fixed_interval", choices = listOf("explicit", "fixed_interval", "surface_mid_bottom", "proportional", "stratification_targeted")),
            MethodSetting.FloatSetting("water_depth_m", "Water depth", defaultValue = 20f, minimum = 0f, unit = "m", decimals = 2),
            MethodSetting.FloatSetting("maximum_depth_m", "Maximum planned depth", defaultValue = 20f, minimum = 0f, unit = "m", decimals = 2),
            MethodSetting.FloatSetting("bottom_clearance_m", "Near-bottom clearance", defaultValue = 1f, minimum = 0f, unit = "m", decimals = 2),
            MethodSetting.FloatSetting("interval_m", "Fixed interval", defaultValue = 5f, minimum = 0.01f, unit = "m", decimals = 2),
            MethodSetting.TextSetting("explicit_depths_m", "Explicit depths", description = "Comma-separated metres.", defaultValue = "0,5,10"),
            MethodSetting.TextSetting("depth_fractions", "Proportional depths", description = "0 to 1 fractions.", defaultValue = "0,0.25,0.5,0.75,1"),
            MethodSetting.FloatSetting("thermocline_depth_m", "Thermocline depth", defaultValue = 10f, minimum = 0f, unit = "m", decimals = 2),
            MethodSetting.FloatSetting("stratification_offset_m", "Above / below feature offset", defaultValue = 2f, minimum = 0f, unit = "m", decimals = 2)
        ),
        As100AquaticInstrumentCheckMethod.ID to listOf(
            MethodSetting.TextSetting("instrument_id", "Instrument ID", defaultValue = ""),
            MethodSetting.ChoiceSetting("check_type", "Check type", defaultValue = "pre_deployment", choices = listOf("pre_deployment", "post_deployment", "calibration", "field_verification", "blank_standard", "cleaning", "service", "battery", "clock_sync", "sensor_zero", "other")),
            MethodSetting.TextSetting("timestamp_utc", "Timestamp UTC", defaultValue = ""),
            MethodSetting.TextSetting("reference", "Reference material / instrument", defaultValue = ""),
            MethodSetting.TextSetting("expected_value", "Expected value", defaultValue = ""),
            MethodSetting.TextSetting("observed_value", "Observed value", defaultValue = ""),
            MethodSetting.TextSetting("tolerance", "Tolerance", defaultValue = ""),
            MethodSetting.ChoiceSetting("result", "Explicit result", defaultValue = "", choices = listOf("", "PASS", "FAIL", "RECORDED")),
            MethodSetting.TextSetting("corrective_action", "Corrective action", defaultValue = ""),
            MethodSetting.TextSetting("next_due", "Next calibration / check due", defaultValue = ""),
            MethodSetting.TextSetting("operator", "Operator", defaultValue = ""),
            MethodSetting.TextSetting("notes", "Notes", defaultValue = "")
        ),
        As100AquaticFieldQcMethod.ID to listOf(
            MethodSetting.FloatSetting("station_water_depth_m", "Station water depth", defaultValue = 0f, minimum = 0f, unit = "m", decimals = 2),
            MethodSetting.FloatSetting("secchi_depth_m", "Secchi depth", defaultValue = 0f, minimum = 0f, unit = "m", decimals = 2),
            MethodSetting.FloatSetting("sample_depth_m", "Sample depth", defaultValue = 0f, minimum = 0f, unit = "m", decimals = 2),
            MethodSetting.FloatSetting("ctd_max_depth_m", "CTD maximum depth", defaultValue = 0f, minimum = 0f, unit = "m", decimals = 2),
            MethodSetting.FloatSetting("depth_tolerance_m", "Depth tolerance", defaultValue = 2f, minimum = 0f, unit = "m", decimals = 2),
            MethodSetting.IntSetting("planned_sample_count", "Planned samples", defaultValue = 0, minimum = 0),
            MethodSetting.IntSetting("completed_sample_count", "Completed samples", defaultValue = 0, minimum = 0),
            MethodSetting.BooleanSetting("blank_required", "Field blank required", defaultValue = false),
            MethodSetting.BooleanSetting("blank_recorded", "Field blank recorded", defaultValue = false),
            MethodSetting.BooleanSetting("duplicate_required", "Duplicate required", defaultValue = false),
            MethodSetting.BooleanSetting("duplicate_recorded", "Duplicate recorded", defaultValue = false),
            MethodSetting.BooleanSetting("calibration_current", "Calibration/check current", defaultValue = true),
            MethodSetting.BooleanSetting("gps_present", "GPS present", defaultValue = true)
        ),
        As100AquaticTransectMethod.ID to listOf(
            MethodSetting.TextSetting("transect_id", "Transect ID", defaultValue = ""),
            MethodSetting.TextSetting("start_utc", "Start UTC", defaultValue = ""),
            MethodSetting.TextSetting("end_utc", "End UTC", defaultValue = ""),
            MethodSetting.FloatSetting("start_latitude", "Start latitude", defaultValue = 0f, minimum = -90f, maximum = 90f, decimals = 6),
            MethodSetting.FloatSetting("start_longitude", "Start longitude", defaultValue = 0f, minimum = -180f, maximum = 180f, decimals = 6),
            MethodSetting.FloatSetting("end_latitude", "End latitude", defaultValue = 0f, minimum = -90f, maximum = 90f, decimals = 6),
            MethodSetting.FloatSetting("end_longitude", "End longitude", defaultValue = 0f, minimum = -180f, maximum = 180f, decimals = 6),
            MethodSetting.IntSetting("station_count", "Stations / waypoints", defaultValue = 0, minimum = 0),
            MethodSetting.TextSetting("platform", "Vessel / platform", defaultValue = ""),
            MethodSetting.TextSetting("notes", "Notes", defaultValue = "")
        ),
        As100AquaticMooringMethod.ID to listOf(
            MethodSetting.TextSetting("mooring_id", "Mooring ID", defaultValue = ""),
            MethodSetting.ChoiceSetting("event", "Event", defaultValue = "deploy", choices = listOf("deploy", "recover", "service", "inspect", "replace_instrument", "other")),
            MethodSetting.TextSetting("timestamp_utc", "Timestamp UTC", defaultValue = ""),
            MethodSetting.FloatSetting("latitude", "Latitude", defaultValue = 0f, minimum = -90f, maximum = 90f, decimals = 6),
            MethodSetting.FloatSetting("longitude", "Longitude", defaultValue = 0f, minimum = -180f, maximum = 180f, decimals = 6),
            MethodSetting.FloatSetting("water_depth_m", "Water depth", defaultValue = 0f, minimum = 0f, unit = "m", decimals = 2),
            MethodSetting.FloatSetting("line_length_m", "Line length", defaultValue = 0f, minimum = 0f, unit = "m", decimals = 2),
            MethodSetting.TextSetting("sensor_depths_m", "Sensor depths", defaultValue = ""),
            MethodSetting.TextSetting("instrument_ids", "Instrument IDs", defaultValue = ""),
            MethodSetting.IntSetting("instrument_count", "Instrument count", defaultValue = 0, minimum = 0),
            MethodSetting.TextSetting("sampling_interval", "Sampling interval", defaultValue = ""),
            MethodSetting.TextSetting("expected_recovery_date", "Expected recovery date", defaultValue = ""),
            MethodSetting.TextSetting("condition", "Condition / fouling / damage", defaultValue = ""),
            MethodSetting.TextSetting("file_hashes", "Recovered file names / hashes", defaultValue = ""),
            MethodSetting.TextSetting("notes", "Notes", defaultValue = "")
        ),
        As100AquaticSedimentMethod.ID to listOf(
            MethodSetting.TextSetting("sediment_id", "Core / grab ID", defaultValue = ""),
            MethodSetting.ChoiceSetting("method", "Sampling method", defaultValue = "gravity_core", choices = listOf("gravity_core", "piston_core", "box_core", "hand_core", "ekman_grab", "van_veen_grab", "ponar_grab", "other")),
            MethodSetting.TextSetting("station_id", "Station ID", defaultValue = ""),
            MethodSetting.TextSetting("visit_id", "Visit ID", defaultValue = ""),
            MethodSetting.TextSetting("deployment_utc", "Deployment UTC", defaultValue = ""),
            MethodSetting.TextSetting("recovery_utc", "Recovery UTC", defaultValue = ""),
            MethodSetting.FloatSetting("water_depth_m", "Water depth", defaultValue = 0f, minimum = 0f, unit = "m", decimals = 2),
            MethodSetting.FloatSetting("target_penetration_cm", "Target penetration", defaultValue = 0f, minimum = 0f, unit = "cm", decimals = 1),
            MethodSetting.FloatSetting("observed_penetration_cm", "Observed penetration", defaultValue = 0f, minimum = 0f, unit = "cm", decimals = 1),
            MethodSetting.FloatSetting("recovery_length_cm", "Recovered core length", defaultValue = 0f, minimum = 0f, unit = "cm", decimals = 1),
            MethodSetting.FloatSetting("core_diameter_cm", "Core diameter", defaultValue = 0f, minimum = 0f, unit = "cm", decimals = 1),
            MethodSetting.TextSetting("integrity", "Sample integrity", defaultValue = ""),
            MethodSetting.TextSetting("sediment_description", "Sediment description", defaultValue = ""),
            MethodSetting.IntSetting("subsample_count", "Subsample count", defaultValue = 0, minimum = 0),
            MethodSetting.TextSetting("storage", "Preservation / storage", defaultValue = ""),
            MethodSetting.TextSetting("notes", "Notes", defaultValue = "")
        ),
        As100AquaticStationDashboardMethod.ID to emptyList()
    )
}
