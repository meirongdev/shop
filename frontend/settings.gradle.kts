import java.util.Properties

rootProject.name = "shop-kmp"

fun resolveAndroidSdkDir(baseDir: File): String? {
    val envSdk = sequenceOf(
        System.getenv("ANDROID_HOME"),
        System.getenv("ANDROID_SDK_ROOT"),
    ).firstOrNull { !it.isNullOrBlank() }
    if (!envSdk.isNullOrBlank()) {
        return envSdk
    }

    val localProperties = baseDir.resolve("local.properties")
    if (localProperties.isFile) {
        val properties = Properties()
        localProperties.inputStream().use(properties::load)
        properties.getProperty("sdk.dir")
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
    }

    val defaultMacOsSdk = File(System.getProperty("user.home"), "Library/Android/sdk")
    return defaultMacOsSdk.takeIf(File::isDirectory)?.absolutePath
}

val androidSdkAvailable = resolveAndroidSdkDir(settingsDir) != null

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
include(":kmp:seller-app")
if (androidSdkAvailable) {
    include(":kmp:buyer-android-app")
    include(":kmp:seller-android-app")
}

// KMP modules are now relative to frontend/ directory
project(":kmp").projectDir = file("kmp")
project(":kmp:core").projectDir = file("kmp/core")
project(":kmp:ui-shared").projectDir = file("kmp/ui-shared")
project(":kmp:feature-marketplace").projectDir = file("kmp/feature-marketplace")
project(":kmp:feature-cart").projectDir = file("kmp/feature-cart")
project(":kmp:feature-order").projectDir = file("kmp/feature-order")
project(":kmp:feature-wallet").projectDir = file("kmp/feature-wallet")
project(":kmp:feature-profile").projectDir = file("kmp/feature-profile")
project(":kmp:feature-promotion").projectDir = file("kmp/feature-promotion")
project(":kmp:feature-auth").projectDir = file("kmp/feature-auth")
project(":kmp:buyer-app").projectDir = file("kmp/buyer-app")
project(":kmp:seller-app").projectDir = file("kmp/seller-app")
if (androidSdkAvailable) {
    project(":kmp:buyer-android-app").projectDir = file("kmp/buyer-android-app")
    project(":kmp:seller-android-app").projectDir = file("kmp/seller-android-app")
}
