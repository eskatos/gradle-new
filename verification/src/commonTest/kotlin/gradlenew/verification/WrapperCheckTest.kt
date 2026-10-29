package gradlenew.verification

import gradlenew.substitution.FileEntry
import gradlenew.substitution.FileSet
import kotlin.test.Test
import kotlin.test.assertEquals

class WrapperCheckTest {

    private val template = fakeInit(gradlenew.substitution.ProjectInputs("zqxproj-name", null, null), "basic")

    @Test
    fun passesWhenTheWrapperJarHasThePinnedDigest() {
        assertEquals(emptyList(), WrapperCheck.inspect("basic.kotlin", template, fakeWrapper))
    }

    @Test
    fun failsOnAnotherWrapperJar() {
        val tampered = FileSet.of(template.entries.map {
            if (it.path == WrapperCheck.PATH) FileEntry(it.path, it.content + 0x01, it.executable) else it
        })
        val failures = WrapperCheck.inspect("basic.kotlin", tampered, fakeWrapper)
        assertEquals(1, failures.size)
        assertEquals("basic.kotlin", failures.single().subject)
        assertEquals("${WrapperCheck.PATH} has SHA-256 ${fakeWrapper.digest(tampered.entry(WrapperCheck.PATH)!!.content)}, expected ${fakeWrapper.expectedSha256}", failures.single().message)
    }

    @Test
    fun failsWhenTheTemplateHasNoWrapperJar() {
        val without = FileSet.of(template.entries.filterNot { it.path == WrapperCheck.PATH })
        assertEquals(listOf(Failure("basic.kotlin", "the template has no ${WrapperCheck.PATH}")), WrapperCheck.inspect("basic.kotlin", without, fakeWrapper))
    }
}
