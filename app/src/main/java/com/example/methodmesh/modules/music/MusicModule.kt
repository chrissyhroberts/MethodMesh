package com.example.methodmesh.modules.music

import com.example.methodmesh.modules.MethodMeshModule
import com.example.methodmesh.modules.RilBinding
import com.example.methodmesh.settings.MethodSetting
import com.example.methodmesh.core.methodmesh.runtime.As100Method
import com.example.methodmesh.transport.workflow.ui.CapabilityScreenSpec

object MusicModule : MethodMeshModule {
    override val moduleId = "music"
    override val displayName = "Music"
    override val summary = "Tempo, pitch, harmony, rhythm creation, looping, beat making, composition, practice and live performance tools."

    private val canonicalMethods: List<As100Method> = listOf(
        As100TapTempoMethod,
        As100MetronomeMethod,
        As100NoteFrequencyMethod,
        As100TransposeMethod,
        As100IntervalMethod,
        As100ChordMethod,
        As100ChordGuideMethod,
        As100TempoConvertMethod,
        As100DelayTimeMethod,
        As100TemperamentMethod,
        As100PolyrhythmMethod,
        As100TempoTrainerMethod,
        As100HarmonicsMethod,
        As100SetListTimingMethod,
        As100MusicReferenceMethod,
        As100PracticeDashboardMethod,
        As100PerformanceDashboardMethod,
        As100ReferenceDashboardMethod,
        As100RhythmCaptureMethod,
        As100EuclideanRhythmMethod,
        As100PatternMutationMethod,
        As100ProbabilityPatternMethod,
        As100ChordProgressionMethod,
        As100ArpeggiatorMethod,
        As100BasslineMethod,
        As100MelodySequencerMethod,
        As100MotifGeneratorMethod,
        As100DrumMachineMethod,
        As100BeatPadsMethod,
        As100LiveLooperMethod,
        As100SongSketchMethod,
        As100JamDashboardMethod,
        As100SongSketchDashboardMethod
    )

    override fun as100Methods(): List<As100Method> = canonicalMethods

