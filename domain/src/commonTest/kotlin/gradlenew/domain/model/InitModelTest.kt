package gradlenew.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class InitModelTest {

    @Test
    fun aConsistentModelIsAccepted() {
        val model = model()
        assertEquals(listOf("java-application", "basic"), model.buildTypes.map { it.id })
        assertEquals(javaApplication(), model.buildType("java-application"))
    }

    @Test
    fun lookingUpAnUnknownBuildTypeFails() {
        val failure = assertFailsWith<IllegalArgumentException> { model().buildType("rust-application") }
        assertTrue(failure.message!!.contains("rust-application"))
    }

    @Test
    fun idsMustBeUniqueWithinEachVocabularyAndAmongBuildTypes() {
        assertFailsWith<IllegalArgumentException> { model(dsls = listOf(kotlinDsl, kotlinDsl)) }
        assertFailsWith<IllegalArgumentException> { model(buildTypes = listOf(basicBuild, basicBuild)) }
    }

    @Test
    fun aChoiceDefaultMustBeOneOfItsValues() {
        assertFailsWith<IllegalArgumentException> { Choice(listOf("kotlin"), "groovy") }
        assertFailsWith<IllegalArgumentException> { Choice(emptyList(), "kotlin") }
        assertFailsWith<IllegalArgumentException> { TestFrameworkChoice("single-project", listOf("spock"), "junit-jupiter") }
        assertEquals(null, TestFrameworkChoice("single-project", emptyList(), null).default)
    }

    @Test
    fun buildTypesMayOnlyReferenceDefinedVocabularyIds() {
        assertFailsWith<IllegalArgumentException> { model(componentTypes = listOf(basic)) }
        assertFailsWith<IllegalArgumentException> { model(languages = emptyList()) }
        assertFailsWith<IllegalArgumentException> { model(dsls = listOf(kotlinDsl)) }
        assertFailsWith<IllegalArgumentException> { model(modularizationOptions = listOf(singleProject)) }
        assertFailsWith<IllegalArgumentException> { model(testFrameworks = listOf(junitJupiter)) }
    }

    @Test
    fun testFrameworkChoicesMustCoverExactlyTheSupportedModularizationOptions() {
        assertFailsWith<IllegalArgumentException> {
            model(buildTypes = listOf(javaApplication(testFrameworks = listOf(TestFrameworkChoice("single-project", listOf("junit-jupiter"), "junit-jupiter")))))
        }
        assertFailsWith<IllegalArgumentException> {
            model(buildTypes = listOf(javaApplication(modularization = Choice(listOf("single-project"), "single-project"))))
        }
    }

    @Test
    fun javaVersionDefaultMustNotBeBelowTheMinimum() {
        assertFailsWith<IllegalArgumentException> { JavaVersionParameter(minimum = 17, default = 11) }
        assertEquals(JavaVersionParameter(7, 21), model().javaVersion)
    }

    @Test
    fun testFrameworksForAModularizationOptionAreLookedUpOnTheBuildType() {
        val type = javaApplication()
        assertEquals(listOf("junit-jupiter"), type.testFrameworksFor("with-library-projects").values)
        assertFailsWith<IllegalArgumentException> { type.testFrameworksFor("unknown") }
    }
}
