# Drop-in

Replace the existing Display module folder with this folder at:

`app/src/main/java/com/example/methodmesh/modules/display/`

v0.4.0 exposes:

- `display.show`
- `display.timer`
- `display.debate_timer`
- `display.clock`

No shared/core source edit or new Gradle dependency is intended.

Then run:

```bash
./gradlew :app:compileDebugKotlin
./gradlew :app:testDebugUnitTest
```
