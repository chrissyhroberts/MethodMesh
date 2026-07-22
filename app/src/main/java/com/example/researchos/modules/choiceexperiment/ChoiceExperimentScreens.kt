package com.example.researchos.modules.choiceexperiment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.researchos.core.researchos.ExecutionResult
import com.example.researchos.core.researchos.withInvocationContext
import com.example.researchos.transport.OutputFormatter
import com.example.researchos.transport.workflow.ui.CapabilityScreenContext
import com.example.researchos.transport.workflow.ui.CapabilityScreenScaffold
import com.example.researchos.transport.workflow.ui.CapabilityScreenSpec
import com.example.researchos.transport.workflow.ui.IntentExample
import com.example.researchos.transport.workflow.ui.IntentExampleDropdown

internal abstract class DceCapabilityScreen(
    private val method: DceMethod
) : CapabilityScreenSpec {
    override val capabilityId: String = method.id
    override val title: String = method.title
    override val description: String = "Run a ${method.title.lowercase()} task and return structured DCE JSON."

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        val config = remember(context.action.settings) { DceConfigParser.from(context.action.settings, method) }
        var resetCounter by remember { mutableIntStateOf(0) }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }
        var status by remember { mutableStateOf("Ready.") }

        fun complete(resultJson: String, responseCount: Int, extra: Map<String, String> = emptyMap()) {
            val execution = DceResultFactory.complete(
                method = method,
                request = DceResultFactory.requestFor(
                    method = method,
                    action = context.action.canonicalId,
                    context = context.request.invocationContext.asMap(context.action.canonicalId) + context.action.settings
                ),
                config = config,
                resultJson = resultJson,
                responseCount = responseCount,
                extraValues = extra
            ).withInvocationContext(context.request.invocationContext)
            result = execution
            status = "Task complete. Review the result and press Confirm."
        }

        CapabilityScreenScaffold(
            title = title,
            capabilityId = context.action.canonicalId,
            context = context,
            canGoBack = context.stepNumber > 1,
            capturedResult = result,
            resultPreview = result?.let { OutputFormatter.fields(it, includeProvenance = false) }.orEmpty(),
            onBack = onBack,
            onRetry = {
                resetCounter += 1
                result = null
                status = "Ready."
            },
            onConfirm = { result?.let(onConfirmed) },
            onCancel = onCancel
        ) {
            Text(status)
            Spacer(Modifier.height(10.dp))
            TaskBody(
                config = config,
                resetCounter = resetCounter,
                isComplete = result != null,
                onComplete = ::complete
            )

            Spacer(Modifier.height(16.dp))
            IntentExampleDropdown(
                capabilityId = method.id,
                examples = getMethodExamples(method)
            )
        }
    }

    @Composable
    protected abstract fun TaskBody(
        config: DceConfig,
        resetCounter: Int,
        isComplete: Boolean,
        onComplete: (String, Int, Map<String, String>) -> Unit
    )

    private fun getMethodExamples(method: DceMethod): List<IntentExample> = when (method) {
        DceMethod.Pairwise -> listOf(
            IntentExample(
                label = "Pairwise comparison (3 rounds)",
                description = "Compare 3 options in pairwise rounds",
                intentUri = "com.example.researchos.EXECUTE_METHOD(method_id='dce.pairwise',options='Option A|Option B|Option C',rounds='3')"
            ),
            IntentExample(
                label = "With seed",
                description = "Reproducible design using a seed",
                intentUri = "com.example.researchos.EXECUTE_METHOD(method_id='dce.pairwise',options='Speed|Cost|Safety',rounds='4',seed='study_001')"
            )
        )
        DceMethod.MaxDiff -> listOf(
            IntentExample(
                label = "Best-worst scaling (4 rounds)",
                description = "Select best and worst from attribute sets",
                intentUri = "com.example.researchos.EXECUTE_METHOD(method_id='dce.maxdiff',options='Speed|Cost|Safety|Comfort',rounds='4')"
            ),
            IntentExample(
                label = "4 items per round",
                description = "Show 4 items in each round",
                intentUri = "com.example.researchos.EXECUTE_METHOD(method_id='dce.maxdiff',options='Quality|Price|Service|Selection',rounds='5',items_per_round='4')"
            )
        )
        DceMethod.Ranking -> listOf(
            IntentExample(
                label = "Full ranking",
                description = "Rank all options from best to worst",
                intentUri = "com.example.researchos.EXECUTE_METHOD(method_id='dce.ranking',options='First|Second|Third|Fourth')"
            ),
            IntentExample(
                label = "Top-k ranking",
                description = "Rank only top 3 options",
                intentUri = "com.example.researchos.EXECUTE_METHOD(method_id='dce.ranking',options='Option1|Option2|Option3|Option4|Option5',top_k='3')"
            )
        )
        DceMethod.Points -> listOf(
            IntentExample(
                label = "100-point allocation",
                description = "Distribute 100 points across options",
                intentUri = "com.example.researchos.EXECUTE_METHOD(method_id='dce.points',options='Feature A|Feature B|Feature C|Feature D')"
            ),
            IntentExample(
                label = "Custom points budget",
                description = "Allocate custom number of points",
                intentUri = "com.example.researchos.EXECUTE_METHOD(method_id='dce.points',options='Attr1|Attr2|Attr3',points='50')"
            )
        )
        DceMethod.Conjoint -> listOf(
            IntentExample(
                label = "Conjoint analysis (3 profiles)",
                description = "Choose preferred product profiles",
                intentUri = "com.example.researchos.EXECUTE_METHOD(method_id='dce.conjoint',rounds='3',profiles_per_round='2')"
            ),
            IntentExample(
                label = "With predefined attributes",
                description = "Conjoint with specific attributes",
                intentUri = "com.example.researchos.EXECUTE_METHOD(method_id='dce.conjoint',rounds='5',profiles_per_round='2',seed='study_001')"
            )
        )
    }
}

