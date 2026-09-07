package com.example.methodmesh.modules.music

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

private fun boolPatternJson(pattern: List<Boolean>) = JSONArray(pattern.map { if (it) 1 else 0 }).toString()

object As100RhythmCaptureMethod : MusicPureMethod(
    "music.rhythm_capture", "Tap rhythm", "Infer pulse from taps and quantise a performed rhythm while retaining raw timing offsets.",
    MusicFieldSet("music_rhythm_capture", listOf("bpm","steps_per_beat","pattern","pattern_json","raw_offsets_ms_json","quantized_offsets_ms_json","mean_error_ms")),
    listOf("tap_times_ms","steps_per_beat","bars","beats_per_bar","bpm"), "music.rhythm.capture"
) {
    const val ID = "music.rhythm_capture"
    override fun calculate(settings: Map<String,String>) = runCatching {
        val taps = MusicMethodSupport.setting(settings,"tap_times_ms").split(",",";"," ").mapNotNull { it.trim().toDoubleOrNull() }
        val spb = MusicMethodSupport.setting(settings,"steps_per_beat","4").toInt()
        val bars = MusicMethodSupport.setting(settings,"bars","1").toInt()
        val beats = MusicMethodSupport.setting(settings,"beats_per_bar","4").toInt()
        val bpm = MusicMethodSupport.setting(settings,"bpm","").toDoubleOrNull()?.takeIf { it > 0.0 }
        val r = MusicCreationAlgorithms.captureRhythm(taps,spb,bars,beats,bpm)
        val pattern = MusicCreationAlgorithms.patternString(r.pattern)
        MusicMethodSupport.ok(fields,mapOf(
            "bpm" to "%.2f".format(Locale.US, r.bpm), "steps_per_beat" to r.stepsPerBeat, "pattern" to pattern,
            "pattern_json" to boolPatternJson(r.pattern), "raw_offsets_ms_json" to JSONArray(r.rawOffsetsMs).toString(),
            "quantized_offsets_ms_json" to JSONArray(r.quantizedOffsetsMs).toString(), "mean_error_ms" to "%.2f".format(Locale.US, r.meanQuantizationErrorMs)
        ), "${"%.1f".format(Locale.US, r.bpm)} BPM · $pattern")
    }.getOrElse { MusicMethodSupport.fail(fields,it.message ?: "Unable to capture rhythm.") }
}

object As100EuclideanRhythmMethod : MusicPureMethod(
    "music.euclidean_rhythm", "Euclidean rhythm", "Evenly distribute a chosen number of hits across a step grid and rotate the result.",
    MusicFieldSet("music_euclidean", listOf("steps","pulses","rotation","pattern","pattern_json")),
    listOf("steps","pulses","rotation"), "music.rhythm.euclidean"
) {
    const val ID="music.euclidean_rhythm"
    override fun calculate(settings:Map<String,String>)=runCatching{
        val steps=MusicMethodSupport.setting(settings,"steps","16").toInt(); val pulses=MusicMethodSupport.setting(settings,"pulses","5").toInt(); val rotation=MusicMethodSupport.setting(settings,"rotation","0").toInt()
        val p=MusicCreationAlgorithms.euclidean(steps,pulses,rotation); val text=MusicCreationAlgorithms.patternString(p)
        MusicMethodSupport.ok(fields,mapOf("steps" to steps,"pulses" to pulses,"rotation" to rotation,"pattern" to text,"pattern_json" to boolPatternJson(p)),text)
    }.getOrElse{MusicMethodSupport.fail(fields,it.message?:"Invalid Euclidean rhythm settings.")}
}

object As100PatternMutationMethod : MusicPureMethod(
    "music.pattern_mutation", "Pattern mutation", "Create a reproducible rhythmic variation by thinning, densifying, rotating, inverting or perturbing a step pattern.",
    MusicFieldSet("music_pattern_mutation", listOf("input_pattern","mode","amount","seed","output_pattern","output_json")),
    listOf("pattern","mode","amount","seed"), "music.rhythm.mutation"
) {
    const val ID="music.pattern_mutation"
    override fun calculate(settings:Map<String,String>)=runCatching{
        val text=MusicMethodSupport.setting(settings,"pattern","x...x...x...x..."); val p=MusicCreationAlgorithms.parsePattern(text); require(p.isNotEmpty()){"Provide a step pattern."}
        val mode=MusicMethodSupport.setting(settings,"mode","random"); val amount=MusicMethodSupport.setting(settings,"amount","0.25").toDouble(); val seed=MusicMethodSupport.setting(settings,"seed","1").toLong()
        val out=MusicCreationAlgorithms.mutate(p,mode,amount,seed); val outText=MusicCreationAlgorithms.patternString(out)
        MusicMethodSupport.ok(fields,mapOf("input_pattern" to MusicCreationAlgorithms.patternString(p),"mode" to mode,"amount" to amount,"seed" to seed,"output_pattern" to outText,"output_json" to boolPatternJson(out)),outText)
    }.getOrElse{MusicMethodSupport.fail(fields,it.message?:"Unable to mutate pattern.")}
}

