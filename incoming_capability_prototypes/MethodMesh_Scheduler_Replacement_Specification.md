# MethodMesh Scheduler — Replacement Specification

**Document status:** implementation specification  
**Scope:** replacement of the existing MethodMesh scheduler  
**Working name:** Scheduler  
**Key visual mode:** Composite Timed Sequence  
**Target architecture:** current MethodMesh module/preset/protocol architecture  
**Intent:** handoff specification for implementation in Work mode

---

# 1. Executive summary

Replace the existing MethodMesh scheduler rather than extending it.

The current scheduler is an early implementation built around an older MethodMesh execution model in which schedules directly target several kinds of internal method/action. The replacement scheduler should be built around the current architecture, where **Presets are the reusable executable unit**.

The new Scheduler must support both:

1. **relative, Day-1-anchored schedules**, such as a 22-day medication regimen or clinical-trial follow-up schedule; and
2. **calendar/rule schedules**, such as every Monday, every second Tuesday, or every hour between 09:00 and 14:00.

The central object is a **Schedule Plan**. A plan defines:

- how it starts;
- when it ends;
- one or more swimlanes / activity streams;
- when events occur;
- one or more actions at each occurrence;
- notification and completion behaviour.

The primary finite-sequence authoring interface is a **Gantt-like / swimming-pool view**, called **Composite Timed Sequence**.

A scheduled action has only two fundamental target types:

- **Notifier**
- **Preset**

The Scheduler must not directly know how to run pulse capture, weather retrieval, Enketo, ODK, camera tools, protocols, or individual capabilities. Those behaviours belong in Presets. The Scheduler only knows that a named Preset from the Preset Library is due to run.

A scheduled occurrence may contain **multiple actions**.

Every Schedule Plan must have an explicit termination policy. **Forever is a valid explicit end option.** There must be no accidental implicit forever.

---

# 2. Product principles

## 2.1 One scheduler, multiple temporal models

Do not build separate engines for:

- alarms;
- recurring reminders;
- countdown sequences;
- calendar schedules;
- medication schedules;
- trial follow-up schedules;
- hourly schedules.

They are different expressions of the same scheduling model.

The Scheduler should support:

- manual Day-1 initiation;
- absolute start;
- calendar recurrence;
- bounded intraday recurrence;
- relative day patterns;
- explicit event grids;
- indefinite schedules where the user deliberately selects **Forever**.

## 2.2 Presets are the executable target

The old scheduler target model is obsolete.

The replacement scheduler should not target raw method IDs, protocols, URLs, forms, clipboard actions, or other scheduler-specific action types.

Scheduled actions are:

```text
NOTIFIER
PRESET
```

A Preset may itself:

- launch an external Enketo form;
- launch an ODK form;
- capture pulse using the camera;
- collect local weather;
- run one or several MethodMesh capabilities;
- execute a protocol;
- perform a background-safe automatic task;
- open an interactive workflow.

The Scheduler should not need to understand any of those implementation details.

## 2.3 Plans and running instances are different objects

A **Schedule Plan** is reusable configuration.

Example:

> Progesterone + Patch — 22 days

It has no actual Day-1 calendar date until it starts.

When the user presses **GO**, the Scheduler creates a **Schedule Instance**.

Example:

```text
Plan
Progesterone + Patch
22 days

GO pressed:
9 Sep 2026 14:22

Instance
Day 1: 9 Sep 2026
End: 30 Sep 2026
```

The Plan must remain unchanged when an instance runs.

The Instance owns actual:

- timestamps;
- generated occurrences;
- completion states;
- snoozes;
- missed events;
- notification delivery;
- preset execution IDs;
- returned results;
- failures;
- overrides performed during execution.

This distinction is essential for research use because planned activity and observed activity must not be conflated.

---

# 3. Core domain model

The conceptual model is:

```text
SchedulePlan
    |
    +-- activation policy
    |
    +-- termination policy
    |
    +-- lanes[]
    |      |
    |      +-- default timing
    |      +-- default actions[]
    |      +-- default notification policy
    |      +-- default completion policy
    |      +-- optional completion window
    |
    +-- timing rules / cells[]
    |
    +-- visual/editor metadata

ScheduleInstance
    |
    +-- plan reference/snapshot
    +-- anchored_at
    +-- effective start
    +-- effective end, if finite
    +-- occurrence states[]
    +-- action execution states[]
    +-- history / audit
```

A useful concrete model might resemble:

