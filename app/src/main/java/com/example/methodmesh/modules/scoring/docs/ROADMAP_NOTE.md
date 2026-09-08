# Scoring roadmap

The v0.2 module deliberately establishes the stable boundary first: one scoring engine, persistent session IDs, event history, capability-first surfaces and ODK roundtrip.

Future improvements should remain inside the module unless they are genuinely generic MethodMesh framework improvements.

Candidate follow-ons:

- richer explicit round ledger for `score.rounds`;
- configurable quick-action buttons for generic counters;
- per-sport periods/quarters/halves and cards/fouls where useful;
- cricket innings/wickets/overs as a dedicated structured ruleset;
- darts 301/501 and double-out rules;
- golf hole-by-hole scorecard and par handling;
- archery ends and totals;
- bowling frames;
- snooker/pool frame scoring where practical;
- deciding-set volleyball target rules;
- tennis match-format variants and super tie-breaks;
- optional haptic score acknowledgement;
- session pin/lock controls for pitch-side use;
- generic dashboard active-session provider once the shared dashboard contract supports it;
- widgets that attach to existing `score_session_id` values;
- timer-module composition for game/period clocks;
- explicit session export/import if cross-device transfer becomes a generic requirement;
- retention/cleanup policy harmonised with MethodMesh operational-state rules;
- tests around corrupted/truncated persistence files and recovery.

Do not solve these by teaching shared MethodMesh UI about individual sports.
