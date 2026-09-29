import java.util.Properties

plugins {
    alias(libs.plugins.agp)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "fr.croustille"
    compileSdk = 37

    defaultConfig {
        applicationId = "fr.croustille"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        vectorDrawables { useSupportLibrary = true }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.material3)
    implementation(libs.material3.window)
    implementation(libs.ui)
    implementation(libs.ui.tooling.preview)
    implementation(libs.material.icons)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime)
    implementation(libs.navigation.compose)
    implementation(libs.retrofit)
    implementation(libs.retrofit.json)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization)
    implementation(libs.coroutines)
    implementation(libs.coil)
    implementation(libs.coil.network)
    implementation(libs.datastore)
    implementation(libs.security.crypto)
    implementation(libs.core.ktx)
    implementation(libs.work.runtime)
    implementation(libs.jsoup)
}