object As100ProbabilityPatternMethod : MusicPureMethod(
    "music.probability_pattern", "Probability sequencer", "Realise a reproducible hit pattern from per-step trigger probabilities.",
    MusicFieldSet("music_probability", listOf("probabilities_json","seed","pattern","pattern_json")),
    listOf("probabilities","seed"), "music.rhythm.probability"
) {
    const val ID="music.probability_pattern"
    override fun calculate(settings:Map<String,String>)=runCatching{
        val probs=MusicMethodSupport.setting(settings,"probabilities","1,0,0.4,0,1,0,0.25,0").split(",",";"," ").mapNotNull{it.trim().toDoubleOrNull()}; require(probs.isNotEmpty())
        val seed=MusicMethodSupport.setting(settings,"seed","1").toLong(); val p=MusicCreationAlgorithms.probabilityRealization(probs,seed); val text=MusicCreationAlgorithms.patternString(p)
        MusicMethodSupport.ok(fields,mapOf("probabilities_json" to JSONArray(probs).toString(),"seed" to seed,"pattern" to text,"pattern_json" to boolPatternJson(p)),text)
    }.getOrElse{MusicMethodSupport.fail(fields,it.message?:"Invalid probabilities.")}
}

object As100ChordProgressionMethod : MusicPureMethod(
    "music.chord_progression", "Chord progression", "Build a progression from Roman numerals or generate a reproducible diatonic progression.",
    MusicFieldSet("music_progression", listOf("key","mode","source","chords","chords_json","seed")),
    listOf("root","mode","roman","bars","seed","prefer_flats"), "music.harmony.progression"
) {
    const val ID="music.chord_progression"
    override fun calculate(settings:Map<String,String>)=runCatching{
        val root=MusicMethodSupport.setting(settings,"root","C"); val mode=MusicMethodSupport.setting(settings,"mode","major"); val roman=MusicMethodSupport.setting(settings,"roman",""); val bars=MusicMethodSupport.setting(settings,"bars","4").toInt(); val seed=MusicMethodSupport.setting(settings,"seed","1").toLong(); val flats=MusicMethodSupport.setting(settings,"prefer_flats","false").toBoolean()
        val chords=if(roman.isNotBlank()) MusicCreationAlgorithms.progressionFromRoman(root,mode,roman.split(Regex("[,;\\s-]+")),flats) else MusicCreationAlgorithms.generateProgression(root,mode,bars,seed,flats)
        val text=chords.joinToString(" | "); MusicMethodSupport.ok(fields,mapOf("key" to root,"mode" to mode,"source" to if(roman.isNotBlank())"roman" else "generated","chords" to text,"chords_json" to JSONArray(chords).toString(),"seed" to seed),text)
    }.getOrElse{MusicMethodSupport.fail(fields,it.message?:"Unable to build progression.")}
}

object As100ArpeggiatorMethod : MusicPureMethod(
    "music.arpeggiator", "Arpeggiator", "Turn a chord into an ordered note sequence across one or more octaves.",
    MusicFieldSet("music_arpeggiator", listOf("chord","pattern","octaves","notes","notes_json")),
    listOf("chord","pattern","octaves","base_octave","prefer_flats"), "music.melody.arpeggio"
) {
    const val ID="music.arpeggiator"
    override fun calculate(settings:Map<String,String>)=runCatching{
        val chord=MusicMethodSupport.setting(settings,"chord","Am"); val pattern=MusicMethodSupport.setting(settings,"pattern","up"); val oct=MusicMethodSupport.setting(settings,"octaves","2").toInt(); val base=MusicMethodSupport.setting(settings,"base_octave","3").toInt(); val flats=MusicMethodSupport.setting(settings,"prefer_flats","false").toBoolean()
        val notes=MusicCreationAlgorithms.arpeggiate(chord,pattern,oct,base,flats); val text=notes.joinToString(" ")
        MusicMethodSupport.ok(fields,mapOf("chord" to chord,"pattern" to pattern,"octaves" to oct,"notes" to text,"notes_json" to JSONArray(notes).toString()),text)
    }.getOrElse{MusicMethodSupport.fail(fields,it.message?:"Unable to arpeggiate chord.")}
}

