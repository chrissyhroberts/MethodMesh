package com.example.methodmesh.modules.screentools

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.time.Instant
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypted, versioned selection definitions. Pixels, OCR text and values are never stored. */
object ScreenMemoryStore {
    private const val KEY_ALIAS = "methodmesh.screen_tools.selection_memory.v001"
    private const val SCHEMA = "methodmesh.screen.selection_definition.v1"

    private fun directory(context: Context) =
        File(context.noBackupFilesDir, "screen_selection_memory").apply { mkdirs() }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build()
            )
        }.generateKey()
    }

    private fun encrypt(input: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        return cipher.iv + cipher.doFinal(input)
    }

    private fun decrypt(input: ByteArray): ByteArray {
        require(input.size > 28) { "Invalid selection memory record" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, input.copyOfRange(0, 12)))
        return cipher.doFinal(input.copyOfRange(12, input.size))
    }

    private fun validId(id: String) = id.matches(Regex("[a-zA-Z0-9_-]{1,90}"))

    private fun fileFor(context: Context, id: String, version: Int) =
        File(directory(context), "$id--v$version.mmemory")

    private fun allVersions(context: Context, id: String): List<Pair<Int, File>> {
        if (!validId(id)) return emptyList()
        val pattern = Regex("^${Regex.escape(id)}--v(\\d+)\\.mmemory$")
        return directory(context).listFiles().orEmpty().mapNotNull { file ->
            pattern.matchEntire(file.name)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { it to file }
        }.sortedByDescending { it.first }
    }

    private fun decode(file: File): JSONObject? = runCatching {
        JSONObject(String(decrypt(file.readBytes()), StandardCharsets.UTF_8))
    }.getOrNull()

    fun list(context: Context): List<JSONObject> = directory(context)
        .listFiles { file -> file.extension == "mmemory" }
        .orEmpty()
        .mapNotNull(::decode)
        .groupBy { it.optString("id") }
        .values
        .mapNotNull { versions -> versions.maxByOrNull { it.optInt("version") } }
        .sortedByDescending { it.optString("updated_at") }

    fun get(context: Context, id: String, version: Int? = null): JSONObject? {
        val match = if (version == null) {
            allVersions(context, id).firstOrNull()
        } else {
            allVersions(context, id).firstOrNull { it.first == version }
        }
        return match?.second?.let(::decode)
    }

    fun save(context: Context, name: String, selections: List<ScreenSelection>, id: String = ""): JSONObject {
        require(name.trim().length in 1..100) { "Memory name must be 1–100 characters" }
        require(selections.isNotEmpty() && selections.size <= 32) { "Select 1–32 regions" }
        require(selections.map { it.name.trim().lowercase() }.distinct().size == selections.size) {
            "Selection names must be unique"
        }
        val stableId = id.takeIf(::validId) ?: UUID.randomUUID().toString()
        val nextVersion = (get(context, stableId)?.optInt("version") ?: 0) + 1
        val value = JSONObject().apply {
            put("schema", SCHEMA)
            put("id", stableId)
            put("name", name.trim())
            put("version", nextVersion)
            put("updated_at", Instant.now().toString())
            put("coordinate_frame", "normalised_image")
            put("fields", JSONArray().apply { selections.forEach { put(it.toJson()) } })
        }
        val destination = fileFor(context, stableId, nextVersion)
        require(!destination.exists()) { "Selection memory revision already exists" }
        val temporary = File(directory(context), "$stableId--v$nextVersion.partial")
        temporary.writeBytes(encrypt(value.toString().toByteArray(StandardCharsets.UTF_8)))
        check(temporary.renameTo(destination)) { "Could not commit selection memory" }
        return value
    }

    fun selections(definition: JSONObject): List<ScreenSelection> {
        require(definition.optString("schema") == SCHEMA) { "Unknown selection memory schema" }
        val fields = definition.getJSONArray("fields")
        return (0 until fields.length()).map { index ->
            val field = fields.getJSONObject(index)
            val roi = field.getJSONArray("roi")
            ScreenSelection(
                id = field.optString("id").ifBlank { UUID.randomUUID().toString() },
                name = field.getString("name"),
                region = ScreenRegion(
                    roi.getDouble(0).toFloat(),
                    roi.getDouble(1).toFloat(),
                    roi.getDouble(2).toFloat(),
                    roi.getDouble(3).toFloat()
                )
            )
        }
    }

    fun delete(context: Context, id: String): Boolean {
        val files = allVersions(context, id).map { it.second }
        if (files.isEmpty()) return false
        return files.map(File::delete).all { it }
    }
}
