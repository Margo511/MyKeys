plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

val supabasePublishableKey = providers
    .gradleProperty("mykeys.supabase.publishableKey")
    .orElse(providers.environmentVariable("MY_KEYS_SUPABASE_PUBLISHABLE_KEY"))
    .orElse("")
    .get()
require(
    supabasePublishableKey.isBlank() ||
        Regex("^sb_publishable_[A-Za-z0-9_-]+$").matches(supabasePublishableKey),
) { "My Keys accepts only a Supabase publishable key in the Android client." }

fun String.asBuildConfigString(): String =
    "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

dependencies {
    implementation(projects.shared)
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.foundation)
    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)
}

android {
    namespace = "app.mykeys.mobile"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "app.mykeys.mobile"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    buildTypes {
        debug {
            buildConfigField("String", "SUPABASE_URL", "\"http://10.0.2.2:54321\"")
            buildConfigField(
                "String",
                "SUPABASE_PUBLISHABLE_KEY",
                supabasePublishableKey.asBuildConfigString(),
            )
        }
        release {
            isMinifyEnabled = false
            buildConfigField(
                "String",
                "SUPABASE_URL",
                "\"${providers.gradleProperty("mykeys.supabase.url").orElse("https://configuration-required.invalid").get()}\"",
            )
            buildConfigField(
                "String",
                "SUPABASE_PUBLISHABLE_KEY",
                supabasePublishableKey.asBuildConfigString(),
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = true
        // API 36 es el último target estable soportado por AGP 9.1; el SDK 37 instalado es preliminar.
        disable += "OldTargetApi"
        warningsAsErrors = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        allWarningsAsErrors.set(true)
    }
}
