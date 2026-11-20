package com.example.methodmesh.modules.paperbridge

import android.content.Context
import com.example.methodmesh.transport.OutputExportRepository

internal data class PaperBridgeShareAttachment(
    val name: String,
    val uri: String
)

/** Committed-result persistence only; reusable schema/workspace saves are separate. */
internal object PaperBridgeResultActions {
    fun saveToDownloads(
        context: Context,
        label: String,
        text: String,
        attachments: List<PaperBridgeShareAttachment>,
        jsonText: String = ""
    ): OutputExportRepository.DownloadsExport = OutputExportRepository.saveToDownloads(
        context = context,
        label = label,
        text = text,
        mediaUris = attachments.map { it.uri }.distinct(),
        jsonText = jsonText
    )
}
