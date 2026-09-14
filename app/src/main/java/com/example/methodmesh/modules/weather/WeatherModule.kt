package com.example.methodmesh.modules.weather

import androidx.compose.runtime.Composable
import com.example.methodmesh.core.methodmesh.ExecutionResult
import com.example.methodmesh.core.methodmesh.runtime.As100Method
import com.example.methodmesh.modules.MethodMeshModule
import com.example.methodmesh.modules.RilBinding
import com.example.methodmesh.settings.MethodSetting
import com.example.methodmesh.transport.workflow.ui.CapabilityHostPresentation
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenContext
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec

object WeatherModule : MethodMeshModule {
    override val moduleId = "weather"
    override val displayName = "Weather"
    override val summary = "Meteorological dashboard, radar, rainfall, detailed forecast and research-ready weather capture."
    override val iconKey = "weather"

    override fun as100Methods(): List<As100Method> = listOf(
        As100WeatherDashboardMethod,
        As100WeatherConditionsMethod,
        As100WeatherForecastMethod,
        As100WeatherPrecipitationMethod,
        As100WeatherRadarMethod,
        As100WeatherMeteogramMethod,
        As100WeatherWindMethod,
        As100WeatherAtmosphereMethod,
        As100WeatherSunMethod,
        As100WeatherSnapshotMethod,
        As100WeatherModelCompareMethod
    )

    override fun rilBindings() = listOf(
        RilBinding("open weather", As100WeatherDashboardMethod.id, "Open the Weather dashboard"),
        RilBinding("check weather", As100WeatherConditionsMethod.id, "Get current weather conditions"),
        RilBinding("weather forecast", As100WeatherForecastMethod.id, "Inspect the weather forecast"),
        RilBinding("check rainfall", As100WeatherPrecipitationMethod.id, "Inspect precipitation timing and amount"),
        RilBinding("open weather radar", As100WeatherRadarMethod.id, "Inspect observed weather radar"),
        RilBinding("open meteogram", As100WeatherMeteogramMethod.id, "Inspect detailed meteorological time series"),
        RilBinding("check wind", As100WeatherWindMethod.id, "Inspect wind and gusts"),
        RilBinding("inspect atmosphere", As100WeatherAtmosphereMethod.id, "Inspect pressure-level meteorology"),
        RilBinding("check sun and uv", As100WeatherSunMethod.id, "Inspect sunrise, sunset and UV"),
        RilBinding("capture weather snapshot", As100WeatherSnapshotMethod.id, "Capture research weather context at a timestamp"),
        RilBinding("compare weather models", As100WeatherModelCompareMethod.id, "Inspect ensemble spread")
    )

    override fun capabilityScreens(): List<CapabilityScreenSpec> = screens

