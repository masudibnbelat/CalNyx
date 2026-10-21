plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// Automatic versioning (same scheme as Tk Koi Gelo): versionName is
// the latest git tag ("v1.1.0" -> "1.1.0"), versionCode is the git
// commit count — both read from git at build time, never hand-edited.
// Release flow: commit -> git tag vX.Y.Z -> build (the APK stamps
// itself with that tag) -> push -> GitHub release with the same tag.
// Before a release build, `git describe --tags --abbrev=0` must
// print the intended tag. If git or a tag is missing, the build
// falls back to 1.0.0 so local builds never break.
fun gitText(vararg args: String): String {
    return try {
        val process = ProcessBuilder("git", *args)
            .directory(rootDir)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
        val text = process.inputStream.bufferedReader().readText().trim()
        if (process.waitFor() == 0) text else ""
    } catch (e: Exception) {
        ""
    }
}

val appVersionName: String = gitText("describe", "--tags", "--abbrev=0")
    .removePrefix("v")
    .ifEmpty { "1.0.0" }
val appVersionCode: Int = gitText("rev-list", "--count", "HEAD").toIntOrNull() ?: 1

android {
    namespace = "com.calnyx.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.calnyx.app"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Core
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.8.3")

    // ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Math expression evaluator
    implementation("net.objecthunter:exp4j:0.4.8")

    // Debug
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
