import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization) apply false
}

allprojects {
    group = "@pluginGroup@"
    version = "@pluginVersion@"
}

kotlin {
    jvm {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":plugin-api"))
        }
        jvmTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.ktor.serialization.kotlinx.json)
        }
    }
}

apply(from = "packaging/eva-plugin.gradle.kts")
