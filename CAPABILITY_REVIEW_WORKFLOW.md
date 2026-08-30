# MethodMesh capability review workflow

Capabilities are split into two lifecycle states:

- **Development**: available for testing and iteration, but not yet treated as stable.
- **Production**: reviewed, tested, documented, and suitable for routine protocol/ODK use.

For now, every capability starts in **Development**.

## Promotion checklist

A capability can move to Production when a review confirms:

1. Native UX works as a toolbox action.
   - The user sees the main result clearly.
   - Sharing prioritises the core result or media.
   - Save/export is available but not the dominant native path.

2. ODK/external UX works.
   - Supplied input fields are accepted without extra taps where appropriate.
   - Returned extras include the main result fields.
   - Audit JSON is returned as a background/ancillary field.

3. Preset behaviour is correct.
   - Fixed settings are saved.
   - Runtime fields are requested from the user when run natively.
   - Runtime fields can be supplied by ODK without being accidentally hardcoded.

4. Output contract is clear.
   - Core result fields are named consistently.
   - ALCOA/audit fields are available when requested.
   - Full JSON is available as a single auditable payload, not as UI clutter.

5. Documentation and examples are updated.
   - Capability docs describe native use.
   - ODK intent examples/forms are current.
   - Known limitations are recorded.

6. A version note records the reviewed behaviour.

Promotion is done explicitly in the app lifecycle allow-list, not automatically.