```text
SchedulePlan
    id
    name
    description
    activation
    termination
    lanes[]
    rules[]
    created_at
    updated_at
    version

ScheduleLane
    id
    name
    default_time
    default_actions[]
    default_notification_policy
    default_completion_policy
    default_window

ScheduleRule
    lane_id
    temporal_rule
    optional_overrides

ScheduleOccurrence
    id
    instance_id
    lane_id
    scheduled_at
    window_open
    window_close
    actions[]
    state
    completed_at
```

Exact Kotlin structure may differ, but these semantic boundaries should remain.

---

# 4. Activation / start policies

Every plan must explicitly define how it starts.

## 4.1 Manual Day-1 start

This is central to Composite Timed Sequence.

```text
activation = MANUAL_DAY_ONE
```

The reusable plan remains dormant until the user presses **GO**.

Pressing GO:

1. records the exact initiation timestamp;
2. establishes the current local calendar date as **Day 1**;
3. creates a Schedule Instance;
4. generates/arms the next required events;
5. exposes the running instance on the Scheduler dashboard.

Typical widget:

```text
Progesterone + Patch
22-day sequence

[ START DAY 1 ]
```

After initiation:

```text
Progesterone + Patch
Day 1 of 22
Next: Change patch · Day 5 · 07:00
```

There must be protection against accidental duplicate initiation.

## 4.2 Absolute start

Example:

```text
Start:
1 October 2026
09:00
Europe/London
```

The schedule begins at that absolute local date/time.

## 4.3 Calendar/rule activation

For schedules whose timing is inherently calendar based, the plan may become active immediately or on a specified start date.

Examples:

```text
Every Monday at 09:00
Start 1 October
```

```text
Every second Tuesday at 14:00
Start now
```

```text
Every hour between 09:00 and 14:00
Monday-Friday
Start 1 November
```

## 4.4 External/manual programmatic initiation

The engine should allow a saved plan to be instantiated by another MethodMesh surface where the architecture already supports it.

This may include:

- a Preset;
- a widget;
- an external MethodMesh invocation;
- ODK/XLSForm where appropriate.

This is still initiation of a saved Schedule Plan, not direct scheduling of raw capability methods.

---

# 5. Termination / end policies

Every Schedule Plan must contain an explicit termination policy.

There must be no default or accidental implicit forever.

Supported end modes should include:

## 5.1 Fixed number of occurrences

```text
End after:
12 occurrences
```

## 5.2 Fixed duration

Examples:

```text
Run for:
22 days
```

```text
Run for:
6 weeks
```

```text
Run for:
3 months
```

## 5.3 Absolute end date/time

```text
End:
31 March 2027
23:59
```

## 5.4 Parent/sequence end

Useful for a lane whose recurrence should continue until the containing finite sequence ends.

Example:

```text
Patch
Every 5 days
Stop: end of sequence
```

## 5.5 Forever

**Forever must be an explicit user-selectable termination option.**

Example:

```text
Every Monday at 09:00
End: Forever
```

Forever is appropriate for genuinely ongoing activities such as:

- weekly equipment checks;
- recurring household routines;
- permanent operational reminders;
- long-running monitoring workflows.

The UI should make this deliberate:

```text
Ends
○ After number of occurrences
○ After duration
○ On date
● Forever
```

Do not silently choose Forever when the user leaves an end field blank.

For a Forever schedule, runtime storage must not attempt to materialise an infinite list of future occurrences. Only a bounded future horizon / next occurrence(s) should be calculated and armed.

---

# 6. Composite Timed Sequence

## 6.1 Definition

**Composite Timed Sequence** is the primary visual editor for finite relative schedules.

The metaphor is a swimming pool:

- horizontal axis = relative Day number;
- vertical axis = swimlanes / activities;
- marked cells = an activity occurs on that day.

Example:

```text
Progesterone + Patch
Sequence length: 22 days

                 DAY
             1  2  3  4  5  6  7  8  9 10 11 12 13 14 15 16 17 18 19 20 21 22

Patch        ·  ·  ·  ·  ■  ·  ·  ·  ·  ■  ·  ·  ·  ·  ■  ·  ·  ·  ·  ■  ·  ·
Progesterone ·  ·  ·  ·  ·  ·  ·  ·  ·  ·  ·  ·  ·  ·  ■  ■  ■  ■  ■  ■  ■  ·
```

Pressing GO establishes Day 1.

## 6.2 Pool length

The user first defines the finite sequence horizon.

Example:

```text
Sequence length
22 days
```

This creates the horizontal grid.

For long sequences, the editor must remain usable through horizontal scrolling/zooming and suitable day/week aggregation.

## 6.3 Swimlanes

Each activity is a lane.

Examples:

