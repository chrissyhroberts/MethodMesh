package com.example.methodmesh.modules.surveying

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.core.methodmesh.withInvocationContext
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.workflow.ui.CapabilityPresentationMode
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenScaffold
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object TraverseBookCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100TraverseBookMethod.id
    override val title = "Traverse field book"
    override val description = "Persistent traverse book with live coordinates, closure QC and optional adjustment."

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        val appContext = LocalContext.current
        val requestedJobId = context.action.settings["survey_job_id"]
            ?: context.action.settings["input_survey_job_id"]
            ?: context.request.settings["survey_job_id"]
            ?: context.request.settings["input_survey_job_id"]
            ?: ""
        var selectedJobId by rememberSaveable { mutableStateOf(requestedJobId) }
        var job by remember { mutableStateOf<SurveyRepository.TraverseJob?>(null) }
        var latestExecution by remember { mutableStateOf<ExecutionResult?>(null) }
        var status by rememberSaveable { mutableStateOf("Loading traverse field book…") }
        var jobName by rememberSaveable { mutableStateOf(context.setting("survey_job_name", "Traverse")) }
        var startId by rememberSaveable { mutableStateOf(context.setting("start_point_id", "START")) }
        var startE by rememberSaveable { mutableStateOf(context.setting("start_easting", "0")) }
        var startN by rememberSaveable { mutableStateOf(context.setting("start_northing", "0")) }
        var closeE by rememberSaveable { mutableStateOf(context.setting("close_easting", "")) }
        var closeN by rememberSaveable { mutableStateOf(context.setting("close_northing", "")) }
        var adjustment by rememberSaveable { mutableStateOf(context.setting("adjustment_mode", "none")) }
        var minPrecision by rememberSaveable { mutableStateOf(context.setting("minimum_relative_precision", "")) }
        var nextId by rememberSaveable { mutableStateOf("P1") }
        var bearing by rememberSaveable { mutableStateOf("") }
        var distance by rememberSaveable { mutableStateOf("") }

        val keepLiveDashboard =
            context.presentationMode == CapabilityPresentationMode.Dashboard ||
                context.isNativePresetRun ||
                context.request.source.equals("intent_test", ignoreCase = true)

        fun snapshot(current: SurveyRepository.TraverseJob, keepInvalid: Boolean = false) {
            val input = context.request.invocationContext.asMap(As100TraverseBookMethod.id) +
                context.request.settings + context.action.settings + SurveyRepository.traverseContext(current)
            val calculation = As100TraverseBookMethod.calculateSafely(input)
            val request = As100TraverseBookMethod.request(As100TraverseBookMethod.id, input, emptyList(), emptyList())
            val execution = As100TraverseBookMethod.result(request, calculation)
                .withInvocationContext(context.request.invocationContext)
            latestExecution = if (calculation.valid || keepInvalid) execution else null
            status = if (calculation.valid) calculation.mainResult else calculation.error.ifBlank { calculation.mainResult }
        }

        fun loadOrCreate() {
            val existing = selectedJobId.takeIf { it.isNotBlank() }?.let { SurveyRepository.traverseJob(appContext, it) }
                ?: if (requestedJobId.isBlank()) SurveyRepository.traverseJobs(appContext).firstOrNull() else null
            if (existing != null) {
                job = existing
                selectedJobId = existing.id
                jobName = existing.name
                startId = existing.startPointId
                startE = existing.startEasting.toString()
                startN = existing.startNorthing.toString()
                closeE = existing.closeEasting?.toString().orEmpty()
                closeN = existing.closeNorthing?.toString().orEmpty()
                adjustment = existing.adjustmentMode
                minPrecision = existing.minimumRelativePrecision?.toString().orEmpty()
                nextId = "P${existing.legs.size + 1}"
                snapshot(existing, keepInvalid = !keepLiveDashboard)
            } else if (keepLiveDashboard) {
                val created = SurveyRepository.newTraverseJob(
                    name = jobName,
                    startPointId = startId,
                    startEasting = startE.toDoubleOrNull() ?: 0.0,
                    startNorthing = startN.toDoubleOrNull() ?: 0.0,
                    closeEasting = closeE.toDoubleOrNull(),
                    closeNorthing = closeN.toDoubleOrNull(),
                    adjustmentMode = adjustment,
                    minimumRelativePrecision = minPrecision.toDoubleOrNull()
                )
                val saved = SurveyRepository.saveTraverseJob(appContext, created)
                job = saved
                selectedJobId = saved.id
                status = "New traverse book created. Add the first leg."
            } else {
                // External callers should get a structured failure rather than a native dialog.
                val input = context.request.invocationContext.asMap(As100TraverseBookMethod.id) +
                    context.request.settings + context.action.settings +
                    mapOf("traverse_legs" to "", "survey_job_id" to requestedJobId)
                val calc = As100TraverseBookMethod.calculateSafely(input)
                val req = As100TraverseBookMethod.request(As100TraverseBookMethod.id, input, emptyList(), emptyList())
                latestExecution = As100TraverseBookMethod.result(req, calc).withInvocationContext(context.request.invocationContext)
                status = calc.error.ifBlank { "Traverse job not found." }
            }
        }

        LaunchedEffect(Unit) { loadOrCreate() }

        fun saveSettings() {
            val current = job ?: return
            val se = startE.toDoubleOrNull()
            val sn = startN.toDoubleOrNull()
            if (se == null || sn == null) {
                status = "Start easting and northing must be numeric."
                return
            }
            val updated = current.copy(
                name = jobName.ifBlank { "Traverse" },
                startPointId = startId.ifBlank { "START" },
                startEasting = se,
                startNorthing = sn,
                closeEasting = closeE.toDoubleOrNull(),
                closeNorthing = closeN.toDoubleOrNull(),
                adjustmentMode = adjustment,
                minimumRelativePrecision = minPrecision.toDoubleOrNull()
            )
            val saved = SurveyRepository.saveTraverseJob(appContext, updated)
            job = saved
            context.onSettingsChanged(SurveyRepository.traverseContext(saved))
            snapshot(saved)
        }

        fun addLeg() {
            val current = job ?: return
            val az = bearing.toDoubleOrNull()
            val dist = distance.toDoubleOrNull()
            if (az == null || dist == null || dist < 0.0) {
                status = "Enter a numeric bearing and non-negative distance."
                return
            }
            val saved = SurveyRepository.saveTraverseJob(
                appContext,
                current.copy(legs = current.legs + SurveyCalculations.TraverseLeg(nextId.ifBlank { "P${current.legs.size + 1}" }, az, dist))
            )
            job = saved
            nextId = "P${saved.legs.size + 1}"
            bearing = ""
            distance = ""
            snapshot(saved)
        }

        fun undoLeg() {
            val current = job ?: return
            if (current.legs.isEmpty()) return
            val saved = SurveyRepository.saveTraverseJob(appContext, current.copy(legs = current.legs.dropLast(1)))
            job = saved
            nextId = "P${saved.legs.size + 1}"
            if (saved.legs.isEmpty()) {
                latestExecution = null
                status = "Traverse is empty."
            } else snapshot(saved)
        }

        fun newJob() {
            val created = SurveyRepository.newTraverseJob("Traverse", "START", 0.0, 0.0)
            val saved = SurveyRepository.saveTraverseJob(appContext, created)
            job = saved
            selectedJobId = saved.id
            jobName = saved.name
            startId = saved.startPointId
            startE = "0"
            startN = "0"
            closeE = ""
            closeN = ""
            adjustment = "none"
            minPrecision = ""
            nextId = "P1"
            latestExecution = null
            status = "New traverse book created."
        }

        val scaffoldResult = if (keepLiveDashboard) null else latestExecution
        CapabilityScreenScaffold(
            title = title,
            capabilityId = capabilityId,
            context = context,
            canGoBack = context.stepNumber > 1,
            capturedResult = scaffoldResult,
            resultPreview = scaffoldResult?.let { OutputFormatter.fields(it, includeProvenance = false) }.orEmpty(),
            onBack = onBack,
            onRetry = { loadOrCreate() },
            onConfirm = { latestExecution?.let(onConfirmed) },
            onCancel = onCancel
        ) {
            Column(Modifier.fillMaxWidth()) {
                val current = job
                if (current == null) {
                    Text(status)
                } else {
                    TraverseDashboardSummary(current)
                    Spacer(Modifier.height(8.dp))
                    TraversePlan(current)
                    Spacer(Modifier.height(10.dp))

                    Text("Job", style = MaterialTheme.typography.titleMedium)
                    if (context.settingShouldBeShown("survey_job_name"))
                        OutlinedTextField(jobName, { jobName = it }, label = { Text("Job name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (context.settingShouldBeShown("start_easting")) OutlinedTextField(startE, { startE = it }, label = { Text("Start E") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                        if (context.settingShouldBeShown("start_northing")) OutlinedTextField(startN, { startN = it }, label = { Text("Start N") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (context.settingShouldBeShown("close_easting")) OutlinedTextField(closeE, { closeE = it }, label = { Text("Close E") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                        if (context.settingShouldBeShown("close_northing")) OutlinedTextField(closeN, { closeN = it }, label = { Text("Close N") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                    }
                    if (context.settingShouldBeShown("adjustment_mode")) {
                        Text("Adjustment", style = MaterialTheme.typography.labelLarge)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("none", "bowditch", "transit").forEach { mode ->
                                if (adjustment == mode) Button({ adjustment = mode }, Modifier.weight(1f)) { Text("✓ $mode") }
                                else OutlinedButton({ adjustment = mode }, Modifier.weight(1f)) { Text(mode) }
                            }
                        }
                    }
                    if (context.settingShouldBeShown("minimum_relative_precision"))
                        OutlinedTextField(minPrecision, { minPrecision = it }, label = { Text("Minimum relative precision 1:n") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                    OutlinedButton(::saveSettings, Modifier.fillMaxWidth()) { Text("Save job settings") }

                    Spacer(Modifier.height(10.dp))
                    Text("Add traverse leg", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(nextId, { nextId = it }, label = { Text("To point ID") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(bearing, { bearing = it }, label = { Text("Azimuth °") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                        OutlinedTextField(distance, { distance = it }, label = { Text("Distance m") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                    }
                    Button(::addLeg, Modifier.fillMaxWidth()) { Text("Add leg") }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(::undoLeg, Modifier.weight(1f), enabled = current.legs.isNotEmpty()) { Text("Undo last") }
                        OutlinedButton(::newJob, Modifier.weight(1f)) { Text("New job") }
                    }
                    TraverseRecentRows(current)
                    Text(status, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
                    OutlinedButton({ snapshot(current) }, Modifier.fillMaxWidth()) { Text("Refresh snapshot") }
                    if (keepLiveDashboard && latestExecution != null) {
                        Button({ latestExecution?.let(onConfirmed) }, Modifier.fillMaxWidth()) {
                            Text(if (context.isNativePresetRun) "Finish" else "Use this snapshot")
                        }
                    }
                }
            }
        }
    }
}

object LevellingBookCapabilityScreen : CapabilityScreenSpec {
    override val capabilityId = As100LevellingBookMethod.id
    override val title = "Levelling field book"
    override val description = "Persistent BS/IS/FS levelling book with live RL, arithmetic and closure checks."

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        val appContext = LocalContext.current
        val requestedJobId = context.setting("survey_job_id", "")
        var selectedJobId by rememberSaveable { mutableStateOf(requestedJobId) }
        var job by remember { mutableStateOf<SurveyRepository.LevelJob?>(null) }
        var latestExecution by remember { mutableStateOf<ExecutionResult?>(null) }
        var status by rememberSaveable { mutableStateOf("Loading levelling field book…") }
        var jobName by rememberSaveable { mutableStateOf(context.setting("survey_job_name", "Levelling")) }
        var startRl by rememberSaveable { mutableStateOf(context.setting("start_reduced_level_m", "100")) }
        var closeRl by rememberSaveable { mutableStateOf(context.setting("known_close_reduced_level_m", "")) }
        var distribute by rememberSaveable { mutableStateOf(context.setting("distribute_closure", "true").toBooleanStrictOrNull() ?: true) }
        var station by rememberSaveable { mutableStateOf("BM") }
        var type by rememberSaveable { mutableStateOf("BS") }
        var reading by rememberSaveable { mutableStateOf("") }
        var distance by rememberSaveable { mutableStateOf("0") }

        val keepLiveDashboard =
            context.presentationMode == CapabilityPresentationMode.Dashboard || context.isNativePresetRun ||
                context.request.source.equals("intent_test", ignoreCase = true)

        fun snapshot(current: SurveyRepository.LevelJob, keepInvalid: Boolean = false) {
            val input = context.request.invocationContext.asMap(As100LevellingBookMethod.id) +
                context.request.settings + context.action.settings + SurveyRepository.levelContext(current)
            val calc = As100LevellingBookMethod.calculateSafely(input)
            val request = As100LevellingBookMethod.request(As100LevellingBookMethod.id, input, emptyList(), emptyList())
            val execution = As100LevellingBookMethod.result(request, calc).withInvocationContext(context.request.invocationContext)
            latestExecution = if (calc.valid || keepInvalid) execution else null
            status = if (calc.valid) calc.mainResult else calc.error.ifBlank { calc.mainResult }
        }

        fun loadOrCreate() {
            val existing = selectedJobId.takeIf { it.isNotBlank() }?.let { SurveyRepository.levelJob(appContext, it) }
                ?: if (requestedJobId.isBlank()) SurveyRepository.levelJobs(appContext).firstOrNull() else null
            if (existing != null) {
                job = existing
                selectedJobId = existing.id
                jobName = existing.name
                startRl = existing.startReducedLevel.toString()
                closeRl = existing.knownCloseReducedLevel?.toString().orEmpty()
                distribute = existing.distributeClosure
                station = if (existing.observations.isEmpty()) "BM" else "P${existing.observations.size + 1}"
                snapshot(existing, keepInvalid = !keepLiveDashboard)
            } else if (keepLiveDashboard) {
                val created = SurveyRepository.newLevelJob(
                    jobName,
                    startRl.toDoubleOrNull() ?: 100.0,
                    closeRl.toDoubleOrNull(),
                    distribute
                )
                val saved = SurveyRepository.saveLevelJob(appContext, created)
                job = saved
                selectedJobId = saved.id
                status = "New levelling book created. Add the initial backsight."
            } else {
                val input = context.request.invocationContext.asMap(As100LevellingBookMethod.id) + context.request.settings + context.action.settings +
                    mapOf("level_observations" to "", "survey_job_id" to requestedJobId, "start_reduced_level_m" to startRl)
                val calc = As100LevellingBookMethod.calculateSafely(input)
                val request = As100LevellingBookMethod.request(As100LevellingBookMethod.id, input, emptyList(), emptyList())
                latestExecution = As100LevellingBookMethod.result(request, calc).withInvocationContext(context.request.invocationContext)
                status = calc.error.ifBlank { "Levelling job not found." }
            }
        }
        LaunchedEffect(Unit) { loadOrCreate() }

        fun saveSettings() {
            val current = job ?: return
            val start = startRl.toDoubleOrNull()
            if (start == null) {
                status = "Starting reduced level must be numeric."
                return
            }
            val saved = SurveyRepository.saveLevelJob(appContext, current.copy(
                name = jobName.ifBlank { "Levelling" },
                startReducedLevel = start,
                knownCloseReducedLevel = closeRl.toDoubleOrNull(),
                distributeClosure = distribute
            ))
            job = saved
            context.onSettingsChanged(SurveyRepository.levelContext(saved))
            if (saved.observations.isNotEmpty()) snapshot(saved)
        }

        fun addObservation() {
            val current = job ?: return
            val r = reading.toDoubleOrNull()
            val d = distance.toDoubleOrNull()
            if (r == null || r < 0.0 || d == null || d < 0.0) {
                status = "Enter non-negative numeric reading and distance."
                return
            }
            val obs = SurveyCalculations.LevelObservation(station.ifBlank { "P${current.observations.size + 1}" }, type, r, d)
            val saved = SurveyRepository.saveLevelJob(appContext, current.copy(observations = current.observations + obs))
            job = saved
            reading = ""
            distance = "0"
            if (type == "FS") {
                // Most likely next action at a change point is a BS on the same station.
                type = "BS"
            } else {
                station = "P${saved.observations.size + 1}"
            }
            snapshot(saved)
        }

        fun undoObservation() {
            val current = job ?: return
            if (current.observations.isEmpty()) return
            val saved = SurveyRepository.saveLevelJob(appContext, current.copy(observations = current.observations.dropLast(1)))
            job = saved
            if (saved.observations.isEmpty()) {
                latestExecution = null
                status = "Levelling book is empty."
            } else snapshot(saved)
        }

        fun newJob() {
            val saved = SurveyRepository.saveLevelJob(appContext, SurveyRepository.newLevelJob("Levelling", 100.0))
            job = saved
            selectedJobId = saved.id
            jobName = saved.name
            startRl = "100"
            closeRl = ""
            distribute = true
            station = "BM"
            type = "BS"
            reading = ""
            distance = "0"
            latestExecution = null
            status = "New levelling book created."
        }

        val scaffoldResult = if (keepLiveDashboard) null else latestExecution
        CapabilityScreenScaffold(
            title = title,
            capabilityId = capabilityId,
            context = context,
            canGoBack = context.stepNumber > 1,
            capturedResult = scaffoldResult,
            resultPreview = scaffoldResult?.let { OutputFormatter.fields(it, includeProvenance = false) }.orEmpty(),
            onBack = onBack,
            onRetry = { loadOrCreate() },
            onConfirm = { latestExecution?.let(onConfirmed) },
            onCancel = onCancel
        ) {
            Column(Modifier.fillMaxWidth()) {
                val current = job
                if (current == null) {
                    Text(status)
                } else {
                    LevellingDashboardSummary(current)
                    Spacer(Modifier.height(8.dp))
                    LevelProfile(current)
                    Spacer(Modifier.height(10.dp))
                    Text("Job", style = MaterialTheme.typography.titleMedium)
                    if (context.settingShouldBeShown("survey_job_name"))
                        OutlinedTextField(jobName, { jobName = it }, label = { Text("Job name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (context.settingShouldBeShown("start_reduced_level_m")) OutlinedTextField(startRl, { startRl = it }, label = { Text("Start RL") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                        if (context.settingShouldBeShown("known_close_reduced_level_m")) OutlinedTextField(closeRl, { closeRl = it }, label = { Text("Known close RL") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                    }
                    if (context.settingShouldBeShown("distribute_closure")) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Distribute closure correction", modifier = Modifier.weight(1f))
                            Switch(distribute, { distribute = it })
                        }
                    }
                    OutlinedButton(::saveSettings, Modifier.fillMaxWidth()) { Text("Save job settings") }

                    Spacer(Modifier.height(10.dp))
                    Text("Add staff reading", style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(station, { station = it }, label = { Text("Station / point ID") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    Text("Reading type", style = MaterialTheme.typography.labelLarge)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("BS", "IS", "FS").forEach { candidate ->
                            if (type == candidate) Button({ type = candidate }, Modifier.weight(1f)) { Text("✓ $candidate") }
                            else OutlinedButton({ type = candidate }, Modifier.weight(1f)) { Text(candidate) }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(reading, { reading = it }, label = { Text("Reading m") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                        OutlinedTextField(distance, { distance = it }, label = { Text("Distance from previous m") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                    }
                    Button(::addObservation, Modifier.fillMaxWidth()) { Text("Add observation") }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(::undoObservation, Modifier.weight(1f), enabled = current.observations.isNotEmpty()) { Text("Undo last") }
                        OutlinedButton(::newJob, Modifier.weight(1f)) { Text("New job") }
                    }
                    LevellingRecentRows(current)
                    Text(status, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
                    OutlinedButton({ snapshot(current) }, Modifier.fillMaxWidth()) { Text("Refresh snapshot") }
                    if (keepLiveDashboard && latestExecution != null) {
                        Button({ latestExecution?.let(onConfirmed) }, Modifier.fillMaxWidth()) {
                            Text(if (context.isNativePresetRun) "Finish" else "Use this snapshot")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TraverseDashboardSummary(job: SurveyRepository.TraverseJob) {
    val result = runCatching {
        SurveyCalculations.traverse(
            SurveyCalculations.Point(job.startPointId, job.startEasting, job.startNorthing),
            job.legs,
            if (job.closeEasting != null && job.closeNorthing != null) SurveyCalculations.Point("CLOSE", job.closeEasting, job.closeNorthing) else null,
            job.adjustmentMode,
            job.minimumRelativePrecision
        )
    }.getOrNull()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        MetricCard("Legs", job.legs.size.toString(), Modifier.weight(1f))
        MetricCard("Length", result?.totalDistance?.let { "${it.f2()} m" } ?: "—", Modifier.weight(1f))
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        MetricCard("Closure", result?.linearMisclosure?.let { "${it.f3()} m" } ?: "Open", Modifier.weight(1f))
        MetricCard("Precision", result?.relativePrecision?.let { if (it.isInfinite()) "∞" else "1:${it.f0()}" } ?: "—", Modifier.weight(1f))
    }
}

@Composable
private fun LevellingDashboardSummary(job: SurveyRepository.LevelJob) {
    val result = runCatching { SurveyCalculations.reduceLevelBook(job.startReducedLevel, job.observations, job.knownCloseReducedLevel, job.distributeClosure) }.getOrNull()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        MetricCard("Readings", job.observations.size.toString(), Modifier.weight(1f))
        MetricCard("Final RL", result?.finalReducedLevel?.let { "${it.f4()} m" } ?: "—", Modifier.weight(1f))
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        MetricCard("Closure", result?.closureError?.let { "${it.signF4()} m" } ?: "Open", Modifier.weight(1f))
        MetricCard("ΣBS−ΣFS check", result?.arithmeticCheckError?.let { "${it.signF6()} m" } ?: "—", Modifier.weight(1f))
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier.padding(vertical = 2.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(10.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun TraversePlan(job: SurveyRepository.TraverseJob) {
    val result = runCatching {
        SurveyCalculations.traverse(
            SurveyCalculations.Point(job.startPointId, job.startEasting, job.startNorthing), job.legs,
            if (job.closeEasting != null && job.closeNorthing != null) SurveyCalculations.Point("CLOSE", job.closeEasting, job.closeNorthing) else null,
            job.adjustmentMode, job.minimumRelativePrecision
        )
    }.getOrNull()
    val points = result?.points.orEmpty()
    Box(Modifier.fillMaxWidth().aspectRatio(1.7f).background(MaterialTheme.colorScheme.surfaceVariant).border(1.dp, MaterialTheme.colorScheme.outline)) {
        if (points.size < 2) {
            Text("Traverse plot appears after the first leg.", modifier = Modifier.padding(12.dp))
        } else {
            val lineColor = MaterialTheme.colorScheme.primary
            val pointColor = MaterialTheme.colorScheme.onSurface
            Canvas(Modifier.fillMaxWidth().aspectRatio(1.7f).padding(12.dp)) {
                val minE = points.minOf { it.adjustedEasting }
                val maxE = points.maxOf { it.adjustedEasting }
                val minN = points.minOf { it.adjustedNorthing }
                val maxN = points.maxOf { it.adjustedNorthing }
                val spanE = max(maxE - minE, 1e-9)
                val spanN = max(maxN - minN, 1e-9)
                fun xy(p: SurveyCalculations.TraversePoint): Offset {
                    val x = ((p.adjustedEasting - minE) / spanE).toFloat() * size.width
                    val y = size.height - ((p.adjustedNorthing - minN) / spanN).toFloat() * size.height
                    return Offset(x, y)
                }
                points.zipWithNext().forEach { (a, b) -> drawLine(lineColor, xy(a), xy(b), strokeWidth = 4f) }
                points.forEach { drawCircle(pointColor, radius = 7f, center = xy(it)) }
            }
        }
    }
}

@Composable
private fun LevelProfile(job: SurveyRepository.LevelJob) {
    val result = runCatching { SurveyCalculations.reduceLevelBook(job.startReducedLevel, job.observations, job.knownCloseReducedLevel, job.distributeClosure) }.getOrNull()
    val rows = result?.rows.orEmpty().filter { it.reducedLevel != null && it.type != "BS" }
    Box(Modifier.fillMaxWidth().aspectRatio(2.0f).background(MaterialTheme.colorScheme.surfaceVariant).border(1.dp, MaterialTheme.colorScheme.outline)) {
        if (rows.size < 2) {
            Text("Level profile appears after two reduced points.", modifier = Modifier.padding(12.dp))
        } else {
            val lineColor = MaterialTheme.colorScheme.primary
            val pointColor = MaterialTheme.colorScheme.onSurface
            Canvas(Modifier.fillMaxWidth().aspectRatio(2.0f).padding(12.dp)) {
                val minRl = rows.minOf { it.reducedLevel!! }
                val maxRl = rows.maxOf { it.reducedLevel!! }
                val span = max(maxRl - minRl, 1e-9)
                fun xy(index: Int): Offset {
                    val x = index.toFloat() / max(rows.lastIndex, 1) * size.width
                    val y = size.height - (((rows[index].reducedLevel!! - minRl) / span).toFloat() * size.height)
                    return Offset(x, y)
                }
                for (i in 0 until rows.lastIndex) drawLine(lineColor, xy(i), xy(i + 1), strokeWidth = 4f)
                rows.indices.forEach { drawCircle(pointColor, 6f, xy(it)) }
            }
        }
    }
}

@Composable
private fun TraverseRecentRows(job: SurveyRepository.TraverseJob) {
    if (job.legs.isEmpty()) return
    Text("Recent legs", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 10.dp))
    job.legs.takeLast(8).forEachIndexed { index, leg ->
        Text("${job.legs.size - min(8, job.legs.size) + index + 1}. ${leg.toId}: ${leg.bearingDeg.f4()}° / ${leg.distance.f3()} m", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun LevellingRecentRows(job: SurveyRepository.LevelJob) {
    if (job.observations.isEmpty()) return
    Text("Recent readings", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 10.dp))
    job.observations.takeLast(10).forEach { obs ->
        Text("${obs.station}  ${obs.type}  ${obs.reading.f4()} m  Δd ${obs.distanceFromPrevious.f2()} m", style = MaterialTheme.typography.bodySmall)
    }
}

private fun CapabilityScreenContext.setting(key: String, default: String): String =
    action.settings[key] ?: action.settings["input_$key"] ?: request.settings[key] ?: request.settings["input_$key"] ?: default

private fun Double.f0(): String = String.format(Locale.US, "%.0f", this)
private fun Double.f2(): String = String.format(Locale.US, "%.2f", this)
private fun Double.f3(): String = String.format(Locale.US, "%.3f", this)
private fun Double.f4(): String = String.format(Locale.US, "%.4f", this)
private fun Double.signF4(): String = (if (this >= 0) "+" else "") + f4()
private fun Double.signF6(): String = (if (this >= 0) "+" else "") + String.format(Locale.US, "%.6f", this)
