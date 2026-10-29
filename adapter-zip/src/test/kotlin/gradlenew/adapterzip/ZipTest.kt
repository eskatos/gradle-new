package gradlenew.adapterzip

import gradlenew.substitution.FileEntry
import gradlenew.substitution.FileSet
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import java.time.LocalDateTime
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ZipTest {

    private val files = FileSet.of(
        FileEntry("gradlew", "#!/bin/sh\necho hi\n".encodeToByteArray(), true),
        FileEntry("gradlew.bat", "@echo off\r\n".encodeToByteArray(), false),
        FileEntry("app/src/main/java/com/acme/App.java", "package com.acme;\n".repeat(50).encodeToByteArray(), false),
        FileEntry("gradle/wrapper/gradle-wrapper.jar", ByteArray(4096) { (it * 31 % 251).toByte() }, false),
        FileEntry("empty.txt", ByteArray(0), false),
        FileEntry("café.txt", "accent".encodeToByteArray(), false),
    )

    @Test
    fun roundTripsPathsContentAndTheExecutableFlag() {
        assertEquals(files, Zip.read(Zip.write(files)))
    }

    @Test
    fun writesIdenticalBytesForIdenticalFileSets() {
        val reordered = FileSet.of(files.entries.reversed())

        assertTrue(Zip.write(files).contentEquals(Zip.write(reordered)))
    }

    @Test
    fun isReadableByJavaUtilZipWithSortedEntriesAndFixedTimestamps() {
        val entries = mutableListOf<Pair<String, ByteArray>>()
        val times = mutableSetOf<LocalDateTime>()
        ZipInputStream(ByteArrayInputStream(Zip.write(files))).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                entries += entry.name to zip.readBytes()
                times += entry.timeLocal
            }
        }

        assertEquals(files.entries.map { it.path }, entries.map { it.first })
        assertTrue(files.entries.zip(entries).all { (expected, actual) -> expected.content.contentEquals(actual.second) })
        assertEquals(setOf(LocalDateTime.of(1980, 1, 1, 0, 0)), times)
    }

    @Test
    fun recordsUnixPermissionsReadableByTheJdkZipFileSystem() {
        val zip = Files.createTempFile("permissions", ".zip").apply { Files.write(this, Zip.write(files)) }

        FileSystems.newFileSystem(URI.create("jar:" + zip.toUri()), mapOf("enablePosixFileAttributes" to "true")).use { fs ->
            assertTrue(PosixFilePermission.OWNER_EXECUTE in Files.getPosixFilePermissions(fs.getPath("/gradlew")))
            assertFalse(PosixFilePermission.OWNER_EXECUTE in Files.getPosixFilePermissions(fs.getPath("/gradlew.bat")))
            assertTrue(PosixFilePermission.OTHERS_READ in Files.getPosixFilePermissions(fs.getPath("/gradlew.bat")))
        }
    }

    @Test
    fun readsZipsWrittenByZipOutputStreamWithDataDescriptors() {
        val bytes = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { zip ->
                zip.putNextEntry(ZipEntry("dir/"))
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("dir/a.txt"))
                zip.write("hello".repeat(100).encodeToByteArray())
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("b.bin").apply { method = ZipEntry.STORED; size = 3; compressedSize = 3; crc = java.util.zip.CRC32().apply { update(byteArrayOf(1, 2, 3)) }.value })
                zip.write(byteArrayOf(1, 2, 3))
                zip.closeEntry()
            }
        }.toByteArray()

        assertEquals(
            FileSet.of(FileEntry("dir/a.txt", "hello".repeat(100).encodeToByteArray(), false), FileEntry("b.bin", byteArrayOf(1, 2, 3), false)),
            Zip.read(bytes),
        )
    }

    @Test
    fun roundTripsTheWrapperFilesOfARealBuild() {
        val root = File("..").canonicalFile
        val real = FileSet.of(
            listOf("gradlew", "gradlew.bat", "gradle/wrapper/gradle-wrapper.jar", "gradle/wrapper/gradle-wrapper.properties").map { path ->
                val file = root.resolve(path)
                FileEntry(path, file.readBytes(), file.canExecute())
            },
        )

        val read = Zip.read(Zip.write(real))

        assertEquals(real, read)
        assertTrue(read.entry("gradlew")!!.executable)
    }

    @Test
    fun rejectsCorruptArchives() {
        assertFailsWith<IllegalArgumentException> { Zip.read(byteArrayOf(1, 2, 3)) }
        val bytes = Zip.write(files)
        val corrupt = bytes.copyOf().also { it[70] = (it[70] + 1).toByte() }
        assertFailsWith<IllegalArgumentException> { Zip.read(corrupt) }
    }
}
