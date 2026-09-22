package com.example.methodmesh.modules.textdocuments

import com.example.methodmesh.modules.MethodMeshModule
import com.example.methodmesh.modules.RilBinding
import com.example.methodmesh.settings.MethodSetting

object TextDocumentsModule : MethodMeshModule {
    override val moduleId = "textdocuments"
    override val displayName = "Text documents"
    override val summary = "Create, open, edit, save, copy and share plain text, Markdown, JSON and JSON Lines documents."
    override val iconKey = "document"

    override fun as100Methods() = listOf(As100TextDocumentsMethod)

    override fun rilBindings() = listOf(
        RilBinding("open text documents", As100TextDocumentsMethod.ID, "Open the Text documents workspace"),
        RilBinding("edit text document", As100TextDocumentsMethod.ID, "Edit supplied or locally selected text"),
        RilBinding("create text document", As100TextDocumentsMethod.ID, "Create a text, Markdown, JSON or JSON Lines document")
    )

    override fun capabilityScreens() = listOf(TextDocumentsCapabilityScreen)

    override fun capabilitySettings() = mapOf(
        As100TextDocumentsMethod.ID to listOf(
            MethodSetting.ChoiceSetting(
                "document_mode",
                "Open mode",
                description = "Choose library to open the toolkit, new to create a blank document, or edit to open caller-supplied text directly.",
                defaultValue = "library",
                choices = listOf("library", "new", "edit")
            ),
            MethodSetting.TextSetting(
                "document_text",
                "Document text",
                description = "Text supplied by a preset, protocol or ODK caller. Leave blank to choose or create a document interactively.",
                defaultValue = ""
            ),
            MethodSetting.TextSetting(
                "document_title",
                "Document title",
                description = "Suggested human-facing document name.",
                defaultValue = "document.txt"
            ),
            MethodSetting.ChoiceSetting(
                "document_format",
                "Document format",
                defaultValue = "text",
                choices = listOf("text", "markdown", "json", "jsonl")
            )
        )
    )
}
