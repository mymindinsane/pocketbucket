import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest

plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.compose) apply false
    alias(libs.plugins.android.application) apply false
}

// CI selects one available simulator and shares it across both native test suites.
subprojects {
    tasks.withType<KotlinNativeSimulatorTest>().configureEach {
        providers.environmentVariable("IOS_SIMULATOR_ID").orNull?.let { device.set(it) }
    }
}
