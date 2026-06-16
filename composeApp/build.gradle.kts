import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    id("com.android.application")
    kotlin("multiplatform")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    jvm("desktop")

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":shared"))

                implementation(Dependencies.composeRuntime)
                implementation(Dependencies.composeFoundation)
                implementation(Dependencies.composeMaterial3)
                implementation(Dependencies.composeComponents)

                implementation(Dependencies.voyagerNavigator)
                implementation(Dependencies.voyagerScreen)
                implementation(Dependencies.voyagerTabNavigator)

                implementation(Dependencies.koinCompose)

                implementation(Dependencies.commonmark)
                implementation(Dependencies.commonmarkGfmTables)
                implementation(Dependencies.commonmarkGfmStrikethrough)
                implementation(Dependencies.commonmarkAutolink)
                implementation(Dependencies.commonmarkTaskList)

                implementation(Dependencies.kotlinxCoroutines)
                implementation(Dependencies.kotlinxDateTime)
            }
        }

        val androidMain by getting {
            dependencies {
                implementation("androidx.activity:activity-compose:1.9.0")
                implementation("androidx.core:core-ktx:1.13.1")
            }
        }

        val desktopMain by getting {
            dependencies {
                implementation("org.jetbrains.skiko:skiko-awt-runtime-windows-x64:0.8.15")
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.lumecard.app.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            packageName = "LumeCard"
            packageVersion = "1.2.0"
            vendor = "AiDev"

            windows {
                menuGroup = "LumeCard"
                upgradeUuid = "229b7cca-e9d0-4ee7-9e2e-c9c8dd3d71ce"
            }
        }
    }
}

android {
    namespace = "com.lumecard.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.lumecard.app"
        minSdk = 26
        targetSdk = 35
        versionCode = (System.getenv("VERSION_CODE")?.toIntOrNull()
            ?: project.findProperty("VERSION_CODE")?.toString()?.toIntOrNull()
            ?: 2)
        versionName = System.getenv("VERSION_NAME")
            ?: project.findProperty("VERSION_NAME")?.toString()
            ?: "1.2.0"
    }

    signingConfigs {
        create("release") {
            val ksFile = System.getenv("KEYSTORE_FILE")
                ?: project.findProperty("KEYSTORE_FILE")?.toString()
            if (ksFile != null && file(ksFile).exists()) {
                storeFile = file(ksFile)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                    ?: project.findProperty("KEYSTORE_PASSWORD")?.toString()
                keyAlias = System.getenv("KEY_ALIAS")
                    ?: project.findProperty("KEY_ALIAS")?.toString()
                keyPassword = System.getenv("KEY_PASSWORD")
                    ?: project.findProperty("KEY_PASSWORD")?.toString()
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            val ksFile = System.getenv("KEYSTORE_FILE")
                ?: project.findProperty("KEYSTORE_FILE")?.toString()
            if (ksFile != null && file(ksFile).exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

