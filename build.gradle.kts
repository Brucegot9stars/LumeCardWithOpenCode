plugins {
    kotlin("multiplatform") version "2.4.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.0" apply false
    id("org.jetbrains.compose") version "1.11.1" apply false
    id("app.cash.sqldelight") version "2.3.2" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.0" apply false
    id("com.android.library") version "9.2.1" apply false
    id("com.android.application") version "9.2.1" apply false
}

subprojects {
    configurations.configureEach {
        resolutionStrategy {
            force(
                "androidx.lifecycle:lifecycle-runtime:2.8.7",
                "androidx.lifecycle:lifecycle-runtime-compose:2.8.7",
                "androidx.lifecycle:lifecycle-common:2.8.7",
            )
        }
    }
}
