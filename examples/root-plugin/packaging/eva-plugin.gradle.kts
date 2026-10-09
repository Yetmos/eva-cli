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
        // 鐙珛宸ョ▼閲嶆柊鍙戝竷鏃朵繚鐣欎綔鑰呭鏋勫缓閰嶇疆鐨勪慨鏀广€?
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
    // 鍏变韩鎺ュ彛鍙綔涓虹嫭绔嬪伐绋嬬殑缂栬瘧杈撳叆锛沯vmJar 涓嶅皢瀹冧滑鎵撹繘鎻掍欢銆?
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
            // 鐙珛宸ョ▼淇濈暀闅忓寘鎻愪緵鐨勬煡璇㈢紪璇戝叆鍙ｏ紝涓嶅鍒跺涓诲畨瑁呭櫒鍜屽姞杞藉櫒銆?
            include("com/eva_cli/core/widgets/plug/PlugManager.kt")
        }
        into("sdk/plugin-api/src/commonMain/kotlin")
    }
}

tasks.named<Test>("jvmTest") {
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