object As100BasslineMethod : MusicPureMethod(
    "music.bassline", "Bassline generator", "Generate a simple root, root-fifth, octave or walking bassline from chord symbols.",
    MusicFieldSet("music_bassline", listOf("progression","style","notes","notes_json")),
    listOf("progression","style","octave","prefer_flats"), "music.melody.bassline"
) {
    const val ID="music.bassline"
    override fun calculate(settings:Map<String,String>)=runCatching{
        val progression=MusicMethodSupport.setting(settings,"progression","Am F C G").split(Regex("[|,;\\s]+" )).filter{it.isNotBlank()}; val style=MusicMethodSupport.setting(settings,"style","roots"); val octave=MusicMethodSupport.setting(settings,"octave","2").toInt(); val flats=MusicMethodSupport.setting(settings,"prefer_flats","false").toBoolean()
        val notes=MusicCreationAlgorithms.bassline(progression,style,octave,flats); val text=notes.joinToString(" ")
        MusicMethodSupport.ok(fields,mapOf("progression" to progression.joinToString(" | "),"style" to style,"notes" to text,"notes_json" to JSONArray(notes).toString()),text)
    }.getOrElse{MusicMethodSupport.fail(fields,it.message?:"Unable to generate bassline.")}
}

object As100MelodySequencerMethod : MusicPureMethod(
    "music.melody_sequence", "Scale-locked melody", "Map scale degrees to note names so a small sequencer can remain locked to a chosen key and scale.",
    MusicFieldSet("music_melody_sequence", listOf("root","scale","degrees","notes","notes_json")),
    listOf("root","scale","degrees","octave","prefer_flats"), "music.melody.sequence"
) {
    const val ID="music.melody_sequence"
    override fun calculate(settings:Map<String,String>)=runCatching{
        val root=MusicMethodSupport.setting(settings,"root","C"); val scale=MusicMethodSupport.setting(settings,"scale","minor_pentatonic"); val degrees=MusicMethodSupport.setting(settings,"degrees","1,3,4,5,3,2,1").split(",",";"," ").mapNotNull{it.trim().toIntOrNull()}; require(degrees.isNotEmpty())
        val octave=MusicMethodSupport.setting(settings,"octave","4").toInt(); val flats=MusicMethodSupport.setting(settings,"prefer_flats","false").toBoolean(); val notes=MusicCreationAlgorithms.melodyFromDegrees(root,scale,degrees,octave,flats); val text=notes.joinToString(" ")
        MusicMethodSupport.ok(fields,mapOf("root" to root,"scale" to scale,"degrees" to degrees.joinToString(","),"notes" to text,"notes_json" to JSONArray(notes).toString()),text)
    }.getOrElse{MusicMethodSupport.fail(fields,it.message?:"Unable to create melody.")}
}

object As100MotifGeneratorMethod : MusicPureMethod(
    "music.motif_generator", "Motif variations", "Generate retrograde, inversion, retrograde-inversion and transposed versions of a short melodic motif.",
    MusicFieldSet("music_motif", listOf("original","retrograde","inversion","retrograde_inversion","transposed","variations_json")),
    listOf("notes","transpose_semitones"), "music.melody.motif"
) {
    const val ID="music.motif_generator"
    override fun calculate(settings:Map<String,String>)=runCatching{
        val notes=MusicMethodSupport.setting(settings,"notes","C4 E4 G4 A4").split(",",";"," ").filter{it.isNotBlank()}; val semis=MusicMethodSupport.setting(settings,"transpose_semitones","2").toInt(); val vars=MusicCreationAlgorithms.motifVariations(notes,semis)
        val json=JSONObject();vars.forEach{(k,v)->json.put(k,JSONArray(v))}; MusicMethodSupport.ok(fields,mapOf("original" to vars["original"]!!.joinToString(" "),"retrograde" to vars["retrograde"]!!.joinToString(" "),"inversion" to vars["inversion"]!!.joinToString(" "),"retrograde_inversion" to vars["retrograde_inversion"]!!.joinToString(" "),"transposed" to vars["transposed"]!!.joinToString(" "),"variations_json" to json.toString()),"Inversion: ${vars["inversion"]!!.joinToString(" ")}")
    }.getOrElse{MusicMethodSupport.fail(fields,it.message?:"Unable to generate motif variations.")}
}

