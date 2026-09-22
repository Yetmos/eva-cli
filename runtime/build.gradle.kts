import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinJvm)
    `java-library`
}

// 生成版本号
val generateRuntimeBuildInfo = tasks.register<Sync>("generateRuntimeBuildInfo") {
    val runtimeVersion = project.version.toString()
    val versionLiteral = runtimeVersion
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("$", "\\$")
        .replace("\r", "\\r")
        .replace("\n", "\\n")
    inputs.property("runtimeVersion", runtimeVersion)
    filteringCharset = "UTF-8"
    from("src/main/templates") {
        expand("runtimeVersion" to versionLiteral)
    }
    into(layout.buildDirectory.dir("generated/sources/runtimeBuildInfo/kotlin"))
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
    sourceSets.named("main") {
        kotlin.srcDir(generateRuntimeBuildInfo)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    api(project(":core"))
}
