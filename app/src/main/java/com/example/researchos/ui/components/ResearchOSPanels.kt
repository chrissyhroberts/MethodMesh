package com.example.researchos.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.researchos.core.GraphOutput
import com.example.researchos.core.Method
import com.example.researchos.core.MethodField
import com.example.researchos.core.MethodFieldType
import com.example.researchos.core.MethodOutputValidator
import com.example.researchos.core.RequiredWhen
import com.example.researchos.core.ResearchRuntime
import com.example.researchos.core.researchos.ExecutionResult
import com.example.researchos.core.researchos.InvocationContext
import com.example.researchos.core.researchos.KnowledgeObjectType
import com.example.researchos.core.researchos.TransformationStatus
import com.example.researchos.core.researchos.runtime.As100MethodRegistry
import com.example.researchos.settings.SettingsState

/**
 * ResearchOS operational debug view for one capability.
 *
 * 0. Caller context: ODK/form context that supplies the stable subject entity.
 * 1. Capability: the declared graph contract.
 * 2. Execution: the current invocation result / return payload.
 * 3. Graph: the objects that now exist in the in-memory knowledge graph for
 *    this method and subject.
 */
@Composable
fun ResearchOSPanels(
    method: Method,
    settingsState: SettingsState,
    modifier: Modifier = Modifier
) {
    var refreshKey by remember { mutableIntStateOf(0) }
    var executionMessage by remember { mutableStateOf<String?>(null) }
    var caller by remember { mutableStateOf(ResearchRuntime.session.invocationContext.caller) }
    var entityType by remember { mutableStateOf(ResearchRuntime.session.invocationContext.entityType) }
    var entityId by remember { mutableStateOf(ResearchRuntime.session.invocationContext.entityId) }
    var visitId by remember { mutableStateOf(ResearchRuntime.session.invocationContext.visitId) }
    var formId by remember { mutableStateOf(ResearchRuntime.session.invocationContext.formId) }
    var operatorId by remember { mutableStateOf(ResearchRuntime.session.invocationContext.operatorId) }

    val invocationContext = InvocationContext(
        caller = caller.ifBlank { "odk" },
        entityType = entityType.ifBlank { "participant" },
        entityId = entityId.ifBlank { "P001" },
        visitId = visitId,
        formId = formId,
        operatorId = operatorId
    )
    ResearchRuntime.session.setInvocationContext(invocationContext)

    val output = method.buildOutput(settingsState)
    val validation = MethodOutputValidator.validate(
        schema = method.outputSchema,
        output = output
    )

    Column(modifier = modifier.fillMaxWidth()) {
        InvocationContextPanel(
            context = invocationContext,
            caller = caller,
            onCallerChanged = { caller = it },
            entityType = entityType,
            onEntityTypeChanged = { entityType = it },
            entityId = entityId,
            onEntityIdChanged = { entityId = it },
            visitId = visitId,
            onVisitIdChanged = { visitId = it },
            formId = formId,
            onFormIdChanged = { formId = it },
            operatorId = operatorId,
            onOperatorIdChanged = { operatorId = it }
        )

        CapabilityPanel(method = method)

        ExecutionPanel(
            method = method,
            settingsState = settingsState,
            validationText = if (validation.valid) "Return payload matches declared transport fields." else validation.messages.joinToString("; "),
            executionMessage = executionMessage,
            invocationContext = invocationContext,
            onRecordExecution = {
                val result = runCatching {
                    val asMethod = As100MethodRegistry.require(method.manifest.id)
                    val request = asMethod.request(
                        action = method.manifest.id,
                        context = invocationContext.asMap() + mapOf("requested_capability" to method.manifest.id)
                    )
                    ResearchRuntime.session.record(
                        asMethod.execute(
                            request = request,
                            settingsState = settingsState,
                            transport = null
                        )
                    )
                }

                executionMessage = result.fold(
                    onSuccess = { recorded ->
                        refreshKey += 1
                        "Recorded ${recorded.summary()} to ${invocationContext.canonicalEntityId}."
                    },
                    onFailure = { error ->
                        "Execution failed: ${error.message ?: error::class.simpleName.orEmpty()}"
                    }
                )
            }
        )

        GraphPanel(
            methodId = method.manifest.id,
            refreshKey = refreshKey,
            invocationContext = invocationContext
        )
    }
}

