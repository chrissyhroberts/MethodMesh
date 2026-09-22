package com.example.methodmesh.modules.conversions

import com.example.methodmesh.core.methodmesh.*
import com.example.methodmesh.core.methodmesh.runtime.As100ExecutionEngine
import com.example.methodmesh.core.methodmesh.runtime.As100Method
import com.example.methodmesh.core.methodmesh.withInvocationContext
import com.example.methodmesh.settings.SettingsState
import org.json.JSONObject
import java.time.*
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToLong

object ConversionFields {
    const val STATUS = "conversion_status"
    const val VALUE = "conversion_value"
    const val UNIT = "conversion_unit"
    const val SUMMARY = "conversion_summary"
    const val METADATA_JSON = "conversion_metadata_json"
    const val ERROR = "conversion_error"
    val outputs = listOf(STATUS, VALUE, UNIT, SUMMARY, METADATA_JSON, ERROR)
}

object As100ConversionsMethod : As100Method {
    const val ID = "conversion.calculate"
    private const val VERSION = "0.1.0"
    override val id = ID
    override val ref = ArchitectureRef(ArchitectureId(ID), "Method", "Conversion and general calculation")
    override val descriptor = MethodDescriptor(
        id = ArchitectureId(ID), methodType = MethodObjectType.Calculation,
        name = "Conversions / General Calculator", version = VERSION,
        description = "Offline unit, percentage, ratio, date and geometry calculations.",
        outputs = ConversionFields.outputs, graphOutputs = listOf(ID),
        parameters = mapOf("category" to "Calculation", "status" to "Development")
    )
    override val contract = MethodContract(method = ref, producedKnowledgeTypes = listOf(KnowledgeObjectType.Observation), producedFields = descriptor.outputs, producedGraphOutputs = descriptor.graphOutputs)
    override fun request(action: String, context: Map<String, String>, signals: List<Signal>, inputs: List<ArchitectureRef>) =
        As100ExecutionEngine.request(action = action, method = ref, context = context, signals = signals, inputs = inputs)
    override fun execute(request: ExecutionRequest, settingsState: SettingsState?, transport: String?): ExecutionResult =
        result(request, calculate(request.context), InvocationContext.from(request.context))

    fun calculate(settings: Map<String, String>): Map<String, String> = runCatching {
        val category = settings.value("category") ?: "length"
        val value = settings.value("value")?.toDoubleOrNull() ?: 0.0
        val value2 = settings.value("value2")?.toDoubleOrNull() ?: 0.0
        val value3 = settings.value("value3")?.toDoubleOrNull() ?: 0.0
        val from = settings.value("from_unit") ?: defaultFrom(category)
        val to = settings.value("to_unit") ?: defaultTo(category)
        val operation = settings.value("operation") ?: defaultOperation(category)
        val date1 = settings.value("date1").orEmpty()
        val date2 = settings.value("date2").orEmpty()
        val shape = settings.value("shape") ?: "rectangle"
        val decimalPlaces = settings.value("decimal_places")?.toIntOrNull()?.coerceIn(0, 10) ?: 4
        if (category == "date_arithmetic") {
            val base = LocalDate.parse(date1)
            val amount = value.toLong()
            val calculated = when (operation) {
                "add_days" -> base.plusDays(amount)
                "add_weeks" -> base.plusWeeks(amount)
                "add_months" -> base.plusMonths(amount)
                "add_years" -> base.plusYears(amount)
                "subtract_days" -> base.minusDays(amount)
                else -> error("Unsupported date arithmetic operation: $operation")
            }
            val working = dateArithmeticWorking(date1, amount, operation, calculated)
            return@runCatching success(calculated.toString(), "date", working, category, operation, "", "", decimalPlaces)
        }
        val outcome = when (category) {
            "temperature" -> Pair(convertTemperature(value, from, to), to)
            "percentage" -> Pair(calculatePercentage(operation, value, value2), if (operation == "percent_of") "" else "%")
            "ratio" -> Pair(ratio(operation, value, value2, value3), "")
            "date_difference" -> Pair(dateDifference(date1, date2).toDouble(), "days")
            "age" -> Pair(age(date1, date2).toDouble(), "years")
            "geometry" -> geometry(shape, operation, value, value2)
            else -> Pair(convertLinear(category, value, from, to), to)
        }
        val rendered = format(outcome.first, decimalPlaces)
        val summary = workingSummary(
            category = category,
            operation = operation,
            shape = shape,
            value = value,
            value2 = value2,
            value3 = value3,
            from = from,
            to = to,
            date1 = date1,
            date2 = date2,
            result = rendered,
            resultUnit = outcome.second,
            decimalPlaces = decimalPlaces
        )
        success(rendered, outcome.second, summary, category, operation, from, to, decimalPlaces)
    }.getOrElse { failure(it.message ?: "Calculation failed.") }


