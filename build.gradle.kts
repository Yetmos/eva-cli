plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.androidMultiplatformLibrary) apply false
}

allprojects {
    group = "com.eva_cli"
    version = "1.0.0-alpha"
}
