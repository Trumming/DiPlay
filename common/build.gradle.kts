plugins {
    id("com.android.library")
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.shilapi.xcertplay.host"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 23
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
    }

    lint {
        // NewApi (and every other check) must stay enforced: the 2026-10-02 BYD car crash was an
        // unguarded API-26 call at minSdk 23 that survived because this module was never linted.
        // Only these explicitly deferred classes are silenced:
        // - MissingPermission: Android S+ Bluetooth runtime permissions; guarded at runtime (runCatching).
        // - RestrictedApi: androidx group-prefix policy on ComponentActivity.dispatchKeyEvent overrides.
        // - StringFormatMatches: suspicious-but-legal %s-with-int formatting; needs a localization pass.
        // - ForegroundServicePermission: Android 14+ FGS-type manifest permissions; deferred (test fleet is API 25/26).
        // - MissingTranslation: untranslated brand/technical terms (carplay, wpa3, ...).
        disable += listOf(
            "MissingPermission",
            "RestrictedApi",
            "StringFormatMatches",
            "ForegroundServicePermission",
            "MissingTranslation",
        )
    }
}

dependencies {
    api(project(":shared"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    testImplementation("org.robolectric:robolectric:4.17")
}
