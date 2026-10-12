import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.testing.Test

plugins {
    alias(libs.plugins.kotlinMultiplatform)
}

kotlin {
    jvm {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":core"))
            implementation(project(":plugins:cmd-plugin-api"))
        }
        jvmTest.dependencies {
            implementation(project(":plugins:cmd-plugin-api"))
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}

apply(from = "packaging/eva-plugin.gradle.kts")

kotlin.sourceSets["jvmTest"].kotlin.srcDir("src/jvmHostTest/kotlin")

val echoStaging = rootProject.project(":plugins:echo-plugin")
    .layout.buildDirectory.dir("eva-staging")
val cmdStage = tasks.named<Sync>("stageEvaPlugin")

tasks.named<Test>("jvmTest") {
    dependsOn(":plugins:echo-plugin:stageEvaPlugin")
    systemProperty(
        "eva.cmdPlugin.stagingDirectory",
        cmdStage.get().destinationDir.absolutePath,
    )
    systemProperty(
        "eva.echoPlugin.stagingDirectory",
        echoStaging.get().asFile.absolutePath,
    )
}
