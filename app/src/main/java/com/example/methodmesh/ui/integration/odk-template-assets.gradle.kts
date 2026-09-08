// MethodMesh ODK/Kobo design-template projection.
// Apply once from the app-module build.gradle.kts:
// apply(from = "src/main/java/com/example/methodmesh/ui/integration/odk-template-assets.gradle.kts")

val methodMeshOdkGenerator = layout.projectDirectory.file(
    "src/main/java/com/example/methodmesh/ui/integration/generate_odk_template_assets.py"
)
val methodMeshOdkSourceRoot = layout.projectDirectory.dir("src/main/java/com/example/methodmesh")
val methodMeshOdkAssetsRoot = layout.projectDirectory.dir("src/main/assets")

val generateMethodMeshOdkTemplateAssets = tasks.register<Exec>("generateMethodMeshOdkTemplateAssets") {
    group = "methodmesh"
    description = "Projects module-owned XLSForms from docs/ into the ODK/Kobo Forms asset catalogue."

    // Watch every workbook under a docs directory. The generator itself filters to
    // canonical example_odk*.xlsx names or workbooks that structurally look like XLSForms.
    inputs.files(
        fileTree(methodMeshOdkSourceRoot) {
            include("**/docs/**/*.xlsx")
        }
    )
    inputs.file(methodMeshOdkGenerator)
    outputs.dir(methodMeshOdkAssetsRoot.dir("methodmesh/odk_templates"))

    commandLine(
        "python3",
        methodMeshOdkGenerator.asFile.absolutePath,
        "--source-root", methodMeshOdkSourceRoot.asFile.absolutePath,
        "--assets-root", methodMeshOdkAssetsRoot.asFile.absolutePath,
        "--no-authoritative-validation"
    )

    doFirst {
        logger.lifecycle("MethodMesh: rebuilding ODK/Kobo XLSForm catalogue from module docs/ folders")
    }
}

// preBuild is a convenient umbrella, but depend directly from every Android asset merge as
// well. That closes a race/ordering loophole and guarantees index.json exists before assets
// are packaged for debug/release variants.
tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(generateMethodMeshOdkTemplateAssets)
}
tasks.matching { it.name.matches(Regex("merge.*Assets")) }.configureEach {
    dependsOn(generateMethodMeshOdkTemplateAssets)
}

// Explicit human-facing alias for the batch validation workflow. The generator embeds the
// validation report in index.json; this task is useful when reviewing the library without
// running a full APK build.
tasks.register("validateMethodMeshOdkForms") {
    group = "methodmesh"
    description = "Regenerates and validates all module-owned MethodMesh XLSForms."
    dependsOn(generateMethodMeshOdkTemplateAssets)
}

tasks.register<Exec>("validateMethodMeshOdkFormsAuthoritative") {
    group = "methodmesh"
    description = "Runs optional pyxform/ODK Validate checks for every module-owned XLSForm."
    inputs.files(fileTree(methodMeshOdkSourceRoot) { include("**/docs/**/*.xlsx") })
    inputs.file(methodMeshOdkGenerator)
    outputs.dir(methodMeshOdkAssetsRoot.dir("methodmesh/odk_templates"))
    commandLine(
        "python3", methodMeshOdkGenerator.asFile.absolutePath,
        "--source-root", methodMeshOdkSourceRoot.asFile.absolutePath,
        "--assets-root", methodMeshOdkAssetsRoot.asFile.absolutePath
    )
}
