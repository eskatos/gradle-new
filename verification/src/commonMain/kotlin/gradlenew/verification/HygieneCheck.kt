package gradlenew.verification

import gradlenew.substitution.FileSet
import gradlenew.substitution.Placeholders
import gradlenew.substitution.ProjectInputs
import gradlenew.substitution.Substitution

object HygieneCheck {

    fun inspect(subject: String, template: FileSet, inputs: ProjectInputs): List<Failure> {
        val substituted = try {
            Substitution.apply(template, inputs)
        } catch (failure: IllegalArgumentException) {
            return listOf(Failure(subject, failure.message ?: "substitution failed"))
        }
        val failures = mutableListOf<Failure>()
        for (entry in substituted.entries) {
            if (Substitution.isText(entry.content)) {
                val text = (entry.path + "\n" + entry.content.decodeToString()).lowercase()
                for (marker in Placeholders.markers.filter { it in text }) {
                    failures += Failure(subject, "${entry.path}: placeholder '$marker' remains after substitution")
                }
            } else {
                for (marker in Placeholders.markers.filter { containsMarker(entry.path, entry.content, it) }) {
                    failures += Failure(subject, "${entry.path}: binary entry contains placeholder '$marker'")
                }
                if (template.entries.none { it.content.contentEquals(entry.content) }) {
                    failures += Failure(subject, "${entry.path}: binary entry was changed by substitution")
                }
            }
        }
        return failures
    }

    private fun containsMarker(path: String, content: ByteArray, marker: String): Boolean =
        marker in path.lowercase() || listOf(marker, marker.replaceFirstChar { it.uppercaseChar() }, marker.uppercase())
            .any { indexOf(content, it.encodeToByteArray()) >= 0 }

    private fun indexOf(content: ByteArray, pattern: ByteArray): Int {
        outer@ for (start in 0..content.size - pattern.size) {
            for (offset in pattern.indices) if (content[start + offset] != pattern[offset]) continue@outer
            return start
        }
        return -1
    }
}
