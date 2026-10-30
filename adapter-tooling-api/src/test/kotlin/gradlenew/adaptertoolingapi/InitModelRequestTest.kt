package gradlenew.adaptertoolingapi

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InitModelRequestTest {

    @Test
    fun requestsAModelThatAgreesWithTheVersionUnderTest() {
        for (version in Pinned.versions) {
            val gradle = Pinned.distribution(version)
            val model = InitHelp.model(gradle)
            val optionValues = InitHelp.optionValues(gradle)

            assertEquals(version, model.gradleVersion)
            assertEquals(optionValues.getValue("type") - "pom", model.buildTypes.map { it.id }.toSet(), version)
            assertEquals(optionValues.getValue("dsl"), model.dsls.map { it.id }.toSet(), version)
            assertEquals(optionValues.getValue("test-framework"), model.testFrameworks.map { it.id }.toSet(), version)

            val componentTypes = model.componentTypes.map { it.id }.toSet()
            val languages = model.languages.map { it.id }.toSet()
            for (type in model.buildTypes) {
                assertTrue(type.componentType in componentTypes, type.id)
                type.language?.let { assertTrue(it in languages, type.id) }
                assertTrue(type.dsls.default in type.dsls.values, type.id)
                assertTrue(type.modularization.default in type.modularization.values, type.id)
                for (choice in type.testFrameworks) {
                    if (choice.values.isEmpty()) assertNull(choice.default, type.id) else assertTrue(choice.default in choice.values, type.id)
                }
            }
            with(model.javaVersion) { assertTrue(minimum <= default && default <= maximum, toString()) }
            val packageName = model.packageName
            if (packageName.pattern != null && packageName.default != null) assertTrue(Regex(packageName.pattern!!).matches(packageName.default!!), packageName.toString())

            val help = InitHelp.optionDescriptions(gradle)
            for (option in model.options) {
                assertEquals(help[option.name], option.description, "--${option.name} in $version")
                if (option.negatable) assertEquals(help["no-${option.name}"], option.negatedDescription, "--no-${option.name} in $version")
            }
            val negated = model.options.filter { it.negatable }.map { "no-${it.name}" }
            assertEquals(help.keys - "rerun" - negated.toSet(), model.options.map { it.name }.toSet(), version)

            val init = InitRunner(gradle)
            val withoutPackage = model.buildTypes.first { !it.supportsPackage }
            val withPackage = model.buildTypes.first { it.supportsPackage }
            assertFailsWith<InitRejected>(withoutPackage.id) { init.run(listOf("--type", withoutPackage.id, "--package", "org.example", "--project-name", "probe")) }
            init.run(listOf("--type", withPackage.id, "--package", "org.example", "--project-name", "probe"))
        }
    }
}
