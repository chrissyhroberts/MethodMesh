package com.example.methodmesh.modules.labbench

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

/** Pure, offline laboratory calculations shared by atomic methods and the dashboard. */
object LabBenchEngine {
    const val VERSION = "0.1.0"
    private const val AVOGADRO = 6.02214076e23
    private const val RCF_CONSTANT = 1.118e-5

    data class DisplayValue(val value: Double, val unit: String) {
        fun text(): String = "${format(value)} ${displayUnit(unit)}"
    }

    private enum class ConcentrationDimension { MOLAR, MASS_VOLUME, MASS_FRACTION, VOLUME_FRACTION, RELATIVE, CELLS, OD }
    private data class NormalisedConcentration(val value: Double, val dimension: ConcentrationDimension)

    fun calculate(calculator: String, settings: Map<String, String>): Map<String, String> = when (calculator) {
        "dilution" -> dilution(settings)
        "molar_solution" -> molarSolution(settings)
        "reconstitute" -> reconstitute(settings)
        "serial_dilution" -> serialDilution(settings)
        "master_mix" -> masterMix(settings)
        "centrifuge" -> centrifuge(settings)
        "concentration" -> concentration(settings)
        "nucleic_acid" -> nucleicAcid(settings)
        "cell_dilution" -> cellDilution(settings)
        "hemocytometer" -> hemocytometer(settings)
        "aliquot" -> aliquot(settings)
        "percent_solution" -> percentSolution(settings)
        else -> failure("labbench_dashboard", settings, "Unknown calculator '$calculator'.")
    }

    fun dilution(settings: Map<String, String>): Map<String, String> {
        val prefix = "lab_dilution"
        return runCalculation(prefix, settings, "C1V1 = C2V2") {
            val stockUnit = settings.require("stock_concentration_unit")
            val targetUnit = settings.require("target_concentration_unit")
            require(stockUnit != "percent_ww" && targetUnit != "percent_ww") { "% w/w is mass-based and cannot be used with a final-volume C1V1=C2V2 dilution. Use the % solution calculator or convert with explicit density first." }
            val mw = settings.optionalDouble("molecular_weight_g_mol")
            val density = settings.optionalDouble("density_g_ml")
            var stock = concentrationValue(settings.requireDouble("stock_concentration"), stockUnit, mw, density)
            var target = concentrationValue(settings.requireDouble("target_concentration"), targetUnit, mw, density)
            if (stock.dimension != target.dimension && setOf(stock.dimension, target.dimension) == setOf(ConcentrationDimension.MOLAR, ConcentrationDimension.MASS_VOLUME)) {
                require(mw != null && mw > 0.0) { "Molecular weight in g/mol is required when stock and target cross molar and mass/volume concentration units." }
                stock = if (stock.dimension == ConcentrationDimension.MASS_VOLUME) NormalisedConcentration(stock.value / mw, ConcentrationDimension.MOLAR) else stock
                target = if (target.dimension == ConcentrationDimension.MASS_VOLUME) NormalisedConcentration(target.value / mw, ConcentrationDimension.MOLAR) else target
            }
            require(stock.dimension == target.dimension) { "Stock and target concentrations must use compatible concentration dimensions." }
            require(stock.value > 0.0 && target.value > 0.0) { "Concentrations must be greater than zero." }
            require(stock.value >= target.value) { "Target concentration cannot exceed stock concentration for a dilution." }
            val finalL = volumeToLitres(settings.requireDouble("final_volume"), settings.require("final_volume_unit"))
            require(finalL > 0.0) { "Final volume must be greater than zero." }
            val stockL = target.value * finalL / stock.value
            val diluentL = finalL - stockL
            val stockDisplay = bestVolume(stockL)
            val diluentDisplay = bestVolume(diluentL)
            val finalDisplay = bestVolume(finalL)
            val factor = stock.value / target.value
            linkedMapOf(
                "${prefix}_stock_volume" to format(stockDisplay.value),
                "${prefix}_stock_volume_unit" to stockDisplay.unit,
                "${prefix}_diluent_volume" to format(diluentDisplay.value),
                "${prefix}_diluent_volume_unit" to diluentDisplay.unit,
                "${prefix}_final_volume" to format(finalDisplay.value),
                "${prefix}_final_volume_unit" to finalDisplay.unit,
                "${prefix}_factor" to format(factor),
                "${prefix}_instruction" to "Use ${stockDisplay.text()} stock + ${diluentDisplay.text()} diluent; final volume ${finalDisplay.text()}."
            )
        }
    }

