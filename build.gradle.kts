// TOP-LEVEL build.gradle.kts

plugins {
    // This manages the Android App version
    alias(libs.plugins.android.application) apply false

    // This manages the Kotlin version
    alias(libs.plugins.kotlin.android) apply false

    // This manages the Compose compiler version
    alias(libs.plugins.kotlin.compose) apply false
}