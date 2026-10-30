package gradlenew.acceptance

import com.microsoft.playwright.Browser
import com.microsoft.playwright.Page
import gradlenew.adapterjson.ErrorJson
import gradlenew.adapterjson.InitModelJson
import gradlenew.adapterjson.VersionsJson
import gradlenew.domain.model.BuildType
import gradlenew.domain.model.InitModel
import gradlenew.domain.model.OfferedVersions
import java.io.File
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.TimeSource
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

class UiJourneyTest {

    @TestFactory
    fun uiJourney(): List<DynamicTest> =
        Browsers.engines.map { engine -> DynamicTest.dynamicTest(engine) { journey(engine) } }

    private fun journey(engine: String) {
        val started = TimeSource.Monotonic.markNow()
        val target = target()
        val offered = VersionsJson.decode(target.get("/api/versions").body())
        val model = InitModelJson.decode(target.get("/v/${offered.stable}/model.json").body())
        val context = Browsers.browser(engine).newContext(Browser.NewContextOptions().setAcceptDownloads(true).setViewportSize(1280, 900))
        try {
            context.addInitScript("window.__copied = []; Object.defineProperty(navigator, 'clipboard', { configurable: true, value: { writeText: (text) => { window.__copied.push(text); return Promise.resolve(); } } });")
            val page = context.newPage()
            page.setDefaultTimeout(10_000.0)
            val requests = ConcurrentHashMap.newKeySet<String>()
            page.onRequest { requests.add(it.url()) }
            page.navigate(target.baseUrl + "/")
            page.waitForSelector("#command .token")

            channels(page, offered)
            everyBuildTypeDrivesTheForm(page, model)
            downloadMatchesApiAndGradleInit(page, target, model, offered)
            rejectionsAppearNextToTheField(page, target)
            explanations(page, model, offered)
            agentSkill(page, target)
            narrowScreens(page, engine)
            offered.rc?.let { rc -> releaseCandidate(page, target, rc) }
            everyRequestStayedOnTheOrigin(requests, target)
        } finally {
            context.close()
        }
        println("UI journey in $engine took ${started.elapsedNow().inWholeMilliseconds} ms")
    }

    private fun channels(page: Page, offered: OfferedVersions) {
        assertTrue(page.isChecked("input[name=version][value=stable]"))
        assertEquals(offered.stable, page.textContent("[data-channel=stable] .channel-version")!!.trim())
        assertEquals(1, page.locator("input[name=version][value=rc]").count(), "the RC switch is always shown")
        assertEquals(offered.rc == null, page.isDisabled("input[name=version][value=rc]"), "the RC switch is disabled exactly when no RC is offered")
        offered.rc?.let { assertEquals(it, page.textContent("[data-channel=rc] .channel-version")!!.trim()) }
        assertTrue(!hasVersionParameter(apiUrl(page)), "the stable channel is the API's default: ${apiUrl(page)}")
    }

    private fun everyBuildTypeDrivesTheForm(page: Page, model: InitModel) {
        for (componentType in model.componentTypes) {
            val types = model.buildTypes.filter { it.componentType == componentType.id }
            if (types.isEmpty()) continue
            page.check("input[name=component-type][value=${componentType.id}]")
            val languages = types.mapNotNull { it.language }.distinct()
            assertEquals(languages.isNotEmpty(), page.isVisible("#field-language"), "language field for ${componentType.id}")
            for (language in languages.ifEmpty { listOf(null) }) {
                if (language != null) page.check("input[name=language][value=$language]")
                val type = types.first { it.language == language }
                assertDependentFields(page, model, type)
                assertTrue(commandText(page).contains("--type ${type.id}"), commandText(page))
                assertTrue(apiUrl(page).contains("type=${type.id}"), apiUrl(page))
                assertTrue(!hasVersionParameter(apiUrl(page)), apiUrl(page))
            }
        }
    }

