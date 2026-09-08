# Roadmap note — Multi-counter

Keep this module generic. Do not let it absorb the dedicated timer or scoring-system modules.

Possible later improvements after the base board is validated:

- drag/reorder entities before session start;
- optional per-entity custom step values;
- a one-tap “next” mode that pauses the current timer and starts the next entity for turn-taking/debate workflows;
- accessible haptic feedback using a generic module-owned setting;
- compact landscape layout tuned for 6–12 simultaneous timers;
- optional explicit checkpoint/snapshot action during a long session, if the closeout contract gains a generic non-final payload mechanism.

Do not add persistent leagues, match histories, interval alarms, lap analytics or sport rules here. Those are separate capability concerns.
