plugins {
    kotlin("multiplatform")
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.0"
    id("app.cash.sqldelight") version "2.0.1"
}

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "17"
            }
        }
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "shared"
            isStatic = true
        }
    }

    jvm("desktop")

    sourceSets {
        val commonMain by getting {
            dependencies {
                // Coroutines
                implementation(Dependencies.kotlinxCoroutines)

                // DateTime
                implementation(Dependencies.kotlinxDateTime)

                // Serialization
                implementation(Dependencies.kotlinxSerialization)

                // SQLDelight
                implementation(Dependencies.sqlDelightRuntime)
                implementation(Dependencies.sqlDelightCoroutines)

                // Ktor
                implementation(Dependencies.ktorClient)
                implementation(Dependencies.ktorContentNegotiation)
                implementation(Dependencies.ktorSerialization)

                // Koin
                implementation(Dependencies.koinCore)
            }
        }

        val androidMain by getting {
            dependencies {
                implementation(Dependencies.ktorOkhttp)
                implementation(Dependencies.sqlDelightAndroid)
            }
        }

        val iosMain by getting {
            dependencies {
                implementation(Dependencies.ktorDarwin)
                implementation(Dependencies.sqlDelightIos)
            }
        }

        val desktopMain by getting {
            dependencies {
                implementation(Dependencies.ktorOkhttp)
                implementation(Dependencies.sqlDelightJvm)
            }
        }
    }
}

sqldelight {
    databases {
        create("LumeCardDatabase") {
            packageName.set("com.lumecard.shared.database")
        }
    }
}
