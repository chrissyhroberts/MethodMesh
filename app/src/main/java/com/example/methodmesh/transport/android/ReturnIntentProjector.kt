package com.example.methodmesh.transport.android

import android.content.ClipData
import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import com.example.methodmesh.transport.ReturnNamespaceProjector

data class ProjectedReturnIntent(
    val extras: Map<String, String?>,
    val clipUris: List<ProjectedClipUri>,
    val flags: Int
)

data class ProjectedClipUri(
    val label: String,
    val uri: String
)

object ReturnIntentProjector {
    fun projectFlatReturn(fields: Map<String, String?>, namespace: String): ProjectedReturnIntent {
        ReturnNamespaceProjector.validate(namespace)
        val extras = fields.entries.associate { (key, value) ->
            ReturnNamespaceProjector.key(key, namespace) to value
        }
        val clipUris = fields.entries.mapNotNull { (key, value) ->
            val text = value?.trim().orEmpty()
            if (!isReturnedContentUri(text)) {
                null
            } else {
                ProjectedClipUri(
                    label = ReturnNamespaceProjector.key(key, namespace),
                    uri = text
                )
            }
        }
        return ProjectedReturnIntent(
            extras = extras,
            clipUris = clipUris,
            flags = if (clipUris.isEmpty()) 0 else Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
    }

    fun applyTo(intent: Intent, contentResolver: ContentResolver, projected: ProjectedReturnIntent) {
        projected.extras.forEach { (key, value) ->
            intent.putExtra(key, value)
        }
        if (projected.clipUris.isNotEmpty()) {
            val first = projected.clipUris.first()
            val clipData = ClipData.newUri(contentResolver, first.label, Uri.parse(first.uri))
            projected.clipUris.drop(1).forEach { clip ->
                clipData.addItem(ClipData.Item(Uri.parse(clip.uri)))
            }
            intent.clipData = clipData
            intent.addFlags(projected.flags)
        }
    }

    private fun isReturnedContentUri(value: String): Boolean =
        value.startsWith("content://")
}
