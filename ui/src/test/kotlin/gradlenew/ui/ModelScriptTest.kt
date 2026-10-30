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
        assertEquals(listOf("stable", "rc"), (form["channels"] as List<*>).map { (it as Map<*, *>)["id"] }, "the RC channel is always offered as a choice")
        assertEquals("9.8.2", ((form["channels"] as List<*>)[0] as Map<*, *>)["version"])
        assertEquals(true, ((form["channels"] as List<*>)[0] as Map<*, *>)["available"])
        assertEquals(null, ((form["channels"] as List<*>)[1] as Map<*, *>)["version"])
        assertEquals(false, ((form["channels"] as List<*>)[1] as Map<*, *>)["available"])
        assertEquals(listOf("application", "library", "gradle-plugin", "basic"), (form["componentTypes"] as List<*>).map { (it as Map<*, *>)["id"] })
        assertEquals("Gradle plugin", ((form["componentTypes"] as List<*>)[2] as Map<*, *>)["label"])
        assertEquals(listOf("java", "kotlin", "groovy", "scala", "cpp", "swift"), (form["languages"] as List<*>).map { (it as Map<*, *>)["id"] })
        assertEquals(listOf("kotlin", "groovy"), (form["dsls"] as List<*>).map { (it as Map<*, *>)["id"] })
        assertEquals(listOf("junit", "testng", "spock", "junit-jupiter"), (form["testFrameworks"] as List<*>).map { (it as Map<*, *>)["id"] })
        assertEquals("JUnit 4", ((form["testFrameworks"] as List<*>)[0] as Map<*, *>)["label"])
        assertEquals(true, form["splitProject"])
        assertEquals(true, form["packageName"])
        assertEquals<Map<*, *>>(mapOf("minimum" to 7, "maximum" to 31), (form["javaVersion"] as Map<*, *>).filterKeys { it != "supported" })
        assertEquals("java-application", form["buildType"])

        val withRc = script.map("formState", model, versionsWithRc, json())
        assertEquals(listOf("stable", "rc"), (withRc["channels"] as List<*>).map { (it as Map<*, *>)["id"] })
        assertEquals("9.9.0-rc-1", ((withRc["channels"] as List<*>)[1] as Map<*, *>)["version"])
        assertEquals(true, ((withRc["channels"] as List<*>)[1] as Map<*, *>)["available"])
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
        val defaults = json("projectName" to "demo-app", "packageName" to "com.acme.demo", "javaVersion" to "17")
        val tokens = script.list("commandTokens", model, versions, defaults)
        assertEquals(
            listOf("gradle", "init", "--type java-application", "--project-name demo-app", "--package com.acme.demo", "--java-version 17", "--use-defaults"),
            tokens.map { it["text"] },
            "options at their default value are left to --use-defaults",
        )
        assertEquals("gradle init --type java-application --project-name demo-app --package com.acme.demo --java-version 17 --use-defaults",
            script.call("commandLine", model, versions, defaults))
        val type = tokens[2]
        assertEquals("type", type["option"])
        assertEquals("java-application", type["value"])
        assertEquals("Set the type of project to generate.", type["description"])
        assertEquals("Application in Java", type["valueLabel"])
        assertEquals(null, tokens[3]["valueLabel"])
        assertEquals("Use default values for options not configured explicitly", tokens.last()["description"])
        assertEquals("gradle", tokens[0]["kind"])
        assertTrue((tokens[0]["description"] as String).contains("9.8.2"))
        assertEquals("init", tokens[1]["kind"])
        assertEquals(tokens.map { it["id"] }.toSet().size, tokens.size, "token ids are unique")

        val chosen = script.list("commandTokens", model, versions, json("dsl" to "groovy", "testFramework" to "spock", "incubating" to true, "comments" to false))
        assertEquals(
            listOf("gradle", "init", "--type java-application", "--dsl groovy", "--test-framework spock", "--project-name demo", "--incubating", "--no-comments", "--use-defaults"),
            chosen.map { it["text"] },
        )
        assertEquals("Groovy DSL", chosen[3]["valueLabel"])
        assertEquals("Spock", chosen[4]["valueLabel"])
        assertEquals("Disables option --comments.", chosen[7]["description"])

        val split = script.list("commandTokens", model, versions, json("splitProject" to true))
        assertEquals(listOf("gradle", "init", "--type java-application", "--project-name demo", "--split-project", "--use-defaults"), split.map { it["text"] },
            "JUnit Jupiter is the default test framework of the split layout")
        assertEquals("Split functionality across multiple subprojects?", split[4]["description"])

        val basic = script.list("commandTokens", model, versions, json("componentType" to "basic", "incubating" to true)).map { it["text"] }
        assertEquals(listOf("gradle", "init", "--type basic", "--project-name demo", "--incubating", "--use-defaults"), basic)

        val rc = script.list("commandTokens", model, versionsWithRc, json("channel" to "rc"))
        assertTrue((rc[0]["description"] as String).contains("9.9.0-rc-1"))
    }

    @TestFactory
    fun apiUrl(): List<DynamicTest> = inEveryEngine { script ->
        val choices = json("projectName" to "demo-app", "packageName" to "com.ac\$me")
        assertEquals(
            "https://gradle.example/api/generate?type=java-application&project-name=demo-app&package=com.ac%24me",
            script.call("apiUrl", model, versions, choices, "\"https://gradle.example\""),
            "the stable channel and options at their default value are left out",
        )
        val tokens = script.list("urlTokens", model, versions, choices, "\"https://gradle.example\"")
        assertEquals("https://gradle.example/api/generate", tokens[0]["text"])
        assertEquals("endpoint", tokens[0]["kind"])
        assertTrue((tokens[0]["description"] as String).contains("9.8.2"))
        assertEquals("type=java-application", tokens[1]["text"])
        assertEquals("type", tokens[1]["option"])
        assertEquals("Set the type of project to generate.", tokens[1]["description"])
        assertEquals("Application in Java", tokens[1]["valueLabel"])
        assertEquals("?", tokens[1]["separator"])
        assertEquals("&", tokens[2]["separator"])

        val chosen = script.list("urlTokens", model, versions, json("javaVersion" to "17", "splitProject" to true, "comments" to false), "\"https://gradle.example\"")
        assertEquals(listOf("https://gradle.example/api/generate", "type=java-application", "project-name=demo", "java-version=17", "split-project=true", "comments=false"), chosen.map { it["text"] })
        assertEquals("Split functionality across multiple subprojects?", chosen[4]["description"])
        assertEquals("Set to true.", chosen[4]["valueSetting"])

        val rc = script.list("urlTokens", model, versionsWithRc, json("channel" to "rc", "componentType" to "basic"), "\"https://gradle.example\"")
        assertEquals(listOf("https://gradle.example/api/generate", "version=rc", "type=basic", "project-name=demo"), rc.map { it["text"] })
        assertEquals("version", rc[1]["kind"])
        assertEquals("?", rc[1]["separator"])
        assertTrue((rc[1]["description"] as String).contains("9.9.0-rc-1"))
    }

    @TestFactory
    fun installCommands(): List<DynamicTest> = inEveryEngine { script ->
        val origin = "\"https://gradle.example\""
        assertEquals("npx skills add https://gradle.example -g", script.call("installCommand", "\"npx\"", origin))
        assertEquals(
            "curl -fsSL --create-dirs -o ~/.claude/skills/gradle-new/SKILL.md https://gradle.example/.well-known/agent-skills/gradle-new/SKILL.md",
            script.call("installCommand", "\"curl\"", origin),
        )

        val npx = script.list("installTokens", "\"npx\"", origin)
        assertEquals(listOf("npx", "skills add", "https://gradle.example", "-g"), npx.map { it["text"] })
        assertEquals(listOf("program", "program", "address", "flag"), npx.map { it["kind"] })
        assertTrue((npx[2]["description"] as String).contains("/.well-known/agent-skills/index.json"), npx[2].toString())
        assertTrue((npx[3]["description"] as String).contains("every project"), npx[3].toString())

        val curl = script.list("installTokens", "\"curl\"", origin)
        assertEquals(
            listOf("curl", "-fsSL", "--create-dirs", "-o ~/.claude/skills/gradle-new/SKILL.md", "https://gradle.example/.well-known/agent-skills/gradle-new/SKILL.md"),
            curl.map { it["text"] },
        )
        assertEquals(listOf("program", "flag", "flag", "flag", "address"), curl.map { it["kind"] })
        assertEquals("-o", curl[3]["flag"])
        assertEquals("~/.claude/skills/gradle-new/SKILL.md", curl[3]["value"])
        assertTrue((curl[3]["description"] as String).contains("Claude Code"), curl[3].toString())

        for (tokens in listOf(npx, curl)) {
            assertEquals(tokens.size, tokens.map { it["id"] }.toSet().size, "token ids are unique")
            for (token in tokens) assertTrue((token["description"] as String).isNotBlank(), token.toString())
        }
    }

    private fun inEveryEngine(test: (Script) -> Unit): List<DynamicTest> =
        Browsers.engines.map { engine -> DynamicTest.dynamicTest(engine) { Script.open(engine).use(test) } }

    private fun json(vararg pairs: Pair<String, Any?>): String = Script.json(*pairs)

    private fun Map<String, Any?>.choice(name: String): Any? = (this["choices"] as Map<*, *>)[name]
}
