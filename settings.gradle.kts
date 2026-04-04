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

// Remap KMP modules to new frontend/ location
project(":kmp").projectDir = file("frontend/kmp")
project(":kmp:core").projectDir = file("frontend/kmp/core")
project(":kmp:ui-shared").projectDir = file("frontend/kmp/ui-shared")
project(":kmp:feature-marketplace").projectDir = file("frontend/kmp/feature-marketplace")
project(":kmp:feature-cart").projectDir = file("frontend/kmp/feature-cart")
project(":kmp:feature-order").projectDir = file("frontend/kmp/feature-order")
project(":kmp:feature-wallet").projectDir = file("frontend/kmp/feature-wallet")
project(":kmp:feature-profile").projectDir = file("frontend/kmp/feature-profile")
project(":kmp:feature-promotion").projectDir = file("frontend/kmp/feature-promotion")
project(":kmp:feature-auth").projectDir = file("frontend/kmp/feature-auth")
project(":kmp:buyer-app").projectDir = file("frontend/kmp/buyer-app")
project(":kmp:buyer-android-app").projectDir = file("frontend/kmp/buyer-android-app")
project(":kmp:seller-app").projectDir = file("frontend/kmp/seller-app")
project(":kmp:seller-android-app").projectDir = file("frontend/kmp/seller-android-app")
