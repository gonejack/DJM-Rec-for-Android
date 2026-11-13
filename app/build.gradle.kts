import java.io.FileInputStream
import java.util.Properties
import com.android.build.api.variant.BuildConfigField
import com.android.build.api.variant.HostTestBuilder

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// ── Keystore helpers (top-level so they can be used by signingConfigs) ─────
// Release signing is configured only when the gitignored keystore.properties exists.
fun loadProperties(path: String): Properties = Properties().apply {
    val source = rootProject.file(path)
    require(source.exists()) { "Missing $path" }
    FileInputStream(source).use(::load)
}

val appVersion = loadProperties("version.properties")
val keystoreFile = rootProject.file("keystore.properties")
val keystore = if (keystoreFile.exists()) loadProperties("keystore.properties") else null
val streamingFile = rootProject.file("streaming.properties")
val streaming = if (streamingFile.exists()) loadProperties("streaming.properties") else null
val twitchClientId = providers.environmentVariable("TWITCH_CLIENT_ID").orNull
    ?: streaming?.getProperty("TWITCH_CLIENT_ID").orEmpty()

android {
    namespace = "com.audiopro.djmrec"
    compileSdk = 35
    // Pinned so CI (and every dev machine) builds native code against the exact same
    // NDK — avoids "works locally, fails/behaves differently in CI" native-build drift.
    ndkVersion = "26.1.10909125"

    val releaseSigning = keystore?.let { properties ->
        signingConfigs.create("release") {
            storeFile = rootProject.file(properties.getProperty("storeFile"))
            storePassword = properties.getProperty("storePassword")
            keyAlias = properties.getProperty("keyAlias")
            keyPassword = properties.getProperty("keyPassword")
        }
    }

    defaultConfig {
        applicationId = "com.audiopro.djmrec"
        minSdk = 29
        targetSdk = 34
        versionCode = appVersion.getProperty("VERSION_CODE").toInt()
        versionName = appVersion.getProperty("VERSION_NAME")

        // Only ship arm64-v8a: all modern DJ-capable Android hardware (USB-C host + UAC2)
        // is 64-bit ARM. Keeping a single ABI keeps the native audio path easy to validate.
        ndk {
            abiFilters += listOf("arm64-v8a")
        }

        externalNativeBuild {
            cmake {
                cppFlags += listOf("-std=c++17", "-fexceptions", "-frtti")
                arguments += listOf(
                    "-DANDROID_STL=c++_shared",
                    "-DANDROID_ARM_NEON=TRUE"
                )
            }
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
            signingConfig = releaseSigning
        }
        debug {
            isDebuggable = true
            // So debug and release can be installed side-by-side
            applicationIdSuffix = ".debug"
        }
        create("local") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        // Compose Preview reuses the unit-test resource APK for XML resources and themes.
        unitTests.isIncludeAndroidResources = true
    }

    packaging {
        jniLibs {
            // We statically link Oboe/FLAC into libdjmrec_audio.so, so only
            // the shared C++ runtime and our own library need to ship.
            useLegacyPackaging = false
        }
    }
}

// Configure generated fields and versioned APK names through the public Variant API.
androidComponents {
    beforeVariants { variantBuilder ->
        // Preview tasks are registered for every variant, including local and release.
        variantBuilder.hostTests.getValue(HostTestBuilder.UNIT_TEST_TYPE).enable = true
    }

    onVariants { variant ->
        val buildConfigFields = checkNotNull(variant.buildConfigFields)
        buildConfigFields.put(
            "TWITCH_CLIENT_ID",
            BuildConfigField("String", "\"${twitchClientId.replace("\"", "\\\"")}\"", null)
        )

        val versionName = appVersion.getProperty("VERSION_NAME")
        variant.outputs.forEach { output ->
            output.outputFileName.set("DJM-Rec-for-Android-v$versionName-${variant.name}.apk")
        }
    }
}

dependencies {
    testImplementation(kotlin("test-junit"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-service:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.activity:activity-ktx:1.9.1")
    implementation("androidx.activity:activity-compose:1.9.1")

    val composeBom = platform("androidx.compose:compose-bom:2026.06.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("com.github.pedroSG94.RootEncoder:library:2.7.2")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
