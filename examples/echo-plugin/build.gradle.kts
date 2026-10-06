import org.jetbrains.kotlin.gradle.dsl.JvmTarget

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
        }
        jvmTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.ktor.serialization.kotlinx.json)
        }
    }
}

val pluginDirectory = layout.buildDirectory.dir("plugin")
val prepareJvmPlugin = tasks.register<Sync>("prepareJvmPlugin") {
    group = "build"
    description = "组装供 JVM 测试宿主加载的插件清单和 JAR。"
    into(pluginDirectory)
    from("plugin.json")
    from(tasks.named<Jar>("jvmJar").flatMap { it.archiveFile }) {
        into("prebuilt/jvm")
        rename { "plugin.jar" }
    }
}

tasks.named<Test>("jvmTest") {
    dependsOn(prepareJvmPlugin)
    inputs.dir(pluginDirectory)
    systemProperty("eva.echoPlugin.directory", pluginDirectory.get().asFile.absolutePath)
    systemProperty("eva.echoPlugin.version", project.version.toString())
}