internal object PairwiseChoiceScreen : DceCapabilityScreen(DceMethod.Pairwise) {
    @Composable
    override fun TaskBody(
        config: DceConfig,
        resetCounter: Int,
        isComplete: Boolean,
        onComplete: (String, Int, Map<String, String>) -> Unit
    ) {
        val rounds = remember(config, resetCounter) { DceDesignGenerator.choiceRounds(config, config.optionsPerRound) }
        var index by remember(config, resetCounter) { mutableIntStateOf(0) }
        var responses by remember(config, resetCounter) { mutableStateOf(listOf<PairwiseResponse>()) }
        if (isComplete) {
            Text("Pairwise task complete.")
            return
        }
        val round = rounds[index]
        Text("Round ${round.roundNumber} of ${rounds.size}", fontWeight = FontWeight.SemiBold)
        Text("Which option do you prefer?")
        Spacer(Modifier.height(8.dp))
        round.shown.forEachIndexed { optionIndex, option ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(Modifier.padding(10.dp)) {
                    Text("Option ${optionIndex + 1}", style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
                    Text(option, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            val updated = responses + PairwiseResponse(round.roundNumber, round.shown, option)
                            responses = updated
                            if (index >= rounds.lastIndex) {
                                onComplete(
                                    DceJson.pairwise(config, updated),
                                    updated.size,
                                    mapOf("selected" to option)
                                )
                            } else {
                                index += 1
                            }
                        }
                    ) { Text("Choose this option") }
                }
            }
        }
    }
}

internal object MaxDiffChoiceScreen : DceCapabilityScreen(DceMethod.MaxDiff) {
    @Composable
    override fun TaskBody(
        config: DceConfig,
        resetCounter: Int,
        isComplete: Boolean,
        onComplete: (String, Int, Map<String, String>) -> Unit
    ) {
        val rounds = remember(config, resetCounter) { DceDesignGenerator.choiceRounds(config, config.itemsPerRound) }
        var index by remember(config, resetCounter) { mutableIntStateOf(0) }
        var responses by remember(config, resetCounter) { mutableStateOf(listOf<MaxDiffResponse>()) }
        var best by remember(index, resetCounter) { mutableStateOf<String?>(null) }
        var worst by remember(index, resetCounter) { mutableStateOf<String?>(null) }
        if (isComplete) {
            Text("MaxDiff task complete.")
            return
        }
        val round = rounds[index]
        Text("Round ${round.roundNumber} of ${rounds.size}", fontWeight = FontWeight.SemiBold)
        Text("Choose the best and worst item in this set.")
        Spacer(Modifier.height(8.dp))
        round.shown.forEach { item ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(Modifier.padding(10.dp)) {
                    Text(item, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { best = item }) { Text(if (best == item) "Best ✓" else "Best") }
                        OutlinedButton(onClick = { worst = item }) { Text(if (worst == item) "Worst ✓" else "Worst") }
                    }
                }
            }
        }
        Button(
            enabled = best != null && worst != null && best != worst,
            onClick = {
                val updated = responses + MaxDiffResponse(round.roundNumber, round.shown, best.orEmpty(), worst.orEmpty())
                responses = updated
                if (index >= rounds.lastIndex) {
                    onComplete(
                        DceJson.maxDiff(config, updated),
                        updated.size,
                        mapOf("best" to best.orEmpty(), "worst" to worst.orEmpty())
                    )
                } else {
                    index += 1
                    best = null
                    worst = null
                }
            }
        ) { Text(if (index >= rounds.lastIndex) "Finish task" else "Next round") }
    }
}