    private fun workingSummary(
        category: String,
        operation: String,
        shape: String,
        value: Double,
        value2: Double,
        value3: Double,
        from: String,
        to: String,
        date1: String,
        date2: String,
        result: String,
        resultUnit: String,
        decimalPlaces: Int
    ): String = when (category) {
        "temperature" -> temperatureWorking(value, from, to, result, decimalPlaces)
        "percentage" -> percentageWorking(operation, value, value2, result, resultUnit, decimalPlaces)
        "ratio" -> ratioWorking(operation, value, value2, value3, result, decimalPlaces)
        "date_difference" -> "$date1 → $date2 = $result days"
        "age" -> "$date1 → ${date2.ifBlank { LocalDate.now().toString() }} = $result completed years"
        "geometry" -> geometryWorking(shape, operation, value, value2, result, resultUnit, decimalPlaces)
        else -> linearWorking(category, value, from, to, result, decimalPlaces)
    }

    private fun linearWorking(category: String, value: Double, from: String, to: String, result: String, decimalPlaces: Int): String {
        val units = unitsByCategory[category] ?: return "${format(value, decimalPlaces)} $from = $result $to"
        val fromFactor = units[from] ?: return "${format(value, decimalPlaces)} $from = $result $to"
        val toFactor = units[to] ?: return "${format(value, decimalPlaces)} $from = $result $to"
        val directFactor = fromFactor / toFactor
        return if (from == to) {
            "${format(value, decimalPlaces)} $from = $result $to"
        } else {
            "1 $from = ${format(directFactor, decimalPlaces)} $to; ${format(value, decimalPlaces)} × ${format(directFactor, decimalPlaces)} = $result $to"
        }
    }

    private fun temperatureWorking(value: Double, from: String, to: String, result: String, decimalPlaces: Int): String {
        val v = format(value, decimalPlaces)
        val fromLabel = temperatureUnitLabel(from)
        val toLabel = temperatureUnitLabel(to)
        return when (from to to) {
            "C" to "F" -> "($v × 9 ÷ 5) + 32 = $result $toLabel"
            "F" to "C" -> "($v − 32) × 5 ÷ 9 = $result $toLabel"
            "C" to "K" -> "$v + 273.15 = $result $toLabel"
            "K" to "C" -> "$v − 273.15 = $result $toLabel"
            "F" to "K" -> "(($v − 32) × 5 ÷ 9) + 273.15 = $result $toLabel"
            "K" to "F" -> "(($v − 273.15) × 9 ÷ 5) + 32 = $result $toLabel"
            else -> "$v $fromLabel = $result $toLabel"
        }
    }

    private fun percentageWorking(operation: String, a: Double, b: Double, result: String, resultUnit: String, decimalPlaces: Int): String {
        val av = format(a, decimalPlaces)
        val bv = format(b, decimalPlaces)
        val answer = if (resultUnit.isBlank()) result else "$result $resultUnit"
        return when (operation) {
            "percent_of" -> "$av% of $bv = ($av ÷ 100) × $bv = $answer"
            "what_percent" -> "$av ÷ $bv × 100 = $answer"
            "percent_change" -> "($bv − $av) ÷ |$av| × 100 = $answer"
            "increase_by_percent" -> "$av × (1 + $bv ÷ 100) = $answer"
            "decrease_by_percent" -> "$av × (1 − $bv ÷ 100) = $answer"
            else -> "$av, $bv = $answer"
        }
    }

    private fun ratioWorking(operation: String, a: Double, b: Double, c: Double, result: String, decimalPlaces: Int): String = when (operation) {
        "a_to_b" -> "${format(a, decimalPlaces)} ÷ ${format(b, decimalPlaces)} = $result"
        "solve_proportion" -> "${format(a, decimalPlaces)}:${format(b, decimalPlaces)} = ${format(c, decimalPlaces)}:X; X = (${format(b, decimalPlaces)} × ${format(c, decimalPlaces)}) ÷ ${format(a, decimalPlaces)} = $result"
        else -> "${format(a, decimalPlaces)} ÷ ${format(b, decimalPlaces)} = $result"
    }