    override fun capabilitySettings(): Map<String, List<MethodSetting>> = mapOf(
        As100WeatherDashboardMethod.id to commonLocation() + listOf(
            float("threshold_mm_per_hour", "Meaningful rain threshold", 0.2f, 0f, 50f, "mm/h"),
            offline()
        ),
        As100WeatherConditionsMethod.id to commonLocation() + listOf(
            text("target_time_iso", "Target time", "Optional ISO-8601 UTC time; blank means current."),
            offline()
        ),
        As100WeatherForecastMethod.id to commonLocation() + listOf(
            text("target_time_iso", "Selected time", "Optional ISO-8601 UTC time."),
            int("horizon_hours", "Forecast horizon", 168, 1, 384, "h"),
            offline()
        ),
        As100WeatherPrecipitationMethod.id to commonLocation() + listOf(
            text("target_time_iso", "Window start", "Optional ISO-8601 UTC time; blank begins at current provider time."),
            int("horizon_hours", "Look-ahead", 24, 1, 384, "h"),
            float("threshold_mm_per_hour", "Meaningful rain threshold", 0.2f, 0f, 50f, "mm/h"),
            offline()
        ),
        As100WeatherRadarMethod.id to commonLocation() + listOf(
            text("frame_time_iso", "Radar frame time", "Optional ISO-8601 UTC time; blank selects newest available frame."),
            float("zoom", "Initial radar zoom", 5f, 1f, 7f, ""),
            offline()
        ),
        As100WeatherMeteogramMethod.id to commonLocation() + listOf(
            text("target_time_iso", "Selected time", "Optional ISO-8601 UTC time."),
            int("horizon_hours", "Meteogram horizon", 72, 1, 384, "h"),
            offline()
        ),
        As100WeatherWindMethod.id to commonLocation() + listOf(
            text("target_time_iso", "Selected time", "Optional ISO-8601 UTC time."),
            offline()
        ),
        As100WeatherAtmosphereMethod.id to commonLocation() + listOf(
            text("target_time_iso", "Selected time", "Optional ISO-8601 UTC time."),
            offline()
        ),
        As100WeatherSunMethod.id to commonLocation() + listOf(
            text("target_time_iso", "Selected date/time", "Optional ISO-8601 UTC time."),
            offline()
        ),
        As100WeatherSnapshotMethod.id to commonLocation() + listOf(
            text("target_time_iso", "Event timestamp", "ISO-8601 UTC timestamp for the research event."),
            choice("source_policy", "Source policy", "best_available", listOf("best_available", "reanalysis", "historical_forecast", "forecast"))
        ),
        As100WeatherModelCompareMethod.id to commonLocation() + listOf(
            text("target_time_iso", "Selected time", "Optional ISO-8601 UTC time; blank uses the nearest ensemble step.")
        )
    )

    private val screens = as100Methods().map { method ->
        object : CapabilityScreenSpec {
            override val capabilityId = method.id
            override val title = when(method.id) {
                "weather.dashboard" -> "Weather"
                "weather.conditions" -> "Conditions"
                "weather.forecast" -> "Forecast"
                "weather.precipitation" -> "Precipitation"
                "weather.radar" -> "Radar"
                "weather.meteogram" -> "Meteogram"
                "weather.wind" -> "Wind"
                "weather.atmosphere" -> "Atmosphere"
                "weather.sun" -> "Sun & UV"
                "weather.snapshot" -> "Weather snapshot"
                else -> "Model comparison"
            }
            override val description = method.descriptor.description.orEmpty()
            override val hostPresentation = CapabilityHostPresentation.Immersive

            @Composable
            override fun Render(
                context: CapabilityScreenContext,
                onBack: () -> Unit,
                onConfirmed: (ExecutionResult) -> Unit,
                onCancel: () -> Unit
            ) {
                if (method.id == As100WeatherDashboardMethod.id) {
                    WeatherDashboardScreen(context,onBack,onConfirmed,onCancel)
                } else {
                    WeatherToolScreen(method as WeatherMethodBase,context,onBack,onConfirmed,onCancel)
                }
            }
        }
    }

    private fun commonLocation() = listOf(
        text("latitude","Latitude","Leave blank to acquire current device location.","Location"),
        text("longitude","Longitude","Leave blank to acquire current device location.","Location")
    )
    private fun text(id:String,label:String,description:String?=null,group:String?=null)=
        MethodSetting.TextSetting(id=id,label=label,description=description,group=group,defaultValue="")
    private fun int(id:String,label:String,default:Int,min:Int,max:Int,unit:String?=null)=
        MethodSetting.IntSetting(id=id,label=label,defaultValue=default,minimum=min,maximum=max,unit=unit)
    private fun float(id:String,label:String,default:Float,min:Float,max:Float,unit:String)=
        MethodSetting.FloatSetting(id=id,label=label,defaultValue=default,minimum=min,maximum=max,step=0.1f,unit=unit,decimals=1)
    private fun choice(id:String,label:String,default:String,choices:List<String>)=
        MethodSetting.ChoiceSetting(id=id,label=label,defaultValue=default,choices=choices)
    private fun offline()=MethodSetting.BooleanSetting(
        id="offline_only",label="Use cache only",description="Do not attempt a network refresh.",group="Advanced",defaultValue=false
    )
}
