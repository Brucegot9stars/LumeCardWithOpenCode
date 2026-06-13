plugins {
    id("com.android.library")
    kotlin("multiplatform")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("app.cash.sqldelight")
}

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "17"
            }
        }
    }

    jvm("desktop")

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(Dependencies.kotlinxCoroutines)
                implementation(Dependencies.kotlinxDateTime)
                implementation(Dependencies.kotlinxSerialization)

                implementation(Dependencies.sqlDelightRuntime)
                implementation(Dependencies.sqlDelightCoroutines)

                implementation(Dependencies.ktorClient)
                implementation(Dependencies.ktorContentNegotiation)
                implementation(Dependencies.ktorSerialization)

                implementation(Dependencies.koinCore)
            }
        }

        val androidMain by getting {
            dependencies {
                implementation(Dependencies.ktorOkhttp)
                implementation(Dependencies.sqlDelightAndroid)
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

android {
    namespace = "com.lumecard.shared"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

sqldelight {
    databases {
        create("LumeCardDatabase") {
            packageName.set("com.lumecard.shared.database")
        }
    }
}