- Change patch
- Progesterone
- Daily PRO
- Pulse
- Weather
- Blood sample
- Questionnaire

A lane owns defaults such as:

```text
Lane
Progesterone

Default time
20:00

Default actions
Notifier: "Time to take your progesterone tablet"

Confirmation
Done required

Follow-up reminders
2

Follow-up interval
30 minutes
```

A lane may contain one or more actions.

## 6.4 Painting/tagging days

The user can:

- tap a cell to toggle an occurrence;
- drag across cells to paint several occurrences;
- select a range;
- generate cells from a rule;
- clear cells;
- duplicate patterns.

Example rule helpers:

```text
Every 5 days starting Day 5
```

generates:

```text
5, 10, 15, 20
```

Example:

```text
Daily from Day 15 through Day 21
```

generates:

```text
15, 16, 17, 18, 19, 20, 21
```

Example explicit clinical schedule:

```text
Days 1, 2, 3, 5, 7, 13, 21
```

## 6.5 Cell overrides

Lane settings provide defaults.

A particular occurrence/cell may override:

- time;
- actions;
- reminder text;
- notification policy;
- completion window;
- completion requirement.

Example:

```text
Pulse lane
Default: Weekly pulse preset at 09:00

Day 15 override:
Preset: Pulse + BP
Time: 08:00
```

Overrides should be exceptional, not required for ordinary use.

---

# 7. Calendar and rule schedules

The same engine must support schedules that are not conveniently represented as a finite Gantt.

Examples include:

```text
Every Monday at 09:00
```

```text
Every second Tuesday of the month at 14:00
```

```text
Every weekday at 20:00
```

```text
Every 2 days at 19:00
```

```text
Every hour between 09:00 and 14:00
```

```text
Every 30 minutes between 08:00 and 12:00
Monday-Friday
```

```text
Days 1,2,3,5,7,13,21 after manual Day-1 start
```

Cron may remain as an internal or advanced representation where useful, but normal users should not have to author cron expressions.

The UI should provide human-readable recurrence builders.

---

# 8. Scheduled actions

## 8.1 Only two action types

The Scheduler must expose only:

```text
Notifier
Preset
```

Do not reintroduce a large scheduler-specific target enum.

## 8.2 Notifier action

A Notifier is a generic reminder/acknowledgement action.

Example:

```text
Notifier

Title:
Progesterone

Message:
Time to take your progesterone tablet

Sound:
On

Vibration:
On

Notification light:
On where supported

Require confirmation:
Yes

Snooze:
10 minutes

Follow-up reminders:
2

Follow-up interval:
30 minutes
```

Typical actions:

```text
DONE
SNOOZE
```

A notifier may be used for:

- medication;
- patch changes;
- appointments;
- simple observations;
- non-digital tasks.

The Notifier must remain generic. It should not contain domain-specific medication logic.

## 8.3 Preset action

A Preset action references a saved Preset from the MethodMesh Preset Library.

Example:

```text
Action
Run preset

Preset
Weekly pulse capture
```

The scheduler should store a stable preset reference/ID plus enough human-readable metadata for resilience and display.

Examples:

### PRO

```text
Preset:
Evening PRO

Behaviour:
opens external Enketo form
```

### Pulse

```text
Preset:
Camera pulse capture

Behaviour:
runs the camera-based pulse workflow
```

### Weather

```text
Preset:
Local weather conditions

Behaviour:
gets the local conditions and returns a result
```

The Scheduler itself must not contain knowledge of Enketo, camera pulse, weather APIs, or their internal methods.

---

# 9. Multiple actions per occurrence

A single scheduled occurrence may contain multiple actions.

Example:

```text
Day 9 · 12:00

Actions:
1. PRESET     Local weather conditions
2. PRESET     Day 9 pulse capture
3. PRESET     Day 9 PRO
4. NOTIFIER   "Remember to change patch"
```

The UI should make multiple actions visible within the cell/event, for example through small action badges or a count indicator.

If actions are independent, the occurrence may contain several actions.

If strict sequencing, dependencies, or output piping are required, use a **single Preset** containing that workflow rather than rebuilding workflow/protocol semantics inside the Scheduler.

---

# 10. Preset execution modes

Preset actions may be:

## 10.1 Prompted / interactive

Example:

```text
Pulse measurement due
[ START ]
```

Tapping launches the preset.

Completion occurs when the preset returns successful closeout.

Suitable for:

- camera pulse;
- questionnaires;
- specimen collection;
- interactive measurements;
- forms.

## 10.2 Automatic

Where the referenced Preset declares itself safe for unattended/background execution, the Scheduler may run it automatically.

