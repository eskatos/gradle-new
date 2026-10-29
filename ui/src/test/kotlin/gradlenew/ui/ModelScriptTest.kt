package gradlenew.ui

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

class ModelScriptTest {

    private val model = Fixtures.model
    private val versions = Fixtures.versions
    private val versionsWithRc = Fixtures.versionsWithRc

    @TestFactory
    fun formState(): List<DynamicTest> = inEveryEngine { script ->
        val initial = script.map("initialChoices", model)
        assertEquals("stable", initial["channel"])
        assertEquals("application", initial["componentType"])
        assertEquals("java", initial["language"])
        assertEquals("kotlin", initial["dsl"])
        assertEquals("junit-jupiter", initial["testFramework"])
        assertEquals(false, initial["splitProject"])
        assertEquals("demo", initial["projectName"])
        assertEquals("org.example", initial["packageName"])
        assertEquals("21", initial["javaVersion"])
        assertEquals(false, initial["incubating"])
        assertEquals(true, initial["comments"])

        val form = script.map("formState", model, versions, json())
        assertEquals(listOf("stable"), (form["channels"] as List<*>).map { (it as Map<*, *>)["id"] })
        assertEquals("9.8.2", ((form["channels"] as List<*>)[0] as Map<*, *>)["version"])
        assertEquals(listOf("application", "library", "gradle-plugin", "basic"), (form["componentTypes"] as List<*>).map { (it as Map<*, *>)["id"] })
        assertEquals("Gradle plugin", ((form["componentTypes"] as List<*>)[2] as Map<*, *>)["label"])
        assertEquals(listOf("java", "kotlin", "groovy", "scala", "cpp", "swift"), (form["languages"] as List<*>).map { (it as Map<*, *>)["id"] })
        assertEquals(listOf("kotlin", "groovy"), (form["dsls"] as List<*>).map { (it as Map<*, *>)["id"] })
        assertEquals(listOf("junit", "testng", "spock", "junit-jupiter"), (form["testFrameworks"] as List<*>).map { (it as Map<*, *>)["id"] })
        assertEquals("JUnit 4", ((form["testFrameworks"] as List<*>)[0] as Map<*, *>)["label"])
        assertEquals(true, form["splitProject"])
        assertEquals(true, form["packageName"])
        assertEquals<Map<Any?, Any?>>(mapOf("minimum" to 7, "maximum" to 31), (form["javaVersion"] as Map<Any?, Any?>).filterKeys { it != "supported" })
        assertEquals("java-application", form["buildType"])

        val withRc = script.map("formState", model, versionsWithRc, json())
        assertEquals(listOf("stable", "rc"), (withRc["channels"] as List<*>).map { (it as Map<*, *>)["id"] })
        assertEquals("9.9.0-rc-1", ((withRc["channels"] as List<*>)[1] as Map<*, *>)["version"])
    }

    @TestFactory
    fun dependentOptions(): List<DynamicTest> = inEveryEngine { script ->
        val basic = script.map("formState", model, versions, json("componentType" to "basic"))
        assertEquals("basic", basic["buildType"])
        assertEquals(emptyList<Any>(), basic["languages"])
        assertEquals(emptyList<Any>(), basic["testFrameworks"])
        assertEquals(false, basic["splitProject"])
        assertEquals(false, basic["packageName"])
        assertEquals(false, (basic["javaVersion"] as Map<*, *>)["supported"])

        val cpp = script.map("formState", model, versions, json("componentType" to "library", "language" to "cpp"))
        assertEquals("cpp-library", cpp["buildType"])
        assertEquals(false, cpp["packageName"])
        assertEquals(listOf("java", "kotlin", "groovy", "scala", "cpp", "swift"), (cpp["languages"] as List<*>).map { (it as Map<*, *>)["id"] })

        val plugin = script.map("formState", model, versions, json("componentType" to "gradle-plugin", "language" to "swift"))
        assertEquals("gradle-plugin", plugin.choice("componentType"))
        assertEquals("java", plugin.choice("language"), "an unavailable language falls back to the component type's first")
        assertEquals(listOf("java", "kotlin", "groovy"), (plugin["languages"] as List<*>).map { (it as Map<*, *>)["id"] })
        assertEquals(false, plugin["splitProject"])

        val split = script.map("formState", model, versions, json("splitProject" to true, "testFramework" to "spock"))
        assertEquals(listOf("junit-jupiter"), (split["testFrameworks"] as List<*>).map { (it as Map<*, *>)["id"] })
        assertEquals("junit-jupiter", split.choice("testFramework"), "a test framework the split layout does not offer falls back to its default")

        val kept = script.map("formState", model, versions, json("testFramework" to "spock", "dsl" to "groovy"))
        assertEquals("spock", kept.choice("testFramework"))
        assertEquals("groovy", kept.choice("dsl"))
    }

