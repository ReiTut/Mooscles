import org.jetbrains.compose.resources.ResourcesExtension.ResourceClassGeneration
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The Mooscles design system: tokens, components, fonts, icons and strings, in commonMain
// for Android and iOS. A pure UI library on Compose Multiplatform foundation: it depends
// on neither :shared nor Material 3.
plugins {
    // On the build classpath already (root buildscript and the root plugins block), so by id only.
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    // The gallery's LocalAppLocale is an expect object, which Kotlin still marks as Beta.
    compilerOptions { freeCompilerArgs.add("-Xexpect-actual-classes") }
    android {
        namespace = "com.mooscles.designsystem"
        compileSdk = 37
        minSdk = 26
        // Packs the Compose resources (fonts, strings, files) into the Android assets.
        androidResources { enable = true }
        withHostTest { isIncludeAndroidResources = true }
        withDeviceTestBuilder { sourceSetTreeName = "test" }.configure {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            // CMP 1.12.2 maps to Jetpack Compose 1.12.1 on Android, the version the :app BOM
            // already uses. api: Res, FontFamily and the composables are part of the public API.
            api("org.jetbrains.compose.runtime:runtime:1.12.2")
            api("org.jetbrains.compose.foundation:foundation:1.12.2")
            api("org.jetbrains.compose.ui:ui:1.12.2")
            api("org.jetbrains.compose.animation:animation:1.12.2")
            api("org.jetbrains.compose.components:components-resources:1.12.2")
        }
        // Pure tests (tokens, contrast, formatting): the JVM host and the iOS simulator.
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        getByName("androidHostTest").dependencies {
            implementation(kotlin("test-junit"))
            implementation("junit:junit:4.13.2")
        }
        // UI tests (runComposeUiTest) in src/uiTest: the iOS simulator and the emulator, never
        // the JVM host, where runComposeUiTest would need Robolectric.
        iosTest {
            kotlin.srcDir("src/uiTest/kotlin")
            dependencies {
                implementation("org.jetbrains.compose.ui:ui-test:1.12.2")
            }
        }
        getByName("androidDeviceTest") {
            kotlin.srcDir("src/uiTest/kotlin")
            dependencies {
                implementation(kotlin("test-junit"))
                implementation("org.jetbrains.compose.ui:ui-test:1.12.2")
                implementation("androidx.test:runner:1.7.0")
                implementation("androidx.test.ext:junit:1.3.0")
                implementation("androidx.compose.ui:ui-test-junit4:1.12.1")
                implementation("androidx.compose.ui:ui-test-manifest:1.12.1")
            }
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "com.mooscles.designsystem.resources"
    generateResClass = ResourceClassGeneration.Always
}