    override fun rilBindings() = listOf(
        RilBinding("tap tempo", As100TapTempoMethod.ID, "Tap repeatedly to estimate BPM"),
        RilBinding("run metronome", As100MetronomeMethod.ID, "Run an audio, visual and haptic metronome"),
        RilBinding("convert note frequency", As100NoteFrequencyMethod.ID, "Convert between scientific pitch notation and frequency"),
        RilBinding("transpose music", As100TransposeMethod.ID, "Transpose notes or chord symbols"),
        RilBinding("identify interval", As100IntervalMethod.ID, "Identify the interval between two notes"),
        RilBinding("identify chord", As100ChordMethod.ID, "Build or identify a chord"),
        RilBinding("show chord shape", As100ChordGuideMethod.ID, "Show a compact guitar chord fingering"),
        RilBinding("convert tempo", As100TempoConvertMethod.ID, "Convert BPM to note durations"),
        RilBinding("calculate delay time", As100DelayTimeMethod.ID, "Calculate tempo-synchronised delay time"),
        RilBinding("calculate temperament", As100TemperamentMethod.ID, "Calculate an equal-temperament step"),
        RilBinding("generate polyrhythm", As100PolyrhythmMethod.ID, "Generate an A:B polyrhythm cycle"),
        RilBinding("train tempo", As100TempoTrainerMethod.ID, "Generate a stepped practice tempo progression"),
        RilBinding("explore harmonics", As100HarmonicsMethod.ID, "Calculate a harmonic series"),
        RilBinding("time set list", As100SetListTimingMethod.ID, "Calculate total performance duration"),
        RilBinding("music reference", As100MusicReferenceMethod.ID, "Show scale notes and diatonic triads"),
        RilBinding("open practice dashboard", As100PracticeDashboardMethod.ID, "Open the live music practice dashboard"),
        RilBinding("open performance dashboard", As100PerformanceDashboardMethod.ID, "Open the persistent set-list dashboard"),
        RilBinding("open music reference dashboard", As100ReferenceDashboardMethod.ID, "Open the key, scale and chord reference dashboard"),
        RilBinding("tap a rhythm", As100RhythmCaptureMethod.ID, "Capture and quantise a tapped rhythm"),
        RilBinding("generate euclidean rhythm", As100EuclideanRhythmMethod.ID, "Distribute rhythmic hits evenly over a grid"),
        RilBinding("mutate beat", As100PatternMutationMethod.ID, "Create a variation of a rhythmic pattern"),
        RilBinding("probability beat", As100ProbabilityPatternMethod.ID, "Realise a seeded probability rhythm"),
        RilBinding("build chord progression", As100ChordProgressionMethod.ID, "Build or generate a diatonic chord progression"),
        RilBinding("arpeggiate chord", As100ArpeggiatorMethod.ID, "Turn a chord into an arpeggio"),
        RilBinding("generate bassline", As100BasslineMethod.ID, "Generate a bassline from chord symbols"),
        RilBinding("make melody", As100MelodySequencerMethod.ID, "Create a scale-locked melody from degrees"),
        RilBinding("vary motif", As100MotifGeneratorMethod.ID, "Generate transformations of a melodic motif"),
        RilBinding("open drum machine", As100DrumMachineMethod.ID, "Open the synthesized step sequencer"),
        RilBinding("open beat pads", As100BeatPadsMethod.ID, "Finger drum and capture a hit sequence"),
        RilBinding("open live looper", As100LiveLooperMethod.ID, "Record a master phrase and overdub loop layers"),
        RilBinding("make song sketch", As100SongSketchMethod.ID, "Combine rhythm, harmony, melody and structure into a sketch"),
        RilBinding("open jam dashboard", As100JamDashboardMethod.ID, "Open the live music creation dashboard"),
        RilBinding("open song sketch dashboard", As100SongSketchDashboardMethod.ID, "Open the persistent composition sketch dashboard")
    )

    private val canonicalScreens: List<CapabilityScreenSpec> = listOf(
        TapTempoCapabilityScreen,
        MetronomeCapabilityScreen,
        NoteFrequencyCapabilityScreen,
        TransposeCapabilityScreen,
        IntervalCapabilityScreen,
        ChordCapabilityScreen,
        ChordGuideCapabilityScreen,
        TempoConvertCapabilityScreen,
        DelayTimeCapabilityScreen,
        TemperamentCapabilityScreen,
        PolyrhythmCapabilityScreen,
        TempoTrainerCapabilityScreen,
        HarmonicsCapabilityScreen,
        SetListTimingCapabilityScreen,
        MusicReferenceCapabilityScreen,
        PracticeDashboardCapabilityScreen,
        PerformanceDashboardCapabilityScreen,
        ReferenceDashboardCapabilityScreen,
        RhythmCaptureCapabilityScreen,
        EuclideanRhythmCapabilityScreen,
        PatternMutationCapabilityScreen,
        ProbabilityPatternCapabilityScreen,
        ChordProgressionCapabilityScreen,
        ArpeggiatorCapabilityScreen,
        BasslineCapabilityScreen,
        MelodySequencerCapabilityScreen,
        MotifGeneratorCapabilityScreen,
        DrumMachineCapabilityScreen,
        BeatPadsCapabilityScreen,
        LiveLooperCapabilityScreen,
        SongSketchCapabilityScreen,
        JamDashboardCapabilityScreen,
        SongSketchDashboardCapabilityScreen
    )

    override fun capabilityScreens(): List<CapabilityScreenSpec> = canonicalScreens