Example:

```text
Day 9 weather
```

The preset executes, returns a result, and the occurrence is marked complete without requiring user interaction.

If a Preset is not background-safe, automatic execution must not be forced. The editor should:

- prevent selecting automatic execution; or
- validate/warn and convert to prompted execution.

The Scheduler should rely on generic Preset metadata/capabilities, not a hardcoded list of which presets are automatic.

---

# 11. Notifications

Notification behaviour is presentation policy attached to an action or occurrence. It is not the scheduling mechanism itself.

A notification policy should support:

- enabled/disabled;
- title;
- message;
- sound;
- vibration;
- notification light where supported;
- importance/priority;
- lock-screen visibility;
- lock-screen message privacy;
- snooze options;
- follow-up reminders;
- persistent/live status where appropriate.

Android/OEM/user notification-channel settings may override requested sound/light/vibration behaviour. The implementation must not claim otherwise.

## 11.1 Long-running privacy

A long-horizon schedule must not automatically create a persistent lock-screen notification.

Example:

> Day 1 → Progesterone begins Day 15

For private medication use, the default should be:

```text
Persistent status notification: Off
Lock-screen countdown: Off
Reminder text on lock screen: Private/Off
```

A user may deliberately enable persistent presentation for other use cases.

Example:

> Festival in 2 months

Persistent display may show:

```text
2 months 5 days 04:12:09
```

---

# 12. Confirmation, snooze and follow-up reminders

## 12.1 Confirmation

An action may require explicit completion.

For a Notifier:

```text
completion = user presses DONE
```

For a Preset:

```text
completion = preset returns successful closeout
```

## 12.2 Snooze

Where enabled:

```text
SNOOZE
```

temporarily changes the due time for that action occurrence.

Snooze must not change the underlying Schedule Plan.

## 12.3 Follow-up reminders

Follow-up reminders are different from recurrence.

Example:

```text
Progesterone
Scheduled every day for 7 days

Each day's occurrence:
If not confirmed,
remind again every 30 minutes
maximum 2 follow-ups
```

The daily recurrence creates seven primary occurrences.

The follow-up policy belongs to each occurrence and stops when that occurrence is completed.

Do not confuse:

```text
repeat schedule
```

with:

```text
retry/follow-up because this occurrence is incomplete
```

---

# 13. Completion windows

Clinical and research schedules often use windows rather than exact instants.

Support optional windows:

```text
Target:
Day 14 · 09:00

Window opens:
Day 13 · 09:00

Window closes:
Day 15 · 17:00
```

or:

```text
Target:
09:00

Window:
±2 hours
```

A window should affect occurrence state:

```text
UPCOMING
WINDOW_OPEN
DUE
OVERDUE
MISSED
```

Window configuration should remain optional and may initially live under Advanced settings.

---

# 14. Runtime occurrence states

A scheduled occurrence should have explicit runtime state.

At minimum:

```text
UPCOMING
WINDOW_OPEN
DUE
IN_PROGRESS
COMPLETED
SNOOZED
OVERDUE
MISSED
FAILED
SKIPPED
CANCELLED
```

Individual actions within a multi-action occurrence may also need their own state.

An occurrence should only be considered fully completed when its completion policy is satisfied.

For a multi-action occurrence this may mean:

```text
all required actions completed
```

Optional actions should not block completion.

---

# 15. Planned versus actual

Never overwrite the schedule definition to make it match what actually happened.

Store separately:

```text
planned_at
actual_started_at
actual_completed_at
completion_state
execution_id
result_reference
```

This is particularly important for:

- clinical trials;
- longitudinal research;
- adherence monitoring;
- fieldwork;
- audit/provenance.

The Gantt represents the Plan plus runtime status overlay.

---

# 16. Live Gantt / progress view

Once a Composite Timed Sequence is running, the editor becomes a progress view.

Example:

```text
Trial follow-up
Day 9 of 22

                 D1 D2 D3 D4 D5 D6 D7 D8 D9 D10
PRO              ✓  -  ✓  -  !  -  ✓  -  ●   -
Pulse            ✓  -  -  -  -  -  -  ✓  -   -
Weather          -  -  -  -  -  -  -  -  ✓   -
Medication       ✓  ✓  ✓  ✓  ✓  ✓  ✓  ✓  ●   ○
```

Suggested visual semantics:

```text
✓ completed
● due today
○ upcoming
! overdue
× missed
↻ snoozed/retry
- no scheduled activity
```

Do not rely only on colour; use shape/icon/text semantics for accessibility.

---

# 17. Today / next-action view

