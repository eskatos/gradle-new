package gradlenew.domain.generation

import gradlenew.domain.model.InitModel
import gradlenew.domain.model.OfferedVersions
import gradlenew.substitution.ProjectInputs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

class RequestValidationTest {

    private val rcModel = gradle982Model.copy(gradleVersion = "9.9.0-rc-1")
    private val withRc = OfferedVersions("9.8.2", "9.9.0-rc-1")
    private val stableOnly = OfferedVersions("9.8.2", null)

    @Test
    fun appliesTheModelDefaultsForOmittedOptions() {
        val request = accepted("type" to "java-application", "project-name" to "demo-app")

        assertEquals("9.8.2", request.version)
        assertTrue(request.fromAlias)
        assertEquals(TemplateSpec("java-application", "kotlin", "junit-jupiter", "single-project", incubating = false, comments = true), request.spec)
        assertEquals(ProjectInputs("demo-app", "org.example", 21), request.inputs)
        assertEquals("demo-app.zip", request.filename)
        assertEquals("demo-app", request.archiveRoot)
    }

    @Test
    fun usesTheDefaultsOfTheSelectedModularization() {
        val request = accepted("type" to "kotlin-application", "project-name" to "p", "split-project" to "true")

        assertEquals(TemplateSpec("kotlin-application", "kotlin", "junit-jupiter", "with-library-projects", incubating = false, comments = true), request.spec)
        assertEquals("kotlintest", accepted("type" to "kotlin-application", "project-name" to "p").spec.testFramework)
        assertEquals("groovy", accepted("type" to "groovy-library", "project-name" to "p").spec.dsl)
    }

    @Test
    fun acceptsEveryOptionExplicitly() {
        val request = accepted(
            "version" to "9.8.2", "type" to "java-library", "dsl" to "groovy", "test-framework" to "testng",
            "split-project" to "false", "incubating" to "true", "comments" to "false",
            "project-name" to "my.lib", "package" to "com.acme.lib", "java-version" to "17",
        )

        assertFalse(request.fromAlias)
        assertEquals(TemplateSpec("java-library", "groovy", "testng", "single-project", incubating = true, comments = false), request.spec)
        assertEquals(ProjectInputs("my.lib", "com.acme.lib", 17), request.inputs)
    }

    @Test
    fun resolvesVersionAliases() {
        assertEquals("9.8.2", accepted("version" to "stable", "type" to "basic", "project-name" to "p", offered = withRc).version)
        assertEquals("9.9.0-rc-1", accepted("version" to "rc", "type" to "basic", "project-name" to "p", offered = withRc).version)
        assertEquals("9.9.0-rc-1", accepted("version" to "9.9.0-rc-1", "type" to "basic", "project-name" to "p", offered = withRc).version)
        assertEquals("No release candidate is currently offered.", rejected("version" to "rc", "type" to "basic", "project-name" to "p"))
        assertEquals(
            "Gradle version '9.7.0' is not offered. Offered versions:\n    - '9.8.2'\n    - '9.9.0-rc-1'",
            rejected("version" to "9.7.0", "type" to "basic", "project-name" to "p", offered = withRc),
        )
    }

    @Test
    fun rejectsUnknownOptionsAndOverlongValues() {
        assertEquals(
            "Unknown option 'overwrite'. Supported options:\n    - 'version'\n    - 'type'\n    - 'dsl'\n    - 'test-framework'\n    - 'split-project'\n    - 'incubating'\n    - 'comments'\n    - 'project-name'\n    - 'package'\n    - 'java-version'",
            rejected("type" to "basic", "project-name" to "p", "overwrite" to "true"),
        )
        assertEquals("Value of option 'project-name' is too long (at most 256 characters).", rejected("type" to "basic", "project-name" to "p".repeat(257)))
    }

    @Test
    fun rejectsAMissingOrUnknownType() {
        assertEquals("Missing required option 'type'.", rejected("project-name" to "p"))
        val unknown = rejected("type" to "pom", "project-name" to "p")
        assertTrue(unknown.startsWith("The requested build type 'pom' is not supported. Supported types:\n    - 'basic'\n    - 'cpp-application'\n"), unknown)
        assertTrue(unknown.endsWith("\n    - 'swift-library'"), unknown)
    }

