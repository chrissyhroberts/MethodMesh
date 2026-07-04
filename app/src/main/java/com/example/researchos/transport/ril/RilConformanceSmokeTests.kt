package com.example.researchos.transport.ril

/**
 * Lightweight parser conformance checks for environments where the Android
 * module is being edited without a full unit-test source set.
 *
 * These checks intentionally exercise the canonical parser only. They are not
 * invoked in production, but they provide executable examples for future JVM or
 * instrumentation tests.
 */
object RilConformanceSmokeTests {
    data class Case(
        val name: String,
        val ril: String,
        val expectedActions: List<String>,
        val expectedSubject: String,
        val expectedReturns: List<String>,
        val expectedFormat: String = "json"
    )

    data class Result(
        val name: String,
        val passed: Boolean,
        val message: String
    )

    val cases = listOf(
        Case(
            name = "single NFC scan",
            ril = "WHAT; scan nfc; WHERE; participant/P001; RESULT; return observation.nfc.uid as tag_uid; format json",
            expectedActions = listOf("nfc.read"),
            expectedSubject = "participant/P001",
            expectedReturns = listOf("tag_uid")
        ),
        Case(
            name = "NFC plus identity verification",
            ril = "WHAT; scan nfc; verify identity fingerprint; WHERE; participant/P001; RESULT; return observation.nfc.uid as tag_uid; return observation.identity.verified as verified; format json",
            expectedActions = listOf("nfc.read", "identity.verify"),
            expectedSubject = "participant/P001",
            expectedReturns = listOf("tag_uid", "verified")
        ),
        Case(
            name = "one-line section syntax",
            ril = "WHAT scan nfc WHERE participant/P001 RESULT return observation.nfc.uid as tag_uid format json",
            expectedActions = listOf("nfc.read"),
            expectedSubject = "participant/P001",
            expectedReturns = listOf("tag_uid")
        )
    )

    fun runAll(): List<Result> = cases.map { testCase ->
        val parsed = RilRequestParser.parse(testCase.ril, source = "ril_smoke_test")
        val problems = mutableListOf<String>()

        if (parsed.actionIds != testCase.expectedActions) {
            problems.add("actions=${parsed.actionIds}, expected=${testCase.expectedActions}")
        }
        if (parsed.context["subject"] != testCase.expectedSubject) {
            problems.add("subject=${parsed.context["subject"]}, expected=${testCase.expectedSubject}")
        }
        val aliases = parsed.returnSelectors.map { it.alias }
        if (aliases != testCase.expectedReturns) {
            problems.add("returns=$aliases, expected=${testCase.expectedReturns}")
        }
        if (parsed.returnMode?.id != testCase.expectedFormat) {
            problems.add("format=${parsed.returnMode?.id}, expected=${testCase.expectedFormat}")
        }

        Result(
            name = testCase.name,
            passed = problems.isEmpty(),
            message = problems.ifEmpty { listOf("ok") }.joinToString("; ")
        )
    }
}
