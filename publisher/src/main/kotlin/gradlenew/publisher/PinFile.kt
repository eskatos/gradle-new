package gradlenew.publisher

import gradlenew.versions.Checksums
import gradlenew.versions.Pin
import gradlenew.versions.PinnedVersion
import gradlenew.versions.Slot
import java.io.File

object PinFile {

    private val KEYS = Slot.entries.flatMap { listOf(it.id, "${it.id}.distribution-sha256", "${it.id}.wrapper-sha256", "${it.id}.daemon-java") }.toSet()

    fun read(file: File): Pin {
        val values = mutableMapOf<String, String>()
        for ((number, raw) in file.readLines().withIndex()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            require('=' in line) { "${file.name} line ${number + 1}: expected key=value, got '$line'" }
            val key = line.substringBefore('=').trim()
            require(key in KEYS) { "${file.name} line ${number + 1}: unknown key '$key', expected one of $KEYS" }
            require(values.put(key, line.substringAfter('=').trim()) == null) { "${file.name}: '$key' is given more than once" }
        }
        return Pin(
            requireNotNull(pinned(file, values, Slot.STABLE)) { "${file.name} has no stable version" },
            requireNotNull(pinned(file, values, Slot.PREVIOUS)) { "${file.name} has no previous major version" },
            pinned(file, values, Slot.RC),
        )
    }

    fun write(file: File, pin: Pin) {
        file.writeText(Slot.entries.joinToString("") { slot(it.id, pin[it]) })
    }

    private fun slot(name: String, pinned: PinnedVersion?): String =
        "$name=${pinned?.version ?: ""}\n" +
            "$name.distribution-sha256=${pinned?.checksums?.distribution ?: ""}\n" +
            "$name.wrapper-sha256=${pinned?.checksums?.wrapper ?: ""}\n" +
            "$name.daemon-java=${pinned?.java ?: ""}\n"

    private fun pinned(file: File, values: Map<String, String>, slot: Slot): PinnedVersion? {
        val version = values[slot.id].orEmpty()
        val java = values["${slot.id}.daemon-java"].orEmpty()
        val checksums = checksums(file, values, slot.id)
        if (version.isEmpty()) {
            require(java.isEmpty() && checksums == null) { "${file.name}: '${slot.id}' is empty but has a Java or checksums" }
            return null
        }
        require(java.isNotEmpty()) { "${file.name}: '${slot.id}' needs '${slot.id}.daemon-java', the Java that runs Gradle $version" }
        val javaVersion = requireNotNull(java.toIntOrNull()) { "${file.name}: '${slot.id}.daemon-java' must be a Java version, got '$java'" }
        return PinnedVersion(version, javaVersion, checksums)
    }

    private fun checksums(file: File, values: Map<String, String>, slot: String): Checksums? {
        val distribution = values["$slot.distribution-sha256"].orEmpty()
        val wrapper = values["$slot.wrapper-sha256"].orEmpty()
        if (distribution.isEmpty() && wrapper.isEmpty()) return null
        require(distribution.isNotEmpty() && wrapper.isNotEmpty()) { "${file.name}: '$slot' needs both its distribution and its wrapper SHA-256, or neither" }
        return Checksums(distribution, wrapper)
    }
}