The everyday operational view should not force the user to inspect the whole Gantt.

Above the Gantt, show:

```text
TODAY — DAY 9

12:00  Local weather        ✓ Captured automatically
20:00  Evening PRO          START
20:00  Progesterone         DUE
```

Then:

```text
NEXT
Tomorrow 20:00 · Progesterone
```

This should be the primary running-sequence UX.

---

# 18. Scheduler dashboard

Create a polished Scheduler dashboard.

It should show:

## 18.1 Running

```text
Progesterone + Patch
Day 9 of 22
Next: Progesterone · 20:00

Trial follow-up
Day 14 of 28
2 actions due today
```

## 18.2 Upcoming calendar plans

```text
Weekly equipment check
Next Monday · 09:00
Ends: Forever
```

## 18.3 Dormant manual-start plans

```text
Travel medication course
14 days

[ START DAY 1 ]
```

## 18.4 Management actions

Appropriate actions include:

- open;
- start;
- pause where conceptually valid;
- stop;
- disable;
- duplicate;
- edit;
- archive;
- inspect history.

Stopping a running instance must not silently delete the reusable Plan.

---

# 19. Views

The same underlying scheduler data should support several views.

## 19.1 Gantt / Composite Timed Sequence

Best for:

- finite relative schedules;
- medication regimens;
- trials;
- longitudinal study plans;
- rehabilitation programmes;
- lab protocols.

## 19.2 Rules / calendar view

Best for:

- every Monday;
- nth weekday;
- recurring operational schedules;
- hourly windows;
- Forever schedules.

## 19.3 Agenda view

Best for:

- what is due today;
- what is next;
- overdue items;
- immediate actions.

Do not create separate scheduling engines for these views.

---

# 20. Example: Progesterone + Patch

```text
PLAN
Progesterone + Patch

Activation:
Manual Day 1

Termination:
22 days

Lane: Change patch
Time: 07:00
Active days: 5, 10, 15, 20
Actions:
    NOTIFIER
        message = "Change patch"
        Done required = true
        Snooze = 30 min
        Follow-ups = 1

Lane: Progesterone
Time: 20:00
Active days: 15, 16, 17, 18, 19, 20, 21
Actions:
    NOTIFIER
        message = "Time to take your progesterone tablet"
        Done required = true
        Snooze = 10 min
        Follow-ups = 2
        Follow-up interval = 30 min
```

Persistent countdown/lock-screen status may be disabled for the whole plan.

---

# 21. Example: clinical trial follow-up

```text
PLAN
Trial follow-up

Activation:
Manual Day 1 = participant enrolment

Termination:
28 days
```

Gantt:

```text
                 1  2  3  4  5  6  7  8  9 ... 15 ... 21 ... 28

PRO              ■  ·  ■  ·  ■  ·  ■  ·  ■
Pulse            ■  ·  ·  ·  ·  ·  ·  ■  ·      ■
Weather          ·  ·  ·  ·  ·  ·  ·  ·  ■
Blood            ■                       ■              ■
```

### PRO lane

```text
Time:
20:00

Action:
PRESET

Preset:
Evening PRO

Execution:
Prompted

Completion:
Preset successfully completes
```

The preset may launch an external Enketo form. Scheduler does not know that.

### Pulse lane

```text
Time:
09:00

Action:
PRESET

Preset:
Camera pulse capture

Execution:
Prompted
```

### Weather lane

```text
Day:
9

Time:
12:00

Action:
PRESET

Preset:
Local weather conditions

Execution:
Automatic if preset declares unattended/background-safe execution
```

---

# 22. Example: calendar operations schedule

```text
PLAN
Equipment checks

Activation:
1 October 2026

Timing:
Every Monday
09:00

Action:
PRESET
Weekly equipment check

Termination:
Forever
```

---

# 23. Example: bounded hourly schedule

```text
PLAN
Hourly observation

Activation:
1 November 2026

Days:
Monday-Friday

Within-day rule:
Every 1 hour
09:00 through 14:00

Action:
NOTIFIER
"Record observation"

Termination:
6 weeks
```

Generated times include:

```text
09:00
10:00
11:00
12:00
13:00
14:00
```

provided those semantics are explicitly represented by the UI.

---

# 24. Preset Library integration

When configuring a Preset action, show the actual Preset Library.

The scheduler editor should provide:

```text
What happens?

○ Notification
● Run preset

Preset
[ Weekly pulse capture           ▾ ]
```

Allow search/filter using normal Preset Library metadata.

Do not ask users to enter:

- raw method IDs;
- protocol IDs;
- URLs;
- action strings;
- package names.