    private fun geometryWorking(shape: String, operation: String, a: Double, b: Double, result: String, unit: String, decimalPlaces: Int): String {
        val av = format(a, decimalPlaces)
        val bv = format(b, decimalPlaces)
        val answer = if (unit.isBlank()) result else "$result $unit"
        return when (shape to operation) {
            "rectangle" to "area" -> "$av × $bv = $answer"
            "rectangle" to "perimeter" -> "2 × ($av + $bv) = $answer"
            "triangle" to "area" -> "($av × $bv) ÷ 2 = $answer"
            "circle" to "area" -> "π × $av² = $answer"
            "circle" to "circumference" -> "2 × π × $av = $answer"
            else -> "$operation = $answer"
        }
    }

    private fun dateArithmeticWorking(date: String, amount: Long, operation: String, result: LocalDate): String = when (operation) {
        "add_days" -> "$date + $amount days = $result"
        "add_weeks" -> "$date + $amount weeks = $result"
        "add_months" -> "$date + $amount months = $result"
        "add_years" -> "$date + $amount years = $result"
        "subtract_days" -> "$date − $amount days = $result"
        else -> "$date $operation $amount = $result"
    }

    private fun temperatureUnitLabel(unit: String): String = when (unit) {
        "C" -> "°C"
        "F" -> "°F"
        else -> unit
    }

    private fun convertLinear(category: String, value: Double, from: String, to: String): Double {
        val units = unitsByCategory[category] ?: error("Unsupported conversion category: $category")
        val fromFactor = units[from] ?: error("Unsupported $category unit: $from")
        val toFactor = units[to] ?: error("Unsupported $category unit: $to")
        return value * fromFactor / toFactor
    }

    private fun convertTemperature(v: Double, from: String, to: String): Double {
        val c = when (from) { "C" -> v; "F" -> (v - 32.0) * 5.0 / 9.0; "K" -> v - 273.15; else -> error("Unsupported temperature unit: $from") }
        return when (to) { "C" -> c; "F" -> c * 9.0 / 5.0 + 32.0; "K" -> c + 273.15; else -> error("Unsupported temperature unit: $to") }
    }

    private fun calculatePercentage(operation: String, a: Double, b: Double): Double = when (operation) {
        "percent_of" -> a / 100.0 * b
        "what_percent" -> if (b == 0.0) error("Reference value cannot be zero.") else a / b * 100.0
        "percent_change" -> if (a == 0.0) error("Starting value cannot be zero.") else (b - a) / abs(a) * 100.0
        "increase_by_percent" -> a * (1.0 + b / 100.0)
        "decrease_by_percent" -> a * (1.0 - b / 100.0)
        else -> error("Unsupported percentage operation: $operation")
    }

    private fun ratio(operation: String, a: Double, b: Double, c: Double): Double = when (operation) {
        "a_to_b" -> if (b == 0.0) error("Second value cannot be zero.") else a / b
        "solve_proportion" -> if (a == 0.0) error("A cannot be zero when solving A:B = C:X.") else b * c / a
        else -> if (b == 0.0) error("Second value cannot be zero.") else a / b
    }

    private fun dateDifference(a: String, b: String): Long {
        val d1 = LocalDate.parse(a)
        val d2 = LocalDate.parse(b)
        return ChronoUnit.DAYS.between(d1, d2)
    }

    private fun age(birth: String, at: String): Int {
        val born = LocalDate.parse(birth)
        val target = if (at.isBlank()) LocalDate.now() else LocalDate.parse(at)
        return Period.between(born, target).years
    }

    private fun geometry(shape: String, operation: String, a: Double, b: Double): Pair<Double, String> = when (shape) {
        "rectangle" -> when (operation) { "area" -> Pair(a * b, "square units"); "perimeter" -> Pair(2.0 * (a + b), "units"); else -> error("Rectangle supports area or perimeter.") }
        "triangle" -> when (operation) { "area" -> Pair(a * b / 2.0, "square units"); else -> error("Triangle v0.1.0 supports area using base and height.") }
        "circle" -> when (operation) { "area" -> Pair(PI * a * a, "square units"); "circumference" -> Pair(2.0 * PI * a, "units"); else -> error("Circle supports area or circumference; value is radius.") }
        else -> error("Unsupported geometry shape: $shape")
    }

    private fun success(value: String, unit: String, summary: String, category: String, operation: String, from: String, to: String, decimalPlaces: Int): Map<String, String> {
        val metadata = JSONObject().apply { put("category", category); put("operation", operation); put("from_unit", from); put("to_unit", to); put("decimal_places", decimalPlaces); put("offline", true) }
        return linkedMapOf(ConversionFields.STATUS to "succeeded", ConversionFields.VALUE to value, ConversionFields.UNIT to unit, ConversionFields.SUMMARY to summary, ConversionFields.METADATA_JSON to metadata.toString(), ConversionFields.ERROR to "")
    }
    private fun failure(error: String) = linkedMapOf(ConversionFields.STATUS to "failed", ConversionFields.VALUE to "", ConversionFields.UNIT to "", ConversionFields.SUMMARY to "", ConversionFields.METADATA_JSON to "{}", ConversionFields.ERROR to error)

