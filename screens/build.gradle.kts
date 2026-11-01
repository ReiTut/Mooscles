import org.jetbrains.compose.resources.ResourcesExtension.ResourceClassGeneration
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// The redesigned Mooscles screens, in commonMain for Android and iOS. They read the UI contract
// of :shared (state and actions) and draw it with the :designsystem components only: Compose
// Multiplatform foundation, no Material 3. Their strings are their own Compose resources.
plugins {
    // On the build classpath already (root buildscript and the root plugins block), so by id only.
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    // As in :designsystem, so a platform expect object (haptics, locale) needs no build change.
    compilerOptions { freeCompilerArgs.add("-Xexpect-actual-classes") }
    android {
        namespace = "com.mooscles.screens"
        compileSdk = 37
        minSdk = 26
        // Packs the Compose resources (strings) into the Android assets.
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
            // api: the screens take the contract's types and the design system's in their signatures.
            api(project(":shared"))
            api(project(":designsystem"))
            // The same Compose Multiplatform 1.12.2 the design system uses (Jetpack Compose 1.12.1
            // on Android, the :app BOM's version); declared here because this module compiles
            // composables and generates its own Res.
            api("org.jetbrains.compose.runtime:runtime:1.12.2")
            api("org.jetbrains.compose.foundation:foundation:1.12.2")
            api("org.jetbrains.compose.ui:ui:1.12.2")
            api("org.jetbrains.compose.animation:animation:1.12.2")
            api("org.jetbrains.compose.components:components-resources:1.12.2")
            // Time zone and calendar math only (decision 10); every text comes from our own strings.
            // 0.8.0 is the stable line (the -0.6.x-compat builds keep the old Instant, unused here).
            // api: the share card (ShareCards.from) takes the device's TimeZone from :app.
            api("org.jetbrains.kotlinx:kotlinx-datetime:0.8.0")
        }
        // Pure tests (mappings, formatting, rules): the JVM host and the iOS simulator.
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
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
    packageOfResClass = "com.mooscles.screens.resources"
    generateResClass = ResourceClassGeneration.Always
}

// The UI contract lives in :shared, which the Compose compiler does not process. The screens read
// the same list :app uses (decision 9 of the phase 3 plan), so WorkoutExerciseUi, SetUi and the
// rest count as stable here too and an emission does not recompose every row.
val composeStability = rootProject.layout.projectDirectory.file("app/compose-stability.conf")
composeCompiler { stabilityConfigurationFiles.add(composeStability) }
// With AGP's built-in Kotlin the option above is not an input of the compile tasks, so an edit to
// the file alone would leave them UP-TO-DATE. Declare the file so editing it recompiles.
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
    inputs.file(composeStability).withPropertyName("composeStabilityConfiguration")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}