Those belong inside Presets.

---

# 25. Widgets

Support generic Scheduler widgets.

## 25.1 Manual Day-1 launcher

```text
Progesterone + Patch
22 days

[ START DAY 1 ]
```

## 25.2 Running-instance status

```text
Progesterone + Patch
DAY 9 OF 22

Next
Progesterone · 20:00
```

## 25.3 Immediate action

Where useful:

```text
Evening PRO
Due now

[ START ]
```

Widgets must act on the same Schedule Plan / Instance state as the app. No widget-private schedule state.

---

# 26. Scheduling/runtime strategy

## 26.1 Do not materialise infinity

For a Forever schedule, never generate an infinite event table.

Persist the recurrence rule and calculate a bounded future horizon.

At minimum, arm the next event reliably.

A small rolling future window may also be materialised for UI performance.

## 26.2 Durable scheduling

The schedule must survive:

- app process death;
- device restart;
- screen lock;
- ordinary app upgrades where data format remains compatible;
- timezone changes;
- system clock changes.

Re-evaluate future wall-clock occurrences when timezone/time changes.

## 26.3 Wall-clock versus elapsed time

Calendar schedules are wall-clock concepts and must use proper date/time/timezone semantics.

Do not implement:

```text
tomorrow 19:00
```

as:

```text
now + 86,400 seconds
```

when daylight-saving/timezone behaviour matters.

Use `java.time` and explicit `ZoneId` semantics.

## 26.4 Exact alarms

Use Android's appropriate alarm infrastructure for user-requested time-specific events.

Where the platform does not permit exact delivery, degrade explicitly and expose that state rather than claiming false precision.

Do not keep an Activity alive merely to wait for a future schedule event.

---

# 27. Timezone behaviour

Each plan should define its timezone policy.

At minimum:

```text
DEVICE_LOCAL
FIXED_ZONE
```

Examples:

### Medication while travelling

Potentially:

```text
Follow device local time
```

### Trial site schedule

Potentially:

```text
Fixed Europe/London
```

Changing timezone must not silently corrupt already-defined schedule semantics.

The editor should make the policy understandable.

---

# 28. Editing running schedules

Editing a reusable Plan and editing a running Instance are different actions.

The UI must distinguish:

```text
Edit plan
```

from:

```text
Change this running schedule
```

When a Plan has active instances, changing the Plan should not silently rewrite historical or already-instantiated schedules.

For a running Instance, modifications should be recorded as runtime overrides/audit events.

---

# 29. Stop, cancel, skip and disable

These concepts must remain distinct.

## Stop instance

Terminates the current running instance.

The reusable Plan remains available.

## Disable plan

Prevents future automatic activation/occurrences where applicable.

## Skip occurrence

Marks one occurrence as intentionally skipped.

## Cancel action

Cancels one outstanding action where allowed.

## Delete plan

Removes reusable configuration after explicit confirmation.

Do not overload one control to mean all of these.

---

# 30. History and audit

For every running instance retain a practical history:

- plan ID/version;
- instance ID;
- activation time;
- anchor Day 1;
- planned end policy;
- actual stop/end;
- occurrence ID;
- scheduled time;
- window;
- notification delivery;
- snoozes;
- follow-ups;
- user acknowledgement;
- preset execution ID;
- preset result/closeout reference;
- completion state;
- failure/diagnostic state.

Keep ordinary native UI beef-first. Full audit data belongs in details/export, not in the main operational screen.

---

# 31. Research requirements

The replacement Scheduler should be suitable for:

- clinical-trial participant schedules;
- PRO/ePRO collection;
- specimen schedules;
- scheduled measurements;
- follow-up visits;
- protocol windows;
- longitudinal observational research;
- adherence tracking.

Important invariants:

1. planned state and actual state are separate;
2. late completion does not rewrite the planned schedule;
3. missed events remain visible;
4. successful preset execution can automatically satisfy an occurrence;
5. execution results retain their own MethodMesh provenance;
6. Scheduler should reference results rather than inventing a second result/provenance system.

---

# 32. Relationship to Time Tools

Time Tools remains responsible for direct time primitives such as:

- countdown;
- stopwatch;
- interval timer;
- elapsed-time calculation;
- duration calculator.

The Scheduler owns longitudinal orchestration.

A simple alarm may be represented by a one-event Schedule Plan with a Notifier action.

Long-running/repeating/relative multi-day scheduling should not be implemented as special private timer logic once the new Scheduler exists.

Where appropriate, refactor durable scheduling behaviour out of Time Tools and onto the Scheduler engine.

---

# 33. Replacement of the legacy scheduler