    /** Module-local contract check used by migration QA; shared surfaces still derive from canonical metadata. */
    internal fun contractParityIssues(): List<String> {
        val issues = mutableListOf<String>()
        val methodIds = canonicalMethods.map { it.id }
        val screenIds = canonicalScreens.map { it.capabilityId }
        if (methodIds.size != methodIds.toSet().size) issues += "Duplicate method IDs"
        if (screenIds.size != screenIds.toSet().size) issues += "Duplicate capability screen IDs"
        val missingScreens = methodIds.toSet() - screenIds.toSet()
        val orphanScreens = screenIds.toSet() - methodIds.toSet()
        if (missingScreens.isNotEmpty()) issues += "Methods missing native screens: ${missingScreens.sorted().joinToString()}"
        if (orphanScreens.isNotEmpty()) issues += "Screens without methods: ${orphanScreens.sorted().joinToString()}"
        val settingsIds = capabilitySettings().keys
        val missingSettingsEntries = methodIds.toSet() - settingsIds
        val orphanSettingsEntries = settingsIds - methodIds.toSet()
        if (missingSettingsEntries.isNotEmpty()) issues += "Methods missing settings declarations: ${missingSettingsEntries.sorted().joinToString()}"
        if (orphanSettingsEntries.isNotEmpty()) issues += "Settings declared for unknown methods: ${orphanSettingsEntries.sorted().joinToString()}"
        return issues
    }

