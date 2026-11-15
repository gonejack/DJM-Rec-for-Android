import com.android.build.api.variant.BuildConfigField
import com.android.build.api.variant.HostTestBuilder
import com.ncorti.ktfmt.gradle.TrailingCommaManagementStrategy
import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    // 格式化器：./gradlew :app:ktfmtFormat 统一排版，:app:ktfmtCheck 只检查不改。
    id("com.ncorti.ktfmt.gradle") version "0.28.0"
}

// ── Keystore helpers (top-level so they can be used by signingConfigs) ─────
// Release signing is configured only when the gitignored keystore.properties exists.
fun loadProperties(path: String): Properties =
    Properties().apply {
        val source = rootProject.file(path)
        require(source.exists()) { "Missing $path" }
        FileInputStream(source).use(::load)
    }

val appVersion = loadProperties("version.properties")
val keystoreFile = rootProject.file("keystore.properties")
val keystore = if (keystoreFile.exists()) loadProperties("keystore.properties") else null
val streamingFile = rootProject.file("streaming.properties")
val streaming = if (streamingFile.exists()) loadProperties("streaming.properties") else null
val twitchClientId = providers.environmentVariable("TWITCH_CLIENT_ID").orNull ?: streaming?.getProperty("TWITCH_CLIENT_ID").orEmpty()

android {
    namespace = "com.audiopro.djmrec"
    compileSdk = 37
    // r28c aligns native libraries (including libc++_shared.so) for 16 KB page devices.
    // Keep CI and release builds pinned to this same toolchain.
    ndkVersion = "28.2.13676358"

    val releaseSigning = keystore?.let { properties ->
        signingConfigs.create("release") {
            storeFile = rootProject.file(properties.getProperty("storeFile"))
            storePassword = properties.getProperty("storePassword")
            keyAlias = properties.getProperty("keyAlias")
            keyPassword = properties.getProperty("keyPassword")
        }
    }

    defaultConfig {
        applicationId = "com.audiopro.djmrec.cb5"
        minSdk = 29
        targetSdk = 37
        versionCode = appVersion.getProperty("VERSION_CODE").toInt()
        versionName = appVersion.getProperty("VERSION_NAME")

        // Only ship arm64-v8a: all modern DJ-capable Android hardware (USB-C host + UAC2)
        // is 64-bit ARM. Keeping a single ABI keeps the native audio path easy to validate.
        ndk { abiFilters += listOf("arm64-v8a") }

        externalNativeBuild {
            cmake {
                cppFlags += listOf("-std=c++17", "-fexceptions", "-frtti")
                arguments += listOf("-DANDROID_STL=c++_shared", "-DANDROID_ARM_NEON=TRUE")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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

    // Reuse the captured hardware descriptors as JVM regression fixtures.
    sourceSets.getByName("test").resources.directories.add(rootProject.file("docs/hardware").absolutePath)

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
        buildConfigFields.put("TWITCH_CLIENT_ID", BuildConfigField("String", "\"${twitchClientId.replace("\"", "\\\"")}\"", null))

        val versionName = appVersion.getProperty("VERSION_NAME")
        variant.outputs.forEach { output -> output.outputFileName.set("DJM-Rec-for-Android-v$versionName-${variant.name}.apk") }
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

// ── 代码格式：本项目唯一的排版规则来源 ────────────────────────────────────
// 规则与根目录 .editorconfig 保持一致（Android Studio 的 Reformat Code 用同一套）。
// 不要手工调换行：跑 `./gradlew :app:ktfmtFormat` 让它决定。
ktfmt {
    // 4 空格块缩进（与 kotlin.code.style=official 一致，不要改成 googleStyle() 的 2 空格）
    kotlinLangStyle()
    // 行宽上限：超了才拆行，能放下就保持单行（必须与 .editorconfig 的 max_line_length 一致）
    maxWidth.set(160)
    blockIndent.set(4)
    continuationIndent.set(4)
    // 顺手清理未使用的 import
    removeUnusedImports.set(true)
    // 本项目不用尾随逗号：既不添加也不删除
    trailingCommaManagementStrategy.set(TrailingCommaManagementStrategy.NONE)
}

// ktfmt 插件默认把 ktfmtCheck* 挂到 check 上：只要有一个文件排版不合规，./gradlew check 就会失败。
// 全仓库已统一格式化，所以保留这个门禁；CI 上跑 :app:check 即可拦住不合规的排版。
// 需要临时绕过时用 ./gradlew check -x ktfmtCheck
