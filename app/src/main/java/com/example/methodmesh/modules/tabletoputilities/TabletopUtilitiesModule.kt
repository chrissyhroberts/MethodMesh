package com.example.methodmesh.modules.tabletoputilities

import com.example.methodmesh.modules.MethodMeshModule
import com.example.methodmesh.modules.ModuleDependency
import com.example.methodmesh.modules.RilBinding
import com.example.methodmesh.settings.MethodSetting

object TabletopUtilitiesModule : MethodMeshModule {
    override val moduleId = "tabletoputilities"
    override val displayName = "Tabletop utilities"
    override val summary = "Persistent game workspaces with counters, characters, initiative, effects, scores and session audit history."

    override fun as100Methods() = listOf(As100TabletopUtilitiesMethod)

    override fun rilBindings() = listOf(
        RilBinding("tabletop workspace", As100TabletopUtilitiesMethod.ID, "Open or inspect a persistent tabletop game workspace"),
        RilBinding("game state", As100TabletopUtilitiesMethod.ID, "Read or update tabletop game state"),
        RilBinding("adjust game counter", As100TabletopUtilitiesMethod.ID, "Apply an audited counter change to a tabletop workspace")
    )

    override fun capabilityScreens() = listOf(TabletopUtilitiesCapabilityScreen)

    override fun capabilitySettings() = mapOf(
        As100TabletopUtilitiesMethod.ID to listOf(
            MethodSetting.ChoiceSetting(
                id = "operation",
                label = "Operation",
                description = "Dashboard for normal play, or a single state operation for presets/ODK/protocols.",
                group = "Action",
                defaultValue = "dashboard",
                choices = listOf(
                    "dashboard",
                    "snapshot",
                    "counter_adjust",
                    "counter_set",
                    "session_start",
                    "session_note",
                    "session_finish",
                    "initiative_next",
                    "round_next",
                    "score_record"
                )
            ),
            MethodSetting.TextSetting(
                id = "workspace_id",
                label = "Workspace ID",
                description = "Stable workspace ID. Prefer this for presets and ODK once a workspace exists.",
                group = "Workspace",
                defaultValue = ""
            ),
            MethodSetting.TextSetting(
                id = "workspace_name",
                label = "Workspace name",
                description = "Optional name lookup when an ID is not supplied.",
                group = "Workspace",
                defaultValue = ""
            ),
            MethodSetting.TextSetting(
                id = "target_id",
                label = "Target ID",
                description = "Counter or other entity ID for operations that target one object.",
                group = "Action",
                defaultValue = ""
            ),
            MethodSetting.IntSetting(
                id = "value",
                label = "Value / delta",
                description = "Integer value used by counter adjustment/set operations.",
                group = "Action",
                defaultValue = 0,
                minimum = -100000000,
                maximum = 100000000,
                step = 1
            ),
            MethodSetting.TextSetting(
                id = "note",
                label = "Session name / note",
                description = "Used by session start and session note operations.",
                group = "Action",
                defaultValue = ""
            )
        )
    )

    override fun dependencies() = listOf(
        ModuleDependency(
            moduleId = "dicesimulator",
            reason = "Tabletop Utilities launches the public dice.simulate boundary for rolls and stores returned dice provenance; it does not copy the dice engine."
        )
    )
}
