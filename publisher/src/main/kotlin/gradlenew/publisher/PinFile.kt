package gradlenew.publisher

import gradlenew.versions.Checksums
import gradlenew.versions.Pin
import java.io.File

object PinFile {

    private val SLOTS = listOf("stable", "rc")
    private val KEYS = SLOTS.flatMap { listOf(it, "$it.distribution-sha256", "$it.wrapper-sha256") }.toSet()

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
        val stable = values["stable"]?.takeIf { it.isNotEmpty() }
        requireNotNull(stable) { "${file.name} has no stable version" }
        return Pin(stable, values["rc"]?.takeIf { it.isNotEmpty() }, checksums(file, values, "stable"), checksums(file, values, "rc"))
    }

    fun write(file: File, pin: Pin) {
        file.writeText(slot("stable", pin.stable, pin.stableChecksums) + slot("rc", pin.rc, pin.rcChecksums))
    }

    private fun slot(name: String, version: String?, checksums: Checksums?): String =
        "$name=${version ?: ""}\n$name.distribution-sha256=${checksums?.distribution ?: ""}\n$name.wrapper-sha256=${checksums?.wrapper ?: ""}\n"

    private fun checksums(file: File, values: Map<String, String>, slot: String): Checksums? {
        val distribution = values["$slot.distribution-sha256"].orEmpty()
        val wrapper = values["$slot.wrapper-sha256"].orEmpty()
        if (distribution.isEmpty() && wrapper.isEmpty()) return null
        require(distribution.isNotEmpty() && wrapper.isNotEmpty()) { "${file.name}: '$slot' needs both its distribution and its wrapper SHA-256, or neither" }
        return Checksums(distribution, wrapper)
    }
}