object As100DrumMachineMethod : MusicPureMethod(
    "music.drum_machine", "Drum machine", "Create and play a lightweight multi-lane step-sequencer pattern using synthesized drum voices.",
    MusicFieldSet("music_drum_machine", listOf("bpm","steps","kick","snare","hat","clap","swing","pattern_json")),
    listOf("bpm","steps","kick","snare","hat","clap","swing"), "music.rhythm.drum_machine"
) {
    const val ID="music.drum_machine"
    override fun calculate(settings:Map<String,String>)=runCatching{
        val bpm=MusicMethodSupport.setting(settings,"bpm","110").toDouble(); val steps=MusicMethodSupport.setting(settings,"steps","16").toInt(); require(steps in 4..64 && bpm in 20.0..400.0)
        val kick=MusicMethodSupport.setting(settings,"kick","x...x...x...x..."); val snare=MusicMethodSupport.setting(settings,"snare","....x.......x..."); val hat=MusicMethodSupport.setting(settings,"hat","x.x.x.x.x.x.x.x."); val clap=MusicMethodSupport.setting(settings,"clap","................"); val swing=MusicMethodSupport.setting(settings,"swing","0").toDouble().coerceIn(0.0,0.75)
        val json=JSONObject().put("bpm",bpm).put("steps",steps).put("swing",swing).put("kick",kick).put("snare",snare).put("hat",hat).put("clap",clap)
        MusicMethodSupport.ok(fields,mapOf("bpm" to bpm,"steps" to steps,"kick" to kick,"snare" to snare,"hat" to hat,"clap" to clap,"swing" to swing,"pattern_json" to json.toString()),"${"%.0f".format(Locale.US, bpm)} BPM · $steps-step beat")
    }.getOrElse{MusicMethodSupport.fail(fields,it.message?:"Invalid drum-machine pattern.")}
}

object As100BeatPadsMethod : MusicPureMethod(
    "music.beat_pads", "Beat pads", "Finger-drum with synthesized one-shot percussion pads and optionally capture the hit sequence.",
    MusicFieldSet("music_beat_pads", listOf("hit_count","hits_json","duration_ms")), listOf("hits_json"), "music.rhythm.pads"
) {
    const val ID="music.beat_pads"
    override fun calculate(settings:Map<String,String>)=runCatching{
        val raw=MusicMethodSupport.setting(settings,"hits_json","[]"); val arr=JSONArray(raw); var duration=0L; if(arr.length()>0)duration=arr.optJSONObject(arr.length()-1)?.optLong("at_ms")?:0L
        MusicMethodSupport.ok(fields,mapOf("hit_count" to arr.length(),"hits_json" to arr.toString(),"duration_ms" to duration),"${arr.length()} pad hits")
    }.getOrElse{MusicMethodSupport.fail(fields,"Invalid hit sequence JSON.")}
}

object As100LiveLooperMethod : MusicPureMethod(
    "music.live_looper", "Live looper", "Record a master phrase and overdub microphone layers against the fixed loop duration.",
    MusicFieldSet("music_live_looper", listOf("loop_ms","layer_count","playing","recording","layers_json")), listOf("loop_ms","layer_count","playing","recording","layers_json"), "music.audio.loop"
) {
    const val ID="music.live_looper"
    override fun calculate(settings:Map<String,String>)=runCatching{
        val loop=MusicMethodSupport.setting(settings,"loop_ms","0").toLong(); val layers=MusicMethodSupport.setting(settings,"layer_count","0").toInt(); val json=MusicMethodSupport.setting(settings,"layers_json","[]")
        MusicMethodSupport.ok(fields,mapOf("loop_ms" to loop,"layer_count" to layers,"playing" to MusicMethodSupport.setting(settings,"playing","false"),"recording" to MusicMethodSupport.setting(settings,"recording","false"),"layers_json" to json),if(layers==0)"No loop recorded" else "$layers layer${if(layers==1)"" else "s"} · ${loop} ms")
    }.getOrElse{MusicMethodSupport.fail(fields,it.message?:"Invalid looper snapshot.")}
}

