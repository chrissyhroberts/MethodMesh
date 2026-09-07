package com.example.methodmesh.modules.referencelibrary

import com.example.methodmesh.modules.MethodMeshModule
import com.example.methodmesh.modules.ModuleDependency
import com.example.methodmesh.modules.RilBinding
import com.example.methodmesh.settings.MethodSetting

object ReferenceLibraryModule : MethodMeshModule {
    override val moduleId = "referencelibrary"
    override val displayName = "Reference library"
    override val summary = "Keep useful field references, manuals and document packs accessible offline."
    override val iconKey = "document"

    override fun as100Methods() = listOf(
        As100ReferenceLibraryMethod,
        As100ReferenceLibraryEmailMethod
    )

    override fun rilBindings() = listOf(
        RilBinding("open reference library", As100ReferenceLibraryMethod.ID, "Open the offline reference library"),
        RilBinding("find reference document", As100ReferenceLibraryMethod.ID, "Find and return a reference document"),
        RilBinding("email reference documents", As100ReferenceLibraryEmailMethod.ID, "Prepare an email with library or piped document attachments")
    )

    override fun dependencies() = listOf(
        ModuleDependency(
            "documentscanner",
            "Scan to shelf invokes the installed document.scan capability through its public capability screen and persists the returned PDF into library-managed storage."
        )
    )

    override fun capabilityScreens() = listOf(
        ReferenceLibraryCapabilityScreen,
        ReferenceLibraryEmailCapabilityScreen
    )

    override fun capabilitySettings() = mapOf(
        As100ReferenceLibraryMethod.ID to listOf(
            MethodSetting.TextSetting("document_id", "Document ID", defaultValue = ""),
            MethodSetting.TextSetting("query", "Search", defaultValue = ""),
            MethodSetting.ChoiceSetting(
                "shelf",
                "Shelf",
                defaultValue = "all",
                choices = listOf("all", "first_aid", "medical", "safety", "fieldwork", "equipment", "travel", "personal")
            )
        ),
        As100ReferenceLibraryEmailMethod.ID to listOf(
            MethodSetting.TextSetting("document_ids", "Library document IDs", defaultValue = ""),
            MethodSetting.TextSetting("attachment_uris", "Additional attachment URIs", defaultValue = ""),
            MethodSetting.TextSetting("recipient", "To", defaultValue = ""),
            MethodSetting.TextSetting("cc", "Cc", defaultValue = ""),
            MethodSetting.TextSetting("bcc", "Bcc", defaultValue = ""),
            MethodSetting.TextSetting("subject", "Subject", defaultValue = ""),
            MethodSetting.TextSetting("body", "Message", defaultValue = ""),
            MethodSetting.TextSetting("chooser_title", "Mail chooser title", defaultValue = "Send document copies")
        )
    )
}
