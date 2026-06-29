import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.android.application")
}

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties
import java.io.File

val appVersionProps = Properties().apply {
    val f = rootProject.file("version.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val appVersionName = appVersionProps.getProperty("APP_VERSION_NAME", "0.0.1")
val appVersionCode = appVersionProps.getProperty("APP_VERSION_CODE", "1").toInt()

// Auto-detect WiX 7 installation (skip download on local machine)
val wixDir = File(System.getenv("ProgramFiles") + "\\WiX Toolset v7.0\\bin")
if (wixDir.exists()) {
    System.setProperty("compose.wix.dir", wixDir.absolutePath)
    logger.info("Using WiX 7 at ${wixDir.absolutePath}")
}

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

                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.material)
                implementation(compose.materialIconsExtended)
                implementation(compose.components.resources)

                implementation(Dependencies.voyagerNavigator)
                implementation(Dependencies.voyagerScreen)
                implementation(Dependencies.voyagerTabNavigator)

                implementation(Dependencies.koinCompose)

                implementation(Dependencies.mikepenzMarkdown)
                implementation(Dependencies.mikepenzMarkdownM3)

                implementation(Dependencies.kotlinxCoroutines)
                implementation(Dependencies.kotlinxDateTime)
                implementation(Dependencies.kotlinxSerialization)
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
                implementation(Dependencies.kotlinxCoroutinesSwing)
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.lumecard.app.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "LumeCard"
            packageVersion = appVersionName
            vendor = "AiDev"

            modules("java.sql")

            windows {
                iconFile.set(project.file("src/desktopMain/resources/icon.ico"))
                menuGroup = "LumeCard"
                upgradeUuid = "229b7cca-e9d0-4ee7-9e2e-c9c8dd3d71ce"
            }
        }
    }
}

    android {
        namespace = "com.lumecard.app"
        compileSdk = 36

        defaultConfig {
            applicationId = "com.lumecard.app"
            minSdk = 26
            targetSdk = 36
            versionCode = (System.getenv("VERSION_CODE")?.toIntOrNull()
                ?: project.findProperty("VERSION_CODE")?.toString()?.toIntOrNull()
                ?: appVersionCode)
            versionName = System.getenv("VERSION_NAME")
                ?: project.findProperty("VERSION_NAME")?.toString()
                ?: appVersionName
        }

        applicationVariants.configureEach {
            outputs.configureEach {
                val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
                output.outputFileName = "LumeCard-v${versionName}-${name}.apk"
            }
        }

    signingConfigs {
        create("release") {
            fun readLocalProp(key: String): String? {
                val f = rootProject.file("local.properties")
                if (!f.exists()) return null
                return f.readLines()
                    .firstOrNull { it.startsWith("$key=") }
                    ?.substringAfter("=")
                    ?.replace("\\:", ":")
                    ?.replace("\\\\", "\\")
            }
            val ksFile = System.getenv("KEYSTORE_FILE")
                ?: project.findProperty("KEYSTORE_FILE")?.toString()
                ?: readLocalProp("KEYSTORE_FILE")
            if (ksFile != null && file(ksFile).exists()) {
                storeFile = file(ksFile)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                    ?: project.findProperty("KEYSTORE_PASSWORD")?.toString()
                    ?: readLocalProp("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                    ?: project.findProperty("KEY_ALIAS")?.toString()
                    ?: readLocalProp("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
                    ?: project.findProperty("KEY_PASSWORD")?.toString()
                    ?: readLocalProp("KEY_PASSWORD")
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
            fun readLocalProp(key: String): String? {
                val f = rootProject.file("local.properties")
                if (!f.exists()) return null
                return f.readLines()
                    .firstOrNull { it.startsWith("$key=") }
                    ?.substringAfter("=")
                    ?.replace("\\:", ":")
                    ?.replace("\\\\", "\\")
            }
            val ksFile = System.getenv("KEYSTORE_FILE")
                ?: project.findProperty("KEYSTORE_FILE")?.toString()
                ?: readLocalProp("KEYSTORE_FILE")
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

