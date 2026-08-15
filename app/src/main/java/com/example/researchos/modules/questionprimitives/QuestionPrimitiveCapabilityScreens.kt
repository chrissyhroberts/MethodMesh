package com.example.researchos.modules.questionprimitives

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.researchos.core.researchos.ExecutionResult
import com.example.researchos.transport.OutputFormatter
import com.example.researchos.transport.workflow.ui.CapabilityScreenContext
import com.example.researchos.transport.workflow.ui.CapabilityScreenScaffold
import com.example.researchos.transport.workflow.ui.CapabilityScreenSpec
import com.example.researchos.transport.workflow.ui.IntentExample
import com.example.researchos.transport.workflow.ui.IntentExampleDropdown

object QuestionTextCapabilityScreen : QuestionPrimitiveScreen(
    method = QuestionTextMethod,
    titleText = "Text question",
    descriptionText = "Capture text with optional regex validation."
)

object QuestionNumberCapabilityScreen : QuestionPrimitiveScreen(
    method = QuestionNumberMethod,
    titleText = "Number question",
    descriptionText = "Capture a number with optional range validation."
)

object QuestionSelectOneCapabilityScreen : QuestionPrimitiveScreen(
    method = QuestionSelectOneMethod,
    titleText = "Select one question",
    descriptionText = "Capture one answer from a list."
)

object QuestionSelectMultipleCapabilityScreen : QuestionPrimitiveScreen(
    method = QuestionSelectMultipleMethod,
    titleText = "Select multiple question",
    descriptionText = "Capture multiple answers from a list."
)

