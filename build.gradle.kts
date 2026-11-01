// AGP 9 compiles Kotlin itself (built-in Kotlin) and only brings KGP 2.2.10 and
// KSP 2.2.10-2.0.2 at runtime. The classpath entries pin the newer Kotlin and KSP
// Gradle plugins, as the AGP 9.0 release notes describe.
buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21")
        classpath("com.google.devtools.ksp:symbol-processing-gradle-plugin:2.3.12")
    }
}
plugins {
    id("com.android.application") version "9.3.3" apply false
    id("com.android.kotlin.multiplatform.library") version "9.3.3" apply false
    id("androidx.room") version "2.8.5" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
    id("org.jetbrains.compose") version "1.12.2" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.21" apply false
}
