package com.example.methodmesh.modules.labbench

import com.example.methodmesh.modules.MethodMeshModule
import com.example.methodmesh.modules.ModuleExample
import com.example.methodmesh.modules.RilBinding
import com.example.methodmesh.settings.MethodSetting
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec

object LabBenchModule : MethodMeshModule {
    override val moduleId = "labbench"
    override val displayName = "Lab Bench"
    override val summary = "Offline laboratory calculators for dilutions, solution preparation, master mixes, centrifugation, cell work and common bench planning."
    override val iconKey = "laboratory"

    override fun as100Methods() = LabBenchMethods.all

    override fun capabilityScreens(): List<CapabilityScreenSpec> =
        LabBenchDefinitions.all.map { LabBenchAtomicCapabilityScreen(it) as CapabilityScreenSpec } +
            listOf(LabBenchDashboardCapabilityScreen)

    override fun capabilitySettings(): Map<String, List<MethodSetting>> = buildMap {
        LabBenchDefinitions.all.forEach { definition ->
            put(definition.method.id, definition.inputs.map { it.asMethodSetting() })
        }
        put(
            LabBenchMethods.Dashboard.id,
            listOf(MethodSetting.ChoiceSetting("calculator", "Calculator", defaultValue = "dilution", choices = LabBenchDefinitions.all.map { it.key })) +
                LabBenchDefinitions.all.flatMap { it.inputs }.distinctBy { it.id }.map { it.asMethodSetting() }
        )
    }

    override fun rilBindings() = listOf(
        RilBinding("calculate dilution", LabBenchMethods.Dilution.id, "Calculate stock and diluent volumes"),
        RilBinding("prepare molar solution", LabBenchMethods.MolarSolution.id, "Calculate molar solution preparation"),
        RilBinding("reconstitute reagent", LabBenchMethods.Reconstitute.id, "Calculate reagent reconstitution volume"),
        RilBinding("plan serial dilution", LabBenchMethods.SerialDilution.id, "Plan a serial dilution series"),
        RilBinding("calculate master mix", LabBenchMethods.MasterMix.id, "Scale a reaction master mix"),
        RilBinding("convert centrifuge rcf rpm", LabBenchMethods.Centrifuge.id, "Convert RCF and RPM"),
        RilBinding("convert concentration", LabBenchMethods.Concentration.id, "Convert laboratory concentration units"),
        RilBinding("calculate nucleic acid", LabBenchMethods.NucleicAcid.id, "Calculate DNA/RNA molarity or copy number"),
        RilBinding("calculate cell dilution", LabBenchMethods.CellDilution.id, "Calculate cell or OD dilution"),
        RilBinding("calculate hemocytometer count", LabBenchMethods.Hemocytometer.id, "Calculate cells per mL and viability"),
        RilBinding("plan aliquots", LabBenchMethods.Aliquot.id, "Plan aliquot count or size"),
        RilBinding("prepare percent solution", LabBenchMethods.PercentSolution.id, "Prepare percent solutions"),
        RilBinding("open lab bench", LabBenchMethods.Dashboard.id, "Open the persistent laboratory calculator dashboard")
    )

    override fun examples() = listOf(
        ModuleExample(
            title = "Make a dilution",
            ril = "WHAT; calculate dilution; WITH; stock_concentration=10; stock_concentration_unit=mM; target_concentration=250; target_concentration_unit=uM; final_volume=2; final_volume_unit=mL; RESULT; return lab_dilution_instruction as instruction; format json"
        ),
        ModuleExample(
            title = "Convert centrifuge RCF to RPM",
            ril = "WHAT; convert centrifuge rcf rpm; WITH; mode=rcf_to_rpm; rcf=12000; radius=8.45; radius_unit=cm; RESULT; return lab_centrifuge_rpm as rpm; format json"
        ),
        ModuleExample(
            title = "Scale a PCR master mix",
            ril = "WHAT; calculate master mix; WITH; number_of_reactions=24; overage_mode=percent; overage_value=10; component_volume_unit=uL; RESULT; return lab_master_mix_instruction as instruction; format json",
            notes = "Supply components as Name=volume lines or as a JSON array; suffix a native line name with * to exclude it from the shared mix."
        )
    )
}
