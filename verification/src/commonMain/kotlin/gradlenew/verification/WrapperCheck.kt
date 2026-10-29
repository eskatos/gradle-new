package gradlenew.verification

import gradlenew.substitution.FileSet

class WrapperDigest(val expectedSha256: String, val digest: (ByteArray) -> String)

object WrapperCheck {

    const val PATH = "gradle/wrapper/gradle-wrapper.jar"

    fun inspect(subject: String, files: FileSet, wrapper: WrapperDigest): List<Failure> {
        val jar = files.entry(PATH) ?: return listOf(Failure(subject, "the template has no $PATH"))
        val actual = wrapper.digest(jar.content)
        return if (actual == wrapper.expectedSha256) emptyList() else listOf(Failure(subject, "$PATH has SHA-256 $actual, expected ${wrapper.expectedSha256}"))
    }
}
