package com.eva_cli.core.status

import java.nio.channels.FileChannel
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.UUID

internal actual fun currentPlatform(): Platform = platformFromOsName(
    System.getProperty("os.name").orEmpty(),
)

internal actual fun currentDeviceId(): String = DesktopDeviceId.resolve()

internal fun platformFromOsName(osName: String): Platform {
    val normalizedName = osName.lowercase()
    return when {
        "mac" in normalizedName || "darwin" in normalizedName -> Platform.MACOS
        "win" in normalizedName -> Platform.WINDOWS
        "linux" in normalizedName -> Platform.LINUX
        else -> error("Unsupported operating system: $osName")
    }
}

internal object DesktopDeviceId {
    private val processLock = Any()

    fun resolve(): String {
        val baseDirectory = System.getProperty("user.home")
            ?.nonBlank()
            ?.let(Path::of)
            ?: System.getProperty("java.io.tmpdir")?.nonBlank()?.let(Path::of)
            ?: return UUID.randomUUID().toString()
        return persistentDeviceId(baseDirectory.resolve(".eva").resolve("device-id"))
    }

    internal fun persistentDeviceId(idFile: Path): String = synchronized(processLock) {
        readNonBlank(idFile)?.let { return it }
        return try {
            Files.createDirectories(idFile.parent)
            val lockFile = idFile.resolveSibling("${idFile.fileName}.lock")
            FileChannel.open(
                lockFile,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
            ).use { channel ->
                channel.lock().use {
                    readNonBlank(idFile) ?: writeDeviceIdAtomically(idFile)
                }
            }
        } catch (_: Exception) {
            UUID.randomUUID().toString()
        }
    }

    private fun writeDeviceIdAtomically(idFile: Path): String {
        val generatedId = UUID.randomUUID().toString()
        val temporaryFile = idFile.resolveSibling("${idFile.fileName}.tmp")
        try {
            Files.writeString(
                temporaryFile,
                generatedId,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE,
            )
            try {
                Files.move(
                    temporaryFile,
                    idFile,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporaryFile, idFile, StandardCopyOption.REPLACE_EXISTING)
            }
            return readNonBlank(idFile) ?: generatedId
        } finally {
            Files.deleteIfExists(temporaryFile)
        }
    }

    private fun readNonBlank(path: Path): String? = try {
        Files.readString(path).nonBlank()
    } catch (_: Exception) {
        null
    }

    private fun String.nonBlank(): String? = trim().takeIf(String::isNotEmpty)
}