object As100SongSketchMethod : MusicPureMethod(
    "music.song_sketch", "Song sketch", "Combine tempo, key, progression, beat, bass and melody into a compact reproducible composition sketch.",
    MusicFieldSet("music_song_sketch", listOf("title","bpm","key","progression","beat","bassline","melody","structure","sketch_json")),
    listOf("title","bpm","root","mode","progression","beat","bassline","melody","structure"), "music.composition.sketch"
) {
    const val ID="music.song_sketch"
    override fun calculate(settings:Map<String,String>)=runCatching{
        val title=MusicMethodSupport.setting(settings,"title","Untitled sketch"); val bpm=MusicMethodSupport.setting(settings,"bpm","100").toDouble(); val root=MusicMethodSupport.setting(settings,"root","C"); val mode=MusicMethodSupport.setting(settings,"mode","major"); val progression=MusicMethodSupport.setting(settings,"progression","C | G | Am | F"); val beat=MusicMethodSupport.setting(settings,"beat","x...x...x...x..."); val bass=MusicMethodSupport.setting(settings,"bassline",""); val melody=MusicMethodSupport.setting(settings,"melody",""); val structure=MusicMethodSupport.setting(settings,"structure","Intro 4; Verse 8; Chorus 8")
        val json=JSONObject().put("title",title).put("bpm",bpm).put("key","$root $mode").put("progression",progression).put("beat",beat).put("bassline",bass).put("melody",melody).put("structure",structure)
        MusicMethodSupport.ok(fields,mapOf("title" to title,"bpm" to bpm,"key" to "$root $mode","progression" to progression,"beat" to beat,"bassline" to bass,"melody" to melody,"structure" to structure,"sketch_json" to json.toString()),"$title · ${"%.0f".format(Locale.US, bpm)} BPM · $root $mode")
    }.getOrElse{MusicMethodSupport.fail(fields,it.message?:"Unable to create song sketch.")}
}

object As100JamDashboardMethod : MusicPureMethod(
    "music.jam_dashboard", "Jam dashboard", "Live creation control centre for tempo, drum pattern, rhythm capture and looper state.",
    MusicFieldSet("music_jam_dashboard", listOf("bpm","drum_pattern_json","loop_ms","loop_layers","captured_pattern","snapshot_json")),
    listOf("bpm","drum_pattern_json","loop_ms","loop_layers","captured_pattern"), "music.dashboard.jam"
) {
    const val ID="music.jam_dashboard"
    override fun calculate(settings:Map<String,String>):Map<String,String>{
        val bpm=MusicMethodSupport.setting(settings,"bpm","110"); val drums=MusicMethodSupport.setting(settings,"drum_pattern_json","{}"); val loop=MusicMethodSupport.setting(settings,"loop_ms","0"); val layers=MusicMethodSupport.setting(settings,"loop_layers","0"); val captured=MusicMethodSupport.setting(settings,"captured_pattern","")
        val json=JSONObject().put("bpm",bpm).put("drum_pattern",JSONObject(runCatching{JSONObject(drums)}.getOrDefault(JSONObject()).toString())).put("loop_ms",loop).put("loop_layers",layers).put("captured_pattern",captured)
        return MusicMethodSupport.ok(fields,mapOf("bpm" to bpm,"drum_pattern_json" to drums,"loop_ms" to loop,"loop_layers" to layers,"captured_pattern" to captured,"snapshot_json" to json.toString()),"Jam · $bpm BPM · $layers loop layers")
    }
}

object As100SongSketchDashboardMethod : MusicPureMethod(
    "music.song_sketch_dashboard", "Song sketch dashboard", "Persistent composition board for key, chords, beat, bassline, melody and arrangement structure.",
    MusicFieldSet("music_song_sketch_dashboard", listOf("title","bpm","key","progression","beat","bassline","melody","structure","snapshot_json")),
    listOf("title","bpm","key","progression","beat","bassline","melody","structure"), "music.dashboard.song_sketch"
) {
    const val ID="music.song_sketch_dashboard"
    override fun calculate(settings:Map<String,String>):Map<String,String>{
        val keys=listOf("title","bpm","key","progression","beat","bassline","melody","structure"); val core=keys.associateWith{MusicMethodSupport.setting(settings,it,when(it){"title"->"Untitled sketch";"bpm"->"100";"key"->"C major";else->""})}; val json=JSONObject(core)
        return MusicMethodSupport.ok(fields,core+mapOf("snapshot_json" to json.toString()),"${core["title"]} · ${core["bpm"]} BPM · ${core["key"]}")
    }
}