    @TestFactory
    fun validation(): List<DynamicTest> = inEveryEngine { script ->
        assertEquals(emptyMap<String, Any>(), script.map("validate", model, json()))
        assertTrue(script.map("validate", model, json("packageName" to "1acme")).containsKey("packageName"))
        assertEquals(emptyMap<String, Any>(), script.map("validate", model, json("packageName" to "com.ac\$me")), "Gradle's own pattern accepts \$")
        assertTrue(script.map("validate", model, json("packageName" to "")).containsKey("packageName"))
        assertTrue(script.map("validate", model, json("projectName" to "")).containsKey("projectName"))
        assertTrue(script.map("validate", model, json("javaVersion" to "6")).containsKey("javaVersion"))
        assertTrue(script.map("validate", model, json("javaVersion" to "x")).containsKey("javaVersion"))
        assertEquals(emptyMap<String, Any>(), script.map("validate", model, json("javaVersion" to "40")), "init accepts any Java version above its minimum")
        assertEquals(emptyMap<String, Any>(), script.map("validate", model, json("componentType" to "basic", "packageName" to "1acme")), "a field the type does not take is not validated")
    }

    @TestFactory
    fun commandLine(): List<DynamicTest> = inEveryEngine { script ->
        val tokens = script.list("commandTokens", model, versions, json("projectName" to "demo-app", "packageName" to "com.acme.demo", "javaVersion" to "17"))
        assertEquals(
            listOf("gradle", "init", "--type java-application", "--dsl kotlin", "--test-framework junit-jupiter", "--project-name demo-app", "--package com.acme.demo", "--java-version 17", "--no-split-project", "--no-incubating", "--comments", "--use-defaults"),
            tokens.map { it["text"] },
        )
        assertEquals("gradle init --type java-application --dsl kotlin --test-framework junit-jupiter --project-name demo-app --package com.acme.demo --java-version 17 --no-split-project --no-incubating --comments --use-defaults",
            script.call("commandLine", model, versions, json("projectName" to "demo-app", "packageName" to "com.acme.demo", "javaVersion" to "17")))
        val type = tokens[2]
        assertEquals("type", type["option"])
        assertEquals("java-application", type["value"])
        assertEquals("Set the type of project to generate.", type["description"])
        assertEquals("Application in Java", type["valueLabel"])
        assertEquals("Kotlin DSL", tokens[3]["valueLabel"])
        assertEquals("JUnit Jupiter", tokens[4]["valueLabel"])
        assertEquals(null, tokens[5]["valueLabel"])
        assertEquals("no-split-project", tokens[8]["option"])
        assertEquals("Disables option --split-project.", tokens[8]["description"])
        assertEquals("Include clarifying comments in files.", tokens[10]["description"])
        assertEquals("gradle", tokens[0]["kind"])
        assertTrue((tokens[0]["description"] as String).contains("9.8.2"))
        assertEquals("init", tokens[1]["kind"])
        assertEquals(tokens.map { it["id"] }.toSet().size, tokens.size, "token ids are unique")

        val basic = script.list("commandTokens", model, versions, json("componentType" to "basic", "incubating" to true)).map { it["text"] }
        assertEquals(listOf("gradle", "init", "--type basic", "--dsl kotlin", "--project-name demo", "--incubating", "--comments", "--use-defaults"), basic)

        val rc = script.list("commandTokens", model, versionsWithRc, json("channel" to "rc"))
        assertTrue((rc[0]["description"] as String).contains("9.9.0-rc-1"))
    }

    @TestFactory
    fun apiUrl(): List<DynamicTest> = inEveryEngine { script ->
        val choices = json("projectName" to "demo-app", "packageName" to "com.ac\$me")
        assertEquals(
            "https://gradle.example/api/generate?version=stable&type=java-application&dsl=kotlin&test-framework=junit-jupiter&project-name=demo-app&package=com.ac%24me&java-version=21&split-project=false&incubating=false&comments=true",
            script.call("apiUrl", model, versions, choices, "\"https://gradle.example\""),
        )
        val tokens = script.list("urlTokens", model, versions, choices, "\"https://gradle.example\"")
        assertEquals("https://gradle.example/api/generate", tokens[0]["text"])
        assertEquals("endpoint", tokens[0]["kind"])
        assertEquals("version=stable", tokens[1]["text"])
        assertEquals("version", tokens[1]["kind"])
        assertTrue((tokens[1]["description"] as String).contains("9.8.2"))
        assertEquals("type=java-application", tokens[2]["text"])
        assertEquals("type", tokens[2]["option"])
        assertEquals("Set the type of project to generate.", tokens[2]["description"])
        assertEquals("Application in Java", tokens[2]["valueLabel"])
        assertEquals("split-project", tokens[8]["option"])
        assertEquals("Split functionality across multiple subprojects?", tokens[8]["description"])
        assertEquals("?", tokens[1]["separator"])
        assertEquals("&", tokens[2]["separator"])

        val rc = script.call("apiUrl", model, versionsWithRc, json("channel" to "rc", "componentType" to "basic"), "\"https://gradle.example\"") as String
        assertTrue(rc.startsWith("https://gradle.example/api/generate?version=rc&type=basic&dsl=kotlin&project-name=demo&incubating=false&comments=true"), rc)
    }

    private fun inEveryEngine(test: (Script) -> Unit): List<DynamicTest> =
        Browsers.engines.map { engine -> DynamicTest.dynamicTest(engine) { Script.open(engine).use(test) } }

    private fun json(vararg pairs: Pair<String, Any?>): String = Script.json(*pairs)

    private fun Map<String, Any?>.choice(name: String): Any? = (this["choices"] as Map<*, *>)[name]
}
