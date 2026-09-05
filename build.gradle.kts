plugins {
    kotlin("multiplatform") version "2.4.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.0" apply false
    id("org.jetbrains.compose") version "1.11.1" apply false
    id("app.cash.sqldelight") version "2.3.2" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.0" apply false
    id("com.android.library") version "9.2.1" apply false
    id("com.android.application") version "9.2.1" apply false
}

subprojects {
    configurations.configureEach {
        resolutionStrategy {
            force("androidx.lifecycle:lifecycle-runtime:2.8.7")
            // 强制 material3 用项目原版本 1.9.0（稳定版），覆盖 richeditor 1.0.0 强加的
            // material3-desktop:1.11.0-alpha07（alpha 版的 Text 组件对 fontSize / fontWeight
            // SpanStyle 渲染异常，会导致富文本「加粗」「字号」丢失）。
            force("org.jetbrains.compose.material3:material3:1.9.0")
            force("org.jetbrains.compose.material3:material3-desktop:1.9.0")
        }
    }
}
