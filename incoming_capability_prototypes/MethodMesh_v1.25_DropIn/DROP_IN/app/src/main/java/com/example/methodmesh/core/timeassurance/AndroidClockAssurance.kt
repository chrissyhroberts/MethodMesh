package com.example.methodmesh.core.timeassurance

import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.time.Instant
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey

internal class AndroidClockSource(
    private val context: Context
) : ClockSource {
    override fun wallTime(): Instant = Instant.now()

    override fun monotonicSnapshot(): MonotonicSnapshot = MonotonicSnapshot(
        elapsedRealtimeMillis = SystemClock.elapsedRealtime(),
        bootSessionId = bootSessionId()
    )

    private fun bootSessionId(): String? {
        runCatching {
            val count = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT)
            return "android_boot_count:$count"
        }
        return runCatching {
            File("/proc/sys/kernel/random/boot_id")
                .readText()
                .trim()
                .takeIf { it.matches(Regex("^[0-9a-fA-F-]{16,64}$")) }
                ?.let { "kernel_boot_id:${it.lowercase()}" }
        }.getOrNull()
    }
}

internal class AndroidClockAnchorStore(
    context: Context
) : ClockAnchorRepository {
    private val atomicFile = AtomicFile(File(context.noBackupFilesDir, FILE_NAME))

    override fun load(): ClockAnchorLoadResult {
        if (!atomicFile.baseFile.exists()) return ClockAnchorLoadResult(anchor = null)
        return runCatching {
            val text = atomicFile.openRead().bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            val root = JSONObject(text)
            val payload = root.getJSONObject("anchor")
            val suppliedMac = root.getString("hmac_sha256").lowercase()
            val expectedMac = hmacHex(canonicalPayload(payload).toByteArray(StandardCharsets.UTF_8))
            require(constantTimeEqualsHex(expectedMac, suppliedMac)) { "clock anchor authentication failed" }

            val anchor = ClockAnchor(
                schemaVersion = payload.getInt("schema_version"),
                trustedTimeIso = payload.getString("trusted_time_iso"),
                anchorElapsedRealtimeMillis = payload.getLong("anchor_elapsed_realtime_ms"),
                bootSessionId = payload.getString("boot_session_id"),
                acquisitionWindowMillis = payload.getLong("acquisition_window_ms"),
                sourcePrecisionMillis = payload.getLong("source_precision_ms"),
                source = payload.getString("source"),
                evidenceHash = payload.getString("evidence_hash"),
                storedAtWallTimeIso = payload.getString("stored_at_wall_time_iso")
            )
            validateStoredAnchor(anchor)
            ClockAnchorLoadResult(anchor)
        }.getOrElse { error ->
            ClockAnchorLoadResult(null, error.message ?: error::class.java.simpleName)
        }
    }

    override fun save(anchor: ClockAnchor) {
        validateStoredAnchor(anchor)
        val payload = JSONObject().apply {
            put("schema_version", anchor.schemaVersion)
            put("trusted_time_iso", anchor.trustedTimeIso)
            put("anchor_elapsed_realtime_ms", anchor.anchorElapsedRealtimeMillis)
            put("boot_session_id", anchor.bootSessionId)
            put("acquisition_window_ms", anchor.acquisitionWindowMillis)
            put("source_precision_ms", anchor.sourcePrecisionMillis)
            put("source", anchor.source)
            put("evidence_hash", anchor.evidenceHash)
            put("stored_at_wall_time_iso", anchor.storedAtWallTimeIso)
        }
        val root = JSONObject().apply {
            put("anchor", payload)
            put("hmac_sha256", hmacHex(canonicalPayload(payload).toByteArray(StandardCharsets.UTF_8)))
        }

        val output = atomicFile.startWrite()
        try {
            output.write(root.toString().toByteArray(StandardCharsets.UTF_8))
            output.flush()
            atomicFile.finishWrite(output)
        } catch (error: Throwable) {
            runCatching { atomicFile.failWrite(output) }
            throw error
        }
    }

    override fun clear() = atomicFile.delete()

    private fun validateStoredAnchor(anchor: ClockAnchor) {
        require(anchor.schemaVersion == 1) { "unsupported clock anchor schema" }
        Instant.parse(anchor.trustedTimeIso)
        Instant.parse(anchor.storedAtWallTimeIso)
        require(anchor.anchorElapsedRealtimeMillis >= 0L)
        require(anchor.bootSessionId.isNotBlank())
        require('\n' !in anchor.bootSessionId && '\r' !in anchor.bootSessionId)
        require(anchor.acquisitionWindowMillis >= 0L)
        require(anchor.sourcePrecisionMillis >= 0L)
        require(anchor.source.isNotBlank() && '\n' !in anchor.source && '\r' !in anchor.source)
        require(anchor.evidenceHash.matches(Regex("^[0-9a-fA-F]{64}$")))
    }

    private fun canonicalPayload(payload: JSONObject): String = listOf(
        "schema_version=${payload.getInt("schema_version")}",
        "trusted_time_iso=${payload.getString("trusted_time_iso")}",
        "anchor_elapsed_realtime_ms=${payload.getLong("anchor_elapsed_realtime_ms")}",
        "boot_session_id=${payload.getString("boot_session_id")}",
        "acquisition_window_ms=${payload.getLong("acquisition_window_ms")}",
        "source_precision_ms=${payload.getLong("source_precision_ms")}",
        "source=${payload.getString("source")}",
        "evidence_hash=${payload.getString("evidence_hash").lowercase()}",
        "stored_at_wall_time_iso=${payload.getString("stored_at_wall_time_iso")}"
    ).joinToString("\n")

    private fun hmacHex(bytes: ByteArray): String = Mac.getInstance(HMAC_ALGORITHM).run {
        init(hmacKey())
        doFinal(bytes).joinToString("") { "%02x".format(it) }
    }

    private fun hmacKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, KEYSTORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
                ).setDigests(KeyProperties.DIGEST_SHA256).build()
            )
            generateKey()
        }
    }

    private fun constantTimeEqualsHex(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var difference = 0
        for (index in a.indices) difference = difference or (a[index].code xor b[index].code)
        return difference == 0
    }

    companion object {
        private const val FILE_NAME = "methodmesh_clock_anchor_v1.json"
        private const val KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "methodmesh_clock_anchor_hmac_v1"
        private const val HMAC_ALGORITHM = "HmacSHA256"
    }
}

