package com.example.methodmesh.modules.expenses

import com.example.methodmesh.modules.MethodMeshModule
import com.example.methodmesh.modules.RilBinding

object ExpensesModule : MethodMeshModule {
    override val moduleId = "expenses"
    override val displayName = "Expenses"
    override val summary = "Manage persistent trip and business expense ledgers with receipt evidence, transparent FX conversion, and export/share, editable ledger settings, and document scanning."

    override fun as100Methods() = listOf(
        As100ExpensesManageMethod
    )

    override fun rilBindings() = listOf(
        RilBinding("manage expenses", As100ExpensesManageMethod.ID),
        RilBinding("open expenses", As100ExpensesManageMethod.ID),
        RilBinding("expense ledger", As100ExpensesManageMethod.ID)
    )

    override fun capabilityScreens() = listOf(
        ExpensesManagerCapabilityScreen
    )

    override fun capabilitySettings() = emptyMap<String, List<com.example.methodmesh.settings.MethodSetting>>()
}
