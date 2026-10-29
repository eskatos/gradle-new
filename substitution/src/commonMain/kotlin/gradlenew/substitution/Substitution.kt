package gradlenew.substitution

data class ProjectInputs(val projectName: String, val packageName: String?, val javaVersion: Int?)

object Placeholders {
    const val PROJECT_NAME = "zqxproj-name"
    const val PACKAGE = "zqxpkg.zqxsub"
    const val JAVA_VERSION = 97

    val inputs = ProjectInputs(PROJECT_NAME, PACKAGE, JAVA_VERSION)

    fun forms(): List<String> = Substitution.forms.map { requireNotNull(it.derive(inputs)) }
}

object Substitution {

    internal class Form(val input: String, val derive: (ProjectInputs) -> String?)

    internal val forms = listOf(
        Form("project name") { it.projectName },
        Form("project name") { Derivations.capitalize(Derivations.toCamelCase(it.projectName)) },
        Form("project name") { Derivations.toNamespace(it.projectName) },
        Form("project name") { Derivations.toNamespace(it.projectName).uppercase() },
        Form("package") { it.packageName },
        Form("package") { it.packageName?.replace('.', '/') },
        Form("Java version") { inputs -> inputs.javaVersion?.let { "JavaLanguageVersion.of($it)" } },
    )

    private class Replacement(val placeholder: String, val value: String?, val input: String)

    fun apply(template: FileSet, inputs: ProjectInputs): FileSet {
        val replacements = forms.map { Replacement(requireNotNull(it.derive(Placeholders.inputs)), it.derive(inputs), it.input) }
        return template.map { entry ->
            val content = textOf(entry.content)?.let { replace(it, replacements, entry.path)?.encodeToByteArray() } ?: entry.content
            FileEntry(replace(entry.path, replacements, entry.path) ?: entry.path, content, entry.executable)
        }
    }

    private fun textOf(content: ByteArray): String? {
        val text = try {
            content.decodeToString(throwOnInvalidSequence = true)
        } catch (_: CharacterCodingException) {
            return null
        }
        return if (text.contains('\u0000')) null else text
    }

    private fun replace(text: String, replacements: List<Replacement>, path: String): String? {
        var position = 0
        var result: StringBuilder? = null
        while (true) {
            val (index, match) = nextMatch(text, position, replacements) ?: break
            val value = requireNotNull(match.value) {
                "'$path' contains the ${match.input} placeholder '${match.placeholder}' but no ${match.input} was given"
            }
            val builder = result ?: StringBuilder(text.length).also { result = it }
            builder.append(text, position, index).append(value)
            position = index + match.placeholder.length
        }
        return result?.append(text, position, text.length)?.toString()
    }

    private fun nextMatch(text: String, from: Int, replacements: List<Replacement>): Pair<Int, Replacement>? {
        var best: Pair<Int, Replacement>? = null
        for (replacement in replacements) {
            val index = text.indexOf(replacement.placeholder, from)
            if (index < 0) continue
            val current = best
            if (current == null || index < current.first || (index == current.first && replacement.placeholder.length > current.second.placeholder.length)) {
                best = index to replacement
            }
        }
        return best
    }
}
