# Android capability surfaces

MethodMesh capabilities should be able to project useful state beyond an open activity without coupling capability logic to Android UI plumbing.

## Principle

A capability declares **surface intent/state**; shared Android infrastructure renders it. A module must not create one-off notification systems when the same primitive can be generic.

## Surfaces worth supporting

### Notification shade / status

- standard notification;
- ongoing notification for active sessions;
- compact coloured status indicator (for example green / amber / red security state);
- notification actions such as Acknowledge, Pause, Stop, Open;
- notification channels and Android 13+ notification permission handling;
- lock-screen visibility policy: public / private / secret;
- progress and chronometer notifications;
- grouped notifications;
- heads-up notification where urgency and Android policy permit it.

A traffic-light capability should publish a semantic status (`normal`, `attention`, `alert`) rather than hard-code notification colours. Shared UI maps semantic state to icon/dot treatment and accessibility labels.

Android does **not** guarantee an arbitrary coloured dot in the status bar itself: small status-bar notification icons are system-rendered/monochrome on modern Android. The notification shade can still show a clear green/amber/red state using supported accent, large-icon or content treatment. Design the contract around semantic state rather than promising a particular OEM rendering.

### Foreground work

Long-running navigation, sensing, timers, recording and similar work may require a foreground service with the mandatory notification. The capability owns state; the shared service host owns Android lifecycle, foreground-service type declarations, permission/policy requirements and notification policy.

### Quick access

- home-screen widgets;
- pinned app shortcuts / dynamic shortcuts;
- Quick Settings tiles;
- notification actions;
- launcher deep links to a preset/protocol/capability;
- managed-device lock-task/kiosk integration where a deployment genuinely needs it (distinct from ordinary user screen pinning).

### Floating / persistent views

Use only where the capability genuinely benefits:

- picture-in-picture for supported activity types;
- Android bubbles for conversational/task-like flows;
- system overlay (`SYSTEM_ALERT_WINDOW`) only where the value clearly justifies the intrusive permission;
- small always-on-top instrument/status view where platform policy permits.

Overlay support should never be the default simply because it is technically possible.

### Other Android integration

- notification badges;
- vibration/haptics and sound channels;
- Do Not Disturb-aware urgency policy;
- full-screen intents only for genuinely eligible urgent use cases;
- share targets;
- intents/deep links from other apps;
- wearable/companion projections where a future module justifies them.

## Proposed generic contract

Future shared metadata should allow a capability/preset/protocol to publish something conceptually like:

```text
SurfaceState
  key
  title
  shortValue
  semanticStatus = neutral | good | attention | alert
  ongoing = true/false
  updatedAt
  tapAction
  actions[]
  privacy
```

This is deliberately not an Android `Notification` object. The shared Android layer chooses the rendering.

## Safety/UX rule

MethodMesh should remain quiet by default. Persistent notifications, overlays, vibration and heads-up behaviour require deliberate user configuration or an explicit protocol/preset requirement.