    fun molarSolution(settings: Map<String, String>): Map<String, String> {
        val prefix = "lab_molar_solution"
        val mode = settings.value("mode") ?: "mass_required"
        return runCalculation(prefix, settings, when (mode) {
            "molarity_from_mass" -> "M = (mass / MW) / volume"
            "volume_from_mass" -> "V = (mass / MW) / M"
            else -> "mass = M × V × MW"
        }) {
            val mw = settings.requireDouble("molecular_weight_g_mol")
            require(mw > 0.0) { "Molecular weight must be greater than zero." }
            var massG: Double? = null
            var molarityM: Double? = null
            var volumeL: Double? = null
            when (mode) {
                "mass_required" -> {
                    molarityM = molarToM(settings.requireDouble("molarity"), settings.require("molarity_unit"))
                    volumeL = volumeToLitres(settings.requireDouble("volume"), settings.require("volume_unit"))
                    massG = molarityM * volumeL * mw
                }
                "molarity_from_mass" -> {
                    massG = massToGrams(settings.requireDouble("mass"), settings.require("mass_unit"))
                    volumeL = volumeToLitres(settings.requireDouble("volume"), settings.require("volume_unit"))
                    molarityM = massG / mw / volumeL
                }
                "volume_from_mass" -> {
                    massG = massToGrams(settings.requireDouble("mass"), settings.require("mass_unit"))
                    molarityM = molarToM(settings.requireDouble("molarity"), settings.require("molarity_unit"))
                    volumeL = massG / mw / molarityM
                }
                else -> error("Unknown mode '$mode'.")
            }
            require((massG ?: 0.0) > 0.0 && (molarityM ?: 0.0) > 0.0 && (volumeL ?: 0.0) > 0.0) { "Mass, molarity and volume must resolve to values greater than zero." }
            val massDisplay = bestMass(massG!!)
            val molarityDisplay = bestMolar(molarityM!!)
            val volumeDisplay = bestVolume(volumeL!!)
            val moles = molarityM * volumeL
            val instruction = when (mode) {
                "mass_required" -> "Weigh ${massDisplay.text()}, dissolve, and make up to ${volumeDisplay.text()} for ${molarityDisplay.text()}."
                "molarity_from_mass" -> "${massDisplay.text()} in ${volumeDisplay.text()} corresponds to ${molarityDisplay.text()}."
                else -> "${massDisplay.text()} at ${molarityDisplay.text()} gives a final volume of ${volumeDisplay.text()}."
            }
            linkedMapOf(
                "${prefix}_mode" to mode,
                "${prefix}_mass" to format(massDisplay.value),
                "${prefix}_mass_unit" to massDisplay.unit,
                "${prefix}_molarity" to format(molarityDisplay.value),
                "${prefix}_molarity_unit" to molarityDisplay.unit,
                "${prefix}_volume" to format(volumeDisplay.value),
                "${prefix}_volume_unit" to volumeDisplay.unit,
                "${prefix}_moles" to format(moles),
                "${prefix}_instruction" to instruction
            )
        }
    }

    fun reconstitute(settings: Map<String, String>): Map<String, String> {
        val prefix = "lab_reconstitute"
        return runCalculation(prefix, settings, "final volume = material amount / target concentration") {
            val amount = settings.requireDouble("material_amount")
            require(amount > 0.0) { "Material amount must be greater than zero." }
            val materialUnit = settings.require("material_unit")
            val targetUnit = settings.require("target_concentration_unit")
            val target = settings.requireDouble("target_concentration")
            require(target > 0.0) { "Target concentration must be greater than zero." }
            val mw = settings.optionalDouble("molecular_weight_g_mol")
            val materialIsMoles = materialUnit in setOf("mol", "mmol", "umol", "nmol", "pmol")
            val targetNormal = concentrationValue(target, targetUnit, mw, settings.optionalDouble("density_g_ml"))
            val volumeL = when {
                materialIsMoles && targetNormal.dimension == ConcentrationDimension.MOLAR -> amountToMoles(amount, materialUnit) / targetNormal.value
                !materialIsMoles && targetNormal.dimension == ConcentrationDimension.MASS_VOLUME -> massToGrams(amount, materialUnit) / targetNormal.value
                materialIsMoles && targetNormal.dimension == ConcentrationDimension.MASS_VOLUME -> {
                    require(mw != null && mw > 0.0) { "Molecular weight is required to convert amount-of-substance to mass concentration." }
                    (amountToMoles(amount, materialUnit) * mw) / targetNormal.value
                }
                !materialIsMoles && targetNormal.dimension == ConcentrationDimension.MOLAR -> {
                    require(mw != null && mw > 0.0) { "Molecular weight is required to convert mass to molar concentration." }
                    (massToGrams(amount, materialUnit) / mw) / targetNormal.value
                }
                else -> error("Material and target concentration units are not compatible for reconstitution.")
            }
            require(volumeL > 0.0) { "Calculated final volume must be greater than zero." }
            val volumeDisplay = bestVolume(volumeL)
            linkedMapOf(
                "${prefix}_final_volume" to format(volumeDisplay.value),
                "${prefix}_final_volume_unit" to volumeDisplay.unit,
                "${prefix}_instruction" to "Add solvent and bring the material to a final volume of ${volumeDisplay.text()}."
            )
        }
    }

    fun serialDilution(settings: Map<String, String>): Map<String, String> {
        val prefix = "lab_serial_dilution"
        return runCalculation(prefix, settings, "Cn = C0 / factor^n") {
            val start = settings.requireDouble("starting_concentration")
            val unit = settings.require("concentration_unit")
            require(unit != "percent_ww") { "% w/w is mass-based and is not supported by this volumetric serial-dilution planner." }
            val factor = settings.requireDouble("dilution_factor")
            val levels = settings.requireInt("number_of_levels")
            val preparedL = volumeToLitres(settings.requireDouble("prepared_volume"), settings.require("prepared_volume_unit"))
            val overage = settings.optionalDouble("overage_percent") ?: 0.0
            require(start > 0.0) { "Starting concentration must be greater than zero." }
            require(factor > 1.0) { "Dilution factor must be greater than 1." }
            require(levels in 2..96) { "Number of levels must be between 2 and 96." }
            require(preparedL > 0.0) { "Prepared volume must be greater than zero." }
            require(overage >= 0.0) { "Overage cannot be negative." }
            val adjustedL = preparedL * (1.0 + overage / 100.0)
            val transferL = adjustedL / factor
            val diluentL = adjustedL - transferL
            val transferDisplay = bestVolume(transferL)
            val diluentDisplay = bestVolume(diluentL)
            val steps = JSONArray()
            for (i in 0 until levels) {
                val obj = JSONObject()
                    .put("level", i + 1)
                    .put("concentration", start / factor.pow(i.toDouble()))
                    .put("concentration_unit", unit)
                if (i == 0) {
                    obj.put("transfer_from_previous", JSONObject.NULL).put("diluent", JSONObject.NULL)
                } else {
                    obj.put("transfer_from_previous", transferDisplay.value)
                        .put("transfer_unit", transferDisplay.unit)
                        .put("diluent", diluentDisplay.value)
                        .put("diluent_unit", diluentDisplay.unit)
                }
                steps.put(obj)
            }
            val end = start / factor.pow((levels - 1).toDouble())
            val totalDiluent = diluentL * (levels - 1)
            val totalDiluentDisplay = bestVolume(totalDiluent)
            linkedMapOf(
                "${prefix}_step_count" to levels.toString(),
                "${prefix}_start_concentration" to format(start),
                "${prefix}_end_concentration" to format(end),
                "${prefix}_concentration_unit" to unit,
                "${prefix}_transfer_volume" to format(transferDisplay.value),
                "${prefix}_transfer_volume_unit" to transferDisplay.unit,
                "${prefix}_diluent_per_step" to format(diluentDisplay.value),
                "${prefix}_diluent_per_step_unit" to diluentDisplay.unit,
                "${prefix}_total_diluent" to format(totalDiluentDisplay.value),
                "${prefix}_total_diluent_unit" to totalDiluentDisplay.unit,
                "${prefix}_steps_json" to steps.toString(),
                "${prefix}_instruction" to "Prepare $levels levels at ${format(factor)}× dilution: for each new level mix ${transferDisplay.text()} from the previous level with ${diluentDisplay.text()} diluent."
            )
        }
    }

