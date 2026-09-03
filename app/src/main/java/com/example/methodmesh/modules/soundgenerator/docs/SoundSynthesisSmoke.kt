import com.example.methodmesh.modules.soundgenerator.SoundSpec
import com.example.methodmesh.modules.soundgenerator.SoundSynthesis

/**
 * Pure Kotlin regression smoke test for synthesis algorithm version 1.0.0.
 *
 * Run from the module folder with:
 * kotlinc SoundSynthesis.kt docs/SoundSynthesisSmoke.kt -include-runtime -d /tmp/sound-smoke.jar
 * java -jar /tmp/sound-smoke.jar
 */
fun main() {
    val tone = SoundSynthesis.render(
        SoundSpec(
            stimulusType = "tone",
            frequencyHz = 1000.0,
            durationMs = 100,
            channel = "left",
            levelDbfs = -20.0
        )
    )
    check(tone.frameCount == 4800)
    check(tone.pcm.indices.filter { it % 2 == 1 }.all { tone.pcm[it].toInt() == 0 })
    check(tone.pcmSha256 == "9c7ada4aba75f745e085e019bd4c0b46742ff60923c1495bfa1225d9248f519c")

    val pink1 = SoundSynthesis.render(
        SoundSpec(
            stimulusType = "noise",
            noiseType = "pink",
            noiseSeedMode = "fixed_seed",
            noiseSeed = "study001",
            durationMs = 100
        )
    )
    val pink2 = SoundSynthesis.render(
        SoundSpec(
            stimulusType = "noise",
            noiseType = "pink",
            noiseSeedMode = "fixed_seed",
            noiseSeed = "study001",
            durationMs = 100
        )
    )
    val pinkOtherSeed = SoundSynthesis.render(
        SoundSpec(
            stimulusType = "noise",
            noiseType = "pink",
            noiseSeedMode = "fixed_seed",
            noiseSeed = "study002",
            durationMs = 100
        )
    )
    check(pink1.pcmSha256 == pink2.pcmSha256)
    check(pink1.pcmSha256 != pinkOtherSeed.pcmSha256)
    check(pink1.pcmSha256 == "7212222264f31910fc2c5c0ad6e63b26a583be3d3db22a989d574901562d2365")

    val sweep = SoundSynthesis.render(
        SoundSpec(
            stimulusType = "sweep",
            sweepStartHz = 250.0,
            sweepEndHz = 8000.0,
            durationMs = 250
        )
    )
    check(sweep.frameCount == 12000)
    check(sweep.pcmSha256 == "5c623eb9cbe3c61b437a3185ecc94bb7521ca62c1234b155c8794b39f1f91309")

    println("Sound synthesis smoke tests passed.")
}
