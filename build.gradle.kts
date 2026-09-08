// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

// BEGIN METHODMESH DOCUMENTATION HYGIENE
tasks.register<Exec>("checkMethodMeshDocumentation") {
    group = "verification"
    description = "Check MethodMesh documentation ownership and canonical-source hygiene."
    workingDir = rootProject.projectDir
    commandLine(
        "python3",
        "tools/documentation/check_methodmesh_documentation.py",
        "--repo",
        rootProject.projectDir.absolutePath
    )
}
// END METHODMESH DOCUMENTATION HYGIENE