    private data class MasterMixComponent(val name: String, val volume: Double, val excluded: Boolean)

    fun masterMix(settings: Map<String, String>): Map<String, String> {
        val prefix = "lab_master_mix"
        return runCalculation(prefix, settings, "component total = per-reaction volume × effective reaction count") {
            val unit = settings.value("component_volume_unit") ?: "uL"
            val components = parseComponents(settings.require("components"))
            require(components.isNotEmpty()) { "At least one master-mix component is required." }
            require(components.all { it.volume >= 0.0 }) { "Component volumes cannot be negative." }
            val reactions = settings.requireDouble("number_of_reactions")
            require(reactions > 0.0) { "Number of reactions must be greater than zero." }
            val overageMode = settings.value("overage_mode") ?: "percent"
            val overage = settings.optionalDouble("overage_value") ?: 0.0
            require(overage >= 0.0) { "Overage cannot be negative." }
            val effective = when (overageMode) {
                "extra_reactions" -> reactions + overage
                else -> reactions * (1.0 + overage / 100.0)
            }
            val componentJson = JSONArray()
            var mixPerReaction = 0.0
            var reactionTotal = 0.0
            components.forEach { component ->
                reactionTotal += component.volume
                val equivalents = if (component.excluded) reactions else effective
                val total = component.volume * equivalents
                if (!component.excluded) mixPerReaction += component.volume
                componentJson.put(
                    JSONObject()
                        .put("name", component.name)
                        .put("per_reaction", component.volume)
                        .put("unit", unit)
                        .put("excluded_from_master_mix", component.excluded)
                        .put("reaction_equivalents", equivalents)
                        .put("total_required", total)
                )
            }
            val masterTotal = mixPerReaction * effective
            val declaredFinal = settings.optionalDouble("declared_final_volume")
            val warning = if (declaredFinal != null && declaredFinal > 0.0 && abs(declaredFinal - reactionTotal) > 1e-6) {
                "Component volumes sum to ${format(reactionTotal)} ${displayUnit(unit)}, not declared ${format(declaredFinal)} ${displayUnit(unit)}."
            } else ""
            val excludedCount = components.count { it.excluded }
            linkedMapOf(
                "${prefix}_effective_reaction_count" to format(effective),
                "${prefix}_master_mix_per_reaction" to format(mixPerReaction),
                "${prefix}_master_mix_total" to format(masterTotal),
                "${prefix}_component_volume_unit" to unit,
                "${prefix}_reaction_total_volume" to format(reactionTotal),
                "${prefix}_components_json" to componentJson.toString(),
                "${prefix}_warning" to warning,
                "${prefix}_instruction" to "Prepare ${format(masterTotal)} ${displayUnit(unit)} master mix for ${format(effective)} reaction equivalents${if (excludedCount > 0) "; add $excludedCount excluded component(s) separately" else ""}."
            )
        }
    }

    fun centrifuge(settings: Map<String, String>): Map<String, String> {
        val prefix = "lab_centrifuge"
        val mode = settings.value("mode") ?: "rcf_to_rpm"
        return runCalculation(prefix, settings, "RCF = 1.118e-5 × radius_cm × RPM²") {
            val radiusCm = lengthToCm(settings.requireDouble("radius"), settings.require("radius_unit"))
            require(radiusCm > 0.0) { "Radius must be greater than zero." }
            val rpm: Double
            val rcf: Double
            when (mode) {
                "rpm_to_rcf" -> {
                    rpm = settings.requireDouble("rpm")
                    require(rpm > 0.0) { "RPM must be greater than zero." }
                    rcf = RCF_CONSTANT * radiusCm * rpm * rpm
                }
                "rcf_to_rpm" -> {
                    rcf = settings.requireDouble("rcf")
                    require(rcf > 0.0) { "RCF must be greater than zero." }
                    rpm = sqrt(rcf / (RCF_CONSTANT * radiusCm))
                }
                else -> error("Unknown mode '$mode'.")
            }
            linkedMapOf(
                "${prefix}_rpm" to format(rpm),
                "${prefix}_rcf" to format(rcf),
                "${prefix}_radius_cm" to format(radiusCm),
                "${prefix}_instruction" to "${format(rcf)} × g corresponds to ${format(rpm)} rpm at a ${format(radiusCm)} cm rotational radius."
            )
        }
    }

