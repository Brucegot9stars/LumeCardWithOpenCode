import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.0"
}

kotlin {
    androidTarget {
        @OptIn(ExperimentalKotlinGradlePluginApi::class)
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    jvm("desktop")

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":shared"))

                // Compose
                implementation(Dependencies.composeRuntime)
                implementation(Dependencies.composeFoundation)
                implementation(Dependencies.composeMaterial3)
                implementation(Dependencies.composeComponents)

                // Voyager
                implementation(Dependencies.voyagerNavigator)
                implementation(Dependencies.voyagerScreen)

                // Koin
                implementation(Dependencies.koinCompose)

                // Coroutines
                implementation(Dependencies.kotlinxCoroutines)
            }
        }

        val androidMain by getting

        val iosMain by getting

        val desktopMain by getting
    }
}

android {
    namespace = "com.lumecard.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.lumecard.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.10"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}
