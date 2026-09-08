package com.example.methodmesh.modules.labbench

import com.example.methodmesh.settings.MethodSetting

enum class LabInputKind { NUMBER, INTEGER, TEXT, MULTILINE, CHOICE }

data class LabInputDef(
    val id: String,
    val label: String,
    val defaultValue: String = "",
    val kind: LabInputKind = LabInputKind.NUMBER,
    val choices: List<String> = emptyList(),
    val description: String = ""
) {
    fun asMethodSetting(): MethodSetting = when (kind) {
        LabInputKind.CHOICE -> MethodSetting.ChoiceSetting(id, label, description.ifBlank { null }, defaultValue = defaultValue, choices = choices)
        else -> MethodSetting.TextSetting(id, label, description.ifBlank { null }, defaultValue = defaultValue)
    }
}

data class LabCalculatorDefinition(
    val key: String,
    val title: String,
    val description: String,
    val method: LabBenchMethod,
    val inputs: List<LabInputDef>
)

object LabBenchDefinitions {
    private val volumeUnits = listOf("uL", "mL", "L")
    private val molarUnits = listOf("M", "mM", "uM", "nM", "pM")
    private val massUnits = listOf("g", "mg", "ug", "ng", "pg")
    private val amountUnits = listOf("mol", "mmol", "umol", "nmol", "pmol")
    private val massVolumeConcentrationUnits = listOf("g/L", "mg/mL", "ug/mL", "ng/uL", "ng/mL", "percent_wv")
    private val converterConcentrationUnits = molarUnits + massVolumeConcentrationUnits + listOf("percent_ww", "percent_vv", "x")
    private val volumetricConcentrationUnits = molarUnits + massVolumeConcentrationUnits + listOf("percent_vv", "x")

    val Dilution = LabCalculatorDefinition(
        "dilution", "Dilution", "Calculate stock and diluent volumes.", LabBenchMethods.Dilution,
        listOf(
            LabInputDef("stock_concentration", "Stock concentration", "10"),
            LabInputDef("stock_concentration_unit", "Stock concentration unit", "mM", LabInputKind.CHOICE, volumetricConcentrationUnits),
            LabInputDef("target_concentration", "Target concentration", "250"),
            LabInputDef("target_concentration_unit", "Target concentration unit", "uM", LabInputKind.CHOICE, volumetricConcentrationUnits),
            LabInputDef("final_volume", "Final volume", "2"),
            LabInputDef("final_volume_unit", "Final volume unit", "mL", LabInputKind.CHOICE, volumeUnits),
            LabInputDef("molecular_weight_g_mol", "Molecular weight (g/mol), if needed", ""),
            LabInputDef("density_g_ml", "Density (g/mL), if needed", "")
        )
    )

    val MolarSolution = LabCalculatorDefinition(
        "molar_solution", "Molar solution", "Prepare a molar solution or solve mass/molarity/volume.", LabBenchMethods.MolarSolution,
        listOf(
            LabInputDef("mode", "Solve for", "mass_required", LabInputKind.CHOICE, listOf("mass_required", "molarity_from_mass", "volume_from_mass")),
            LabInputDef("molecular_weight_g_mol", "Molecular weight (g/mol)", "58.44"),
            LabInputDef("molarity", "Molarity", "0.1"),
            LabInputDef("molarity_unit", "Molarity unit", "M", LabInputKind.CHOICE, molarUnits),
            LabInputDef("volume", "Volume", "500"),
            LabInputDef("volume_unit", "Volume unit", "mL", LabInputKind.CHOICE, volumeUnits),
            LabInputDef("mass", "Mass", ""),
            LabInputDef("mass_unit", "Mass unit", "g", LabInputKind.CHOICE, massUnits)
        )
    )

    val Reconstitute = LabCalculatorDefinition(
        "reconstitute", "Reconstitute", "Calculate final reconstitution volume from material amount and target concentration.", LabBenchMethods.Reconstitute,
        listOf(
            LabInputDef("material_amount", "Material amount", "25"),
            LabInputDef("material_unit", "Material unit", "mg", LabInputKind.CHOICE, massUnits + amountUnits),
            LabInputDef("target_concentration", "Target concentration", "5"),
            LabInputDef("target_concentration_unit", "Target concentration unit", "mg/mL", LabInputKind.CHOICE, molarUnits + massVolumeConcentrationUnits),
            LabInputDef("molecular_weight_g_mol", "Molecular weight (g/mol), if crossing mass/molar units", "")
        )
    )

