import org.apache.tools.ant.filters.ReplaceTokens

val pluginDirectory = layout.buildDirectory.dir("plugin")
val stagingDirectory = layout.buildDirectory.dir("eva-staging")
val coreProject = rootProject.findProject(":core")
val cmdApiProject = rootProject.findProject(":plugins:cmd-plugin-api")
val standaloneApiProject = project.findProject(":plugin-api")
val apiSourceDirectory = coreProject
    ?.layout?.projectDirectory?.dir("src/commonMain/kotlin")
    ?: layout.projectDirectory.dir("sdk/plugin-api/src/commonMain/kotlin")
val cmdApiSourceDirectory = cmdApiProject
    ?.layout?.projectDirectory?.dir("src/commonMain/kotlin")
    ?: layout.projectDirectory.dir("sdk/plugin-api/src/commonMain/kotlin")

tasks.named<Jar>("jvmJar") {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

val prepareJvmPlugin = tasks.register<Sync>("prepareJvmPlugin") {
    group = "build"
    description = "组装供 JVM 宿主加载的插件清单和 JAR。"
    into(pluginDirectory)
    from("plugin.json")
    from(tasks.named<Jar>("jvmJar").flatMap { it.archiveFile }) {
        into("prebuilt/jvm")
        rename { "plugin.jar" }
    }
    if (cmdApiProject != null) {
        dependsOn(":plugins:cmd-plugin-api:jvmJar")
        from(cmdApiProject.layout.buildDirectory.dir("libs")) {
            include("cmd-plugin-api-jvm-*.jar")
            into("prebuilt/jvm/lib")
            rename { "cmd-plugin-api.jar" }
        }
    } else if (standaloneApiProject != null) {
        dependsOn(":plugin-api:jvmJar")
        from(standaloneApiProject.layout.buildDirectory.dir("libs")) {
            include("plugin-api-jvm-*.jar")
            into("prebuilt/jvm/lib")
            rename { "cmd-plugin-api.jar" }
        }
    }
}

val stageEvaPlugin = tasks.register<Sync>("stageEvaPlugin") {
    group = "build"
    description = "组装完整 .eva 发布内容，包含独立源码工程、API 源码和 JVM 产物。"
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
    // 共享接口只作为独立工程的编译输入；jvmJar 不将它们打进插件。
    from(apiSourceDirectory) {
        include("com/eva_cli/core/widgets/Widget.kt")
        include("com/eva_cli/core/widgets/BaseWidget.kt")
        include("com/eva_cli/core/widgets/context/*.kt")
        include("com/eva_cli/core/widgets/event/*.kt")
        include("com/eva_cli/core/widgets/provider/*.kt")
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
    from(cmdApiSourceDirectory) {
        include("com/eva_cli/cmd/api/CommandLinePort.kt")
        into("sdk/plugin-api/src/commonMain/kotlin")
    }
}

tasks.named<Test>("jvmTest") {
    systemProperty("user.dir", layout.buildDirectory.get().asFile.absolutePath)
    dependsOn(stageEvaPlugin)
    inputs.dir(pluginDirectory)
    inputs.dir(stagingDirectory)
    systemProperty("eva.cmdPlugin.stagingDirectory", stagingDirectory.get().asFile.absolutePath)
    if (coreProject != null) {
        dependsOn(":plugins:echo-plugin:stageEvaPlugin")
        dependsOn(":plugins:root-plugin:stageEvaPlugin")
        val echoStaging = rootProject.project(":plugins:echo-plugin")
            .layout.buildDirectory.dir("eva-staging")
        val rootStaging = rootProject.project(":plugins:root-plugin")
            .layout.buildDirectory.dir("eva-staging")
        systemProperty("eva.echoPlugin.stagingDirectory", echoStaging.get().asFile.absolutePath)
        systemProperty("eva.rootPlugin.stagingDirectory", rootStaging.get().asFile.absolutePath)
    }
}

tasks.register<Zip>("assembleEvaPlugin") {
    group = "distribution"
    description = "验证并生成包含源码与预编译 JVM JAR 的 .eva 发布包。"
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