    override fun capabilitySettings() = mapOf(
        As100TapTempoMethod.ID to listOf(
            MethodSetting.TextSetting("tap_intervals_ms", "Tap intervals", "Optional comma-separated millisecond intervals for ODK/protocol use.", defaultValue = "")
        ),
        As100MetronomeMethod.ID to listOf(
            MethodSetting.FloatSetting("bpm", "Tempo", defaultValue = 120f, minimum = 20f, maximum = 400f, step = 1f, unit = "BPM", decimals = 1),
            MethodSetting.IntSetting("beats_per_bar", "Beats per bar", defaultValue = 4, minimum = 1, maximum = 16, step = 1),
            MethodSetting.ChoiceSetting("subdivision", "Subdivision", defaultValue = "quarter", choices = subdivisionChoices),
            MethodSetting.TextSetting("accent_pattern", "Accent beats", "Comma-separated beat numbers; 1 accents the downbeat.", defaultValue = "1"),
            MethodSetting.BooleanSetting("audio_enabled", "Audio click", defaultValue = true),
            MethodSetting.BooleanSetting("visual_enabled", "Visual pulse", defaultValue = true),
            MethodSetting.BooleanSetting("haptic_enabled", "Haptic pulse", defaultValue = false)
        ),
        As100NoteFrequencyMethod.ID to listOf(
            MethodSetting.ChoiceSetting("mode", "Conversion", defaultValue = "note_to_frequency", choices = listOf("note_to_frequency", "frequency_to_note")),
            MethodSetting.TextSetting("note", "Note", "Scientific pitch notation, e.g. A4 or C#3.", defaultValue = "A4"),
            MethodSetting.FloatSetting("frequency_hz", "Frequency", defaultValue = 440f, minimum = 0.01f, maximum = 50000f, step = 0.1f, unit = "Hz", decimals = 3),
            MethodSetting.FloatSetting("reference_a4_hz", "Reference A4", defaultValue = 440f, minimum = 400f, maximum = 480f, step = 0.1f, unit = "Hz", decimals = 1),
            MethodSetting.BooleanSetting("prefer_flats", "Prefer flat note names", defaultValue = false)
        ),
        As100TransposeMethod.ID to listOf(
            MethodSetting.TextSetting("input", "Notes / chords", defaultValue = "C G Am F"),
            MethodSetting.ChoiceSetting("input_type", "Input type", defaultValue = "chords", choices = listOf("chords", "notes")),
            MethodSetting.IntSetting("semitones", "Semitones", defaultValue = 0, minimum = -24, maximum = 24, step = 1),
            MethodSetting.TextSetting("from_key", "From key", "Optional; when both keys are supplied they determine the shift.", defaultValue = ""),
            MethodSetting.TextSetting("to_key", "To key", defaultValue = ""),
            MethodSetting.BooleanSetting("prefer_flats", "Prefer flat note names", defaultValue = false)
        ),
        As100IntervalMethod.ID to listOf(
            MethodSetting.TextSetting("note_a", "First note", defaultValue = "C4"),
            MethodSetting.TextSetting("note_b", "Second note", defaultValue = "G4")
        ),
        As100ChordMethod.ID to listOf(
            MethodSetting.ChoiceSetting("mode", "Mode", defaultValue = "build", choices = listOf("build", "identify")),
            MethodSetting.TextSetting("root", "Root", defaultValue = "C"),
            MethodSetting.ChoiceSetting("quality", "Chord quality", defaultValue = "", choices = chordQualities),
            MethodSetting.TextSetting("notes", "Notes", "Space- or comma-separated notes for identification.", defaultValue = "C E G"),
            MethodSetting.BooleanSetting("prefer_flats", "Prefer flat note names", defaultValue = false)
        ),
        As100ChordGuideMethod.ID to listOf(
            MethodSetting.ChoiceSetting("instrument", "Instrument", defaultValue = "guitar", choices = listOf("guitar")),
            MethodSetting.TextSetting("chord", "Chord", defaultValue = "C")
        ),
        As100TempoConvertMethod.ID to tempoSubdivisionSettings(),
        As100DelayTimeMethod.ID to tempoSubdivisionSettings(defaultSubdivision = "dotted_eighth"),
        As100TemperamentMethod.ID to listOf(
            MethodSetting.IntSetting("divisions", "Equal divisions of octave", defaultValue = 12, minimum = 1, maximum = 120, step = 1),
            MethodSetting.IntSetting("step", "Step", defaultValue = 0, minimum = -240, maximum = 240, step = 1),
            MethodSetting.FloatSetting("reference_hz", "Reference frequency", defaultValue = 440f, minimum = 0.01f, maximum = 50000f, step = 0.1f, unit = "Hz", decimals = 3),
            MethodSetting.IntSetting("reference_step", "Reference step", defaultValue = 0, minimum = -240, maximum = 240, step = 1)
        ),
        As100PolyrhythmMethod.ID to listOf(
            MethodSetting.IntSetting("ratio_a", "Pulse count A", defaultValue = 3, minimum = 1, maximum = 32, step = 1),
            MethodSetting.IntSetting("ratio_b", "Pulse count B", defaultValue = 2, minimum = 1, maximum = 32, step = 1),
            MethodSetting.FloatSetting("bpm", "Cycle tempo", defaultValue = 60f, minimum = 10f, maximum = 300f, step = 1f, unit = "BPM", decimals = 1),
            MethodSetting.BooleanSetting("audio_enabled", "Audio", defaultValue = true),
            MethodSetting.BooleanSetting("haptic_enabled", "Haptics", defaultValue = false)
        ),
        As100TempoTrainerMethod.ID to listOf(
            MethodSetting.FloatSetting("start_bpm", "Start tempo", defaultValue = 80f, minimum = 20f, maximum = 400f, step = 1f, unit = "BPM", decimals = 1),
            MethodSetting.FloatSetting("target_bpm", "Target tempo", defaultValue = 120f, minimum = 20f, maximum = 400f, step = 1f, unit = "BPM", decimals = 1),
            MethodSetting.FloatSetting("increment_bpm", "Increment", defaultValue = 2f, minimum = 0.1f, maximum = 50f, step = 0.5f, unit = "BPM", decimals = 1),
            MethodSetting.IntSetting("bars_per_step", "Bars per step", defaultValue = 4, minimum = 1, maximum = 64, step = 1)
        ),
        As100HarmonicsMethod.ID to listOf(
            MethodSetting.FloatSetting("fundamental_hz", "Fundamental", defaultValue = 110f, minimum = 0.01f, maximum = 20000f, step = 0.1f, unit = "Hz", decimals = 3),
            MethodSetting.IntSetting("count", "Harmonics", defaultValue = 8, minimum = 1, maximum = 64, step = 1),
            MethodSetting.FloatSetting("reference_a4_hz", "Reference A4", defaultValue = 440f, minimum = 400f, maximum = 480f, step = 0.1f, unit = "Hz", decimals = 1),
            MethodSetting.BooleanSetting("prefer_flats", "Prefer flats", defaultValue = false)
        ),
        As100SetListTimingMethod.ID to listOf(
            MethodSetting.TextSetting("durations", "Song durations", "Comma-separated mm:ss values.", defaultValue = "3:30,4:00,3:45"),
            MethodSetting.TextSetting("gaps", "Gaps", "Optional comma-separated gap durations.", defaultValue = "0:20,0:20"),
            MethodSetting.TextSetting("start_time", "Start time", "Optional display/audit value.", defaultValue = "")
        ),
        As100MusicReferenceMethod.ID to referenceSettings(),
        As100PracticeDashboardMethod.ID to listOf(
            MethodSetting.FloatSetting("bpm", "Tempo", defaultValue = 100f, minimum = 20f, maximum = 400f, step = 1f, unit = "BPM", decimals = 1),
            MethodSetting.IntSetting("beats_per_bar", "Beats per bar", defaultValue = 4, minimum = 1, maximum = 16, step = 1),
            MethodSetting.ChoiceSetting("subdivision", "Subdivision", defaultValue = "quarter", choices = subdivisionChoices),
            MethodSetting.FloatSetting("reference_a4_hz", "Reference A4", defaultValue = 440f, minimum = 400f, maximum = 480f, step = 0.1f, unit = "Hz", decimals = 1),
            MethodSetting.TextSetting("exercise", "Exercise", defaultValue = ""),
            MethodSetting.FloatSetting("target_bpm", "Target tempo", defaultValue = 120f, minimum = 20f, maximum = 400f, step = 1f, unit = "BPM", decimals = 1)
        ),
        As100PerformanceDashboardMethod.ID to listOf(
            MethodSetting.TextSetting("set_name", "Set name", defaultValue = "Tonight's set"),
            MethodSetting.TextSetting("planned_start", "Planned start", defaultValue = ""),
            MethodSetting.TextSetting("curfew", "Curfew / end time", defaultValue = "")
        ),
        As100ReferenceDashboardMethod.ID to referenceSettings(),
        As100RhythmCaptureMethod.ID to listOf(
            MethodSetting.IntSetting("steps_per_beat", "Quantisation grid", defaultValue = 4, minimum = 1, maximum = 16, step = 1),
            MethodSetting.IntSetting("bars", "Bars", defaultValue = 1, minimum = 1, maximum = 16, step = 1),
            MethodSetting.IntSetting("beats_per_bar", "Beats per bar", defaultValue = 4, minimum = 1, maximum = 16, step = 1),
            MethodSetting.FloatSetting("bpm", "Forced BPM", defaultValue = 0f, minimum = 0f, maximum = 400f, step = 1f, unit = "BPM", decimals = 1)
        ),
        As100EuclideanRhythmMethod.ID to listOf(
            MethodSetting.IntSetting("steps", "Steps", defaultValue = 16, minimum = 1, maximum = 128, step = 1),
            MethodSetting.IntSetting("pulses", "Hits", defaultValue = 5, minimum = 0, maximum = 128, step = 1),
            MethodSetting.IntSetting("rotation", "Rotation", defaultValue = 0, minimum = -128, maximum = 128, step = 1)
        ),
        As100PatternMutationMethod.ID to listOf(
            MethodSetting.TextSetting("pattern", "Pattern", "x = hit, . = rest", defaultValue = "x...x...x...x..."),
            MethodSetting.ChoiceSetting("mode", "Mutation", defaultValue = "random", choices = listOf("random","sparser","denser","invert","rotate_left","rotate_right","half_time","double_time")),
            MethodSetting.FloatSetting("amount", "Amount", defaultValue = 0.25f, minimum = 0f, maximum = 1f, step = 0.05f, decimals = 2),
            MethodSetting.IntSetting("seed", "Seed", defaultValue = 1, minimum = 0, maximum = 2147483647, step = 1)
        ),
        As100ProbabilityPatternMethod.ID to listOf(
            MethodSetting.TextSetting("probabilities", "Step probabilities", "Comma-separated values from 0 to 1.", defaultValue = "1,0,0.4,0,1,0,0.25,0"),
            MethodSetting.IntSetting("seed", "Seed", defaultValue = 1, minimum = 0, maximum = 2147483647, step = 1)
        ),
        As100ChordProgressionMethod.ID to listOf(
            MethodSetting.TextSetting("root", "Root", defaultValue = "C"),
            MethodSetting.ChoiceSetting("mode", "Mode", defaultValue = "major", choices = listOf("major","minor")),
            MethodSetting.TextSetting("roman", "Roman numerals", "Leave blank to generate.", defaultValue = "I V vi IV"),
            MethodSetting.IntSetting("bars", "Generated bars", defaultValue = 4, minimum = 1, maximum = 64, step = 1),
            MethodSetting.IntSetting("seed", "Seed", defaultValue = 1, minimum = 0, maximum = 2147483647, step = 1),
            MethodSetting.BooleanSetting("prefer_flats", "Prefer flats", defaultValue = false)
        ),
        As100ArpeggiatorMethod.ID to listOf(
            MethodSetting.TextSetting("chord", "Chord", defaultValue = "Am"),
            MethodSetting.ChoiceSetting("pattern", "Pattern", defaultValue = "up", choices = listOf("up","down","up_down","outside_in")),
            MethodSetting.IntSetting("octaves", "Octaves", defaultValue = 2, minimum = 1, maximum = 4, step = 1),
            MethodSetting.IntSetting("base_octave", "Base octave", defaultValue = 3, minimum = 0, maximum = 8, step = 1),
            MethodSetting.BooleanSetting("prefer_flats", "Prefer flats", defaultValue = false)
        ),
        As100BasslineMethod.ID to listOf(
            MethodSetting.TextSetting("progression", "Chord progression", defaultValue = "Am F C G"),
            MethodSetting.ChoiceSetting("style", "Style", defaultValue = "root_fifth", choices = listOf("roots","root_fifth","octaves","walking")),
            MethodSetting.IntSetting("octave", "Octave", defaultValue = 2, minimum = 0, maximum = 7, step = 1),
            MethodSetting.BooleanSetting("prefer_flats", "Prefer flats", defaultValue = false)
        ),
        As100MelodySequencerMethod.ID to listOf(
            MethodSetting.TextSetting("root", "Root", defaultValue = "C"),
            MethodSetting.ChoiceSetting("scale", "Scale", defaultValue = "minor_pentatonic", choices = listOf("major","natural_minor","minor_pentatonic","major_pentatonic","dorian","mixolydian","blues")),
            MethodSetting.TextSetting("degrees", "Scale degrees", defaultValue = "1,3,4,5,3,2,1"),
            MethodSetting.IntSetting("octave", "Octave", defaultValue = 4, minimum = 0, maximum = 8, step = 1),
            MethodSetting.BooleanSetting("prefer_flats", "Prefer flats", defaultValue = false)
        ),
        As100MotifGeneratorMethod.ID to listOf(
            MethodSetting.TextSetting("notes", "Motif notes", defaultValue = "C4 E4 G4 A4"),
            MethodSetting.IntSetting("transpose_semitones", "Transposition", defaultValue = 2, minimum = -24, maximum = 24, step = 1)
        ),
        As100DrumMachineMethod.ID to listOf(
            MethodSetting.FloatSetting("bpm", "Tempo", defaultValue = 110f, minimum = 20f, maximum = 400f, step = 1f, unit = "BPM", decimals = 1),
            MethodSetting.IntSetting("steps", "Steps", defaultValue = 16, minimum = 4, maximum = 64, step = 1),
            MethodSetting.FloatSetting("swing", "Swing", defaultValue = 0f, minimum = 0f, maximum = 0.75f, step = 0.05f, decimals = 2),
            MethodSetting.TextSetting("kick", "Kick", defaultValue = "x...x...x...x..."),
            MethodSetting.TextSetting("snare", "Snare", defaultValue = "....x.......x..."),
            MethodSetting.TextSetting("hat", "Hi-hat", defaultValue = "x.x.x.x.x.x.x.x."),
            MethodSetting.TextSetting("clap", "Clap", defaultValue = "................")
        ),
        As100BeatPadsMethod.ID to emptyList(),
        As100LiveLooperMethod.ID to emptyList(),
        As100SongSketchMethod.ID to listOf(
            MethodSetting.TextSetting("title", "Title", defaultValue = "Untitled sketch"),
            MethodSetting.FloatSetting("bpm", "Tempo", defaultValue = 100f, minimum = 20f, maximum = 400f, step = 1f, unit = "BPM", decimals = 1),
            MethodSetting.TextSetting("root", "Root", defaultValue = "C"),
            MethodSetting.ChoiceSetting("mode", "Mode", defaultValue = "major", choices = listOf("major","minor")),
            MethodSetting.TextSetting("progression", "Progression", defaultValue = "C | G | Am | F"),
            MethodSetting.TextSetting("beat", "Beat", defaultValue = "x...x...x...x..."),
            MethodSetting.TextSetting("bassline", "Bassline", defaultValue = ""),
            MethodSetting.TextSetting("melody", "Melody", defaultValue = ""),
            MethodSetting.TextSetting("structure", "Structure", defaultValue = "Intro 4; Verse 8; Chorus 8")
        ),
        As100JamDashboardMethod.ID to listOf(
            MethodSetting.FloatSetting("bpm", "Tempo", defaultValue = 110f, minimum = 20f, maximum = 400f, step = 1f, unit = "BPM", decimals = 1)
        ),
        As100SongSketchDashboardMethod.ID to listOf(
            MethodSetting.TextSetting("title", "Title", defaultValue = "Untitled sketch"),
            MethodSetting.FloatSetting("bpm", "Tempo", defaultValue = 100f, minimum = 20f, maximum = 400f, step = 1f, unit = "BPM", decimals = 1),
            MethodSetting.TextSetting("key", "Key", defaultValue = "C major"),
            MethodSetting.TextSetting("progression", "Progression", defaultValue = "C | G | Am | F"),
            MethodSetting.TextSetting("beat", "Beat", defaultValue = "x...x...x...x..."),
            MethodSetting.TextSetting("bassline", "Bassline", defaultValue = ""),
            MethodSetting.TextSetting("melody", "Melody", defaultValue = ""),
            MethodSetting.TextSetting("structure", "Structure", defaultValue = "Intro 4; Verse 8; Chorus 8")
        )
    )