    private fun assertDependentFields(page: Page, model: InitModel, type: BuildType) {
        val modularization = type.modularization.default
        assertEquals(type.supportsPackage, page.isVisible("#field-package"), "package field for ${type.id}")
        assertEquals(type.supportsJavaVersion, page.isVisible("#field-java-version"), "Java version field for ${type.id}")
        assertEquals(type.modularization.values.size > 1, page.isVisible("#field-split-project"), "split project field for ${type.id}")
        assertEquals(type.testFrameworksFor(modularization).values.isNotEmpty(), page.isVisible("#field-test-framework"), "test framework field for ${type.id}")
        assertEquals(type.dsls.values.size > 1, page.isVisible("#field-dsl"), "DSL field for ${type.id}")
        val packageChanged = type.supportsPackage && page.inputValue("#package") != model.packageName.default
        val javaVersionChanged = type.supportsJavaVersion && page.inputValue("#java-version") != model.javaVersion.default.toString()
        assertEquals(packageChanged, commandText(page).contains("--package "), "--package only when changed: ${commandText(page)}")
        assertEquals(javaVersionChanged, commandText(page).contains("--java-version "), "--java-version only when changed: ${commandText(page)}")
        assertEquals(packageChanged, apiUrl(page).contains("package="), "package= only when changed: ${apiUrl(page)}")
    }

    private fun downloadMatchesApiAndGradleInit(page: Page, target: Target, model: InitModel, offered: OfferedVersions) {
        val application = model.buildTypes.first { it.componentType == "application" && it.supportsPackage && it.supportsJavaVersion && it.modularization.values.size > 1 }
        page.check("input[name=component-type][value=application]")
        page.check("input[name=language][value=${application.language}]")
        page.check("input[name=dsl][value=${application.dsls.values.last()}]")
        page.check("input[name=test-framework][value=${application.testFrameworksFor(application.modularization.default).values.last()}]")
        page.fill("#project-name", "demo-app")
        page.fill("#package", "com.acme.demo")
        page.fill("#java-version", "17")
        page.setChecked("#incubating", true)
        page.setChecked("#comments", false)

        val command = commandArguments(page)
        assertEquals(listOf("gradle", "init"), command.take(2))
        val url = apiUrl(page)
        assertTrue(url.startsWith(target.baseUrl + "/api/generate?"), url)

        val download = page.waitForDownload { page.click("#download") }
        assertEquals("demo-app.zip", download.suggestedFilename())
        val downloaded = Files.readAllBytes(download.path())
        val fromApi = target.getBytes(url.removePrefix(target.baseUrl))
        assertEquals(200, fromApi.statusCode())
        assertTrue(downloaded.contentEquals(fromApi.body()), "the page's download differs from the API's zip for $url")

        val generated = GeneratedBuild.fromZip(downloaded).mapKeys { it.key.removePrefix("demo-app/") }
        val expected = initResults.getOrPut(command.joinToString(" ")) { GradleOracle(offered.stable).initWithArguments(command.drop(2)) }
        assertEquals(emptyList(), GeneratedBuild.differences(expected, generated), "download differs from `${command.joinToString(" ")}`")
        assertTrue(generated.getValue("gradlew").executable)
    }

    private fun rejectionsAppearNextToTheField(page: Page, target: Target) {
        page.fill("#package", "1acme")
        assertTrue(page.isVisible("#package-error"), "client-side rejection of an invalid package")
        assertTrue(page.textContent("#package-error")!!.isNotBlank())
        assertEquals("true", page.getAttribute("#package", "aria-invalid"))

        page.fill("#package", "com.ac\$me")
        assertTrue(!page.isVisible("#package-error"), "a package Gradle accepts passes client-side validation")
        val expected = ErrorJson.decode(target.get(apiUrl(page).removePrefix(target.baseUrl)).body())
        page.click("#download")
        page.waitForSelector("#package-error", Page.WaitForSelectorOptions().setState(com.microsoft.playwright.options.WaitForSelectorState.VISIBLE))
        assertEquals(expected, page.textContent("#package-error")!!.trim())

        page.fill("#package", "com.acme.demo")
        assertTrue(!page.isVisible("#package-error"))
    }

