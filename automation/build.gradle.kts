plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "dev.owohunt.automation"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    api(libs.coroutines.core)
    api(project(":core"))
    implementation(libs.shizuku.api)
}
