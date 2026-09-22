plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.androidMultiplatformLibrary) apply false
}

allprojects {
    group = "com.eva_cli"
    version = "0.1.0-alpha"
}
