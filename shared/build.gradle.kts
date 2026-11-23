plugins {
    kotlin("multiplatform")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("app.cash.sqldelight")
    id("com.android.library")
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

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val desktopTest by getting {
            dependencies {
                implementation("io.ktor:ktor-client-mock:${Versions.ktor}")
            }
        }
    }
}

android {
    namespace = "com.lumecard.shared"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets {
        getByName("main") {
            assets.srcDir("src/commonMain/resources")
        }
    }
}

sqldelight {
    databases {
        create("LumeCardDatabase") {
            packageName.set("com.lumecard.shared.database")
            dialect("app.cash.sqldelight:sqlite-3-38-dialect:${Versions.sqlDelight}")
        }
    }
}

// Timing-sensitive scaling benchmark. Opt in per run:
//   ./gradlew :shared:desktopTest -PrunScaleBenchmark=true --tests "*ObjectImportPlannerBenchmark"
// Default runs keep it as a no-op so shared CI is not exposed to wall-clock flakiness.
// Kotlin/JVM test tasks are not `org.gradle.api.tasks.testing.Test`, so the property is set on the
// concrete KMP type instead of withType<Test>().
tasks.withType<org.jetbrains.kotlin.gradle.targets.jvm.tasks.KotlinJvmTest>().configureEach {
    systemProperty("runScaleBenchmark", (findProperty("runScaleBenchmark") as String?) ?: "false")
}