abstract class QuestionPrimitiveScreen(
    private val method: QuestionPrimitiveMethod,
    private val titleText: String,
    private val descriptionText: String
) : CapabilityScreenSpec {
    override val capabilityId = method.id
    override val title = titleText
    override val description = descriptionText

    @Composable
    override fun Render(
        context: CapabilityScreenContext,
        onBack: () -> Unit,
        onConfirmed: (ExecutionResult) -> Unit,
        onCancel: () -> Unit
    ) {
        val settings = context.action.settings
        var questionId by rememberSaveable { mutableStateOf(settings.setting("question_id", method.id.replace('.', '_'))) }
        var prompt by rememberSaveable { mutableStateOf(settings.setting("prompt", titleText)) }
        var hint by rememberSaveable { mutableStateOf(settings.setting("hint", "")) }
        var answer by rememberSaveable { mutableStateOf(settings.setting("answer", "")) }
        var required by rememberSaveable { mutableStateOf(settings.setting("required", "false").toBooleanStrictOrNull() ?: false) }
        var regex by rememberSaveable { mutableStateOf(settings.setting("regex", "")) }
        var constraint by rememberSaveable { mutableStateOf(settings.setting("constraint_message", "Answer does not meet the required constraint.")) }
        var optionsText by rememberSaveable { mutableStateOf(settings.setting("options", "Yes|No|Unknown")) }
        var min by rememberSaveable { mutableStateOf(settings.setting("min", "")) }
        var max by rememberSaveable { mutableStateOf(settings.setting("max", "")) }
        var launched by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }
        var status by remember { mutableStateOf("Ready.") }
        val options = optionsText.split('\n', '|', ',').map { it.trim() }.filter { it.isNotBlank() }.distinct()

        fun capture() {
            val requestContext = context.request.invocationContext.asMap(method.id) + settings + mapOf(
                "question_id" to questionId,
                "prompt" to prompt,
                "hint" to hint,
                "answer" to answer,
                "required" to required.toString(),
                "regex" to regex,
                "constraint_message" to constraint,
                "options" to optionsText,
                "min" to min,
                "max" to max
            )
            val request = method.request(method.id, requestContext, emptyList(), emptyList())
            val execution = method.result(request, method.evaluate(requestContext), context.request.invocationContext)
            result = execution
            val preview = OutputFormatter.fields(execution, includeProvenance = false)
            status = preview[QuestionPrimitiveFields.ERROR]?.toString()?.takeIf { it.isNotBlank() }
                ?: preview[QuestionPrimitiveFields.STATUS]?.toString().orEmpty().ifBlank { "Captured." }
            if (context.startsImmediately && answer.isNotBlank()) onConfirmed(execution)
        }

        LaunchedEffect(context.startsImmediately, answer) {
            if (context.startsImmediately && !launched && answer.isNotBlank()) {
                launched = true
                capture()
            }
        }

        CapabilityScreenScaffold(
            title = title,
            capabilityId = capabilityId,
            context = context,
            canGoBack = context.stepNumber > 1,
            capturedResult = result,
            resultPreview = result?.let { OutputFormatter.fields(it, includeProvenance = false) }.orEmpty(),
            onBack = onBack,
            onRetry = { capture() },
            onConfirm = { result?.let(onConfirmed) },
            onCancel = onCancel
        ) {
            OutlinedTextField(value = questionId, onValueChange = { questionId = it }, label = { Text("Question ID") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = prompt, onValueChange = { prompt = it }, label = { Text("Prompt") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = hint, onValueChange = { hint = it }, label = { Text("Hint") }, modifier = Modifier.fillMaxWidth())
            OutlinedButton(onClick = { required = !required }, modifier = Modifier.fillMaxWidth()) {
                Text(if (required) "Required: yes" else "Required: no")
            }
            OutlinedTextField(value = regex, onValueChange = { regex = it }, label = { Text("Regex constraint (optional)") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = constraint, onValueChange = { constraint = it }, label = { Text("Constraint message") }, modifier = Modifier.fillMaxWidth())

            if (method.id == QuestionNumberMethod.id) {
                Row(Modifier.fillMaxWidth()) {
                    OutlinedTextField(value = min, onValueChange = { min = it.numericText() }, label = { Text("Min") }, modifier = Modifier.weight(1f))
                    Spacer(Modifier.padding(4.dp))
                    OutlinedTextField(value = max, onValueChange = { max = it.numericText() }, label = { Text("Max") }, modifier = Modifier.weight(1f))
                }
            }

            if (method.id == QuestionSelectOneMethod.id || method.id == QuestionSelectMultipleMethod.id) {
                OutlinedTextField(
                    value = optionsText,
                    onValueChange = { optionsText = it },
                    label = { Text("Options, separated by |, comma, or newline") },
                    modifier = Modifier.fillMaxWidth()
                )
                Column {
                    options.forEach { option ->
                        val selected = answer.split('|').map { it.trim() }.contains(option)
                        FilterChip(
                            selected = selected,
                            onClick = {
                                answer = if (method.id == QuestionSelectOneMethod.id) {
                                    option
                                } else {
                                    val current = answer.split('|').map { it.trim() }.filter { it.isNotBlank() }.toMutableList()
                                    if (option in current) current.remove(option) else current.add(option)
                                    current.joinToString("|")
                                }
                            },
                            label = { Text(option) },
                            modifier = Modifier.padding(end = 6.dp, bottom = 4.dp)
                        )
                    }
                }
            }

            OutlinedTextField(
                value = answer,
                onValueChange = { answer = if (method.id == QuestionNumberMethod.id) it.numericText() else it },
                label = { Text("Answer") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            Button(onClick = { capture() }, modifier = Modifier.fillMaxWidth()) { Text("Capture answer") }
            Text(status)
            IntentExampleDropdown(
                capabilityId = capabilityId,
                examples = examples()
            )
        }
    }

    private fun examples() = listOf(
        IntentExample(
            label = "Capture answer",
            description = "Open this question primitive with a prompt and return a flat result.",
            intentUri = "com.example.researchos.EXECUTE_METHOD(method_id='${method.id}',input_question_id='q1',input_prompt='Example question',input_required='true',return_mode='flat')"
        ),
        IntentExample(
            label = "Pre-filled answer",
            description = "Supply an answer directly for automated protocol testing.",
            intentUri = "com.example.researchos.EXECUTE_METHOD(method_id='${method.id}',input_question_id='q1',input_prompt='Example question',input_answer='test',return_mode='flat')"
        )
    )
}

private fun Map<String, String>.setting(key: String, fallback: String): String =
    this[key] ?: this["input_$key"] ?: fallback

private fun String.numericText(): String =
    filterIndexed { index, char -> char.isDigit() || char == '.' || (char == '-' && index == 0) }