internal object RankingChoiceScreen : DceCapabilityScreen(DceMethod.Ranking) {
    @Composable
    override fun TaskBody(
        config: DceConfig,
        resetCounter: Int,
        isComplete: Boolean,
        onComplete: (String, Int, Map<String, String>) -> Unit
    ) {
        val rounds = remember(config, resetCounter) { DceDesignGenerator.choiceRounds(config, config.optionsPerRound) }
        var index by remember(config, resetCounter) { mutableIntStateOf(0) }
        var responses by remember(config, resetCounter) { mutableStateOf(listOf<RankingResponse>()) }
        var ranking by remember(index, resetCounter) { mutableStateOf(rounds[index].shown) }
        if (isComplete) {
            Text("Ranking task complete.")
            return
        }
        val round = rounds[index]
        Text("Round ${round.roundNumber} of ${rounds.size}", fontWeight = FontWeight.SemiBold)
        Text("Move options until they are ranked from most preferred to least preferred.")
        Spacer(Modifier.height(8.dp))
        ranking.forEachIndexed { itemIndex, item ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${itemIndex + 1}. $item", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    OutlinedButton(enabled = itemIndex > 0, onClick = { ranking = ranking.swap(itemIndex, itemIndex - 1) }) { Text("↑") }
                    OutlinedButton(enabled = itemIndex < ranking.lastIndex, onClick = { ranking = ranking.swap(itemIndex, itemIndex + 1) }) { Text("↓") }
                }
            }
        }
        Button(onClick = {
            val updated = responses + RankingResponse(round.roundNumber, round.shown, ranking)
            responses = updated
            if (index >= rounds.lastIndex) {
                onComplete(
                    DceJson.ranking(config, updated),
                    updated.size,
                    mapOf("top_ranked" to ranking.firstOrNull().orEmpty())
                )
            } else {
                index += 1
            }
        }) { Text(if (index >= rounds.lastIndex) "Finish task" else "Next round") }
    }

    private fun List<String>.swap(i: Int, j: Int): List<String> = toMutableList().also { list ->
        val tmp = list[i]
        list[i] = list[j]
        list[j] = tmp
    }
}

internal object PointsChoiceScreen : DceCapabilityScreen(DceMethod.Points) {
    @Composable
    override fun TaskBody(
        config: DceConfig,
        resetCounter: Int,
        isComplete: Boolean,
        onComplete: (String, Int, Map<String, String>) -> Unit
    ) {
        val allocations = remember(config, resetCounter) { mutableStateMapOf<String, Int>().also { map -> config.options.forEach { map[it] = 0 } } }
        val used = allocations.values.sum()
        val remaining = config.totalPoints - used
        if (isComplete) {
            Text("Points allocation complete.")
            return
        }
        Text("Allocate ${config.totalPoints} points across the options.", fontWeight = FontWeight.SemiBold)
        Text("Remaining: $remaining")
        Spacer(Modifier.height(8.dp))
        config.options.forEach { item ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(item, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    OutlinedButton(enabled = (allocations[item] ?: 0) > 0, onClick = { allocations[item] = (allocations[item] ?: 0) - 1 }) { Text("−") }
                    Text((allocations[item] ?: 0).toString(), fontFamily = FontFamily.Monospace)
                    OutlinedButton(enabled = remaining > 0, onClick = { allocations[item] = (allocations[item] ?: 0) + 1 }) { Text("+") }
                }
            }
        }
        Button(enabled = remaining == 0, onClick = {
            val finalAllocations = config.options.associateWith { allocations[it] ?: 0 }
            val top = finalAllocations.maxByOrNull { it.value }?.key.orEmpty()
            onComplete(
                DceJson.points(config, finalAllocations),
                1,
                mapOf("top_allocated" to top, "total_points" to config.totalPoints.toString())
            )
        }) { Text("Finish task") }
    }
}

internal object ConjointChoiceScreen : DceCapabilityScreen(DceMethod.Conjoint) {
    @Composable
    override fun TaskBody(
        config: DceConfig,
        resetCounter: Int,
        isComplete: Boolean,
        onComplete: (String, Int, Map<String, String>) -> Unit
    ) {
        val rounds = remember(config, resetCounter) { DceDesignGenerator.conjointRounds(config) }
        var index by remember(config, resetCounter) { mutableIntStateOf(0) }
        var responses by remember(config, resetCounter) { mutableStateOf(listOf<ConjointResponse>()) }
        if (isComplete) {
            Text("Conjoint task complete.")
            return
        }
        val round = rounds[index]
        Text("Round ${round.roundNumber} of ${rounds.size}", fontWeight = FontWeight.SemiBold)
        Text("Choose the preferred profile.")
        Spacer(Modifier.height(8.dp))
        round.profiles.forEach { profile ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(Modifier.padding(10.dp)) {
                    Text("Profile ${profile["profile_id"]}", fontWeight = FontWeight.Bold)
                    profile.filterKeys { it != "profile_id" }.forEach { (key, value) ->
                        Text("$key: $value")
                    }
                    Spacer(Modifier.height(6.dp))
                    Button(onClick = {
                        val selected = profile["profile_id"].orEmpty()
                        val updated = responses + ConjointResponse(round.roundNumber, round.profiles, selected)
                        responses = updated
                        if (index >= rounds.lastIndex) {
                            onComplete(
                                DceJson.conjoint(config, updated),
                                updated.size,
                                mapOf("selected_profile" to selected)
                            )
                        } else {
                            index += 1
                        }
                    }) { Text("Choose this profile") }
                }
            }
        }
    }
}
