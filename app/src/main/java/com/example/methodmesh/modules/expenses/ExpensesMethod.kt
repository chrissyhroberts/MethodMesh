package com.example.methodmesh.modules.expenses

import com.example.methodmesh.core.methodmesh.ArchitectureId
import com.example.methodmesh.core.methodmesh.ArchitectureRef
import com.example.methodmesh.core.methodmesh.ExecutionRequest
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.core.methodmesh.InvocationContext
import com.example.methodmesh.core.methodmesh.KnowledgeObjectType
import com.example.methodmesh.core.methodmesh.MethodContract
import com.example.methodmesh.core.methodmesh.MethodDescriptor
import com.example.methodmesh.core.methodmesh.MethodObjectType
import com.example.methodmesh.core.methodmesh.Observation
import com.example.methodmesh.core.methodmesh.ProvenanceContext
import com.example.methodmesh.core.methodmesh.Signal
import com.example.methodmesh.core.methodmesh.Transformation
import com.example.methodmesh.core.methodmesh.TransformationStatus
import com.example.methodmesh.core.methodmesh.runtime.As100ExecutionEngine
import com.example.methodmesh.core.methodmesh.runtime.As100Method
import com.example.methodmesh.core.methodmesh.withInvocationContext
import com.example.methodmesh.settings.SettingsState

private const val VERSION = "0.4.0"

object ExpensesManageFields {
    const val STATUS = "expenses_manage_status"
    const val LEDGER_ID = "expenses_manage_ledger_id"
    const val LEDGER_NAME = "expenses_manage_ledger_name"
    const val TOTAL_HOME = "expenses_manage_total_home"
    const val HOME_CURRENCY = "expenses_manage_home_currency"
    const val EXPENSE_COUNT = "expenses_manage_expense_count"
    const val ERROR = "expenses_manage_error"

    val outputs = listOf(
        STATUS,
        LEDGER_ID,
        LEDGER_NAME,
        TOTAL_HOME,
        HOME_CURRENCY,
        EXPENSE_COUNT,
        ERROR
    )
}

object As100ExpensesManageMethod : As100Method {
    const val ID = "expenses.manage"

    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "Manage expenses")

    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID),
        methodType = MethodObjectType.Calculation,
        name = "Expenses",
        version = VERSION,
        description = "Open and manage a persistent expense ledger.",
        outputs = ExpensesManageFields.outputs,
        graphOutputs = listOf(ID),
        parameters = mapOf(
            "category" to "Field operations",
            "status" to "Development"
        )
    )

    override val contract = MethodContract(
        method = ref,
        producedKnowledgeTypes = listOf(KnowledgeObjectType.Observation),
        producedFields = ExpensesManageFields.outputs,
        producedGraphOutputs = descriptor.graphOutputs
    )

    override fun request(
        action: String,
        context: Map<String, String>,
        signals: List<Signal>,
        inputs: List<ArchitectureRef>
    ): ExecutionRequest = As100ExecutionEngine.request(
        action = action,
        method = ref,
        context = context,
        signals = signals,
        inputs = inputs
    )

    override fun execute(
        request: ExecutionRequest,
        settingsState: SettingsState?,
        transport: String?
    ): ExecutionResult {
        val values = mapOf(
            ExpensesManageFields.STATUS to "failed",
            ExpensesManageFields.LEDGER_ID to "",
            ExpensesManageFields.LEDGER_NAME to "",
            ExpensesManageFields.TOTAL_HOME to "",
            ExpensesManageFields.HOME_CURRENCY to "",
            ExpensesManageFields.EXPENSE_COUNT to "",
            ExpensesManageFields.ERROR to "Expenses is an interactive persistent-ledger capability."
        )
        return result(request, values, InvocationContext.from(request.context))
    }

    fun result(
        request: ExecutionRequest,
        values: Map<String, String>,
        invocation: InvocationContext?
    ): ExecutionResult {
        val ok = values[ExpensesManageFields.STATUS] == "succeeded"
        val provenance = ProvenanceContext("methodmesh.expenses", id, VERSION)
        val observation = Observation(
            phenomenon = id,
            subject = null,
            values = values,
            temporalContext = request.temporalContext,
            provenance = provenance
        )
        val transformation = Transformation(
            action = id,
            method = ref,
            outputs = listOf(
                ArchitectureRef(
                    observation.id,
                    observation.objectType,
                    observation.phenomenon
                )
            ),
            status = if (ok) TransformationStatus.Succeeded else TransformationStatus.Failed,
            temporalContext = request.temporalContext,
            provenance = provenance
        )
        return As100ExecutionEngine.complete(
            request = request,
            status = if (ok) TransformationStatus.Succeeded else TransformationStatus.Failed,
            observations = listOf(observation),
            transformations = listOf(transformation),
            diagnostics = if (ok) emptyMap() else mapOf(
                ExpensesManageFields.ERROR to values[ExpensesManageFields.ERROR].orEmpty()
            )
        ).withInvocationContext(invocation)
    }
}