@Composable
private fun InvocationContextPanel(
    context: InvocationContext,
    caller: String,
    onCallerChanged: (String) -> Unit,
    entityType: String,
    onEntityTypeChanged: (String) -> Unit,
    entityId: String,
    onEntityIdChanged: (String) -> Unit,
    visitId: String,
    onVisitIdChanged: (String) -> Unit,
    formId: String,
    onFormIdChanged: (String) -> Unit,
    operatorId: String,
    onOperatorIdChanged: (String) -> Unit
) {
    ResearchOSPanelCard(
        number = "0",
        title = "Caller context",
        subtitle = "ODK supplies the current entity and visit context for this invocation."
    ) {
        Text(
            text = "Current subject: ${context.canonicalEntityId}",
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = "Recorded graph objects are attached to this subject unless the incoming request supplies a more specific context.",
            modifier = Modifier.padding(top = 4.dp)
        )
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            OutlinedTextField(
                value = caller,
                onValueChange = onCallerChanged,
                label = { Text("Caller") },
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value = entityType,
                onValueChange = onEntityTypeChanged,
                label = { Text("Entity type") },
                modifier = Modifier.weight(1f)
            )
        }
        OutlinedTextField(
            value = entityId,
            onValueChange = onEntityIdChanged,
            label = { Text("Entity ID") },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
        Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            OutlinedTextField(
                value = visitId,
                onValueChange = onVisitIdChanged,
                label = { Text("Visit ID") },
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value = formId,
                onValueChange = onFormIdChanged,
                label = { Text("Form ID") },
                modifier = Modifier.weight(1f)
            )
        }
        OutlinedTextField(
            value = operatorId,
            onValueChange = onOperatorIdChanged,
            label = { Text("Operator ID") },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
    }
}

@Composable
private fun CapabilityPanel(method: Method) {
    ResearchOSPanelCard(
        number = "1",
        title = "Capability",
        subtitle = "This method declares it will produce these graph objects."
    ) {
        if (method.outputSchema.graphOutputs.isEmpty()) {
            Text(
                text = "No ResearchOS graph outputs declared.",
                modifier = Modifier.padding(top = 4.dp)
            )
        } else {
            method.outputSchema.graphOutputs.forEach { output ->
                GraphOutputDeclaration(output = output)
            }
        }

        if (method.outputSchema.fields.isNotEmpty()) {
            Text(
                text = "Flat fields returned to caller",
                modifier = Modifier.padding(top = 12.dp),
                fontWeight = FontWeight.SemiBold
            )
            method.outputSchema.fields.forEach { field ->
                TransportDeclaration(field = field)
            }
        }
    }
}

