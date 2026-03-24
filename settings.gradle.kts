rootProject.name = "shop-kmp"

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

include(":kmp:core")
include(":kmp:ui-shared")
include(":kmp:feature-marketplace")
include(":kmp:feature-cart")
include(":kmp:feature-order")
include(":kmp:feature-wallet")
include(":kmp:feature-profile")
include(":kmp:feature-promotion")
include(":kmp:feature-auth")
include(":kmp:buyer-app")
include(":kmp:buyer-android-app")
include(":kmp:seller-app")
include(":kmp:seller-android-app")
