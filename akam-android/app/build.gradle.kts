import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "app.akam"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "app.akam"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        // only ABIs akam-core is compiled for; JNA ships more, which would
        // otherwise let the APK install on devices that then crash loading akam_core
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
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
    }
}

// --- akam-core (Rust) integration ---

val cargoDir = rootProject.layout.projectDirectory.dir("../akam-core")
val jniLibsDir = layout.projectDirectory.dir("src/main/jniLibs")
val uniffiOutDir = layout.buildDirectory.dir("generated/uniffi")

// ponytail: fall back to PATH when cargo isn't in the default rustup location
val cargo = File(System.getProperty("user.home"), ".cargo/bin/cargo.exe")
    .takeIf { it.exists() }?.absolutePath ?: "cargo"

// cargo-ndk locates the NDK through ANDROID_HOME; AGP 9 no longer exposes sdkDirectory
val localProps = Properties()
rootProject.file("local.properties").takeIf { it.exists() }?.reader()?.use { localProps.load(it) }
val androidSdkDir: String? = localProps.getProperty("sdk.dir") ?: System.getenv("ANDROID_HOME")

fun Exec.rustEnv() {
    environment("PATH", System.getenv("PATH") + File.pathSeparator + File(System.getProperty("user.home"), ".cargo/bin"))
    androidSdkDir?.let { environment("ANDROID_HOME", it) }
}

val cargoNdkBuild = tasks.register<Exec>("cargoNdkBuild") {
    description = "Compiles akam-core for Android ABIs into jniLibs"
    workingDir = cargoDir.asFile
    inputs.dir(cargoDir.dir("src"))
    inputs.files(cargoDir.file("Cargo.toml"), cargoDir.file("build.rs"))
    outputs.dir(jniLibsDir)
    rustEnv()
    commandLine(
        cargo, "ndk",
        "-t", "arm64-v8a",
        "-t", "x86_64",
        "-o", jniLibsDir.asFile.absolutePath,
        "build", "--release"
    )
}

val generateUniffiBindings = tasks.register<Exec>("generateUniffiBindings") {
    description = "Generates UniFFI Kotlin bindings from the compiled library"
    dependsOn(cargoNdkBuild)
    workingDir = cargoDir.asFile
    inputs.dir(cargoDir.dir("src"))
    outputs.dir(uniffiOutDir)
    rustEnv()
    commandLine(
        cargo, "run", "--features", "cli", "--bin", "uniffi-bindgen", "--",
        "generate",
        "--library", jniLibsDir.file("arm64-v8a/libakam_core.so").asFile.absolutePath,
        "--language", "kotlin",
        "--out-dir", uniffiOutDir.get().asFile.absolutePath
    )
}

android.sourceSets.getByName("main").kotlin.directories.add(uniffiOutDir.get().asFile.absolutePath)

tasks.named("preBuild") {
    dependsOn(generateUniffiBindings)
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.jna) {
        artifact { type = "aar" }
    }
    testImplementation(libs.junit)
}