    @Test
    fun validatesTheJavaVersionLikeInit() {
        assertEquals(
            "Target Java version: '6' is not a supported target version. It must be equal to or greater than 7",
            rejected("type" to "java-application", "project-name" to "p", "java-version" to "6"),
        )
        assertEquals(
            "Invalid target Java version 'abc'. The version must be an integer.",
            rejected("type" to "java-application", "project-name" to "p", "java-version" to "abc"),
        )
        assertEquals(25, accepted("type" to "java-application", "project-name" to "p", "java-version" to "025").inputs.javaVersion)
        assertEquals(99, accepted("type" to "scala-library", "project-name" to "p", "java-version" to "99").inputs.javaVersion)
    }

    @Test
    fun ignoresTheJavaVersionForTypesWithoutJavaTargetsLikeInit() {
        assertEquals(null, accepted("type" to "kotlin-gradle-plugin", "project-name" to "p", "java-version" to "17").inputs.javaVersion)
        assertEquals(null, accepted("type" to "cpp-library", "project-name" to "p", "java-version" to "6").inputs.javaVersion)
        assertEquals(null, accepted("type" to "basic", "project-name" to "p", "java-version" to "abc").inputs.javaVersion)
    }

    @Test
    fun requiresAValidProjectName() {
        assertEquals("Missing required option 'project-name'.", rejected("type" to "basic"))
        for (valid in listOf("demo", "demo-app", "my.cool_thing", "A1", "x-")) {
            assertEquals(valid, accepted("type" to "basic", "project-name" to valid).inputs.projectName)
        }
        for (invalid in listOf("1demo", "-demo", "demo.", "my app", "demo/app", "démo", "a\"b")) {
            assertEquals(
                "Project name: '$invalid' is not valid - it must start with a letter, contain only letters, digits, '.', '_' and '-', and not end with '.'.",
                rejected("type" to "basic", "project-name" to invalid),
            )
        }
    }

    @Test
    fun rejectsSplitProjectWhereTheModelDoesNotOfferIt() {
        assertEquals("Split project is not supported for 'kotlin-gradle-plugin' build type.", rejected("type" to "kotlin-gradle-plugin", "project-name" to "p", "split-project" to "true"))
        assertEquals("single-project", accepted("type" to "kotlin-gradle-plugin", "project-name" to "p", "split-project" to "false").spec.modularization)
    }

    @Test
    fun rejectsInvalidBooleans() {
        for (option in listOf("split-project", "incubating", "comments")) {
            assertEquals(
                "Invalid value 'yes' for option '$option'. Expected 'true' or 'false'.",
                rejected("type" to "java-application", "project-name" to "p", option to "yes"),
            )
        }
    }

    @Test
    fun validatesTheDslLikeInit() {
        assertEquals(
            "The requested build script DSL 'yaml' is not supported. Supported DSLs:\n    - 'kotlin'\n    - 'groovy'",
            rejected("type" to "java-application", "project-name" to "p", "dsl" to "yaml"),
        )
        val kotlinOnly = gradle982Model.copy(buildTypes = gradle982Model.buildTypes.map {
            if (it.id == "basic") it.copy(dsls = it.dsls.copy(values = listOf("kotlin"), default = "kotlin")) else it
        })
        assertEquals(
            "The requested DSL 'groovy' is not supported for 'basic' build type",
            rejected("type" to "basic", "project-name" to "p", "dsl" to "groovy", model = kotlinOnly),
        )
    }

