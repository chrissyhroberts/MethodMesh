# Fix v0.3.1

Fixes Android/Kotlin Compose compilation failure in `CryptoUx.kt`:

`Cannot access 'val RowColumnParentData?.weight: Float': it is internal in file.`

Cause: the file explicitly imported `androidx.compose.foundation.layout.weight`. In the MethodMesh Compose toolchain, that symbol resolves to an internal layout implementation detail. `CryptoChoiceRow` already executes inside a `RowScope`, where the public `Modifier.weight(...)` extension is available from the receiver scope.

Fix: remove the explicit `weight` import. No external dependency or host change is required.
