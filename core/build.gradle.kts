import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
}

val generateBuildInfo = tasks.register<Sync>("generateBuildInfo") {
    val sdkVersion = project.version.toString()
    val versionLiteral = sdkVersion
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("$", "\\$")
        .replace("\r", "\\r")
        .replace("\n", "\\n")

    inputs.property("sdkVersion", sdkVersion)
    filteringCharset = "UTF-8"
    from("src/commonMain/templates") {
        expand("sdkVersion" to versionLiteral)
    }
    into(layout.buildDirectory.dir("generated/sources/buildInfo/commonMain/kotlin"))
}

kotlin {
    jvm {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
        }
    }
    iosArm64()
    iosSimulatorArm64()

    android {
        namespace = "com.eva_cli.core"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
    }

    sourceSets {
        commonMain {
            kotlin.srcDir(generateBuildInfo)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
