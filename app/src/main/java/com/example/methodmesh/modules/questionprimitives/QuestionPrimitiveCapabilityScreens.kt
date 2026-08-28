package com.example.methodmesh.modules.questionprimitives

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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.transport.OutputFormatter
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityPresentationMode
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenScaffold
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec
import com.example.methodmesh.transport.workflow.ui.IntentExample
import com.example.methodmesh.transport.workflow.ui.IntentExampleDropdown
import kotlinx.coroutines.delay

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
        val initialAnswer = settings.setting("answer", "")
        var answer by rememberSaveable { mutableStateOf(initialAnswer) }
        var required by rememberSaveable { mutableStateOf(settings.setting("required", "false").toBooleanStrictOrNull() ?: false) }
        var regex by rememberSaveable { mutableStateOf(settings.setting("regex", "")) }
        var constraint by rememberSaveable { mutableStateOf(settings.setting("constraint_message", "Answer does not meet the required constraint.")) }
        var optionsText by rememberSaveable { mutableStateOf(settings.setting("options", "Yes|No|Unknown")) }
        var exclusiveOptionsText by rememberSaveable { mutableStateOf(settings.setting("exclusive_options", "")) }
        var exclusiveGroupsText by rememberSaveable { mutableStateOf(settings.setting("exclusive_groups", "")) }
        var min by rememberSaveable { mutableStateOf(settings.setting("min", "")) }
        var max by rememberSaveable { mutableStateOf(settings.setting("max", "")) }
        var launched by rememberSaveable(context.action.canonicalId) { mutableStateOf(false) }
        var result by remember { mutableStateOf<ExecutionResult?>(null) }
        var status by remember { mutableStateOf("Ready.") }
        val intentPresentation = context.presentationMode == CapabilityPresentationMode.IntentLaunch
        val allowPrefilledAutoReturn = context.startsImmediately &&
            !context.request.source.equals("intent_test", ignoreCase = true)
        val selectionQuestion = method.id == QuestionSelectOneMethod.id || method.id == QuestionSelectMultipleMethod.id
        val answerFocusRequester = remember { FocusRequester() }
        val keyboardController = LocalSoftwareKeyboardController.current
        val options = optionsText.split('\n', '|', ',').map { it.trim() }.filter { it.isNotBlank() }.distinct()

        LaunchedEffect(questionId, prompt, hint, required, regex, constraint, optionsText, exclusiveOptionsText, exclusiveGroupsText, min, max) {
            context.onSettingsChanged(
                mapOf(
                    "question_id" to questionId,
                    "prompt" to prompt,
                    "hint" to hint,
                    "required" to required.toString(),
                    "regex" to regex,
                    "constraint_message" to constraint,
                    "options" to optionsText,
                    "exclusive_options" to exclusiveOptionsText,
                    "exclusive_groups" to exclusiveGroupsText,
                    "min" to min,
                    "max" to max
                )
            )
        }

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
                "exclusive_options" to exclusiveOptionsText,
                "exclusive_groups" to exclusiveGroupsText,
                "min" to min,
                "max" to max
            )
            val request = method.request(method.id, requestContext, emptyList(), emptyList())
            val execution = method.result(request, method.evaluate(requestContext), context.request.invocationContext)
            val preview = OutputFormatter.fields(execution, includeProvenance = false)
            val error = preview[QuestionPrimitiveFields.ERROR]?.toString()?.takeIf { it.isNotBlank() }
            val succeeded = preview[QuestionPrimitiveFields.STATUS]?.toString() == "succeeded"
            status = error ?: preview[QuestionPrimitiveFields.STATUS]?.toString().orEmpty().ifBlank { "Captured." }
            if (succeeded || !intentPresentation) {
                result = execution
            }
        }

        LaunchedEffect(allowPrefilledAutoReturn, context.action.canonicalId) {
            if (allowPrefilledAutoReturn && !launched && initialAnswer.isNotBlank()) {
                launched = true
                capture()
            }
        }

        LaunchedEffect(intentPresentation, selectionQuestion, context.action.canonicalId) {
            if (intentPresentation && !selectionQuestion) {
                delay(250)
                runCatching {
                    answerFocusRequester.requestFocus()
                    keyboardController?.show()
                }
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
            if (intentPresentation) {
                if (selectionQuestion) {
                    Text(prompt, style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
                    if (hint.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(hint, style = androidx.compose.material3.MaterialTheme.typography.bodyLarge)
                    }
                }
                if (required) {
                    Spacer(Modifier.height(6.dp))
                    Text("Required", style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
                }
                Spacer(Modifier.height(18.dp))
            } else {
                Text("Configure question", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = questionId, onValueChange = { questionId = it }, label = { Text("Question ID") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = prompt, onValueChange = { prompt = it }, label = { Text("Prompt") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = hint, onValueChange = { hint = it }, label = { Text("Hint") }, modifier = Modifier.fillMaxWidth())
                OutlinedButton(onClick = { required = !required }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (required) "Required: yes" else "Required: no")
                }
                OutlinedTextField(value = regex, onValueChange = { regex = it }, label = { Text("Regex constraint (optional)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = constraint, onValueChange = { constraint = it }, label = { Text("Constraint message") }, modifier = Modifier.fillMaxWidth())
            }

            if (!intentPresentation && method.id == QuestionNumberMethod.id) {
                Row(Modifier.fillMaxWidth()) {
                    OutlinedTextField(value = min, onValueChange = { min = it.numericText() }, label = { Text("Min") }, modifier = Modifier.weight(1f))
                    Spacer(Modifier.padding(4.dp))
                    OutlinedTextField(value = max, onValueChange = { max = it.numericText() }, label = { Text("Max") }, modifier = Modifier.weight(1f))
                }
            }

            if (selectionQuestion) {
                if (!intentPresentation) {
                    OutlinedTextField(
                        value = optionsText,
                        onValueChange = { optionsText = it },
                        label = { Text("Options, separated by |, comma, or newline") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (method.id == QuestionSelectMultipleMethod.id) {
                        OutlinedTextField(
                            value = exclusiveOptionsText,
                            onValueChange = { exclusiveOptionsText = it },
                            label = { Text("Exclusive options, e.g. None|Unknown") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = exclusiveGroupsText,
                            onValueChange = { exclusiveGroupsText = it },
                            label = { Text("Mutually exclusive groups, one per line, e.g. Yes|No") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
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

            if (!intentPresentation || !selectionQuestion) {
                OutlinedTextField(
                    value = answer,
                    onValueChange = { answer = it },
                    label = { Text(if (intentPresentation) prompt else "Answer") },
                    placeholder = if (intentPresentation && hint.isNotBlank()) {
                        { Text(hint) }
                    } else {
                        null
                    },
                    keyboardOptions = if (method.id == QuestionNumberMethod.id) {
                        KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    } else {
                        KeyboardOptions.Default
                    },
                    singleLine = method.id == QuestionNumberMethod.id,
                    minLines = if (method.id == QuestionTextMethod.id) 4 else 1,
                    maxLines = if (method.id == QuestionTextMethod.id) 8 else 1,
                    modifier = if (intentPresentation && !selectionQuestion) {
                        Modifier.fillMaxWidth().focusRequester(answerFocusRequester)
                    } else {
                        Modifier.fillMaxWidth()
                    }
                )
            }
            Spacer(Modifier.height(10.dp))
            Button(onClick = { capture() }, modifier = Modifier.fillMaxWidth()) {
                Text(if (intentPresentation) "Continue" else "Capture answer")
            }
            if (status.isNotBlank() && status != "Ready.") {
                Spacer(Modifier.height(8.dp))
                Text(status)
            }
            if (!intentPresentation) {
                IntentExampleDropdown(
                    capabilityId = capabilityId,
                    examples = examples()
                )
            }
        }
    }

    private fun examples() = listOf(
        IntentExample(
            label = "Capture answer",
            description = "Open this question primitive with a prompt and return a flat result.",
            intentUri = "com.example.methodmesh.EXECUTE_METHOD(method_id='${method.id}',input_question_id='q1',input_prompt='Example question',input_required='true',return_mode='flat')"
        ),
        IntentExample(
            label = "Pre-filled answer",
            description = "Supply an answer directly for automated protocol testing.",
            intentUri = "com.example.methodmesh.EXECUTE_METHOD(method_id='${method.id}',input_question_id='q1',input_prompt='Example question',input_answer='test',return_mode='flat')"
        )
    )
}

private fun Map<String, String>.setting(key: String, fallback: String): String =
    this[key] ?: this["input_$key"] ?: fallback

private fun String.numericText(): String =
    filterIndexed { index, char -> char.isDigit() || char == '.' || (char == '-' && index == 0) }