object ClockAssuranceRuntime {
    @Volatile private var service: ClockAssuranceService? = null

    @Synchronized
    fun initialise(context: Context) {
        if (service != null) return
        val app = context.applicationContext
        service = ClockAssuranceService(AndroidClockSource(app), AndroidClockAnchorStore(app))
    }

    internal fun monotonicSnapshot(): MonotonicSnapshot = current().monotonicSnapshot()

    internal fun publishVerifiedAnchor(candidate: VerifiedTimeAnchorCandidate): ClockAnchorPublishResult =
        current().publishVerifiedAnchor(candidate)

    fun snapshot(): ClockEvidenceSnapshot = current().snapshot()

    fun observe(policy: ClockAssurancePolicy): ClockAssuranceObservation =
        current().observe(policy)

    fun assessTemporalWindow(
        validFrom: Instant?,
        validUntil: Instant?,
        observation: ClockAssuranceObservation,
        boundaryPolicy: TemporalBoundaryPolicy = TemporalBoundaryPolicy()
    ): TemporalWindowAssessment =
        current().assessTemporalWindow(validFrom, validUntil, observation, boundaryPolicy)

    internal fun clearAnchorForRecovery() = current().clearAnchor()

    private fun current(): ClockAssuranceService =
        service ?: error("ClockAssuranceRuntime has not been initialised by MethodMeshApplication.")
}