    private val subdivisionChoices = listOf("whole", "half", "quarter", "eighth", "sixteenth", "thirty_second", "dotted_half", "dotted_quarter", "dotted_eighth", "quarter_triplet", "eighth_triplet", "sixteenth_triplet")
    private val chordQualities = listOf("", "m", "dim", "aug", "sus2", "sus4", "6", "m6", "7", "maj7", "m7", "mMaj7", "dim7", "m7b5", "add9", "9", "maj9", "m9")

    private fun tempoSubdivisionSettings(defaultSubdivision: String = "quarter") = listOf(
        MethodSetting.FloatSetting("bpm", "Tempo", defaultValue = 120f, minimum = 20f, maximum = 400f, step = 1f, unit = "BPM", decimals = 1),
        MethodSetting.ChoiceSetting("subdivision", "Subdivision", defaultValue = defaultSubdivision, choices = subdivisionChoices)
    )

    private fun referenceSettings() = listOf(
        MethodSetting.TextSetting("root", "Root", defaultValue = "C"),
        MethodSetting.ChoiceSetting("scale", "Scale", defaultValue = "major", choices = listOf("major", "natural_minor", "harmonic_minor", "melodic_minor", "major_pentatonic", "minor_pentatonic", "blues", "dorian", "mixolydian")),
        MethodSetting.BooleanSetting("prefer_flats", "Prefer flats", defaultValue = false)
    )
}
