package gradlenew.publisher

import gradlenew.versions.Pin
import java.io.File

object PinFile {

    private val KEYS = setOf("stable", "rc")

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
        return Pin(stable, values["rc"]?.takeIf { it.isNotEmpty() })
    }

    fun write(file: File, pin: Pin) {
        file.writeText("stable=${pin.stable}\nrc=${pin.rc ?: ""}\n")
    }
}