    fun concentration(settings: Map<String, String>): Map<String, String> {
        val prefix = "lab_concentration"
        return runCalculation(prefix, settings, "unit-aware concentration conversion") {
            val value = settings.requireDouble("value")
            val from = settings.require("from_unit")
            val to = settings.require("to_unit")
            require(value >= 0.0) { "Concentration cannot be negative." }
            val mw = settings.optionalDouble("molecular_weight_g_mol")
            val density = settings.optionalDouble("density_g_ml")
            if (from == to) {
                return@runCalculation linkedMapOf(
                    "${prefix}_input_value" to format(value),
                    "${prefix}_input_unit" to from,
                    "${prefix}_value" to format(value),
                    "${prefix}_unit" to to,
                    "${prefix}_assumptions" to "",
                    "${prefix}_instruction" to "${format(value)} ${displayUnit(from)} = ${format(value)} ${displayUnit(to)}."
                )
            }
            val normal = concentrationValue(value, from, mw, density)
            val converted = concentrationFromNormal(normal, to, mw, density)
            val assumptions = mutableListOf<String>()
            if ((from == "percent_ww" || to == "percent_ww") && density != null) assumptions += "Density supplied explicitly as ${format(density)} g/mL."
            if ((normal.dimension == ConcentrationDimension.MOLAR && to in massConcentrationUnits()) || (from in molarUnits() && to in massConcentrationUnits()) || (from in massConcentrationUnits() && to in molarUnits())) {
                if (mw != null) assumptions += "Molecular weight supplied explicitly as ${format(mw)} g/mol."
            }
            linkedMapOf(
                "${prefix}_input_value" to format(value),
                "${prefix}_input_unit" to from,
                "${prefix}_value" to format(converted),
                "${prefix}_unit" to to,
                "${prefix}_assumptions" to assumptions.joinToString(" "),
                "${prefix}_instruction" to "${format(value)} ${displayUnit(from)} = ${format(converted)} ${displayUnit(to)}."
            )
        }
    }

    fun nucleicAcid(settings: Map<String, String>): Map<String, String> {
        val prefix = "lab_nucleic_acid"
        val mode = settings.value("mode") ?: "mass_conc_to_molarity"
        return runCalculation(prefix, settings, "nucleic-acid molecular amount conversion") {
            val type = settings.value("nucleic_acid_type") ?: "dsDNA"
            val length = settings.requireDouble("length")
            require(length > 0.0) { "Length must be greater than zero." }
            val override = settings.optionalDouble("molecular_weight_override")
            val mw = override?.takeIf { it > 0.0 } ?: when (type) {
                "ssDNA" -> length * 330.0
                "RNA" -> length * 340.0
                else -> length * 660.0
            }
            var molarityM: Double? = null
            var massConcGPerL: Double? = null
            var copies: Double? = null
            var copiesPerUL: Double? = null
            var volumeL: Double? = null
            var massG: Double? = null
            when (mode) {
                "mass_conc_to_molarity" -> {
                    massConcGPerL = massConcentrationToGPerL(settings.requireDouble("mass_concentration"), settings.require("mass_concentration_unit"), settings.optionalDouble("density_g_ml"))
                    molarityM = massConcGPerL / mw
                }
                "molarity_to_mass_conc" -> {
                    molarityM = molarToM(settings.requireDouble("molarity"), settings.require("molarity_unit"))
                    massConcGPerL = molarityM * mw
                }
                "mass_to_copies" -> {
                    massG = massToGrams(settings.requireDouble("mass"), settings.require("mass_unit"))
                    copies = massG / mw * AVOGADRO
                }
                "copies_per_ul" -> {
                    massConcGPerL = massConcentrationToGPerL(settings.requireDouble("mass_concentration"), settings.require("mass_concentration_unit"), settings.optionalDouble("density_g_ml"))
                    molarityM = massConcGPerL / mw
                    copiesPerUL = molarityM * AVOGADRO / 1e6
                }
                "reconstitution" -> {
                    massG = massToGrams(settings.requireDouble("mass"), settings.require("mass_unit"))
                    molarityM = molarToM(settings.requireDouble("target_molarity"), settings.require("target_molarity_unit"))
                    volumeL = (massG / mw) / molarityM
                }
                else -> error("Unknown mode '$mode'.")
            }
            val molDisplay = molarityM?.let(::bestMolar)
            val massConcDisplay = massConcGPerL?.let(::bestMassConcentration)
            val volumeDisplay = volumeL?.let(::bestVolume)
            val instruction = when (mode) {
                "mass_conc_to_molarity" -> "Mass concentration corresponds to ${molDisplay!!.text()} using MW ${format(mw)} g/mol."
                "molarity_to_mass_conc" -> "${molDisplay!!.text()} corresponds to ${massConcDisplay!!.text()} using MW ${format(mw)} g/mol."
                "mass_to_copies" -> "Estimated molecule count: ${format(copies!!)} copies using MW ${format(mw)} g/mol."
                "copies_per_ul" -> "Estimated concentration: ${format(copiesPerUL!!)} copies/µL using MW ${format(mw)} g/mol."
                else -> "Bring the nucleic acid to ${volumeDisplay!!.text()} final volume to obtain ${molDisplay!!.text()}."
            }
            linkedMapOf(
                "${prefix}_mode" to mode,
                "${prefix}_molecular_weight_g_mol" to format(mw),
                "${prefix}_assumptions" to if (override != null) "Molecular weight override supplied explicitly." else when (type) {
                    "ssDNA" -> "Molecular weight estimated at 330 g/mol per nucleotide."
                    "RNA" -> "Molecular weight estimated at 340 g/mol per nucleotide."
                    else -> "Molecular weight estimated at 660 g/mol per base pair."
                },
                "${prefix}_molarity" to (molDisplay?.let { format(it.value) } ?: ""),
                "${prefix}_molarity_unit" to (molDisplay?.unit ?: ""),
                "${prefix}_mass_concentration" to (massConcDisplay?.let { format(it.value) } ?: ""),
                "${prefix}_mass_concentration_unit" to (massConcDisplay?.unit ?: ""),
                "${prefix}_copy_number" to (copies?.let(::format) ?: ""),
                "${prefix}_copies_per_ul" to (copiesPerUL?.let(::format) ?: ""),
                "${prefix}_final_volume" to (volumeDisplay?.let { format(it.value) } ?: ""),
                "${prefix}_final_volume_unit" to (volumeDisplay?.unit ?: ""),
                "${prefix}_instruction" to instruction
            )
        }
    }

