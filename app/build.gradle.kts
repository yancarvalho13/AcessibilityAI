plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val envVars: Map<String, String> = run {
    val envFile = rootProject.file(".env")
    if (!envFile.exists()) {
        emptyMap()
    } else {
        envFile.readLines()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("#") && it.contains("=") }
            .associate { line ->
                val idx = line.indexOf('=')
                val key = line.substring(0, idx).trim()
                val rawValue = line.substring(idx + 1).trim()
                val value = rawValue.removePrefix("\"").removeSuffix("\"")
                key to value
            }
    }
}

fun envOrDefault(name: String, defaultValue: String = ""): String {
    return envVars[name] ?: System.getenv(name) ?: defaultValue
}

fun asBuildConfigString(value: String): String {
    val escaped = value.replace("\\", "\\\\").replace("\"", "\\\"")
    return "\"$escaped\""
}

android {
    namespace = "com.example.myapplication"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.myapplication"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField(
            "String",
            "GEMINI_API_KEY",
            asBuildConfigString(envOrDefault("GEMINI_API_KEY")),
        )
        buildConfigField(
            "String",
            "GEMINI_PHOTO_PROMPT",
            asBuildConfigString(envOrDefault("GEMINI_PHOTO_PROMPT", "O que esta acontecendo nesta foto?")),
        )
        buildConfigField(
            "String",
            "GEMINI_VIDEO_PROMPT",
            asBuildConfigString(envOrDefault("GEMINI_VIDEO_PROMPT", "O que esta acontecendo neste video?")),
        )
        buildConfigField(
            "String",
            "GEMINI_MODEL",
            asBuildConfigString(envOrDefault("GEMINI_MODEL", "gemini-2.5-flash")),
        )
        buildConfigField(
            "String",
            "GEMINI_API_VERSION",
            asBuildConfigString(envOrDefault("GEMINI_API_VERSION", "v1beta")),
        )
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

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.video)
    implementation(libs.androidx.camera.view)
    implementation(libs.okhttp)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
