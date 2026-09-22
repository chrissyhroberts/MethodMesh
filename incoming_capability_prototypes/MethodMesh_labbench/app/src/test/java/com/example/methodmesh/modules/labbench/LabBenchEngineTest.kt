package com.example.methodmesh.modules.labbench

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LabBenchEngineTest {
    @Test
    fun `dilution handles mixed molarity units`() {
        val result = LabBenchEngine.dilution(mapOf(
            "stock_concentration" to "10", "stock_concentration_unit" to "mM",
            "target_concentration" to "250", "target_concentration_unit" to "uM",
            "final_volume" to "2", "final_volume_unit" to "mL"
        ))
        assertEquals("succeeded", result["lab_dilution_status"])
        assertEquals("50", result["lab_dilution_stock_volume"])
        assertEquals("uL", result["lab_dilution_stock_volume_unit"])
        assertEquals("1.95", result["lab_dilution_diluent_volume"])
        assertEquals("mL", result["lab_dilution_diluent_volume_unit"])
    }


    @Test
    fun `dilution crosses mass and molar units only with molecular weight`() {
        val missing = LabBenchEngine.dilution(mapOf(
            "stock_concentration" to "10", "stock_concentration_unit" to "mg/mL",
            "target_concentration" to "50", "target_concentration_unit" to "mM",
            "final_volume" to "1", "final_volume_unit" to "mL"
        ))
        assertEquals("failed", missing["lab_dilution_status"])

        val result = LabBenchEngine.dilution(mapOf(
            "stock_concentration" to "10", "stock_concentration_unit" to "mg/mL",
            "target_concentration" to "50", "target_concentration_unit" to "mM",
            "molecular_weight_g_mol" to "100",
            "final_volume" to "1", "final_volume_unit" to "mL"
        ))
        assertEquals("succeeded", result["lab_dilution_status"])
        assertEquals("500", result["lab_dilution_stock_volume"])
        assertEquals("uL", result["lab_dilution_stock_volume_unit"])
    }

    @Test
    fun `volumetric dilution rejects percent ww`() {
        val result = LabBenchEngine.dilution(mapOf(
            "stock_concentration" to "10", "stock_concentration_unit" to "percent_ww",
            "target_concentration" to "5", "target_concentration_unit" to "percent_ww",
            "final_volume" to "10", "final_volume_unit" to "mL"
        ))
        assertEquals("failed", result["lab_dilution_status"])
        assertTrue(result["lab_dilution_error"].orEmpty().contains("% w/w"))
    }

    @Test
    fun `molar solution calculates sodium chloride mass`() {
        val result = LabBenchEngine.molarSolution(mapOf(
            "mode" to "mass_required", "molecular_weight_g_mol" to "58.44",
            "molarity" to "0.1", "molarity_unit" to "M", "volume" to "500", "volume_unit" to "mL"
        ))
        assertEquals("succeeded", result["lab_molar_solution_status"])
        assertEquals("2.922", result["lab_molar_solution_mass"])
        assertEquals("g", result["lab_molar_solution_mass_unit"])
    }

    @Test
    fun `reconstitution calculates final volume`() {
        val result = LabBenchEngine.reconstitute(mapOf(
            "material_amount" to "25", "material_unit" to "mg",
            "target_concentration" to "5", "target_concentration_unit" to "mg/mL"
        ))
        assertEquals("succeeded", result["lab_reconstitute_status"])
        assertEquals("5", result["lab_reconstitute_final_volume"])
        assertEquals("mL", result["lab_reconstitute_final_volume_unit"])
    }

    @Test
    fun `serial dilution emits requested levels`() {
        val result = LabBenchEngine.serialDilution(mapOf(
            "starting_concentration" to "100", "concentration_unit" to "uM",
            "dilution_factor" to "2", "number_of_levels" to "4",
            "prepared_volume" to "1000", "prepared_volume_unit" to "uL", "overage_percent" to "0"
        ))
        assertEquals("succeeded", result["lab_serial_dilution_status"])
        assertEquals(4, JSONArray(result["lab_serial_dilution_steps_json"]!!).length())
        assertEquals("12.5", result["lab_serial_dilution_end_concentration"])
    }

    @Test
    fun `master mix excludes template from shared mix`() {
        val result = LabBenchEngine.masterMix(mapOf(
            "components" to "Mix=10\nPrimer=1\nWater=8\nTemplate*=1",
            "component_volume_unit" to "uL", "number_of_reactions" to "10",
            "overage_mode" to "percent", "overage_value" to "10", "declared_final_volume" to "20"
        ))
        assertEquals("succeeded", result["lab_master_mix_status"])
        assertEquals("209", result["lab_master_mix_master_mix_total"])
        assertTrue(result["lab_master_mix_instruction"].orEmpty().contains("separately"))
    }

    @Test
    fun `centrifuge round trip is stable`() {
        val forward = LabBenchEngine.centrifuge(mapOf("mode" to "rcf_to_rpm", "rcf" to "12000", "radius" to "8.45", "radius_unit" to "cm"))
        val rpm = forward["lab_centrifuge_rpm"]!!.toDouble()
        val reverse = LabBenchEngine.centrifuge(mapOf("mode" to "rpm_to_rcf", "rpm" to rpm.toString(), "radius" to "8.45", "radius_unit" to "cm"))
        assertTrue(kotlin.math.abs(reverse["lab_centrifuge_rcf"]!!.toDouble() - 12000.0) < 5.0)
    }

    @Test
    fun `mass concentration to molarity requires molecular weight`() {
        val result = LabBenchEngine.concentration(mapOf("value" to "1", "from_unit" to "mg/mL", "to_unit" to "mM"))
        assertEquals("failed", result["lab_concentration_status"])
        assertTrue(result["lab_concentration_error"].orEmpty().contains("Molecular weight"))
    }

    @Test
    fun `nucleic acid copy number is positive`() {
        val result = LabBenchEngine.nucleicAcid(mapOf(
            "mode" to "mass_to_copies", "nucleic_acid_type" to "dsDNA", "length" to "5000",
            "mass" to "10", "mass_unit" to "ng"
        ))
        assertEquals("succeeded", result["lab_nucleic_acid_status"])
        assertTrue(result["lab_nucleic_acid_copy_number"]!!.toDouble() > 1e9)
    }

    @Test
    fun `cell dilution calculates sample and medium`() {
        val result = LabBenchEngine.cellDilution(mapOf(
            "starting_concentration" to "20000000", "target_concentration" to "2500000", "concentration_unit" to "cells/mL",
            "final_volume" to "10", "final_volume_unit" to "mL"
        ))
        assertEquals("1.25", result["lab_cell_dilution_sample_volume"])
        assertEquals("8.75", result["lab_cell_dilution_medium_volume"])
    }

    @Test
    fun `hemocytometer uses explicit counted volume`() {
        val result = LabBenchEngine.hemocytometer(mapOf(
            "total_cells" to "280", "number_of_regions" to "4", "dilution_factor" to "1", "region_volume_ul" to "0.1"
        ))
        assertEquals("700000", result["lab_hemocytometer_cells_per_ml"])
    }


    @Test
    fun `hemocytometer requires live and dead together`() {
        val result = LabBenchEngine.hemocytometer(mapOf(
            "total_cells" to "280", "number_of_regions" to "4", "dilution_factor" to "1", "region_volume_ul" to "0.1",
            "live_cells" to "250"
        ))
        assertEquals("failed", result["lab_hemocytometer_status"])
    }

    @Test
    fun `aliquot planner preserves reserve and dead volume`() {
        val result = LabBenchEngine.aliquot(mapOf(
            "mode" to "size_to_count", "available_volume" to "10", "volume_unit" to "mL", "aliquot_volume" to "0.2",
            "reserve_volume" to "0.5", "dead_volume" to "0.1", "overage_percent" to "0"
        ))
        assertEquals("47", result["lab_aliquot_count"])
    }


    @Test
    fun `aliquot overage reports target and dispense volumes separately`() {
        val result = LabBenchEngine.aliquot(mapOf(
            "mode" to "size_to_count", "available_volume" to "10", "volume_unit" to "mL", "aliquot_volume" to "0.2",
            "reserve_volume" to "0", "dead_volume" to "0", "overage_percent" to "10"
        ))
        assertEquals("45", result["lab_aliquot_count"])
        assertEquals("200", result["lab_aliquot_aliquot_volume"])
        assertEquals("220", result["lab_aliquot_dispense_volume"])
        assertTrue(result["lab_aliquot_instruction"].orEmpty().contains("overage"))
    }

    @Test
    fun `percent wv uses make up to semantics`() {
        val result = LabBenchEngine.percentSolution(mapOf("mode" to "w_v", "percent" to "4", "final_amount" to "250", "final_amount_unit" to "mL"))
        assertEquals("10", result["lab_percent_solution_component_amount"])
        assertEquals("g", result["lab_percent_solution_component_unit"])
        assertTrue(result["lab_percent_solution_instruction"].orEmpty().contains("make up to"))
    }
}