This is a replacement, not an extension.

Remove the old scheduler's conceptual model and UI once the new implementation is functional.

Specifically, do not preserve the old multi-target enum as the architecture for the new scheduler.

The old concepts such as direct scheduling of:

- capability;
- protocol;
- web form;
- ODK form;
- clipboard;

should be superseded by:

```text
NOTIFIER
PRESET
```

If a legacy action still matters, encapsulate it in a Preset.

## 33.1 Legacy saved schedules

Do not compromise the new design merely to preserve v0.01-era saved objects.

Implementation should inspect whether meaningful legacy schedules actually need migration.

Preferred order:

1. implement the new model cleanly;
2. if legacy stored schedules are present and conversion is straightforward, offer a one-time migration into new Plans;
3. otherwise clearly retire the legacy store and document the change.

Do not maintain two schedulers indefinitely.

---

# 34. Canonical MethodMesh surfaces

The Scheduler must remain available through normal MethodMesh architecture rather than only through its dashboard.

The exact canonical method IDs should be decided against the current repository conventions, but the functional API should include equivalents of:

```text
schedule.plan.create
schedule.plan.start
schedule.plan.stop
schedule.plan.status
schedule.instance.status
```

Potential additional functions:

```text
schedule.plan.list
schedule.instance.list
schedule.occurrence.complete
schedule.occurrence.skip
```

Do not expose unnecessary low-level API simply because the internal model contains it.

---

# 35. Presets, protocols and ODK

The Scheduler is itself orchestration infrastructure, but its useful public operations should still compose with MethodMesh where appropriate.

Examples:

- a Preset may start a named Schedule Plan;
- ODK may initiate Day 1 for a preconfigured study Schedule Plan;
- a protocol may start/stop or inspect a schedule instance;
- schedule actions launch Presets from the library.

Do not make external callers reconstruct complex Schedule Plans field-by-field unless there is a genuine use case. For research workflows, prefer selecting a saved Plan by stable ID and initiating an instance.

---

# 36. Visual design

The Scheduler should look like a first-class modern MethodMesh surface.

Design goals:

- visually calm;
- immediately legible;
- dense information only where useful;
- large clear Day/Today/Next state;
- tactile Gantt grid;
- easy tap/drag painting;
- clear selected lane;
- polished cards;
- smooth horizontal pan/zoom;
- no raw JSON/configuration in normal use;
- no developer-looking cron editor as the default.

The Gantt/swimming-pool editor is a core product surface, not a debug view.

---

# 37. Accessibility

The schedule must remain understandable without relying solely on colour.

Use:

- icons;
- shape;
- text;
- labels;
- accessible content descriptions.

Support larger text where feasible.

Touch targets must remain adequate even when the Gantt is dense.

Provide an Agenda/list alternative for users who cannot comfortably use the grid.

---

# 38. Validation requirements

At minimum test:

## Plan / instance semantics

- Manual GO correctly establishes Day 1.
- A Plan is not mutated when an Instance starts.
- Multiple concurrent instances of a reusable Plan behave correctly where allowed.
- Duplicate GO presses are guarded.
- Finite duration end is correct.
- Occurrence-count termination is correct.
- Absolute end is correct.
- Forever has no artificial generated end.

## Relative schedules

- explicit days `1,2,3,5,7,13,21`;
- every N days starting at an offset;
- daily bounded range;
- lane defaults and cell overrides.

## Calendar schedules

- weekdays;
- weekends;
- weekly;
- every N weeks;
- nth weekday;
- bounded intraday recurrence;
- DST transitions;
- timezone changes.

## Multiple actions

- several actions at one occurrence;
- required versus optional actions;
- occurrence completion only when required actions complete.

## Notifier

- message;
- Done;
- Snooze;
- sound/vibration/light request;
- privacy;
- follow-up count;
- follow-up interval;
- outstanding follow-ups cancel after completion.

## Preset

- preset lookup by stable reference;
- prompted execution;
- automatic execution where supported;
- successful preset closes action;
- failed preset leaves appropriate failed/outstanding state;
- missing/deleted preset is visible as a configuration/runtime error rather than silently ignored.

## Persistence

- reboot;
- process death;
- time change;
- timezone change;
- disabled plan;
- stopped instance.

## Forever

- explicit Forever selection persists;
- next occurrence continues to calculate;
- no infinite occurrence materialisation;
- disabling/stopping works;
- dashboard clearly shows `Ends: Forever`.

## Research behaviour

- planned and actual timestamps remain distinct;
- missed/late events are not rewritten;
- completion windows behave correctly;
- execution IDs/results remain traceable.

