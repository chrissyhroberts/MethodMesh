# Attribution and design lineage

Paper Bridge is a new native Kotlin implementation for MethodMesh.

Its anchor-based paper workflow is conceptually descended from:

- `chrissyhroberts/OMR_LSHTM` — <https://github.com/chrissyhroberts/OMR_LSHTM>
- upstream `Udayraj123/OMRChecker` — <https://github.com/Udayraj123/OMRChecker>

The earlier project demonstrated four-marker page rectification, coordinate-defined OMR fields and explicit multimark handling. Paper Bridge preserves those architectural ideas but does not copy the Python/OpenCV implementation. The new extraction engine is implemented against Android bitmap/matrix APIs and MethodMesh's existing ML Kit dependencies.

`OMR_LSHTM` contains an upstream GPL licence file; do not copy source from that repository into MethodMesh without a deliberate licence review. This module handoff was written as a clean rewrite from observed behavior/design concepts.

Google ML Kit is already a MethodMesh application dependency; its applicable terms remain those of the dependency already admitted by the main project.
