import org.gradle.language.base.plugins.LifecycleBasePlugin

plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.multiplatform) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.kmp.library) apply false
}

fun hasUsableXcode(): Boolean =
    runCatching {
        ProcessBuilder("/usr/bin/xcrun", "xcodebuild", "-version")
            .redirectErrorStream(true)
            .start()
            .let { process ->
                process.inputStream.bufferedReader().use { it.readText() }
                process.waitFor() == 0
            }
    }.getOrDefault(false)

val xcodeAvailable = hasUsableXcode()

val aggregateFrontendTests = tasks.register("test") {
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    description = "Runs frontend KMP and Android unit tests for all included modules and available toolchains."
}

subprojects {
    plugins.withId("org.jetbrains.kotlin.multiplatform") {
        aggregateFrontendTests.configure {
            dependsOn(tasks.matching { it.name == "wasmJsTest" })
            if (xcodeAvailable) {
                dependsOn(tasks.matching { it.name == "iosSimulatorArm64Test" })
            }
        }
    }
    plugins.withId("com.android.application") {
        aggregateFrontendTests.configure {
            dependsOn(tasks.matching { it.name == "testDebugUnitTest" })
        }
    }
}
