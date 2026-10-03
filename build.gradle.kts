plugins {
    id("com.android.application") version "9.4.1" apply false
    // No org.jetbrains.kotlin.android: AGP 9 compiles Kotlin itself ("built-in Kotlin") and
    // rejects that plugin. The Kotlin Gradle plugin version is still pinned to 2.4.20, because
    // plugin.compose (below) depends on kotlin-gradle-plugin at its own version and Gradle picks
    // the highest KGP on the build classpath. Keep the two Kotlin plugins below in lockstep.
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20" apply false
    // Since KSP 2.3.0, KSP is versioned independently of Kotlin (no "<kotlin>-<ksp>" suffix).
    id("com.google.devtools.ksp") version "2.3.12" apply false
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0" apply false
}
