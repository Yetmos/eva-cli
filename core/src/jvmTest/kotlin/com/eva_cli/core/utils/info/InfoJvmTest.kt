package com.eva_cli.core.utils.info

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class InfoJvmTest {
    @Test
    fun mapsSupportedDesktopOperatingSystems() {
        assertEquals(Platform.MACOS, platformFromOsName("Mac OS X"))
        assertEquals(Platform.MACOS, platformFromOsName("Darwin"))
        assertEquals(Platform.WINDOWS, platformFromOsName("Windows 11"))
        assertEquals(Platform.LINUX, platformFromOsName("Linux"))
    }

    @Test
    fun rejectsUnsupportedDesktopOperatingSystem() {
        assertFailsWith<IllegalStateException> {
            platformFromOsName("Plan 9")
        }
    }

    @Test
    fun persistsRepairsAndReusesDesktopDeviceId() {
        val temporaryDirectory = Files.createTempDirectory("eva-device-id-test")
        val idFile = temporaryDirectory.resolve("nested").resolve("device-id")
        try {
            val firstId = DesktopDeviceId.persistentDeviceId(idFile)
            val secondId = DesktopDeviceId.persistentDeviceId(idFile)

            assertTrue(firstId.isNotBlank())
            assertEquals(firstId, secondId)
            assertEquals(firstId, Files.readString(idFile))

            Files.writeString(idFile, "  ")
            val repairedId = DesktopDeviceId.persistentDeviceId(idFile)
            assertTrue(repairedId.isNotBlank())
            assertEquals(repairedId, Files.readString(idFile))
        } finally {
            Files.deleteIfExists(idFile)
            Files.deleteIfExists(idFile.resolveSibling("${idFile.fileName}.tmp"))
            Files.deleteIfExists(idFile.resolveSibling("${idFile.fileName}.lock"))
            Files.deleteIfExists(idFile.parent)
            Files.deleteIfExists(temporaryDirectory)
        }
    }

    @Test
    fun exposesGeneratedSdkAndCurrentPlatform() {
        assertTrue(Info.sdk.isNotBlank())
        assertEquals(Info.sdk, Info.sdkVersion)
        assertEquals(currentPlatform(), Info.platform)
    }
}
