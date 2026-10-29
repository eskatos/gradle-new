package gradlenew.domain.generation

import gradlenew.domain.model.OfferedVersions
import kotlin.test.Test
import kotlin.test.assertEquals

class ModelLookupTest {

    private val stableOnly = OfferedVersions("9.8.2", null)
    private val withRc = OfferedVersions("9.8.2", "9.9.0-rc-1")

    @Test
    fun theStableChannelIsTheDefault() {
        assertEquals(ModelLookup.Found("9.8.2", immutable = false), ModelLookup.resolve(emptyList(), stableOnly))
        assertEquals(ModelLookup.Found("9.8.2", immutable = false), ModelLookup.resolve(listOf("version" to "stable"), stableOnly))
    }

    @Test
    fun theReleaseCandidateChannelResolvesWhileOneIsOffered() {
        assertEquals(ModelLookup.Found("9.9.0-rc-1", immutable = false), ModelLookup.resolve(listOf("version" to "rc"), withRc))
        assertEquals(
            ModelLookup.Rejected("No release candidate is currently offered.", "version"),
            ModelLookup.resolve(listOf("version" to "rc"), stableOnly),
        )
    }

    @Test
    fun aConcreteOfferedVersionIsImmutable() {
        assertEquals(ModelLookup.Found("9.8.2", immutable = true), ModelLookup.resolve(listOf("version" to "9.8.2"), withRc))
        assertEquals(ModelLookup.Found("9.9.0-rc-1", immutable = true), ModelLookup.resolve(listOf("version" to "9.9.0-rc-1"), withRc))
    }

    @Test
    fun rejectsWhatGenerationRejects() {
        assertEquals(
            ModelLookup.Rejected("Gradle version '9.7.0' is not offered. Offered versions:\n    - '9.8.2'", "version"),
            ModelLookup.resolve(listOf("version" to "9.7.0"), stableOnly),
        )
        assertEquals(
            ModelLookup.Rejected("Option 'version' is given more than once.", "version"),
            ModelLookup.resolve(listOf("version" to "stable", "version" to "rc"), withRc),
        )
        assertEquals(
            ModelLookup.Rejected("Value of option 'version' is too long (at most ${RequestValidation.MAX_VALUE_LENGTH} characters).", "version"),
            ModelLookup.resolve(listOf("version" to "x".repeat(RequestValidation.MAX_VALUE_LENGTH + 1)), stableOnly),
        )
    }

    @Test
    fun acceptsOnlyTheVersion() {
        assertEquals(
            ModelLookup.Rejected("Unknown option 'type'. Supported options:\n    - 'version'", "type"),
            ModelLookup.resolve(listOf("type" to "java-application"), stableOnly),
        )
    }
}