    fun cellDilution(settings: Map<String, String>): Map<String, String> {
        val prefix = "lab_cell_dilution"
        return runCalculation(prefix, settings, "C1V1 = C2V2") {
            val unit = settings.require("concentration_unit")
            val start = settings.requireDouble("starting_concentration")
            val target = settings.requireDouble("target_concentration")
            require(start > 0.0 && target > 0.0) { "Cell/OD concentrations must be greater than zero." }
            require(start >= target) { "Target concentration cannot exceed starting concentration for a dilution." }
            val finalL = volumeToLitres(settings.requireDouble("final_volume"), settings.require("final_volume_unit"))
            require(finalL > 0.0) { "Final volume must be greater than zero." }
            val sampleL = target * finalL / start
            val mediumL = finalL - sampleL
            val sampleDisplay = bestVolume(sampleL)
            val mediumDisplay = bestVolume(mediumL)
            val finalDisplay = bestVolume(finalL)
            linkedMapOf(
                "${prefix}_sample_volume" to format(sampleDisplay.value),
                "${prefix}_sample_volume_unit" to sampleDisplay.unit,
                "${prefix}_medium_volume" to format(mediumDisplay.value),
                "${prefix}_medium_volume_unit" to mediumDisplay.unit,
                "${prefix}_final_volume" to format(finalDisplay.value),
                "${prefix}_final_volume_unit" to finalDisplay.unit,
                "${prefix}_concentration_unit" to unit,
                "${prefix}_instruction" to "Use ${sampleDisplay.text()} cell suspension + ${mediumDisplay.text()} medium to prepare ${finalDisplay.text()} at ${format(target)} ${displayUnit(unit)}."
            )
        }
    }

    fun hemocytometer(settings: Map<String, String>): Map<String, String> {
        val prefix = "lab_hemocytometer"
        return runCalculation(prefix, settings, "cells/mL = mean count × dilution factor / counted-region volume (mL)") {
            val regions = settings.requireDouble("number_of_regions")
            val dilution = settings.optionalDouble("dilution_factor") ?: 1.0
            val regionVolumeUL = settings.optionalDouble("region_volume_ul") ?: 0.1
            require(regions > 0.0) { "Number of counting regions must be greater than zero." }
            require(dilution > 0.0) { "Dilution factor must be greater than zero." }
            require(regionVolumeUL > 0.0) { "Counted-region volume must be greater than zero." }
            val live = settings.optionalDouble("live_cells")
            val dead = settings.optionalDouble("dead_cells")
            require((live == null) == (dead == null)) { "Supply both live_cells and dead_cells for viability, or leave both blank." }
            require((live ?: 0.0) >= 0.0 && (dead ?: 0.0) >= 0.0) { "Live/dead counts cannot be negative." }
            val total = if (live != null && dead != null) live + dead else settings.requireDouble("total_cells")
            require(total >= 0.0) { "Cell count cannot be negative." }
            val mean = total / regions
            val regionVolumeMl = regionVolumeUL / 1000.0
            val cellsPerMl = mean * dilution / regionVolumeMl
            val viability = if (live != null && dead != null && live + dead > 0.0) 100.0 * live / (live + dead) else null
            linkedMapOf(
                "${prefix}_cells_per_ml" to format(cellsPerMl),
                "${prefix}_mean_cells_per_region" to format(mean),
                "${prefix}_viability_percent" to (viability?.let(::format) ?: ""),
                "${prefix}_instruction" to "Estimated concentration: ${format(cellsPerMl)} cells/mL${viability?.let { "; viability ${format(it)}%" } ?: ""}."
            )
        }
    }

    fun aliquot(settings: Map<String, String>): Map<String, String> {
        val prefix = "lab_aliquot"
        val mode = settings.value("mode") ?: "size_to_count"
        return runCalculation(prefix, settings, "available usable volume = available - reserve - dead volume") {
            val availableL = volumeToLitres(settings.requireDouble("available_volume"), settings.require("volume_unit"))
            val reserveL = settings.optionalDouble("reserve_volume")?.let { volumeToLitres(it, settings.require("volume_unit")) } ?: 0.0
            val deadL = settings.optionalDouble("dead_volume")?.let { volumeToLitres(it, settings.require("volume_unit")) } ?: 0.0
            val overage = settings.optionalDouble("overage_percent") ?: 0.0
            require(availableL > 0.0) { "Available volume must be greater than zero." }
            require(reserveL >= 0.0 && deadL >= 0.0 && overage >= 0.0) { "Reserve, dead volume and overage cannot be negative." }
            val usableL = availableL - reserveL - deadL
            require(usableL > 0.0) { "Reserve and dead volume leave no usable material." }
            val count: Int
            val targetAliquotL: Double
            val dispenseL: Double
            when (mode) {
                "count_to_size" -> {
                    count = settings.requireInt("number_of_aliquots")
                    require(count > 0) { "Number of aliquots must be greater than zero." }
                    dispenseL = usableL / count
                    targetAliquotL = dispenseL / (1.0 + overage / 100.0)
                }
                else -> {
                    targetAliquotL = volumeToLitres(settings.requireDouble("aliquot_volume"), settings.require("volume_unit"))
                    require(targetAliquotL > 0.0) { "Aliquot volume must be greater than zero." }
                    dispenseL = targetAliquotL * (1.0 + overage / 100.0)
                    count = kotlin.math.floor(usableL / dispenseL).toInt()
                }
            }
            require(count > 0 && targetAliquotL > 0.0 && dispenseL > 0.0) { "Available material is insufficient for one aliquot." }
            val usedL = dispenseL * count
            val remainderL = usableL - usedL
            val aliquotDisplay = bestVolume(targetAliquotL)
            val dispenseDisplay = bestVolume(dispenseL)
            val remainderDisplay = bestVolume(remainderL.coerceAtLeast(0.0))
            val reserveDisplay = bestVolume(reserveL)
            val instruction = if (overage > 0.0) {
                "Plan $count target aliquots of ${aliquotDisplay.text()}; allocate ${dispenseDisplay.text()} per aliquot including ${format(overage)}% overage. ${remainderDisplay.text()} usable material remains after reserve/dead-volume allowance."
            } else {
                "Prepare $count aliquots of ${aliquotDisplay.text()}; ${remainderDisplay.text()} usable material remains after the requested reserve/dead-volume allowance."
            }
            linkedMapOf(
                "${prefix}_count" to count.toString(),
                "${prefix}_aliquot_volume" to format(aliquotDisplay.value),
                "${prefix}_aliquot_volume_unit" to aliquotDisplay.unit,
                "${prefix}_dispense_volume" to format(dispenseDisplay.value),
                "${prefix}_dispense_volume_unit" to dispenseDisplay.unit,
                "${prefix}_remainder" to format(remainderDisplay.value),
                "${prefix}_remainder_unit" to remainderDisplay.unit,
                "${prefix}_reserve" to format(reserveDisplay.value),
                "${prefix}_reserve_unit" to reserveDisplay.unit,
                "${prefix}_instruction" to instruction
            )
        }
    }

