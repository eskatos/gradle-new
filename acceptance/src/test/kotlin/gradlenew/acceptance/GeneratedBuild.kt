package gradlenew.acceptance

import java.io.File
import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import kotlin.io.path.isRegularFile
import kotlin.io.path.readBytes
import kotlin.io.path.relativeTo

data class GeneratedFile(val content: ByteArray, val executable: Boolean) {
    override fun equals(other: Any?) = other is GeneratedFile && executable == other.executable && content.contentEquals(other.content)
    override fun hashCode() = 31 * content.contentHashCode() + executable.hashCode()
}

object GeneratedBuild {

    fun fromDirectory(dir: File): Map<String, GeneratedFile> =
        dir.walkTopDown()
            .onEnter { it == dir || it.name != ".gradle" }
            .filter { it.isFile }
            .associate { it.relativeTo(dir).invariantSeparatorsPath to GeneratedFile(it.readBytes(), it.canExecute()) }
            .toSortedMap()

    fun fromZip(bytes: ByteArray): Map<String, GeneratedFile> {
        val zip = Files.createTempFile("generated", ".zip").apply { Files.write(this, bytes) }
        return FileSystems.newFileSystem(URI.create("jar:" + zip.toUri()), mapOf("enablePosixFileAttributes" to "true")).use { fs ->
            val root = fs.getPath("/")
            Files.walk(root).use { paths ->
                paths.filter { it.isRegularFile() }.toList().associate { path ->
                    path.relativeTo(root).toString() to GeneratedFile(path.readBytes(), isExecutable(path))
                }.toSortedMap()
            }
        }
    }

    fun differences(expected: Map<String, GeneratedFile>, actual: Map<String, GeneratedFile>): List<String> =
        (expected.keys + actual.keys).sorted().mapNotNull { path ->
            val e = expected[path]
            val a = actual[path]
            when {
                e == null -> "unexpected $path"
                a == null -> "missing $path"
                e.executable != a.executable -> "$path: executable ${a.executable}, expected ${e.executable}"
                !e.content.contentEquals(a.content) -> "$path: content differs"
                else -> null
            }
        }

    private fun isExecutable(path: Path): Boolean =
        PosixFilePermission.OWNER_EXECUTE in Files.getPosixFilePermissions(path)
}
