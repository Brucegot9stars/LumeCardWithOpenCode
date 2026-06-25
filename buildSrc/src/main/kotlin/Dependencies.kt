object Versions {
    const val kotlin = "2.4.0"
    const val compose = "1.11.1"
    const val sqlDelight = "2.3.2"
    const val ktor = "2.3.12"
    const val koin = "3.5.6"
    const val voyager = "1.0.1"
    const val kotlinxCoroutines = "1.11.0"
    const val kotlinxDateTime = "0.7.1"
    const val kotlinxSerialization = "1.11.0"
    const val mikepenzMarkdown = "0.41.0"
}

object Dependencies {
    // Compose — managed by the org.jetbrains.compose plugin via compose.* accessors

    // SQLDelight
    const val sqlDelightRuntime = "app.cash.sqldelight:runtime:${Versions.sqlDelight}"
    const val sqlDelightCoroutines = "app.cash.sqldelight:coroutines-extensions:${Versions.sqlDelight}"
    const val sqlDelightAndroid = "app.cash.sqldelight:android-driver:${Versions.sqlDelight}"
    const val sqlDelightJvm = "app.cash.sqldelight:sqlite-driver:${Versions.sqlDelight}"
    const val sqlDelightIos = "app.cash.sqldelight:native-driver:${Versions.sqlDelight}"
    const val sqlDelightDialect = "app.cash.sqldelight:sqlite-3-38-dialect:${Versions.sqlDelight}"

    // Ktor
    const val ktorClient = "io.ktor:ktor-client-core:${Versions.ktor}"
    const val ktorOkhttp = "io.ktor:ktor-client-okhttp:${Versions.ktor}"
    const val ktorDarwin = "io.ktor:ktor-client-darwin:${Versions.ktor}"
    const val ktorContentNegotiation = "io.ktor:ktor-client-content-negotiation:${Versions.ktor}"
    const val ktorSerialization = "io.ktor:ktor-serialization-kotlinx-json:${Versions.ktor}"

    // Koin
    const val koinCore = "io.insert-koin:koin-core:${Versions.koin}"
    const val koinCompose = "io.insert-koin:koin-compose:1.1.5"

    // Voyager
    const val voyagerNavigator = "cafe.adriel.voyager:voyager-navigator:${Versions.voyager}"
    const val voyagerScreen = "cafe.adriel.voyager:voyager-screenmodel:${Versions.voyager}"
    const val voyagerTabNavigator = "cafe.adriel.voyager:voyager-tab-navigator:${Versions.voyager}"

    // Mikepenz Markdown Renderer
    const val mikepenzMarkdown = "com.mikepenz:multiplatform-markdown-renderer:${Versions.mikepenzMarkdown}"
    const val mikepenzMarkdownM3 = "com.mikepenz:multiplatform-markdown-renderer-m3:${Versions.mikepenzMarkdown}"

    // Kotlinx
    const val kotlinxCoroutines = "org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.kotlinxCoroutines}"
    const val kotlinxCoroutinesSwing = "org.jetbrains.kotlinx:kotlinx-coroutines-swing:${Versions.kotlinxCoroutines}"
    const val kotlinxDateTime = "org.jetbrains.kotlinx:kotlinx-datetime:${Versions.kotlinxDateTime}"
    const val kotlinxSerialization = "org.jetbrains.kotlinx:kotlinx-serialization-json:${Versions.kotlinxSerialization}"
}