    fun percentSolution(settings: Map<String, String>): Map<String, String> {
        val prefix = "lab_percent_solution"
        val mode = settings.value("mode") ?: "w_v"
        return runCalculation(prefix, settings, when (mode) {
            "v_v" -> "% v/v = volume component / final solution volume × 100"
            "w_w" -> "% w/w = mass component / final solution mass × 100"
            else -> "% w/v = grams component per 100 mL final solution"
        }) {
            val pct = settings.requireDouble("percent")
            require(pct in 0.0..100.0) { "Percentage must be between 0 and 100." }
            when (mode) {
                "w_v" -> {
                    val finalL = volumeToLitres(settings.requireDouble("final_amount"), settings.require("final_amount_unit"))
                    require(finalL > 0.0) { "Final volume must be greater than zero." }
                    val massG = pct * (finalL * 1000.0) / 100.0
                    val massDisplay = bestMass(massG)
                    val volumeDisplay = bestVolume(finalL)
                    linkedMapOf(
                        "${prefix}_component_amount" to format(massDisplay.value),
                        "${prefix}_component_unit" to massDisplay.unit,
                        "${prefix}_remainder_amount" to "",
                        "${prefix}_remainder_unit" to "",
                        "${prefix}_instruction" to "Weigh ${massDisplay.text()} and make up to ${volumeDisplay.text()} for ${format(pct)}% w/v."
                    )
                }
                "v_v" -> {
                    val finalL = volumeToLitres(settings.requireDouble("final_amount"), settings.require("final_amount_unit"))
                    require(finalL > 0.0) { "Final volume must be greater than zero." }
                    val componentL = finalL * pct / 100.0
                    val componentDisplay = bestVolume(componentL)
                    val finalDisplay = bestVolume(finalL)
                    linkedMapOf(
                        "${prefix}_component_amount" to format(componentDisplay.value),
                        "${prefix}_component_unit" to componentDisplay.unit,
                        "${prefix}_remainder_amount" to "",
                        "${prefix}_remainder_unit" to "",
                        "${prefix}_instruction" to "Measure ${componentDisplay.text()} component and make up to ${finalDisplay.text()} final volume for ${format(pct)}% v/v."
                    )
                }
                "w_w" -> {
                    val finalG = massToGrams(settings.requireDouble("final_amount"), settings.require("final_amount_unit"))
                    require(finalG > 0.0) { "Final mass must be greater than zero." }
                    val componentG = finalG * pct / 100.0
                    val remainderG = finalG - componentG
                    val componentDisplay = bestMass(componentG)
                    val remainderDisplay = bestMass(remainderG)
                    linkedMapOf(
                        "${prefix}_component_amount" to format(componentDisplay.value),
                        "${prefix}_component_unit" to componentDisplay.unit,
                        "${prefix}_remainder_amount" to format(remainderDisplay.value),
                        "${prefix}_remainder_unit" to remainderDisplay.unit,
                        "${prefix}_instruction" to "Combine ${componentDisplay.text()} component with ${remainderDisplay.text()} remainder for ${format(pct)}% w/w."
                    )
                }
                else -> error("Unknown percent-solution mode '$mode'.")
            }
        }
    }

    private inline fun runCalculation(prefix: String, settings: Map<String, String>, formula: String, block: () -> LinkedHashMap<String, String>): Map<String, String> =
        try {
            val core = block()
            val output = linkedMapOf<String, String>()
            output["${prefix}_status"] = "succeeded"
            output.putAll(core)
            val assumptions = core.entries.filter { it.key.endsWith("_assumptions") && it.value.isNotBlank() }.map { it.value }
            val warnings = core.entries.filter { it.key.endsWith("_warning") && it.value.isNotBlank() }.map { it.value }
            output["${prefix}_audit_json"] = auditJson(prefix, settings, formula, core, assumptions, warnings)
            output["${prefix}_error"] = ""
            output
        } catch (t: Throwable) {
            failure(prefix, settings, t.message ?: "Calculation failed.", formula)
        }

    private fun failure(prefix: String, settings: Map<String, String>, message: String, formula: String = ""): Map<String, String> = linkedMapOf(
        "${prefix}_status" to "failed",
        "${prefix}_instruction" to "",
        "${prefix}_audit_json" to auditJson(prefix, settings, formula, emptyMap(), emptyList(), listOf(message)),
        "${prefix}_error" to message
    )

    private fun methodIdForPrefix(prefix: String): String = when (prefix) {
        "lab_dilution" -> "labbench.dilution"
        "lab_molar_solution" -> "labbench.molar_solution"
        "lab_reconstitute" -> "labbench.reconstitute"
        "lab_serial_dilution" -> "labbench.serial_dilution"
        "lab_master_mix" -> "labbench.master_mix"
        "lab_centrifuge" -> "labbench.centrifuge"
        "lab_concentration" -> "labbench.concentration"
        "lab_nucleic_acid" -> "labbench.nucleic_acid"
        "lab_cell_dilution" -> "labbench.cell_dilution"
        "lab_hemocytometer" -> "labbench.hemocytometer"
        "lab_aliquot" -> "labbench.aliquot"
        "lab_percent_solution" -> "labbench.percent_solution"
        "labbench_dashboard" -> "labbench.dashboard"
        else -> prefix
    }

