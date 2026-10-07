package com.eva_cli.core.runtime

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ConfigureTest {
    @Test
    fun createsRuntimeDirectoriesIncludingMissingParents() = withWorkingDirectory { root ->
        val runtimeRoot = root.resolve("nested/runtime")
        System.setProperty("user.dir", runtimeRoot.absolutePath)

        Configure.start()

        val expectedDirectories = setOf("Plugins", "Logs", "Data", "Events", "Backup")
        assertEquals(expectedDirectories, runtimeRoot.list()?.toSet())
        for (name in expectedDirectories) {
            assertTrue(runtimeRoot.resolve(name).isDirectory, "$name should be a directory")
        }
    }

    @Test
    fun repeatedChecksPreserveExistingDataAndRestoreMissingDirectories() = withWorkingDirectory { root ->
        Configure.checkDic()
        val record = root.resolve("Data/record.txt")
        record.writeText("existing data")
        assertTrue(root.resolve("Events").delete())

        Configure.checkDic()
        Configure.checkDic()

        assertEquals("existing data", record.readText())
        assertTrue(root.resolve("Events").isDirectory)
    }

    @Test
    fun rejectsFileCollisionsWithoutOverwritingFiles() = withWorkingDirectory { root ->
        val collision = root.resolve("Plugins")
        collision.writeText("keep me")

        val error = assertFailsWith<IllegalStateException> { Configure.checkDic() }

        assertTrue(error.message.orEmpty().contains(collision.absolutePath))
        assertEquals("keep me", collision.readText())
    }

    @Test
    fun rejectsRootPathThatIsAFile() = withWorkingDirectory { root ->
        val collision = root.resolve("runtime")
        collision.writeText("keep root")
        System.setProperty("user.dir", collision.absolutePath)

        assertFailsWith<IllegalStateException> { Configure.checkDic() }

        assertEquals("keep root", collision.readText())
    }

    private fun withWorkingDirectory(block: (File) -> Unit) {
        val root = Files.createTempDirectory("eva-configure-test").toFile()
        val previous = System.getProperty("user.dir")
        try {
            System.setProperty("user.dir", root.absolutePath)
            block(root)
        } finally {
            if (previous == null) System.clearProperty("user.dir") else System.setProperty("user.dir", previous)
            root.deleteRecursively()
        }
    }
}
