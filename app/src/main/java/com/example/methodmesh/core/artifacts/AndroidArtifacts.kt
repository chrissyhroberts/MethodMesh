package com.example.methodmesh.core.artifacts

import android.content.Context
import java.io.File

object AndroidArtifacts {
    @Volatile private var instance: ArtifactService? = null
    fun service(context: Context): ArtifactService = instance ?: synchronized(this) {
        instance ?: ArtifactService(File(context.filesDir, "artifacts/store"),
            File(context.cacheDir, "artifacts/workspace"), { uri ->
                context.contentResolver.openInputStream(android.net.Uri.parse(uri))
                    ?: error("Cannot read external artifact")
            }).also { instance = it }
    }
}