    fun result(request: ExecutionRequest, values: Map<String, String>, invocation: InvocationContext?): ExecutionResult {
        val ok = values[ConversionFields.STATUS] == "succeeded"
        val observation = Observation(phenomenon = ID, subject = null, values = values, temporalContext = request.temporalContext, provenance = ProvenanceContext("methodmesh.conversions", ID, VERSION))
        val transformation = Transformation(action = ID, method = ref, outputs = listOf(ArchitectureRef(observation.id, observation.objectType, observation.phenomenon)), status = if (ok) TransformationStatus.Succeeded else TransformationStatus.Failed, temporalContext = request.temporalContext, provenance = ProvenanceContext("methodmesh.conversions", ID, VERSION))
        return As100ExecutionEngine.complete(request, if (ok) TransformationStatus.Succeeded else TransformationStatus.Failed, observations = listOf(observation), transformations = listOf(transformation), diagnostics = if (ok) emptyMap() else mapOf(ConversionFields.ERROR to values[ConversionFields.ERROR].orEmpty())).withInvocationContext(invocation)
    }

    private fun Map<String, String>.value(key: String) = (this[key] ?: this["input_$key"])?.takeIf { it.isNotBlank() }
    private fun defaultFrom(category: String) = when (category) { "temperature" -> "C"; else -> unitsByCategory[category]?.keys?.firstOrNull() ?: "" }
    private fun defaultTo(category: String) = when (category) { "temperature" -> "F"; else -> unitsByCategory[category]?.keys?.drop(1)?.firstOrNull() ?: defaultFrom(category) }
    private fun defaultOperation(category: String) = when (category) { "percentage" -> "percent_of"; "ratio" -> "a_to_b"; "geometry" -> "area"; "date_arithmetic" -> "add_days"; else -> "convert" }
    private fun format(v: Double, decimalPlaces: Int): String {
        val places = decimalPlaces.coerceIn(0, 10)
        return String.format(Locale.ROOT, "%.${places}f", v).let { rendered ->
            if (places == 0) rendered else rendered.trimEnd('0').trimEnd('.')
        }
    }

    val unitsByCategory = linkedMapOf(
        "length" to linkedMapOf("m" to 1.0, "km" to 1000.0, "cm" to 0.01, "mm" to 0.001, "in" to 0.0254, "ft" to 0.3048, "yd" to 0.9144, "mi" to 1609.344),
        "area" to linkedMapOf("m2" to 1.0, "km2" to 1_000_000.0, "cm2" to 0.0001, "ft2" to 0.09290304, "acre" to 4046.8564224, "hectare" to 10000.0),
        "volume" to linkedMapOf("L" to 1.0, "mL" to 0.001, "m3" to 1000.0, "cm3" to 0.001, "US_gal" to 3.785411784, "UK_gal" to 4.54609, "US_fl_oz" to 0.0295735295625),
        "mass" to linkedMapOf("kg" to 1.0, "g" to 0.001, "mg" to 0.000001, "lb" to 0.45359237, "oz" to 0.028349523125),
        "speed" to linkedMapOf("m/s" to 1.0, "km/h" to 0.2777777777778, "mph" to 0.44704, "knot" to 0.514444444444),
        "pressure" to linkedMapOf("Pa" to 1.0, "kPa" to 1000.0, "bar" to 100000.0, "psi" to 6894.757293168, "mmHg" to 133.322387415),
        "energy" to linkedMapOf("J" to 1.0, "kJ" to 1000.0, "Wh" to 3600.0, "kWh" to 3_600_000.0, "cal" to 4.184, "kcal" to 4184.0),
        "power" to linkedMapOf("W" to 1.0, "kW" to 1000.0, "MW" to 1_000_000.0, "hp" to 745.699871582),
        "angle" to linkedMapOf("rad" to 1.0, "deg" to PI / 180.0, "grad" to PI / 200.0),
        "data_size" to linkedMapOf("B" to 1.0, "KB" to 1000.0, "MB" to 1_000_000.0, "GB" to 1_000_000_000.0, "KiB" to 1024.0, "MiB" to 1_048_576.0, "GiB" to 1_073_741_824.0)
    )
}