@Composable
private fun ExecutionPanel(
    method: Method,
    settingsState: SettingsState,
    validationText: String,
    executionMessage: String?,
    invocationContext: InvocationContext,
    onRecordExecution: () -> Unit
) {
    val output = method.buildOutput(settingsState)
    val matchingExecutions = ResearchRuntime.session.executionResults
        .filter { it.request.method.id.value == method.manifest.id }
        .filter { it.request.context["context_entity_id"] == invocationContext.canonicalEntityId || it.request.context["subject_id"] == invocationContext.canonicalEntityId }
        .sortedByDescending { it.request.temporalContext.systemTimeEpochMs }

    ResearchOSPanelCard(
        number = "2",
        title = "Execution",
        subtitle = "This invocation returned these values."
    ) {
        Button(onClick = onRecordExecution) {
            Text("Record current invocation to graph")
        }

        executionMessage?.let {
            Text(
                text = it,
                modifier = Modifier.padding(top = 8.dp),
                fontWeight = FontWeight.SemiBold
            )
        }

        Text(
            text = "Current return payload",
            modifier = Modifier.padding(top = 12.dp),
            fontWeight = FontWeight.SemiBold
        )
        if (output.fields.isEmpty()) {
            Text("No flat return values are available until this capability captures data.")
        } else {
            output.fields.forEach { (key, value) ->
                Text(
                    text = "• ${key.readableIdentifier()} = $value",
                    modifier = Modifier.padding(top = 2.dp),
                    fontFamily = FontFamily.Monospace
                )
            }
        }
        Text(
            text = validationText,
            modifier = Modifier.padding(top = 8.dp)
        )

        if (matchingExecutions.isNotEmpty()) {
            val latest = matchingExecutions.first()
            Text(
                text = "Latest recorded execution for ${invocationContext.canonicalEntityId}",
                modifier = Modifier.padding(top = 12.dp),
                fontWeight = FontWeight.SemiBold
            )
            Text("Status: ${latest.status.displayName()}")
            Text("Request: ${latest.request.id.value.shortId()}")
            Text(latest.summary())
            latest.diagnostics.takeIf { it.isNotEmpty() }?.forEach { (key, value) ->
                Text("• ${key.readableIdentifier()}: $value")
            }
        } else {
            Text(
                text = "No execution for this capability and subject has been recorded yet.",
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}

@Composable
private fun GraphPanel(
    methodId: String,
    refreshKey: Int,
    invocationContext: InvocationContext
) {
    val graph = ResearchRuntime.session.graph()
    val subjectId = invocationContext.canonicalEntityId
    val relatedExecutions = ResearchRuntime.session.executionResults
        .filter { it.request.method.id.value == methodId }
        .filter { it.request.context["context_entity_id"] == subjectId || it.request.context["subject_id"] == subjectId }
        .sortedByDescending { it.request.temporalContext.systemTimeEpochMs }

    val executionIds = relatedExecutions.map { it.request.id.value }.toSet()
    val contextRelationships = graph.asRelationships.values.filter { relationship ->
        relationship.from.id.value == subjectId && relationship.attributes["execution_id"] in executionIds
    }
    val observationIds = relatedExecutions.flatMap { it.observations.map { observation -> observation.id.value } }
        .plus(contextRelationships.filter { it.to.type == "Observation" }.map { it.to.id.value })
        .toSet()
    val stateIds = relatedExecutions.flatMap { it.states.map { state -> state.id.value } }
        .plus(contextRelationships.filter { it.to.type == "State" }.map { it.to.id.value })
        .toSet()
    val entityIds = relatedExecutions.flatMap { it.entities.map { entity -> entity.id.value } }
        .plus(subjectId)
        .plus(contextRelationships.filter { it.to.type == "Entity" }.map { it.to.id.value })
        .toSet()
    val relationshipIds = relatedExecutions.flatMap { it.relationships.map { relationship -> relationship.id.value } }
        .plus(contextRelationships.map { it.id.value })
        .toSet()
    val transformationIds = relatedExecutions.flatMap { it.transformations.map { transformation -> transformation.id.value } }
        .plus(contextRelationships.filter { it.to.type == "Transformation" }.map { it.to.id.value })
        .toSet()

    ResearchOSPanelCard(
        number = "3",
        title = "Graph",
        subtitle = "These objects now exist in the knowledge graph."
    ) {
        Text(
            text = "Graph snapshot #$refreshKey • subject $subjectId",
            style = MaterialTheme.typography.labelSmall
        )
        Text(
            text = "All graph objects: ${graph.asEntities.size} entities • ${graph.asObservations.size} observations • ${graph.asStates.size} states • ${graph.transformations.size} transformations • ${graph.asRelationships.size} relationships",
            modifier = Modifier.padding(top = 4.dp)
        )

        if (relatedExecutions.isEmpty()) {
            Text(
                text = "No graph objects have been recorded for this capability and subject yet. Use the execution panel to record the current invocation, or run a live capture with this context selected.",
                modifier = Modifier.padding(top = 8.dp)
            )
            return@ResearchOSPanelCard
        }

        GraphSection(title = "Entities") {
            graph.asEntities.values.filter { it.id.value in entityIds }.forEach { entity ->
                Text("• ${entity.entityType.readableIdentifier()} ${entity.id.value.shortId()}")
                entity.attributes.forEach { (key, value) -> Text("  - ${key.readableIdentifier()}: $value") }
            }
        }

        GraphSection(title = "Observations") {
            graph.asObservations.values.filter { it.id.value in observationIds }.forEach { observation ->
                Text("• ${observation.phenomenon.readableIdentifier()} ${observation.id.value.shortId()}")
                observation.subject?.let { Text("  subject: ${it.label ?: it.id.value}") }
                observation.values.forEach { (key, value) -> Text("  ${key.readableIdentifier()}: $value") }
            }
        }

        GraphSection(title = "States") {
            graph.asStates.values.filter { it.id.value in stateIds }.forEach { state ->
                Text("• ${state.stateType.readableIdentifier()} ${state.id.value.shortId()}")
                Text("  subject: ${state.subject.label ?: state.subject.id.value}")
                state.values.forEach { (key, value) -> Text("  ${key.readableIdentifier()}: $value") }
            }
        }

        GraphSection(title = "Transformations") {
            graph.transformations.values.filter { it.id.value in transformationIds }.forEach { transformation ->
                Text("• ${transformation.action.readableIdentifier()} — ${transformation.status.displayName()} ${transformation.id.value.shortId()}")
                Text("  outputs: ${transformation.outputs.joinToString { it.label ?: it.id.value.shortId() }}")
            }
        }

        GraphSection(title = "Context relationships") {
            graph.asRelationships.values.filter { it.id.value in relationshipIds }.forEach { relationship ->
                Text("• ${relationship.relationshipType.readableIdentifier()}: ${relationship.from.label ?: relationship.from.id.value.shortId()} → ${relationship.to.label ?: relationship.to.id.value.shortId()}")
                relationship.attributes.takeIf { it.isNotEmpty() }?.forEach { (key, value) ->
                    Text("  ${key.readableIdentifier()}: $value")
                }
            }
        }
    }
}

@Composable
private fun ResearchOSPanelCard(
    number: String,
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row {
                Text(
                    text = number,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            Column(modifier = Modifier.padding(top = 10.dp)) {
                content()
            }
        }
    }
}

@Composable
private fun GraphOutputDeclaration(output: GraphOutput) {
    Text(
        text = "• ${output.objectType.displayName()}: ${output.targetName().readableIdentifier()}",
        modifier = Modifier.padding(top = 8.dp),
        fontWeight = FontWeight.SemiBold
    )
    output.subjectRole?.let { Text("  subject: ${it.readableIdentifier()}") }
    output.description?.takeIf { it.isNotBlank() }?.let { Text("  $it") }
    output.fields.forEach { field ->
        val label = field.description?.takeIf { it.isNotBlank() } ?: field.id.readableIdentifier()
        Text("  - $label: ${field.type.displayName()}, ${field.requiredWhen.displayName()}")
    }
}

@Composable
private fun TransportDeclaration(field: MethodField) {
    val mapping = field.graphPath?.let { " ← ${it.readablePath()}" } ?: ""
    Text(
        text = "• ${field.label}: ${field.type.displayName()}, ${field.requiredWhen.displayName()}$mapping",
        modifier = Modifier.padding(top = 2.dp)
    )
}

@Composable
private fun GraphSection(
    title: String,
    content: @Composable () -> Unit
) {
    Text(
        text = title,
        modifier = Modifier.padding(top = 10.dp),
        fontWeight = FontWeight.SemiBold
    )
    content()
}

private fun ExecutionResult.summary(): String =
    "${entities.size} entities • ${observations.size} observations • ${states.size} states • ${transformations.size} transformations • ${relationships.size} relationships"

private fun KnowledgeObjectType.displayName(): String = when (this) {
    KnowledgeObjectType.Entity -> "Entity"
    KnowledgeObjectType.Attribute -> "Attribute"
    KnowledgeObjectType.Observation -> "Observation"
    KnowledgeObjectType.Relationship -> "Relationship"
    KnowledgeObjectType.Classification -> "Classification"
    KnowledgeObjectType.State -> "State"
}

private fun MethodFieldType.displayName(): String = when (this) {
    MethodFieldType.Text -> "text"
    MethodFieldType.Integer -> "integer"
    MethodFieldType.Float -> "number"
    MethodFieldType.Boolean -> "yes/no"
    MethodFieldType.Json -> "structured data"
}

private fun RequiredWhen.displayName(): String = when (this) {
    RequiredWhen.Always -> "required"
    RequiredWhen.OnSuccessfulCapture -> "required after capture"
    RequiredWhen.IfAvailable -> "optional when available"
    RequiredWhen.PreviewOnly -> "preview only"
    RequiredWhen.TransportOnly -> "transport only"
}

private fun TransformationStatus.displayName(): String = when (this) {
    TransformationStatus.Requested -> "requested"
    TransformationStatus.Succeeded -> "succeeded"
    TransformationStatus.Failed -> "failed"
    TransformationStatus.Cancelled -> "cancelled"
    TransformationStatus.Unsupported -> "unsupported"
}

private fun GraphOutput.targetName(): String =
    phenomenon ?: stateType ?: relationshipType ?: entityType ?: id

private fun String.shortId(): String =
    if (length <= 12) this else take(8) + "…"

private fun String.readableIdentifier(): String =
    replace('.', ' ')
        .replace('_', ' ')
        .replace('-', ' ')
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

private fun String.readablePath(): String =
    removePrefix("Observation.values.")
        .removePrefix("State.values.")
        .removePrefix("Entity.attributes.")
        .readableIdentifier()
