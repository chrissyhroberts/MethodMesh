# Compass validation record — v0.1.0 Development

## Completed in this packaging environment

Pure compass maths was smoke-tested for:

- degree normalisation;
- 16-point cardinal labels;
- wrap-around signed error at 359°/1°;
- left/right turn direction;
- tolerance boundary behaviour;
- North and arbitrary bearing alignment.

## Not completed in this packaging environment

A complete MethodMesh Android checkout is not mounted locally, so the required full build cannot be claimed here.

After copying `compass/` into the current repository, run:

```bash
./gradlew :app:assembleDebug
```

Then complete physical-device checks listed in `docs/README_Compass.md` before Production promotion.