    private fun auditJson(prefix: String, settings: Map<String, String>, formula: String, outputs: Map<String, String>, assumptions: List<String>, warnings: List<String>): String {
        val inputJson = JSONObject()
        settings.filterKeys { !it.startsWith("methodmesh_") && !it.startsWith("input_methodmesh_") }.toSortedMap().forEach { (k, v) -> inputJson.put(k, v) }
        val outputJson = JSONObject()
        outputs.filterKeys { !it.endsWith("_json") }.forEach { (k, v) -> outputJson.put(k, v) }
        return JSONObject()
            .put("method", methodIdForPrefix(prefix))
            .put("version", VERSION)
            .put("formula", formula)
            .put("inputs", inputJson)
            .put("outputs", outputJson)
            .put("assumptions", JSONArray(assumptions))
            .put("warnings", JSONArray(warnings))
            .toString()
    }

    private fun parseComponents(raw: String): List<MasterMixComponent> {
        val trimmed = raw.trim()
        if (trimmed.startsWith("[")) {
            val array = JSONArray(trimmed)
            return (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                MasterMixComponent(
                    name = obj.optString("name", "Component ${i + 1}"),
                    volume = obj.optDouble("volume", Double.NaN).also { require(it.isFinite()) { "Each JSON component needs a numeric volume." } },
                    excluded = obj.optBoolean("exclude", obj.optBoolean("excluded_from_master_mix", false))
                )
            }
        }
        return trimmed.split('\n', ';').mapNotNull { line ->
            val clean = line.trim()
            if (clean.isBlank()) return@mapNotNull null
            val parts = clean.split('=', limit = 2)
            require(parts.size == 2) { "Master-mix components must use 'Name=volume', one per line." }
            var name = parts[0].trim()
            val excluded = name.endsWith("*")
            if (excluded) name = name.dropLast(1).trim()
            require(name.isNotBlank()) { "Each master-mix component needs a name." }
            val volume = parts[1].trim().toDoubleOrNull() ?: error("Invalid volume for '$name'.")
            MasterMixComponent(name, volume, excluded)
        }
    }

    private fun Map<String, String>.value(key: String): String? = (this[key] ?: this["input_$key"])?.trim()?.takeIf { it.isNotBlank() }
    private fun Map<String, String>.require(key: String): String = value(key) ?: error("Missing required input '$key'.")
    private fun Map<String, String>.requireDouble(key: String): Double = require(key).toDoubleOrNull() ?: error("'$key' must be numeric.")
    private fun Map<String, String>.optionalDouble(key: String): Double? = value(key)?.toDoubleOrNull() ?: value(key)?.let { error("'$key' must be numeric when supplied.") }
    private fun Map<String, String>.requireInt(key: String): Int = require(key).toIntOrNull() ?: error("'$key' must be an integer.")

    private fun volumeToLitres(value: Double, unit: String): Double = value * when (unit) {
        "L" -> 1.0
        "mL" -> 1e-3
        "uL" -> 1e-6
        "nL" -> 1e-9
        else -> error("Unsupported volume unit '$unit'.")
    }

    private fun massToGrams(value: Double, unit: String): Double = value * when (unit) {
        "kg" -> 1e3
        "g" -> 1.0
        "mg" -> 1e-3
        "ug" -> 1e-6
        "ng" -> 1e-9
        "pg" -> 1e-12
        else -> error("Unsupported mass unit '$unit'.")
    }

    private fun amountToMoles(value: Double, unit: String): Double = value * when (unit) {
        "mol" -> 1.0
        "mmol" -> 1e-3
        "umol" -> 1e-6
        "nmol" -> 1e-9
        "pmol" -> 1e-12
        else -> error("Unsupported amount unit '$unit'.")
    }

    private fun molarToM(value: Double, unit: String): Double = value * when (unit) {
        "M" -> 1.0
        "mM" -> 1e-3
        "uM" -> 1e-6
        "nM" -> 1e-9
        "pM" -> 1e-12
        else -> error("Unsupported molarity unit '$unit'.")
    }

    private fun massConcentrationToGPerL(value: Double, unit: String, densityGml: Double?): Double = when (unit) {
        "g/L" -> value
        "mg/mL" -> value
        "ug/mL", "ng/uL" -> value * 1e-3
        "ng/mL" -> value * 1e-6
        "percent_wv" -> value * 10.0
        "percent_ww" -> {
            require(densityGml != null && densityGml > 0.0) { "Density in g/mL is required for % w/w conversions." }
            value / 100.0 * densityGml * 1000.0
        }
        else -> error("Unsupported mass concentration unit '$unit'.")
    }

    private fun concentrationValue(value: Double, unit: String, mw: Double?, density: Double?): NormalisedConcentration = when (unit) {
        in molarUnits() -> NormalisedConcentration(molarToM(value, unit), ConcentrationDimension.MOLAR)
        "percent_ww" -> NormalisedConcentration(value / 100.0, ConcentrationDimension.MASS_FRACTION)
        in massConcentrationUnits() -> NormalisedConcentration(massConcentrationToGPerL(value, unit, density), ConcentrationDimension.MASS_VOLUME)
        "percent_vv" -> NormalisedConcentration(value / 100.0, ConcentrationDimension.VOLUME_FRACTION)
        "x" -> NormalisedConcentration(value, ConcentrationDimension.RELATIVE)
        "cells/mL" -> NormalisedConcentration(value, ConcentrationDimension.CELLS)
        "cells/uL" -> NormalisedConcentration(value * 1000.0, ConcentrationDimension.CELLS)
        "OD" -> NormalisedConcentration(value, ConcentrationDimension.OD)
        else -> error("Unsupported concentration unit '$unit'.")
    }.let { normal ->
        if (normal.dimension == ConcentrationDimension.MOLAR || normal.dimension == ConcentrationDimension.MASS_VOLUME || normal.dimension == ConcentrationDimension.MASS_FRACTION) normal else normal
    }

