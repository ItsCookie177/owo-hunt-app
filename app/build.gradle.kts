plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "dev.owohunt.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.owohunt.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 2
        versionName = "0.1.2"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    buildFeatures.compose = true

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":automation"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.activity.compose)
    implementation(libs.core)

    implementation(libs.shizuku.api)

    implementation(libs.miuix.ui)
    implementation(libs.miuix.core)
    implementation(libs.miuix.blur)
    implementation(libs.miuix.icons)
    implementation(libs.miuix.preference)
    implementation(libs.miuix.squircle)
}
