plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.methodmesh"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.methodmesh"
        minSdk = 27
        targetSdk = 36
        versionCode = 5
        versionName = "2.1.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

// Generate the standalone module index from module-owned source files. This
// keeps discovery automatic without runtime dex reflection or a central list
// of capability implementations.
val generateMethodMeshModuleIndex = tasks.register("generateMethodMeshModuleIndex") {
    val sourceRoot = file("src/main/java/com/example/methodmesh/modules")
    val outputFile = layout.buildDirectory.file("generated/res/methodmeshModuleIndex/raw/methodmesh_module_index.txt")
    inputs.files(fileTree(sourceRoot) { include("**/*Module.kt") })
    outputs.file(outputFile)
    doLast {
        val modules = sourceRoot.walkTopDown()
            .filter { it.isFile && it.name.endsWith("Module.kt") }
            .mapNotNull { file ->
                val packageName = Regex("(?m)^package\\s+([A-Za-z0-9_.]+)").find(file.readText())?.groupValues?.get(1)
                val objectName = Regex("(?m)^object\\s+([A-Za-z0-9_]+Module)\\s*:").find(file.readText())?.groupValues?.get(1)
                if (packageName != null && objectName != null) "$packageName.$objectName" else null
            }
            .sorted()
            .toList()
        val target = outputFile.get().asFile
        target.parentFile.mkdirs()
        target.writeText(modules.joinToString("\n") + "\n")
    }
}
android.sourceSets["main"].res.srcDir(file("$buildDir/generated/res/methodmeshModuleIndex"))
tasks.named("preBuild").configure { dependsOn(generateMethodMeshModuleIndex) }

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    // Force a modern FragmentActivity implementation. Older transitive Fragment
    // versions reject Activity Result API request codes above 16 bits and crash
    // when the ZXing ScanContract is launched from Compose.
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.biometric:biometric:1.2.0-alpha05")
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("com.google.mlkit:barcode-scanning:17.3.0")
    implementation("com.google.mlkit:translate:17.0.3")
    implementation("com.google.android.gms:play-services-mlkit-document-scanner:16.0.0")
    implementation("com.github.mik3y:usb-serial-for-android:v3.10.0")
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.78.1")
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
