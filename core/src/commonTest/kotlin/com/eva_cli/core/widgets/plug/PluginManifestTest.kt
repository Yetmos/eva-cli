package com.eva_cli.core.widgets.plug

import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class PluginManifestTest {
    @Test
    fun decodesPublishedManifestAndPreservesItThroughJsonRoundTrip() {
        val manifest = Json.decodeFromString<PluginManifest>(
            """
            {
              "formatVersion": 1,
              "id": "com.example.chat",
              "name": "Chat Plugin",
              "version": "1.0.0",
              "apiVersion": 1,
              "resources": "resources",
              "platforms": {
                "jvm": {
                  "artifact": "prebuilt/jvm/plugin.jar",
                  "dependencies": "prebuilt/jvm/lib",
                  "entryClass": "com.example.chat.ChatPlugin",
                  "minJavaVersion": 17
                },
                "android": {
                  "artifact": "prebuilt/android/plugin-dex.jar",
                  "entryClass": "com.example.chat.ChatPlugin",
                  "minSdk": 29
                },
                "ios": {
                  "repository": "prebuilt/maven",
                  "coordinate": "com.example:chat-plugin:1.0.0",
                  "factory": "com.example.chat.ChatPlugin"
                }
              },
              "source": {
                "directory": ".",
                "gradleProject": ":",
                "coordinate": "com.example:chat-plugin:1.0.0"
              },
              "builtWith": {
                "kotlin": "2.4.20"
              }
            }
            """.trimIndent(),
        )

        assertEquals("com.example.chat", manifest.id)
        assertEquals("1.0.0", manifest.version)
        assertEquals("prebuilt/jvm/lib", manifest.platforms.jvm?.dependencies)
        assertEquals(17, manifest.platforms.jvm?.minJavaVersion)
        assertEquals("prebuilt/android/plugin-dex.jar", manifest.platforms.android?.artifact)
        assertEquals(29, manifest.platforms.android?.minSdk)
        assertEquals("com.example.chat.ChatPlugin", manifest.platforms.ios?.factory)
        assertEquals("prebuilt/maven", manifest.platforms.ios?.repository)
        assertEquals("com.example:chat-plugin:1.0.0", manifest.source?.coordinate)
        assertEquals("2.4.20", manifest.builtWith?.kotlin)
        assertEquals(manifest, Json.decodeFromString<PluginManifest>(Json.encodeToString(manifest)))
    }

    @Test
    fun acceptsASinglePlatformWithoutOptionalMetadata() {
        val manifest = Json.decodeFromString<PluginManifest>(minimalJvmManifest)

        assertEquals("prebuilt/jvm/plugin.jar", manifest.platforms.jvm?.artifact)
        assertEquals("com.example.chat.ChatPlugin", manifest.platforms.jvm?.entryClass)
        assertNull(manifest.platforms.jvm?.dependencies)
        assertNull(manifest.platforms.jvm?.minJavaVersion)
        assertNull(manifest.platforms.android)
        assertNull(manifest.platforms.ios)
        assertNull(manifest.resources)
        assertNull(manifest.source)
        assertNull(manifest.builtWith)
    }

    @Test
    fun sourceMetadataDefaultsToTheRootGradleProject() {
        val manifest = Json.decodeFromString<PluginManifest>(
            minimalJvmManifest.replace(
                "\"platforms\":",
                "\"source\": {\"coordinate\": \"com.example:chat-plugin:1.0.0\"}, \"platforms\":",
            ),
        )

        assertEquals(".", manifest.source?.directory)
        assertEquals(":", manifest.source?.gradleProject)
    }

    @Test
    fun rejectsMissingIdentityOrPlatformEntryFields() {
        for (requiredField in listOf(
            "\"formatVersion\": 1,",
            "\"id\": \"com.example.chat\",",
            "\"name\": \"Chat Plugin\",",
            "\"version\": \"1.0.0\",",
            "\"apiVersion\": 1,",
            "\"entryClass\": \"com.example.chat.ChatPlugin\",",
        )) {
            assertFailsWith<SerializationException>(requiredField) {
                Json.decodeFromString<PluginManifest>(minimalJvmManifest.replace(requiredField, ""))
            }
        }
    }

    private val minimalJvmManifest = """
        {
          "formatVersion": 1,
          "id": "com.example.chat",
          "name": "Chat Plugin",
          "version": "1.0.0",
          "apiVersion": 1,
          "platforms": {
            "jvm": {
              "entryClass": "com.example.chat.ChatPlugin",
              "artifact": "prebuilt/jvm/plugin.jar"
            }
          }
        }
    """.trimIndent()
}
