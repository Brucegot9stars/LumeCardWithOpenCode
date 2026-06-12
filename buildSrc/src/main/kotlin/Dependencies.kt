object Versions {
    const val kotlin = "2.0.0"
    const val compose = "1.6.1"
    const val sqlDelight = "2.0.1"
    const val ktor = "2.3.11"
    const val koin = "3.5.6"
    const val voyager = "1.0.0"
    const val kotlinxCoroutines = "1.8.0"
    const val kotlinxDateTime = "0.6.0"
    const val kotlinxSerialization = "1.6.3"
}

object Dependencies {
    // Compose
    const val composeRuntime = "org.jetbrains.compose.runtime:runtime:${Versions.compose}"
    const val composeFoundation = "org.jetbrains.compose.foundation:foundation:${Versions.compose}"
    const val composeMaterial3 = "org.jetbrains.compose.material3:material3:${Versions.compose}"
    const val composeComponents = "org.jetbrains.compose.components:components-resources:${Versions.compose}"
    const val composeIcons = "org.jetbrains.compose.components:components-resources:${Versions.compose}"

    // SQLDelight
    const val sqlDelightRuntime = "app.cash.sqldelight:runtime:${Versions.sqlDelight}"
    const val sqlDelightCoroutines = "app.cash.sqldelight:coroutines-extensions:${Versions.sqlDelight}"
    const val sqlDelightAndroid = "app.cash.sqldelight:android-driver:${Versions.sqlDelight}"
    const val sqlDelightJvm = "app.cash.sqldelight:sqlite-driver:${Versions.sqlDelight}"
    const val sqlDelightIos = "app.cash.sqldelight:native-driver:${Versions.sqlDelight}"

    // Ktor
    const val ktorClient = "io.ktor:ktor-client-core:${Versions.ktor}"
    const val ktorOkhttp = "io.ktor:ktor-client-okhttp:${Versions.ktor}"
    const val ktorDarwin = "io.ktor:ktor-client-darwin:${Versions.ktor}"
    const val ktorContentNegotiation = "io.ktor:ktor-client-content-negotiation:${Versions.ktor}"
    const val ktorSerialization = "io.ktor:ktor-serialization-kotlinx-json:${Versions.ktor}"

    // Koin
    const val koinCore = "io.insert-koin:koin-core:${Versions.koin}"
    const val koinCompose = "io.insert-koin:koin-compose:${Versions.koin}"

    // Voyager
    const val voyagerNavigator = "cafe.adriel.voyager:voyager-navigator:${Versions.voyager}"
    const val voyagerScreen = "cafe.adriel.voyager:voyager-screen-model:${Versions.voyager}"

    // Kotlinx
    const val kotlinxCoroutines = "org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.kotlinxCoroutines}"
    const val kotlinxDateTime = "org.jetbrains.kotlinx:kotlinx-datetime:${Versions.kotlinxDateTime}"
    const val kotlinxSerialization = "org.jetbrains.kotlinx:kotlinx-serialization-json:${Versions.kotlinxSerialization}"
}
