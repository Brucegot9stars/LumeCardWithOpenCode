import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    id("com.android.application")
    kotlin("multiplatform")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "17"
            }
        }
    }

    jvm("desktop") {
        mainRun {
            mainClass.set("com.lumecard.app.MainKt")
        }
    }

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

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
