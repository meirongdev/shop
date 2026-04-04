plugins {
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.android.application)
}

android {
    namespace = "dev.meirong.shop.seller.androidapp"
    compileSdk = 35
    defaultConfig {
        applicationId = "dev.meirong.shop.seller"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }
}

dependencies {
    implementation(project(":kmp:seller-app"))
    implementation(libs.activity.compose)
}
