import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose)
    alias(libs.plugins.android.application)
}
kotlin {
    jvmToolchain(21)
    androidTarget { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
    if (System.getProperty("os.name") == "Mac OS X") {
        listOf(iosArm64(), iosSimulatorArm64(), iosX64()).forEach { target ->
            target.binaries.framework {
                baseName = "PocketBucket"
                isStatic = true
            }
        }
    }
    sourceSets {
        commonMain.dependencies {
            implementation(project(":core"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.lifecycle.viewmodel)
            implementation(libs.lifecycle.runtime)
            implementation(libs.serialization.json)
            implementation(libs.ktor.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.coroutines.test)
        }
        androidMain.dependencies {
            implementation(libs.android.activity)
            implementation(libs.ktor.okhttp)
        }
        if (System.getProperty("os.name") == "Mac OS X") {
            iosMain.dependencies { implementation(libs.ktor.darwin) }
        }
    }
}
android {
    namespace = "dev.pocketbucket.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "dev.pocketbucket.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