    val SerialDilution = LabCalculatorDefinition(
        "serial_dilution", "Serial dilution", "Generate a factor-based serial dilution plan.", LabBenchMethods.SerialDilution,
        listOf(
            LabInputDef("starting_concentration", "Starting concentration", "100"),
            LabInputDef("concentration_unit", "Concentration unit", "uM", LabInputKind.CHOICE, volumetricConcentrationUnits),
            LabInputDef("dilution_factor", "Dilution factor", "2"),
            LabInputDef("number_of_levels", "Number of levels", "8", LabInputKind.INTEGER),
            LabInputDef("prepared_volume", "Prepared volume per level", "1000"),
            LabInputDef("prepared_volume_unit", "Prepared-volume unit", "uL", LabInputKind.CHOICE, volumeUnits),
            LabInputDef("overage_percent", "Overage (%)", "0")
        )
    )

    val MasterMix = LabCalculatorDefinition(
        "master_mix", "Master mix", "Scale reagent volumes across reactions. Add * after a component name to exclude it from the shared mix.", LabBenchMethods.MasterMix,
        listOf(
            LabInputDef("components", "Components (Name=volume; * means separate)", "2x mix=10\nPrimer F=0.5\nPrimer R=0.5\nWater=8\nTemplate*=1", LabInputKind.MULTILINE),
            LabInputDef("component_volume_unit", "Component-volume unit", "uL", LabInputKind.CHOICE, volumeUnits),
            LabInputDef("number_of_reactions", "Number of reactions", "24", LabInputKind.INTEGER),
            LabInputDef("overage_mode", "Overage mode", "percent", LabInputKind.CHOICE, listOf("percent", "extra_reactions")),
            LabInputDef("overage_value", "Overage", "10"),
            LabInputDef("declared_final_volume", "Declared final reaction volume (optional)", "20")
        )
    )

    val Centrifuge = LabCalculatorDefinition(
        "centrifuge", "Centrifuge", "Convert RCF (×g) and RPM using rotational radius.", LabBenchMethods.Centrifuge,
        listOf(
            LabInputDef("mode", "Convert", "rcf_to_rpm", LabInputKind.CHOICE, listOf("rcf_to_rpm", "rpm_to_rcf")),
            LabInputDef("radius", "Radius from axis to sample", "8.45"),
            LabInputDef("radius_unit", "Radius unit", "cm", LabInputKind.CHOICE, listOf("mm", "cm", "m")),
            LabInputDef("rcf", "RCF (×g)", "12000"),
            LabInputDef("rpm", "RPM", "")
        )
    )

    val Concentration = LabCalculatorDefinition(
        "concentration", "Concentration", "Convert molar, mass/volume and percent concentration units.", LabBenchMethods.Concentration,
        listOf(
            LabInputDef("value", "Value", "1"),
            LabInputDef("from_unit", "From", "mg/mL", LabInputKind.CHOICE, converterConcentrationUnits),
            LabInputDef("to_unit", "To", "g/L", LabInputKind.CHOICE, converterConcentrationUnits),
            LabInputDef("molecular_weight_g_mol", "Molecular weight (g/mol), if needed", ""),
            LabInputDef("density_g_ml", "Density (g/mL), if needed", "")
        )
    )

