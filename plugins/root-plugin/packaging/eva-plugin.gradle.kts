import org.apache.tools.ant.filters.ReplaceTokens

val pluginDirectory = layout.buildDirectory.dir("plugin")
val stagingDirectory = layout.buildDirectory.dir("eva-staging")
val coreProject = rootProject.findProject(":core")
val apiSourceDirectory = coreProject
    ?.layout?.projectDirectory?.dir("src/commonMain/kotlin")
    ?: layout.projectDirectory.dir("sdk/plugin-api/src/commonMain/kotlin")

tasks.named<Jar>("jvmJar") {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

val prepareJvmPlugin = tasks.register<Sync>("prepareJvmPlugin") {
    group = "build"
    description = "Assemble the JVM plugin manifest and JAR."
    into(pluginDirectory)
    from("plugin.json")
    from(tasks.named<Jar>("jvmJar").flatMap { it.archiveFile }) {
        into("prebuilt/jvm")
        rename { "plugin.jar" }
    }
}

val stageEvaPlugin = tasks.register<Sync>("stageEvaPlugin") {
    group = "build"
    description = "Assemble the complete .eva publication with source, API, and JVM artifacts."
    into(stagingDirectory)
    duplicatesStrategy = DuplicatesStrategy.FAIL

    from(prepareJvmPlugin)
    from(projectDir) {
        include("README.md", "LICENSE", "THIRD_PARTY_NOTICES.md")
        include("src/**", "packaging/**")
    }
    if (coreProject != null) {
        from("packaging/standalone") {
            filter<ReplaceTokens>("tokens" to mapOf(
                "pluginGroup" to project.group.toString(),
                "pluginVersion" to project.version.toString(),
            ))
            filteringCharset = "UTF-8"
        }
    } else {
        // 独立工程重新发布时保留作者对构建配置的修改。
        from(projectDir) {
            include("build.gradle.kts", "settings.gradle.kts", "gradle.properties")
            include("sdk/plugin-api/build.gradle.kts")
        }
    }
    from(rootProject.file("gradlew")) {
        filePermissions { unix("rwxr-xr-x") }
    }
    from(rootProject.file("gradlew.bat"))
    from(rootProject.file("gradle")) {
        include("libs.versions.toml", "wrapper/gradle-wrapper.jar", "wrapper/gradle-wrapper.properties")
        into("gradle")
    }
    // 共享接口只作为独立工程的编译输入；jvmJar 不会将它们打进插件。
    from(apiSourceDirectory) {
        include("com/eva_cli/core/widgets/Widget.kt")
        include("com/eva_cli/core/widgets/BaseWidget.kt")
        include("com/eva_cli/core/widgets/context/*.kt")
        include("com/eva_cli/core/widgets/event/*.kt")
        include("com/eva_cli/core/widgets/provider/*.kt")
        include("com/eva_cli/core/widgets/provider/impl/*.kt")
        include("com/eva_cli/core/widgets/dispatcher/*.kt")
        include("com/eva_cli/core/widgets/plug/Plugin.kt")
        include("com/eva_cli/core/widgets/plug/PluginEntry.kt")
        include("com/eva_cli/core/widgets/plug/PluginManifest.kt")
        if (coreProject == null) {
            // 独立工程保留随包提供的查询编译入口，不复制宿主安装器和加载器。
            include("com/eva_cli/core/widgets/plug/PlugManager.kt")
        }
        into("sdk/plugin-api/src/commonMain/kotlin")
    }
}

tasks.named<Test>("jvmTest") {
    systemProperty("user.dir", layout.buildDirectory.get().asFile.absolutePath)
    dependsOn(stageEvaPlugin)
    inputs.dir(pluginDirectory)
    inputs.dir(stagingDirectory)
    systemProperty("eva.rootPlugin.directory", pluginDirectory.get().asFile.absolutePath)
    systemProperty("eva.rootPlugin.stagingDirectory", stagingDirectory.get().asFile.absolutePath)
    systemProperty("eva.rootPlugin.version", project.version.toString())
}

tasks.register<Zip>("assembleEvaPlugin") {
    group = "distribution"
    description = "Validate and generate the .eva publication."
    dependsOn(tasks.named("check"))
    from(stageEvaPlugin)
    destinationDirectory = layout.buildDirectory.dir("distributions")
    archiveBaseName = project.name
    archiveVersion = project.version.toString()
    archiveExtension = "eva"
    filesMatching("gradlew") {
        permissions { unix("rwxr-xr-x") }
    }
    duplicatesStrategy = DuplicatesStrategy.FAIL
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}