    private fun concentrationFromNormal(normal: NormalisedConcentration, targetUnit: String, mw: Double?, density: Double?): Double {
        if (targetUnit in molarUnits()) {
            val molar = when (normal.dimension) {
                ConcentrationDimension.MOLAR -> normal.value
                ConcentrationDimension.MASS_VOLUME -> {
                    require(mw != null && mw > 0.0) { "Molecular weight in g/mol is required for mass↔molar conversion." }
                    normal.value / mw
                }
                ConcentrationDimension.MASS_FRACTION -> {
                    require(density != null && density > 0.0) { "Density in g/mL is required for % w/w conversion." }
                    require(mw != null && mw > 0.0) { "Molecular weight in g/mol is required for mass↔molar conversion." }
                    (normal.value * density * 1000.0) / mw
                }
                else -> error("Target molarity is incompatible with the source concentration dimension.")
            }
            return molar / when (targetUnit) { "M" -> 1.0; "mM" -> 1e-3; "uM" -> 1e-6; "nM" -> 1e-9; "pM" -> 1e-12; else -> 1.0 }
        }
        if (targetUnit in massConcentrationUnits()) {
            val gPerL = when (normal.dimension) {
                ConcentrationDimension.MASS_VOLUME -> normal.value
                ConcentrationDimension.MOLAR -> {
                    require(mw != null && mw > 0.0) { "Molecular weight in g/mol is required for mass↔molar conversion." }
                    normal.value * mw
                }
                ConcentrationDimension.MASS_FRACTION -> {
                    require(density != null && density > 0.0) { "Density in g/mL is required for % w/w conversion." }
                    normal.value * density * 1000.0
                }
                else -> error("Target mass concentration is incompatible with the source concentration dimension.")
            }
            return when (targetUnit) {
                "g/L", "mg/mL" -> gPerL
                "ug/mL", "ng/uL" -> gPerL * 1000.0
                "ng/mL" -> gPerL * 1e6
                "percent_wv" -> gPerL / 10.0
                "percent_ww" -> {
                    require(density != null && density > 0.0) { "Density in g/mL is required for % w/w conversion." }
                    gPerL / (density * 1000.0) * 100.0
                }
                else -> error("Unsupported target unit '$targetUnit'.")
            }
        }
        return when (targetUnit) {
            "percent_vv" -> {
                require(normal.dimension == ConcentrationDimension.VOLUME_FRACTION) { "% v/v can only convert within volume-fraction units." }
                normal.value * 100.0
            }
            "x" -> {
                require(normal.dimension == ConcentrationDimension.RELATIVE) { "× concentration can only convert within relative concentration units." }
                normal.value
            }
            else -> error("Unsupported target concentration unit '$targetUnit'.")
        }
    }

    private fun bestVolume(litres: Double): DisplayValue {
        val a = abs(litres)
        return when {
            a >= 1.0 -> DisplayValue(litres, "L")
            a >= 1e-3 -> DisplayValue(litres / 1e-3, "mL")
            a >= 1e-6 -> DisplayValue(litres / 1e-6, "uL")
            else -> DisplayValue(litres / 1e-9, "nL")
        }
    }

    private fun bestMass(grams: Double): DisplayValue {
        val a = abs(grams)
        return when {
            a >= 1.0 -> DisplayValue(grams, "g")
            a >= 1e-3 -> DisplayValue(grams / 1e-3, "mg")
            a >= 1e-6 -> DisplayValue(grams / 1e-6, "ug")
            a >= 1e-9 -> DisplayValue(grams / 1e-9, "ng")
            else -> DisplayValue(grams / 1e-12, "pg")
        }
    }

    private fun bestMolar(molar: Double): DisplayValue {
        val a = abs(molar)
        return when {
            a >= 1.0 -> DisplayValue(molar, "M")
            a >= 1e-3 -> DisplayValue(molar / 1e-3, "mM")
            a >= 1e-6 -> DisplayValue(molar / 1e-6, "uM")
            a >= 1e-9 -> DisplayValue(molar / 1e-9, "nM")
            else -> DisplayValue(molar / 1e-12, "pM")
        }
    }

    private fun bestMassConcentration(gPerL: Double): DisplayValue {
        val a = abs(gPerL)
        return when {
            a >= 1.0 -> DisplayValue(gPerL, "mg/mL")
            a >= 1e-3 -> DisplayValue(gPerL * 1000.0, "ug/mL")
            else -> DisplayValue(gPerL * 1e6, "ng/mL")
        }
    }

    private fun lengthToCm(value: Double, unit: String): Double = when (unit) {
        "cm" -> value
        "mm" -> value / 10.0
        "m" -> value * 100.0
        else -> error("Unsupported radius unit '$unit'.")
    }

    private fun molarUnits() = setOf("M", "mM", "uM", "nM", "pM")
    private fun massConcentrationUnits() = setOf("g/L", "mg/mL", "ug/mL", "ng/uL", "ng/mL", "percent_wv", "percent_ww")

    fun displayUnit(unit: String): String = unit
        .replace("uL", "µL")
        .replace("uM", "µM")
        .replace("umol", "µmol")
        .replace("ug", "µg")
        .replace("cells/uL", "cells/µL")
        .replace("ng/uL", "ng/µL")
        .replace("percent_wv", "% w/v")
        .replace("percent_ww", "% w/w")
        .replace("percent_vv", "% v/v")

    fun format(value: Double): String {
        if (!value.isFinite()) return value.toString()
        if (value == 0.0) return "0"
        val a = abs(value)
        return if (a >= 1e6 || a < 1e-4) {
            String.format(Locale.US, "%.4g", value)
        } else {
            String.format(Locale.US, "%.6f", value).trimEnd('0').trimEnd('.')
        }
    }
}