    private fun explanations(page: Page, model: InitModel, offered: OfferedVersions) {
        val descriptions = GradleOracle(offered.stable).initOptionDescriptions().also { gradleDescriptions.putAll(it) }
        assertTrue(!page.isVisible("#explain-command .explanation-item"), "explanations are collapsed by default")
        page.click("#explain-command-toggle")
        page.click("#explain-url-toggle")
        for (block in listOf("#explain-command", "#explain-url")) {
            val items = page.locator("$block .explanation-item")
            val count = items.count()
            assertTrue(count > 3, "$block has $count explanations")
            for (i in 0 until count) {
                val item = items.nth(i)
                val option = item.getAttribute("data-option")
                val description = (item.locator(".explanation-description").textContent() ?: "").trim()
                if (option != null) {
                    assertEquals(descriptions[option], description, "$block explanation of --$option")
                    val value = item.getAttribute("data-value")
                    if (value != null) assertValueLabel(model, option, value, item.locator(".explanation-value").textContent() ?: "")
                } else {
                    assertTrue(description.isNotBlank(), "$block fixed explanation")
                }
            }
            assertTrue(page.textContent("$block [data-kind=gradle], $block [data-kind=endpoint]")!!.contains(offered.stable))
            assertEquals(count, page.locator("$block svg.connectors path").count(), "$block connector lines")
        }
    }

    private fun assertValueLabel(model: InitModel, option: String, value: String, label: String) {
        val expected = when (option) {
            "type" -> model.buildType(value).let { type ->
                listOfNotNull(model.componentTypes.first { it.id == type.componentType }.displayName, type.language?.let { l -> model.languages.first { it.id == l }.displayName })
            }
            "dsl" -> listOf(model.dsls.first { it.id == value }.displayName)
            "test-framework" -> listOf(model.testFrameworks.first { it.id == value }.displayName)
            else -> emptyList()
        }
        for (name in expected) assertTrue(label.contains(name), "explanation of $option=$value says '$label', missing '$name'")
    }

    private fun agentSkill(page: Page, target: Target) {
        assertEquals("Or let your coding agent do it", page.textContent("#agent-title")!!.trim())
        assertTrue(page.locator("#snippet-skill").boundingBox()!!.y > page.locator("#snippet-url").boundingBox()!!.y, "the agent skill window is below the API URL window")
        assertEquals("Agent skill", page.textContent("#snippet-skill .code-title")!!.trim())
        val npx = "npx skills add ${target.baseUrl} -g"
        val curl = "curl -fsSL --create-dirs -o ~/.claude/skills/gradle-new/SKILL.md ${target.baseUrl}/.well-known/agent-skills/gradle-new/SKILL.md"
        assertSkillTab(page, "npx", npx)
        page.click("#copy-skill")
        page.waitForSelector("#copy-skill.is-copied")
        assertEquals(npx, copied(page).last())

        page.click("#tab-curl")
        assertSkillTab(page, "curl", curl)
        page.focus("#tab-curl")
        page.keyboard().press("ArrowLeft")
        assertSkillTab(page, "npx", npx)
        assertEquals("tab-npx", page.evaluate("() => document.activeElement.id"))
        page.keyboard().press("ArrowRight")
        assertSkillTab(page, "curl", curl)
        page.click("#copy-skill")
        page.waitForFunction("command => window.__copied.at(-1) === command", curl)

        page.click("#explain-skill-toggle")
        for (tab in listOf("curl", "npx")) {
            page.click("#tab-$tab")
            val tokens = page.locator("#skill-command .token").count()
            val items = page.locator("#explain-skill .explanation-item")
            assertEquals(tokens, items.count(), "$tab explanations")
            for (i in 0 until items.count()) {
                assertTrue((items.nth(i).locator(".explanation-description").textContent() ?: "").isNotBlank(), "$tab explanation $i")
            }
            page.waitForFunction("count => document.querySelectorAll('#explain-skill svg.connectors path').length === count", tokens)
        }
        page.click("#explain-skill-toggle")

        assertEquals("Read the skill", page.textContent("#read-skill")!!.trim())
        assertEquals("llms.txt", page.textContent("#read-llms")!!.trim())
        for (link in listOf("#read-skill", "#read-llms")) {
            val href = page.locator(link).evaluate("a => a.href") as String
            assertTrue(href.startsWith(target.baseUrl + "/"), href)
            val response = target.get(href.removePrefix(target.baseUrl))
            assertEquals(200, response.statusCode(), href)
            assertEquals("text/plain", response.headers().firstValue("content-type").orElse("").substringBefore(';'), href)
        }
    }

