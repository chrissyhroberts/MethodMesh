package com.example.researchos.modules.choiceexperiment

import com.example.researchos.modules.ModuleExample
import com.example.researchos.modules.ResearchOSModule
import com.example.researchos.modules.RilBinding
import com.example.researchos.transport.workflow.ui.CapabilityScreenSpec

object ChoiceExperimentModule : ResearchOSModule {
    override val moduleId: String = "choiceexperiment"
    override val displayName: String = "Discrete choice experiments"
    override val summary: String = "Interactive discrete-choice, MaxDiff, ranking, points-allocation and conjoint tasks adapted from the XLSForm DCE tooling."

    override fun as100Methods() = listOf(
        DceResultFactory.method(DceMethod.Pairwise),
        DceResultFactory.method(DceMethod.MaxDiff),
        DceResultFactory.method(DceMethod.Ranking),
        DceResultFactory.method(DceMethod.Points),
        DceResultFactory.method(DceMethod.Conjoint)
    )

    override fun rilBindings() = listOf(
        RilBinding("run pairwise", DceMethod.Pairwise.id, "Run a pairwise comparison task"),
        RilBinding("pairwise choice", DceMethod.Pairwise.id, "Run a pairwise comparison task"),
        RilBinding("pairwise comparison", DceMethod.Pairwise.id, "Run a pairwise comparison task"),
        RilBinding("run maxdiff", DceMethod.MaxDiff.id, "Run a MaxDiff / best-worst task"),
        RilBinding("maxdiff", DceMethod.MaxDiff.id, "Run a MaxDiff / best-worst task"),
        RilBinding("best worst", DceMethod.MaxDiff.id, "Run a best-worst scaling task"),
        RilBinding("best worst scaling", DceMethod.MaxDiff.id, "Run a best-worst scaling task"),
        RilBinding("run ranking", DceMethod.Ranking.id, "Run a ranking task"),
        RilBinding("rank options", DceMethod.Ranking.id, "Run a ranking task"),
        RilBinding("ranking", DceMethod.Ranking.id, "Run a ranking task"),
        RilBinding("allocate points", DceMethod.Points.id, "Run a points-allocation task"),
        RilBinding("points allocation", DceMethod.Points.id, "Run a points-allocation task"),
        RilBinding("run points", DceMethod.Points.id, "Run a points-allocation task"),
        RilBinding("run conjoint", DceMethod.Conjoint.id, "Run a conjoint selection task"),
        RilBinding("conjoint", DceMethod.Conjoint.id, "Run a conjoint selection task"),
        RilBinding("conjoint selection", DceMethod.Conjoint.id, "Run a conjoint selection task"),
        RilBinding("conjoint selection", DceMethod.Conjoint.id, "Run a conjoint selection task")
    )

    override fun examples(): List<ModuleExample> = listOf(
        ModuleExample(
            title = "Pairwise comparison",
            ril = "WHAT; pairwise choice(options=A|B|C|D,rounds=3,seed=test001); WHERE; participant/P001; RESULT; return observation.dce.result_json as dce_json; return execution.id as execution_id; format json",
            notes = "Shows two options at a time and returns the selected option for each round."
        ),
        ModuleExample(
            title = "MaxDiff / best-worst",
            ril = "WHAT; maxdiff(options=Speed|Cost|Safety|Comfort,rounds=4,items_per_round=4); WHERE; participant/P001; RESULT; return observation.dce.result_json as dce_json; format json",
            notes = "Shows a set of items and records best/worst selections."
        ),
        ModuleExample(
            title = "Conjoint selection",
            ril = "WHAT; conjoint(rounds=3,profiles_per_round=2,seed=test001); WHERE; participant/P001; RESULT; return observation.dce.result_json as dce_json; format json",
            notes = "Generates profile alternatives from default attribute levels and records selected profiles."
        )
    )

    override fun capabilityScreens(): List<CapabilityScreenSpec> = listOf(
        PairwiseChoiceScreen,
        MaxDiffChoiceScreen,
        RankingChoiceScreen,
        PointsChoiceScreen,
        ConjointChoiceScreen
    )
}
