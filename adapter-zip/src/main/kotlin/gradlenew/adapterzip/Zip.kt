package gradlenew.adapterzip

import gradlenew.substitution.FileEntry
import gradlenew.substitution.FileSet
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32
import java.util.zip.DataFormatException
import java.util.zip.Deflater
import java.util.zip.Inflater

object Zip {

    private const val LOCAL_HEADER = 0x04034b50
    private const val CENTRAL_HEADER = 0x02014b50
    private const val END_OF_CENTRAL_DIRECTORY = 0x06054b50
    private const val VERSION = 20
    private const val MADE_BY_UNIX = (3 shl 8) or VERSION
    private const val UTF8_NAMES = 0x0800
    private const val STORED = 0
    private const val DEFLATED = 8
    private const val DOS_TIME = 0
    private const val DOS_DATE = (1 shl 5) or 1
    private const val REGULAR_FILE = 0b1000_000_000_000_000
    private const val EXECUTABLE_MODE = REGULAR_FILE or 0b111_101_101
    private const val PLAIN_MODE = REGULAR_FILE or 0b110_100_100
    private const val OWNER_EXECUTE = 0b001_000_000

    fun write(files: FileSet): ByteArray {
        require(files.entries.size < 0xffff) { "Too many entries for a zip without zip64: ${files.entries.size}" }
        val out = ByteArrayOutputStream()
        val central = ByteArrayOutputStream()
        for (entry in files.entries) {
            val name = entry.path.encodeToByteArray()
            val crc = CRC32().apply { update(entry.content) }.value.toInt()
            val compressed = deflate(entry.content)
            val offset = out.size()
            out.write(header(30 + name.size) {
                putInt(LOCAL_HEADER); putShort(VERSION); putShort(UTF8_NAMES); putShort(DEFLATED)
                putShort(DOS_TIME); putShort(DOS_DATE); putInt(crc); putInt(compressed.size); putInt(entry.content.size)
                putShort(name.size); putShort(0); put(name)
            })
            out.write(compressed)
            central.write(header(46 + name.size) {
                putInt(CENTRAL_HEADER); putShort(MADE_BY_UNIX); putShort(VERSION); putShort(UTF8_NAMES); putShort(DEFLATED)
                putShort(DOS_TIME); putShort(DOS_DATE); putInt(crc); putInt(compressed.size); putInt(entry.content.size)
                putShort(name.size); putShort(0); putShort(0); putShort(0); putShort(0)
                putInt((if (entry.executable) EXECUTABLE_MODE else PLAIN_MODE) shl 16); putInt(offset); put(name)
            })
        }
        val centralOffset = out.size()
        out.write(central.toByteArray())
        out.write(header(22) {
            putInt(END_OF_CENTRAL_DIRECTORY); putShort(0); putShort(0); putShort(files.entries.size); putShort(files.entries.size)
            putInt(central.size()); putInt(centralOffset); putShort(0)
        })
        return out.toByteArray()
    }

    fun read(bytes: ByteArray): FileSet {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val end = endOfCentralDirectory(bytes)
        val count = buffer.getShort(end + 10).toInt() and 0xffff
        var position = buffer.getInt(end + 16)
        val entries = mutableListOf<FileEntry>()
        repeat(count) {
            requireZip(position + 46 <= bytes.size && buffer.getInt(position) == CENTRAL_HEADER) { "Corrupt central directory" }
            val madeBy = buffer.getShort(position + 4).toInt() and 0xffff
            val method = buffer.getShort(position + 10).toInt() and 0xffff
            val crc = buffer.getInt(position + 16)
            val compressedSize = buffer.getInt(position + 20)
            val size = buffer.getInt(position + 24)
            val nameLength = buffer.getShort(position + 28).toInt() and 0xffff
            val extraLength = buffer.getShort(position + 30).toInt() and 0xffff
            val commentLength = buffer.getShort(position + 32).toInt() and 0xffff
            val externalAttributes = buffer.getInt(position + 38)
            val localOffset = buffer.getInt(position + 42)
            val name = bytes.decodeToString(position + 46, position + 46 + nameLength)
            position += 46 + nameLength + extraLength + commentLength
            if (name.endsWith("/")) return@repeat
            requireZip(localOffset + 30 <= bytes.size && buffer.getInt(localOffset) == LOCAL_HEADER) { "Corrupt local header for '$name'" }
            val dataStart = localOffset + 30 + (buffer.getShort(localOffset + 26).toInt() and 0xffff) + (buffer.getShort(localOffset + 28).toInt() and 0xffff)
            requireZip(dataStart + compressedSize <= bytes.size) { "Truncated data for '$name'" }
            val compressed = bytes.copyOfRange(dataStart, dataStart + compressedSize)
            val content = when (method) {
                STORED -> compressed
                DEFLATED -> inflate(compressed, size, name)
                else -> throw IllegalArgumentException("Unsupported compression method $method for '$name'")
            }
            requireZip(CRC32().apply { update(content) }.value.toInt() == crc) { "CRC mismatch for '$name'" }
            val unix = madeBy shr 8 == 3
            entries += FileEntry(name, content, unix && (externalAttributes ushr 16) and OWNER_EXECUTE != 0)
        }
        return FileSet.of(entries)
    }

    private fun endOfCentralDirectory(bytes: ByteArray): Int {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        var position = bytes.size - 22
        val lowest = maxOf(0, bytes.size - 22 - 0xffff)
        while (position >= lowest) {
            if (buffer.getInt(position) == END_OF_CENTRAL_DIRECTORY) return position
            position--
        }
        throw IllegalArgumentException("Not a zip archive: no end of central directory record")
    }

    private fun deflate(content: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.BEST_COMPRESSION, true)
        try {
            deflater.setInput(content)
            deflater.finish()
            val out = ByteArrayOutputStream()
            val chunk = ByteArray(8192)
            while (!deflater.finished()) out.write(chunk, 0, deflater.deflate(chunk))
            return out.toByteArray()
        } finally {
            deflater.end()
        }
    }

    private fun inflate(compressed: ByteArray, size: Int, name: String): ByteArray {
        val inflater = Inflater(true)
        try {
            inflater.setInput(compressed + 0)
            val content = ByteArray(size)
            var read = 0
            while (read < size) {
                val count = inflater.inflate(content, read, size - read)
                if (count == 0 && (inflater.finished() || inflater.needsInput())) break
                read += count
            }
            requireZip(read == size) { "Truncated deflate stream for '$name'" }
            return content
        } catch (failure: DataFormatException) {
            throw IllegalArgumentException("Corrupt deflate stream for '$name'", failure)
        } finally {
            inflater.end()
        }
    }

    private fun header(size: Int, write: ByteBuffer.() -> Unit): ByteArray =
        ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN).apply(write).array()

    private fun ByteBuffer.putShort(value: Int): ByteBuffer = putShort(value.toShort())

    private inline fun requireZip(condition: Boolean, message: () -> String) = require(condition, message)
}
