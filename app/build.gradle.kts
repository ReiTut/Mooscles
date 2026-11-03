plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "com.mooscles"
    compileSdk = 37
    buildToolsVersion = "36.0.0"
    defaultConfig {
        applicationId = "com.mooscles"
        minSdk = 26
        targetSdk = 35
        versionCode = 24
        versionName = "1.5.3-beta.1"
        // The updater runs everywhere except the side-by-side `preview` build (below).
        buildConfigField("boolean", "UPDATER_ENABLED", "true")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true; buildConfig = true }
    // Both supported languages must remain available for offline in-app switching in bundles too.
    bundle { language { enableSplit = false } }
    buildTypes {
        // Installs beside the distributed app (own applicationId, own data, own launcher entry) and
        // can never update or replace it: the updater is off. Signed with the debug key like debug.
        create("preview") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".preview"
            versionNameSuffix = "-preview"
            buildConfigField("boolean", "UPDATER_ENABLED", "false")
            matchingFallbacks += listOf("debug")
        }
        // A release-like local measurement build. Use the existing distributed
        // certificate, package and data instead of creating another application.
        create("performance") {
            initWith(getByName("release"))
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            // Host test classes run in parallel JVMs. Each fork is its own process, so
            // process-wide singletons (Robolectric sandbox, the cached DataStore) never
            // cross forks. Gradle also caps forks at org.gradle.workers.max (4 here).
            it.maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceIn(1, 4)
            // Gradle's default test heap is 512 MiB; leave room so GC pauses do not
            // eat into wall-clock waits such as Compose's 60 s idle timeout.
            it.maxHeapSize = "1g"
        }
    }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    // LargeHistoryFixture lives with the :shared common tests, which use it too.
    sourceSets.getByName("test").kotlin.directories.add("../shared/src/testFixtures/kotlin")
}
// The UI contract lives in :shared, which the Compose compiler does not process. This keeps the
// stability these types had in :app; see the file for the list and why it has no wildcards.
composeCompiler { stabilityConfigurationFiles.add(layout.projectDirectory.file("compose-stability.conf")) }
// With AGP's built-in Kotlin the option above is not an input of the compile tasks, so an edit to
// the file alone would leave them UP-TO-DATE. Declare the file so editing it recompiles.
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    inputs.file("compose-stability.conf").withPropertyName("composeStabilityConfiguration")
        .withPathSensitivity(PathSensitivity.RELATIVE)
}
dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    // Brings Room, the SQLite driver and kotlinx-serialization through its api dependencies.
    implementation(project(":shared"))
    // Design system (tokens, components, fonts, strings); Compose Multiplatform 1.12.2 maps to the
    // Jetpack Compose 1.12.1 of the BOM above, so it changes no Compose version.
    implementation(project(":designsystem"))
    // The redesigned screens (Compose Multiplatform, on :shared and :designsystem): no new
    // library and no version change, only the module itself.
    implementation(project(":screens"))
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.navigation:navigation-compose:2.10.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.datastore:datastore-preferences:1.3.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("androidx.work:work-runtime-ktx:2.12.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    testImplementation("org.robolectric:robolectric:4.17")
    testImplementation("androidx.test:core:1.7.0")
    testImplementation("androidx.room:room-testing:2.8.5")
    testImplementation("androidx.work:work-testing:2.12.0")
    testImplementation(composeBom)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("androidx.test:rules:1.7.0")
    testImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test:rules:1.7.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    androidTestImplementation("androidx.room:room-testing:2.8.5")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
