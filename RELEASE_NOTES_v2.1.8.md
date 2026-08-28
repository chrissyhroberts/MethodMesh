# MethodMesh v2.1.8 Release Notes

MethodMesh v2.1.8 is a usability and output-polish release. It focuses on making direct capability use feel less like a debug console and more like a field tool: larger text, cleaner fullscreen intent previews, clearer test/save behaviour, persistent result previews, better media feedback, and tighter image annotation/redaction handling.

## Highlights

### App-wide display accessibility

MethodMesh now has global display-accessibility settings in the dashboard. Text scale can be adjusted across the app, and the default has been increased so ordinary field-facing screens are easier to read.

The dashboard action layout has also been changed to avoid cramped horizontal button rows. This prevents buttons from colliding or wrapping awkwardly at larger display scales.

### Cleaner capability testing

Capability cards now distinguish between:

- **Test** — run the capability without saving study output;
- **Test and save** — run the capability and write a normal output package;
- **Open runner** — inspect the interactive runner;
- **Test intent launch** — preview the fullscreen external-intent experience.

Test runs now leave a persistent **Last confirmed result** panel on the capability card rather than disappearing back to the dashboard with no feedback.

### Media previews in test results

The dashboard result preview now detects image URI outputs and shows image previews directly inside the last-result panel. This makes camera/photo capabilities much easier to test because the operator can immediately confirm that the returned media are sensible.

### Better output discipline

Dashboard dry-runs no longer pretend to be study captures. Output writing is explicit through **Test and save** or through real protocol execution.

The output formatter has also been tightened so old dashboard/example placeholder subjects such as:

```text
participant/P001
```

are no longer emitted as if they were real study subjects during dashboard/test runs. Real caller-supplied subjects from ODK or another external workflow remain supported.

### Question primitive refinements

The native question capabilities have been made more usable as actual MethodMesh protocol steps:

- intent previews open the real question/answer interface rather than a settings panel;
- text and number questions focus the answer box correctly;
- text questions support multiline wrapping;
- number questions use numeric keyboard behaviour;
- select-multiple questions support mutually exclusive options and mutually exclusive groups.

This makes the question primitives more realistic as building blocks for native MethodMesh protocols.

### Image annotation and redaction grouping

The scaled photo selector and image redaction tool now appear together under:

```text
Image annotation and redaction
```

This reflects how they are used in practice: one highlights or records selected image regions, the other removes selected image regions before export.

### Image redaction geometry fix

Image redaction no longer changes the image geometry before the operator marks the redaction grid. The redaction grid and touch mapping now align to the displayed image bounds rather than the surrounding white box.

This should make redaction behave more like the scaled-photo selector: the selected cells correspond to the image pixels being edited, not to empty padding around the image.

## Validation

Built with:

```text
./gradlew :app:assembleDebug
```

Result:

```text
BUILD SUCCESSFUL
```

## Notes

This release does not change the core MethodMesh data model. It makes the current runtime safer and easier to use directly, especially for workflows involving saved presets, protocol runs, image outputs, and user-facing question screens.
