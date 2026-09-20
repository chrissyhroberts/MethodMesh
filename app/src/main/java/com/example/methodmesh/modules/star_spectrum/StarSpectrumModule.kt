package com.example.methodmesh.modules.star_spectrum

import com.example.methodmesh.modules.MethodMeshModule
import com.example.methodmesh.modules.RilBinding
import com.example.methodmesh.settings.MethodSetting

object StarSpectrumModule : MethodMeshModule {
    override val moduleId = "star_spectrum"
    override val displayName = "Star spectrum analyser"
    override val summary = "Trace slitless stellar spectra from images with Horne-style optimal extraction, robust two-sided background subtraction, visible extraction QA, automatic consensus A-star wavelength calibration with manual fallback, reusable references, feature detection, and analysis-ready exports."
    override val iconKey = "astronomy"

    override fun as100Methods() = listOf(
        As100StarSpectrumAnalyseMethod,
        As100StarSpectrumReferenceMethod
    )

    override fun capabilityScreens() = listOf(
        StarSpectrumAnalyseCapabilityScreen,
        StarSpectrumReferenceCapabilityScreen
    )

    override fun rilBindings() = listOf(
        RilBinding(
            phrase = "analyse star spectrum",
            actionId = As100StarSpectrumAnalyseMethod.ID,
            description = "Trace, calibrate and analyse a stellar spectrum from an image"
        ),
        RilBinding(
            phrase = "create star spectrum reference",
            actionId = As100StarSpectrumReferenceMethod.ID,
            description = "Automatically solve and save a reusable wavelength calibration from an A-type reference star"
        )
    )

    override fun capabilitySettings() = mapOf(
        As100StarSpectrumAnalyseMethod.ID to listOf(
            MethodSetting.TextSetting(
                "source_image_uri",
                "Spectrum image",
                "Optional image URI supplied by ODK, a preset or another MethodMesh step. Leave blank for the native file picker.",
                "Input",
                ""
            ),
            MethodSetting.TextSetting(
                "reference_id",
                "Wavelength reference",
                "Optional ID of a saved spectrum reference. Leave blank to analyse in pixel coordinates.",
                "Calibration",
                ""
            ),
            MethodSetting.FloatSetting(
                "registration_offset_px",
                "Registration offset",
                "Shift the saved reference solution along the traced spectrum for this observation.",
                "Calibration",
                0f,
                -250f,
                250f,
                0.5f,
                "px",
                1
            ),
            MethodSetting.IntSetting(
                "ribbon_half_width_px",
                "Trace search ribbon",
                "Half-width of the area searched perpendicular to the line you draw.",
                "Extraction",
                28,
                6,
                160,
                2,
                "px"
            ),
            MethodSetting.IntSetting(
                "aperture_half_width_px",
                "Extraction aperture",
                "Half-width of the central target-star extraction aperture.",
                "Extraction",
                5,
                1,
                40,
                1,
                "px"
            ),
            MethodSetting.IntSetting(
                "background_gap_px",
                "Background gap",
                "Gap between the target aperture and local background sidebands.",
                "Extraction",
                4,
                1,
                40,
                1,
                "px"
            ),
            MethodSetting.IntSetting(
                "continuum_window",
                "Continuum window",
                "Rolling robust window used to estimate the slowly varying spectral continuum.",
                "Feature detection",
                101,
                9,
                401,
                2,
                "samples"
            ),
            MethodSetting.FloatSetting(
                "detection_sigma",
                "Feature threshold",
                "Local robust significance threshold for emission or absorption feature candidates.",
                "Feature detection",
                3.5f,
                2f,
                10f,
                0.25f,
                "σ",
                2
            ),
            MethodSetting.ChoiceSetting(
                "detection_mode",
                "Feature type",
                "Detect emission features, absorption features, or both.",
                "Feature detection",
                "both",
                listOf("both", "emission", "absorption")
            )
        ),
        As100StarSpectrumReferenceMethod.ID to listOf(
            MethodSetting.TextSetting(
                "source_image_uri",
                "Reference-star image",
                "Optional image URI supplied by ODK, a preset or another MethodMesh step. Leave blank for the native file picker.",
                "Input",
                ""
            ),
            MethodSetting.TextSetting(
                "reference_name",
                "Reference name",
                "A memorable name for this reusable instrument/reference-star wavelength solution.",
                "Reference",
                "A-star reference"
            ),
            MethodSetting.TextSetting(
                "star_name",
                "Star name",
                "Name of the reference star, for example Vega or Sirius.",
                "Reference",
                ""
            ),
            MethodSetting.TextSetting(
                "spectral_type",
                "Spectral type",
                "Reference-star spectral type. A0V is a useful default for strong Balmer absorption lines.",
                "Reference",
                "A0V"
            ),
            MethodSetting.ChoiceSetting(
                "polynomial_order",
                "Calibration model",
                "Automatic consensus first proposes Balmer anchors. Linear needs at least two anchors; quadratic needs at least three.",
                "Calibration",
                "2",
                listOf("1", "2")
            ),
            MethodSetting.TextSetting(
                "calibration_anchors_json",
                "Calibration anchors",
                "Optional advanced JSON array of distance_px/wavelength_nm anchors. Native use normally proposes anchors automatically and keeps the chart for review/fallback.",
                "Calibration",
                ""
            ),
            MethodSetting.IntSetting(
                "ribbon_half_width_px",
                "Trace search ribbon",
                "Half-width of the area searched perpendicular to the line you draw.",
                "Extraction",
                28,
                6,
                160,
                2,
                "px"
            ),
            MethodSetting.IntSetting(
                "aperture_half_width_px",
                "Extraction aperture",
                "Half-width of the central reference-star extraction aperture.",
                "Extraction",
                5,
                1,
                40,
                1,
                "px"
            ),
            MethodSetting.IntSetting(
                "background_gap_px",
                "Background gap",
                "Gap between the reference-star aperture and local background sidebands.",
                "Extraction",
                4,
                1,
                40,
                1,
                "px"
            )
        )
    )
}