    private fun assertSkillTab(page: Page, tab: String, command: String) {
        val other = if (tab == "npx") "curl" else "npx"
        assertEquals("true", page.getAttribute("#tab-$tab", "aria-selected"))
        assertEquals("0", page.getAttribute("#tab-$tab", "tabindex"))
        assertEquals("false", page.getAttribute("#tab-$other", "aria-selected"))
        assertEquals("-1", page.getAttribute("#tab-$other", "tabindex"))
        assertEquals("tab-$tab", page.getAttribute("#skill-panel", "aria-labelledby"))
        assertEquals(command, page.textContent("#skill-command")!!.replace("\\\n", " ").replace(Regex("\\s+"), " ").trim())
    }

    @Suppress("UNCHECKED_CAST")
    private fun copied(page: Page): List<String> = page.evaluate("() => window.__copied") as List<String>

    private fun narrowScreens(page: Page, engine: String) {
        val dir = File(System.getProperty("acceptance.screenshotsDir") ?: error("acceptance.screenshotsDir is not set")).apply { mkdirs() }
        for (width in listOf(1280, 768, 375)) {
            page.setViewportSize(width, 900)
            page.waitForTimeout(400.0)
            page.screenshot(Page.ScreenshotOptions().setFullPage(true).setPath(dir.resolve("$engine-$width.png").toPath()))
            val hasHorizontalScroll = page.evaluate("() => document.documentElement.scrollWidth > document.documentElement.clientWidth") as Boolean
            assertTrue(!hasHorizontalScroll, "horizontal scrolling at $width pixels")
        }
        page.setViewportSize(375, 900)
        assertTrue(page.isVisible("#explain-command .explanation-item"))
        assertEquals(0, page.locator("#explain-command svg.connectors path").count(), "no connector lines on narrow screens")
        page.setViewportSize(1280, 900)
    }

    private fun releaseCandidate(page: Page, target: Target, rc: String) {
        page.check("input[name=version][value=rc]")
        page.waitForFunction("() => document.querySelector('#api-url').textContent.includes('version=rc')")
        assertTrue(page.textContent("#explain-command [data-kind=gradle]")!!.contains(rc))
        page.check("input[name=component-type][value=application]")
        val url = apiUrl(page)
        val download = page.waitForDownload { page.click("#download") }
        val fromApi = target.getBytes(url.removePrefix(target.baseUrl))
        assertTrue(Files.readAllBytes(download.path()).contentEquals(fromApi.body()), "RC download differs from the API's zip")
        page.check("input[name=version][value=stable]")
        page.waitForFunction("() => !/[?&]version=/.test(document.querySelector('#api-url').textContent.replace(/\\s+/g, ''))")
    }

    private fun everyRequestStayedOnTheOrigin(requests: Set<String>, target: Target) {
        val foreign = requests.filterNot { it.startsWith(target.baseUrl + "/") || it.startsWith("blob:" + target.baseUrl + "/") }
        assertEquals(emptyList(), foreign, "requests outside the page's origin")
    }

    private fun commandText(page: Page): String = page.textContent("#command")!!.replace("\\\n", " ").replace(Regex("\\s+"), " ").trim()

    private fun commandArguments(page: Page): List<String> = commandText(page).split(' ')

    private fun apiUrl(page: Page): String = page.textContent("#api-url")!!.replace(Regex("\\s+"), "")

    private fun hasVersionParameter(url: String): Boolean = Regex("[?&]version=").containsMatchIn(url)

    companion object {
        private var opened: Target? = null
        private val initResults = ConcurrentHashMap<String, Map<String, GeneratedFile>>()
        private val gradleDescriptions = ConcurrentHashMap<String, String>()

        @Synchronized
        fun target(): Target = opened ?: Target.open().also { opened = it }

        @JvmStatic
        @AfterAll
        fun closeTarget() {
            opened?.close()
        }
    }
}