    @Test
    fun validatesTheTestFrameworkForTheModularizationLikeInit() {
        assertEquals(
            "The requested test framework 'xctest' is not supported for 'java-application' build type. Supported frameworks:\n    - 'junit'\n    - 'testng'\n    - 'spock'\n    - 'junit-jupiter'",
            rejected("type" to "java-application", "project-name" to "p", "test-framework" to "xctest"),
        )
        assertEquals(
            "The requested test framework 'spock' is not supported for 'java-application' build type. Supported frameworks:\n    - 'junit-jupiter'",
            rejected("type" to "java-application", "project-name" to "p", "split-project" to "true", "test-framework" to "spock"),
        )
        assertEquals(
            "The requested test framework 'junit' is not supported for 'basic' build type. Supported frameworks:\n    - 'none'",
            rejected("type" to "basic", "project-name" to "p", "test-framework" to "junit"),
        )
        assertEquals(null, accepted("type" to "basic", "project-name" to "p", "test-framework" to "none").spec.testFramework)
    }

    @Test
    fun validatesThePackageLikeInit() {
        assertEquals("Package name is not supported for 'cpp-application' build type.", rejected("type" to "cpp-application", "project-name" to "p", "package" to "org.example"))
        assertEquals(null, accepted("type" to "cpp-application", "project-name" to "p").inputs.packageName)
        for (invalid in listOf("1abc", "class.foo", "a..b", "a.", "a-b", "a._", "org.true")) {
            assertEquals(
                "Package name: '$invalid' is not valid - it may contain invalid characters or reserved words.",
                rejected("type" to "java-application", "project-name" to "p", "package" to invalid),
            )
        }
        assertEquals(
            "Package name: 'a.\$b' is not supported by this service - it must not contain '\$'.",
            rejected("type" to "java-application", "project-name" to "p", "package" to "a.\$b"),
        )
        for (valid in listOf("a", "a.var", "_a.b1", "com.acme.demo")) {
            assertEquals(valid, accepted("type" to "java-application", "project-name" to "p", "package" to valid).inputs.packageName)
        }
    }

    @Test
    fun treatsEmptyValuesAsOmitted() {
        val request = accepted("version" to "", "type" to "java-application", "project-name" to "p", "dsl" to "", "package" to "", "java-version" to "", "test-framework" to "")

        assertEquals("kotlin", request.spec.dsl)
        assertEquals(ProjectInputs("p", "org.example", 21), request.inputs)
    }

    @Test
    fun reportsTheFirstProblemInInitsOrder() {
        assertEquals(
            "Target Java version: '6' is not a supported target version. It must be equal to or greater than 7",
            rejected("type" to "java-application", "java-version" to "6", "dsl" to "yaml", "package" to "1abc"),
        )
        assertEquals(
            "The requested build script DSL 'yaml' is not supported. Supported DSLs:\n    - 'kotlin'\n    - 'groovy'",
            rejected("type" to "java-application", "project-name" to "p", "dsl" to "yaml", "test-framework" to "xctest", "package" to "1abc"),
        )
    }

    @Test
    fun derivesACacheKeyFromTheResolvedRequest() {
        val byAlias = accepted("version" to "stable", "type" to "java-application", "project-name" to "demo")
        val explicit = accepted("version" to "9.8.2", "type" to "java-application", "dsl" to "kotlin", "project-name" to "demo", "package" to "org.example")

        assertEquals(byAlias.cacheKey, explicit.cacheKey)
        assertTrue(byAlias.cacheKey != accepted("type" to "java-application", "project-name" to "other").cacheKey)
    }

    private fun accepted(vararg options: Pair<String, String>, offered: OfferedVersions = stableOnly, model: InitModel = gradle982Model): ResolvedRequest =
        when (val resolution = RequestValidation.resolve(mapOf(*options), offered) { if (it == rcModel.gradleVersion) rcModel else model }) {
            is Resolution.Accepted -> resolution.request
            is Resolution.Rejected -> fail("Expected acceptance of ${options.toList()}, got: ${resolution.message}")
        }

    private fun rejected(vararg options: Pair<String, String>, offered: OfferedVersions = stableOnly, model: InitModel = gradle982Model): String =
        when (val resolution = RequestValidation.resolve(mapOf(*options), offered) { if (it == rcModel.gradleVersion) rcModel else model }) {
            is Resolution.Accepted -> fail("Expected rejection of ${options.toList()}, got: ${resolution.request}")
            is Resolution.Rejected -> resolution.message
        }
}
