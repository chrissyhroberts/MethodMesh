# Animation and haptic specification

## Design rule

Animation communicates scanner state and gives visual confidence. It must never be the only way a state is communicated.

No Lottie dependency is required. Prefer vector drawables, path/alpha/scale animation, ordinary view properties and platform haptics.

## Fingerprint visual

Use a simplified fingerprint composed of approximately 12–20 ridge strokes. The graphic is representational, not a rendering of the participant's captured fingerprint.

### Idle / ready

- subtle breathing halo;
- roughly 1.8–2.5 second cycle;
- low amplitude;
- stop for reduced-motion behaviour.

### Capture

Progressively increase ridge opacity from centre outward. This indicates activity, not literal sensor reconstruction.

### Accepted

1. complete ridge illumination;
2. brief 3–5% scale-in;
3. crossfade/transform to checkmark;
4. single crisp success haptic.

Target transition: roughly 200–350 ms.

### Rejected / poor quality

- ridge opacity drops;
- no aggressive red flash;
- optional gentle double haptic;
- explanatory text immediately states corrective action.

### Verification

Two subtle fingerprint outlines may converge/overlap during matching. On success they resolve into a checkmark. On non-match they separate again; avoid shake effects.

## Multi-finger progress

```text
○ pending
● current
✓ completed
~ retry required
```

Never rely on colour alone.

## Haptics

```text
capture accepted      → one light confirmation
finger set completed  → one stronger confirmation
retry requested       → optional soft double pulse
verified              → one crisp confirmation
```

Do not vibrate continuously while waiting for a finger.
