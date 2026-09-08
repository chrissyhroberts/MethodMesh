# Fix v0.2.1

## Compile failure fixed

`CryptographyModule.dependencies()` now matches the live MethodMesh module contract exactly:

```kotlin
import com.example.methodmesh.modules.ModuleDependency

override fun dependencies(): List<ModuleDependency> = emptyList()
```

The previous `override fun dependencies() = emptyList()` inferred `List<Nothing>` and did not satisfy the overridden `List<ModuleDependency>` return type under the current Kotlin/compiler API. The associated generic type-inference errors were cascading from the same line.

## Standalone rule

No external libraries, Maven dependencies, Gradle changes, manifest changes, or files outside the `cryptography/` capability folder are required.
