@file:OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.android.kmp.library)
}

kotlin {
    androidLibrary {
        namespace = "dev.meirong.shop.seller"
        compileSdk = 35
        minSdk = 26
    }
    iosArm64 {
        binaries.framework {
            baseName = "SellerApp"
            isStatic = true
        }
    }
    iosSimulatorArm64 {
        binaries.framework {
            baseName = "SellerApp"
            isStatic = true
        }
    }
    wasmJs {
        binaries.executable()
        browser {
            commonWebpackConfig {
                outputFileName = "seller-app.js"
            }
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":kmp:core"))
                implementation(project(":kmp:ui-shared"))
                implementation(project(":kmp:feature-marketplace"))
                implementation(project(":kmp:feature-order"))
                implementation(project(":kmp:feature-wallet"))
                implementation(project(":kmp:feature-profile"))
                implementation(project(":kmp:feature-promotion"))
                implementation(project(":kmp:feature-auth"))
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                implementation(libs.navigation.compose)
                implementation(libs.lifecycle.viewmodel.compose)
                implementation(libs.koin.compose)
                implementation(libs.kotlinx.serialization.json)
            }
        }
    }
}
