package com.eva_cli.examples.echo

import com.eva_cli.core.widgets.plug.PluginManifest
import java.io.File
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class EvaPluginPackageTest {
    @Test
    fun releaseContainsAnIndependentSourceProjectAndOnlyFreshJvmArtifacts() {
        val directory = File(checkNotNull(System.getProperty("eva.echoPlugin.stagingDirectory")))
        val manifest = Json.decodeFromString<PluginManifest>(directory.resolve("plugin.json").readText())
        val source = assertNotNull(manifest.source)
        assertEquals(".", source.directory)
        assertEquals(":", source.gradleProject)
        assertEquals("com.eva_cli:echo-plugin:${manifest.version}", source.coordinate)
        assertEquals(KotlinVersion.CURRENT.toString(), assertNotNull(manifest.builtWith).kotlin)

        val requiredFiles = listOf(
            "settings.gradle.kts", "build.gradle.kts", "gradle.properties",
            "gradlew", "gradlew.bat", "gradle/libs.versions.toml",
            "gradle/wrapper/gradle-wrapper.jar", "gradle/wrapper/gradle-wrapper.properties",
            "README.md", "LICENSE", "THIRD_PARTY_NOTICES.md",
            "packaging/eva-plugin.gradle.kts", "sdk/plugin-api/build.gradle.kts",
            "src/commonMain/kotlin/com/eva_cli/examples/echo/EchoPlugin.kt",
            "src/commonMain/kotlin/com/eva_cli/examples/echo/EchoProvider.kt",
            "src/commonMain/kotlin/com/eva_cli/examples/echo/EchoDispatcher.kt",
            "src/commonMain/kotlin/com/eva_cli/examples/echo/RootDispatcher.kt",
            "src/jvmTest/kotlin/com/eva_cli/examples/echo/EchoPluginLoadingTest.kt",
            "sdk/plugin-api/src/commonMain/kotlin/com/eva_cli/core/widgets/Widget.kt",
            "sdk/plugin-api/src/commonMain/kotlin/com/eva_cli/core/widgets/BaseWidget.kt",
            "sdk/plugin-api/src/commonMain/kotlin/com/eva_cli/core/widgets/context/WidgetContext.kt",
            "sdk/plugin-api/src/commonMain/kotlin/com/eva_cli/core/widgets/plug/PluginEntry.kt",
            "sdk/plugin-api/src/commonMain/kotlin/com/eva_cli/core/widgets/plug/PluginManifest.kt",
            "sdk/plugin-api/src/commonMain/kotlin/com/eva_cli/core/widgets/plug/PlugManager.kt",
            "sdk/plugin-api/src/commonMain/kotlin/com/eva_cli/core/widgets/provider/PluginProvider.kt",
            "sdk/plugin-api/src/commonMain/kotlin/com/eva_cli/core/widgets/dispatcher/PluginDispatcher.kt",
            "sdk/plugin-api/src/commonMain/kotlin/com/eva_cli/core/widgets/event/Event.kt",
        )
        requiredFiles.forEach { path ->
            assertTrue(directory.resolve(path).isFile, "Missing rebuild input: $path")
        }

        val excludedNames = setOf("build", ".gradle", ".kotlin", ".git", ".idea", "local.properties", ".DS_Store")
        directory.walkTopDown().drop(1).forEach { file ->
            assertFalse(file.name in excludedNames, "Local build state leaked into release: $file")
        }
        val artifacts = directory.resolve("prebuilt").walkTopDown()
            .filter { it.isFile }.map { it.relativeTo(directory).invariantSeparatorsPath }.toList()
        assertEquals(listOf(assertNotNull(manifest.platforms.jvm).artifact), artifacts)
    }
}
