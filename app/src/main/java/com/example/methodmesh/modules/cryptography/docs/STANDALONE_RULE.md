# Standalone capability rule

A MethodMesh capability is acceptable only if:

1. its source lives entirely under its own module folder;
2. module discovery finds it automatically;
3. adding it requires no registry edit;
4. adding it requires no Gradle dependency edit;
5. removing the folder leaves the rest of MethodMesh compiling;
6. it does not copy another capability's internals merely to avoid a dependency;
7. optional cross-capability workflows compose through public Method IDs rather than source imports.

Cryptography v0.3.1 is designed to this rule and declares no module dependencies.
