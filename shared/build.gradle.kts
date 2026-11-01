import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Code shared by the Android app and a future iOS app. commonMain stays free of
// JVM and Android APIs; androidMain and iosMain hold the platform entry points.
plugins {
    // These plugins are already on the build classpath (root buildscript and the
    // root plugins block), so they are applied here by id only.
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("com.google.devtools.ksp")
    id("androidx.room")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    // Room's @ConstructedBy needs an expect object, which Kotlin still marks as Beta.
    compilerOptions { freeCompilerArgs.add("-Xexpect-actual-classes") }
    android {
        // Not com.mooscles: the app already generates com.mooscles.R.
        namespace = "com.mooscles.shared"
        compileSdk = 37
        minSdk = 26
        withHostTest { isIncludeAndroidResources = true }
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
            api("androidx.room:room-runtime:2.8.5")
            api("androidx.sqlite:sqlite:2.6.2")
            api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.12.0")
            // AppPreferences and RestTimerPersistence take the store ready-made; the Android
            // preferencesDataStore delegate (datastore-preferences, same 1.3.0) stays in :app.
            api("androidx.datastore:datastore-preferences-core:1.3.0")
            // MoosclesViewModel extends the multiplatform ViewModel; same version as the :app lifecycle libraries.
            api("androidx.lifecycle:lifecycle-viewmodel:2.11.0")
            // @VisibleForTesting on the view model's test seams. 1.11.0: the latest stable release on 2026-10-28,
            // with iosArm64 and iosSimulatorArm64 artifacts.
            implementation("androidx.annotation:annotation:1.11.0")
        }
        androidMain.dependencies {
            // AndroidSQLiteDriver (the system SQLite), at the version Room 2.8.5 is built against.
            // sqlite 2.7.x pairs with Room 3 and drops restricted APIs that 2.8.x was compiled with.
            //noinspection GradleDependency
            api("androidx.sqlite:sqlite-framework:2.6.2")
        }
        iosMain.dependencies {
            // BundledSQLiteDriver: SQLite compiled into the app, at the version Room 2.8.5 is built against.
            //noinspection GradleDependency
            implementation("androidx.sqlite:sqlite-bundled:2.6.2")
        }
        // LargeHistoryFixture, shared with the :app host tests (app/build.gradle.kts).
        commonTest { kotlin.srcDir("src/testFixtures/kotlin") }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
        }
        // JVM equivalence tests: the common replacements against the java.* APIs they replace.
        getByName("androidHostTest").dependencies {
            implementation(kotlin("test-junit"))
            implementation("junit:junit:4.13.2")
        }
    }
}

// Same exported files as before the move (app/schemas): the plugin writes them only here.
room { schemaDirectory("$projectDir/schemas") }

// One Room processor per target; never kspCommonMainMetadata, which would generate a second
// WorkoutDatabase_Impl into the common sources.
dependencies {
    add("kspAndroid", "androidx.room:room-compiler:2.8.5")
    add("kspIosArm64", "androidx.room:room-compiler:2.8.5")
    add("kspIosSimulatorArm64", "androidx.room:room-compiler:2.8.5")
}