    val NucleicAcid = LabCalculatorDefinition(
        "nucleic_acid", "Nucleic acid", "DNA/RNA molarity, mass and copy-number calculations.", LabBenchMethods.NucleicAcid,
        listOf(
            LabInputDef("mode", "Calculation", "mass_conc_to_molarity", LabInputKind.CHOICE, listOf("mass_conc_to_molarity", "molarity_to_mass_conc", "mass_to_copies", "copies_per_ul", "reconstitution")),
            LabInputDef("nucleic_acid_type", "Nucleic acid type", "dsDNA", LabInputKind.CHOICE, listOf("dsDNA", "ssDNA", "RNA")),
            LabInputDef("length", "Length (bp or nt)", "5000", LabInputKind.INTEGER),
            LabInputDef("molecular_weight_override", "Molecular weight override (g/mol, optional)", ""),
            LabInputDef("mass_concentration", "Mass concentration", "20"),
            LabInputDef("mass_concentration_unit", "Mass concentration unit", "ng/uL", LabInputKind.CHOICE, listOf("mg/mL", "ug/mL", "ng/uL", "ng/mL")),
            LabInputDef("molarity", "Molarity", ""),
            LabInputDef("molarity_unit", "Molarity unit", "nM", LabInputKind.CHOICE, molarUnits),
            LabInputDef("mass", "Mass", ""),
            LabInputDef("mass_unit", "Mass unit", "ng", LabInputKind.CHOICE, massUnits),
            LabInputDef("target_molarity", "Target molarity", "100"),
            LabInputDef("target_molarity_unit", "Target molarity unit", "nM", LabInputKind.CHOICE, molarUnits)
        )
    )

    val CellDilution = LabCalculatorDefinition(
        "cell_dilution", "Cell / OD dilution", "Calculate suspension and medium volumes.", LabBenchMethods.CellDilution,
        listOf(
            LabInputDef("starting_concentration", "Starting concentration", "20000000"),
            LabInputDef("target_concentration", "Target concentration", "2500000"),
            LabInputDef("concentration_unit", "Concentration unit", "cells/mL", LabInputKind.CHOICE, listOf("cells/mL", "cells/uL", "OD")),
            LabInputDef("final_volume", "Final volume", "10"),
            LabInputDef("final_volume_unit", "Final volume unit", "mL", LabInputKind.CHOICE, volumeUnits)
        )
    )

    val Hemocytometer = LabCalculatorDefinition(
        "hemocytometer", "Hemocytometer", "Convert counts and chamber volume to cells/mL, with optional live/dead viability.", LabBenchMethods.Hemocytometer,
        listOf(
            LabInputDef("total_cells", "Total cells counted", "280", LabInputKind.INTEGER),
            LabInputDef("number_of_regions", "Counting regions", "4", LabInputKind.INTEGER),
            LabInputDef("dilution_factor", "Dilution factor", "1"),
            LabInputDef("region_volume_ul", "Volume per counted region (µL)", "0.1"),
            LabInputDef("live_cells", "Live cells (optional)", "", LabInputKind.INTEGER),
            LabInputDef("dead_cells", "Dead cells (optional)", "", LabInputKind.INTEGER)
        )
    )

    val Aliquot = LabCalculatorDefinition(
        "aliquot", "Aliquot planner", "Calculate aliquot count or maximum aliquot size.", LabBenchMethods.Aliquot,
        listOf(
            LabInputDef("mode", "Plan by", "size_to_count", LabInputKind.CHOICE, listOf("size_to_count", "count_to_size")),
            LabInputDef("available_volume", "Available volume", "10"),
            LabInputDef("volume_unit", "Volume unit", "mL", LabInputKind.CHOICE, volumeUnits),
            LabInputDef("aliquot_volume", "Aliquot volume", "0.2"),
            LabInputDef("number_of_aliquots", "Number of aliquots", "", LabInputKind.INTEGER),
            LabInputDef("reserve_volume", "Reserve volume", "0.5"),
            LabInputDef("dead_volume", "Dead/loss volume", "0.1"),
            LabInputDef("overage_percent", "Per-aliquot overage (%)", "0")
        )
    )

    val PercentSolution = LabCalculatorDefinition(
        "percent_solution", "% solution", "Prepare % w/v, % v/v or % w/w solutions.", LabBenchMethods.PercentSolution,
        listOf(
            LabInputDef("mode", "Percent basis", "w_v", LabInputKind.CHOICE, listOf("w_v", "v_v", "w_w")),
            LabInputDef("percent", "Percent", "4"),
            LabInputDef("final_amount", "Final amount", "250"),
            LabInputDef("final_amount_unit", "Final amount unit", "mL", LabInputKind.CHOICE, volumeUnits + massUnits)
        )
    )

    val all = listOf(Dilution, MolarSolution, Reconstitute, SerialDilution, MasterMix, Centrifuge, Concentration, NucleicAcid, CellDilution, Hemocytometer, Aliquot, PercentSolution)
    val byKey = all.associateBy { it.key }
    val byMethodId = all.associateBy { it.method.id }
}
