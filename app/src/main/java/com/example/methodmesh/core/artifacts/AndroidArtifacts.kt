package com.example.methodmesh.core.artifacts

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Application-scoped service; Activity recreation must not lose in-flight references. */
object AndroidArtifacts {
    @Volatile private var instance: ArtifactService? = null
    fun service(context: Context): ArtifactService = instance ?: synchronized(this) {
        instance ?: create(context.applicationContext).also { instance = it }
    }
    private fun create(context: Context): ArtifactService {
        val service = ArtifactService(File(context.filesDir, "artifacts/store"),
            File(context.cacheDir, "artifacts/workspaces/${UUID.randomUUID()}"),
            { context.assets.open(it) },
            { context.contentResolver.openInputStream(android.net.Uri.parse(it)) ?: error("Cannot read external artifact") })
        val root = context.assets.open("methodmesh/artifacts/index.json").bufferedReader().use { JSONObject(it.readText()) }
        require(root.getInt("schemaVersion") == 1) { "Unsupported artifact index" }
        val entries = root.getJSONArray("artifacts")
        for (i in 0 until entries.length()) {
            val item = entries.getJSONObject(i)
            service.registerBundled(Artifact(ArtifactRef(item.getString("id")), item.getString("displayName"),
                item.getString("mimeType"), ArtifactOrigin.BUNDLED, ArtifactLifecycle.PERSISTENT,
                item.getString("assetPath"), item.getString("sha256")))
        }
        return service
    }
}
