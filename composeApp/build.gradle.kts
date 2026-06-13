import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
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

        val desktopMain by getting {
            dependencies {
                implementation("org.jetbrains.skiko:skiko-awt-runtime-windows-x64:0.8.15")
            }
        }
    }
}