---

# 39. Suggested implementation phases

## Phase 1 — replace model

Implement:

- SchedulePlan;
- ScheduleInstance;
- ScheduleLane;
- temporal rules;
- NotifierAction;
- PresetAction;
- termination policies including Forever;
- repository/persistence.

Do not build UI on top of the legacy `ResearchSchedule` model.

## Phase 2 — recurrence engine

Implement/test:

- explicit relative days;
- interval days;
- daily;
- weekdays;
- weekly;
- nth weekday;
- bounded hourly/minute cadence;
- fixed duration;
- count end;
- absolute end;
- Forever.

## Phase 3 — runtime

Implement:

- activation;
- next-occurrence calculation;
- alarms;
- reboot restoration;
- notifications;
- snooze/follow-up;
- preset launch;
- automatic preset execution where permitted;
- completion state.

## Phase 4 — Composite Timed Sequence editor

Implement:

- pool length;
- lanes;
- Gantt;
- tap/drag paint;
- rule-to-cells helpers;
- lane defaults;
- cell overrides;
- multi-action indicators.

## Phase 5 — dashboard / Agenda

Implement:

- running instances;
- dormant Day-1 plans;
- upcoming calendar plans;
- Today;
- Next;
- overdue;
- history.

## Phase 6 — integration and legacy removal

- integrate Preset Library chooser;
- widgets;
- public MethodMesh operations;
- ODK/external initiation where appropriate;
- remove legacy scheduler UI/model;
- migrate or retire legacy stored schedules;
- update documentation.

---

# 40. Non-goals

Do not turn the Scheduler into:

- a replacement Preset engine;
- a replacement Protocol engine;
- a second result/provenance system;
- a medical-regimen-specific feature;
- a trial-specific feature;
- a raw cron editor;
- a generic task-management/to-do app.

The Scheduler's job is:

> **At the right relative or calendar time, make one or more generic Notifiers and/or saved Presets due, track whether they were completed, and continue according to an explicitly bounded or deliberately Forever schedule.**

---

# 41. Acceptance scenario A — medication sequence

A user has saved:

> Progesterone + Patch — 22 days

They place a generic MethodMesh Scheduler widget on the launcher.

They press:

```text
START DAY 1
```

The Scheduler:

1. creates an instance;
2. records Day 1;
3. does not show an unwanted 22-day persistent lock-screen notification;
4. reminds the user to change the patch on configured days at 07:00;
5. reminds the user to take progesterone on Days 15–21 at 20:00;
6. supports Done, Snooze and configured follow-ups;
7. shows current Day and next action on the dashboard/widget;
8. ends the instance after Day 22.

No medication-specific code exists in Scheduler.

---

# 42. Acceptance scenario B — trial participant

On enrolment, staff start:

> Trial Follow-up — 28 days

The Scheduler establishes enrolment day as Day 1.

During the instance:

- every other day at 20:00, a saved Preset opens an Enketo PRO;
- every week at 09:00, a saved Preset launches camera pulse capture;
- on Day 9 at 12:00, a saved background-safe Preset captures local weather conditions;
- scheduled windows are displayed;
- completed, overdue and missed activities are visible on the Gantt;
- successful preset closeout satisfies the corresponding action;
- planned and actual timestamps remain distinct.

The Scheduler contains no Enketo-, pulse-, camera-, weather-, or trial-specific execution code.

---

# 43. Acceptance scenario C — perpetual operations schedule

A user creates:

> Weekly equipment check

```text
Start:
1 October 2026

When:
Every Monday at 09:00

Action:
Preset — Weekly equipment check

End:
Forever
```

The dashboard displays:

```text
Weekly equipment check
Next Monday · 09:00
Ends: Forever
```

Only the required future event(s) are armed. The runtime does not attempt to create infinite records.

The user can later disable, edit, or delete the plan explicitly.

---

# 44. Final architectural rule

The replacement Scheduler should have a very small understanding of the rest of MethodMesh.

It should understand:

- Schedule Plans;
- time;
- recurrence;
- occurrences;
- notification policy;
- completion state;
- Preset references.

It should **not** understand the internal semantics of what a Preset does.

That boundary is what lets the same scheduler cleanly support:

- medication;
- patch changes;
- PROs;
- camera pulse;
- weather;
- clinical trials;
- field studies;
- lab procedures;
- equipment maintenance;
- ordinary reminders;

without becoming a collection of domain-specific special cases.

**Composite Timed Sequence is the finite, Gantt-like authoring view of this scheduler. The Scheduler itself is the general temporal orchestration engine for MethodMesh.**
