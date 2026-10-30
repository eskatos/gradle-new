package gradlenew.ui

import com.microsoft.playwright.Browser
import com.microsoft.playwright.Page
import com.sun.net.httpserver.HttpExchange
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

class PageTest {

    private val zip = byteArrayOf(0x50, 0x4b, 0x05, 0x06) + ByteArray(18)

    @TestFactory
    fun showsGradleByDevelocityInTheHeader(): List<DynamicTest> = onPage { page, _ ->
        assertEquals("https://gradle.org/", page.getAttribute("#gradle-home", "href"))
        assertEquals("Gradle", page.getAttribute("#gradle-home img", "alt"))
        assertEquals("by", page.textContent(".site-header .brand-by")!!.trim())
        assertEquals("https://develocity.ai/", page.getAttribute("#develocity-home", "href"))
        assertEquals("Develocity", page.getAttribute("#develocity-home img", "alt"))
        assertEquals("by", page.evaluate("() => [...document.querySelector('.site-header .brand-lockup').childNodes].map((n) => n.textContent.trim()).filter(Boolean).join(' ')"))
        assertEquals("Gradle by Develocity", page.locator(".site-header .brand-lockup").evaluate("e => [...e.querySelectorAll('img, .brand-by')].map((n) => n.alt || n.textContent.trim()).join(' ')"))
        page.waitForFunction("() => [...document.querySelectorAll('.site-header img')].every((i) => i.complete && i.naturalWidth > 0)")
        @Suppress("UNCHECKED_CAST")
        val whiteShare = page.evaluate("""() => {
            const image = document.querySelector('#develocity-home img');
            const canvas = document.createElement('canvas');
            canvas.width = image.naturalWidth; canvas.height = image.naturalHeight;
            const context = canvas.getContext('2d');
            context.drawImage(image, 0, 0);
            const pixels = context.getImageData(0, 0, canvas.width, canvas.height).data;
            let opaque = 0, light = 0;
            for (let i = 0; i < pixels.length; i += 4) {
                if (pixels[i + 3] < 128) continue;
                opaque++;
                if (pixels[i] > 200 && pixels[i + 1] > 200 && pixels[i + 2] > 200) light++;
            }
            return opaque === 0 ? 0 : light / opaque;
        }""") as Number
        assertTrue(whiteShare.toDouble() > 0.3, "the Develocity wordmark renders white on the dark header, share of light pixels: $whiteShare")
    }

    @TestFactory
    fun rendersTheFormFromTheModel(): List<DynamicTest> = onPage { page, _ ->
        assertTrue(page.isVisible("#form-fields"))
        assertTrue(!page.isVisible("#form-loading"))
        assertEquals("9.8.2", page.textContent("[data-stable-version]"))
        assertTrue(page.isChecked("input[name=version][value=stable]"))
        assertEquals("9.8.2", page.textContent("[data-channel=stable] .channel-version")!!.trim())
        assertEquals(1, page.locator("input[name=version][value=rc]").count(), "the RC switch is always shown")
        assertTrue(page.isDisabled("input[name=version][value=rc]"), "and disabled without an RC")
        assertEquals("None available", page.textContent("[data-channel=rc] .choice-note")!!.trim())
        assertEquals("not-allowed", page.evaluate("() => getComputedStyle(document.querySelector('[data-channel=rc]')).cursor"))
        page.click("[data-channel=rc]", com.microsoft.playwright.Page.ClickOptions().setForce(true))
        assertTrue(page.isChecked("input[name=version][value=stable]"), "a disabled RC switch cannot be selected")
        assertEquals(listOf("stable", "rc", "previous"), values(page, "input[name=version]"))
        assertEquals(listOf("application", "library", "gradle-plugin", "basic"), values(page, "input[name=component-type]"))
        assertEquals(listOf("java", "kotlin", "groovy", "scala", "cpp", "swift"), values(page, "input[name=language]"))
        assertEquals(listOf("kotlin", "groovy"), values(page, "input[name=dsl]"))
        assertEquals(listOf("junit", "testng", "spock", "junit-jupiter"), values(page, "input[name=test-framework]"))
        assertTrue(page.isChecked("input[name=test-framework][value=junit-jupiter]"))
        assertEquals("JUnit Jupiter", page.textContent("label:has(input[name=test-framework][value=junit-jupiter]) .choice-label"))
        assertTrue(page.isChecked("input[name=component-type][value=application]"))
        assertTrue(page.isChecked("input[name=language][value=java]"))
        assertEquals("demo", page.inputValue("#project-name"))
        assertEquals("org.example", page.inputValue("#package"))
        assertEquals("21", page.inputValue("#java-version"))
        assertTrue(!page.isChecked("#split-project") && !page.isChecked("#incubating") && page.isChecked("#comments"))
        assertEquals("Set the build script DSL to be used in generated scripts.", page.textContent("[data-hint=dsl]"))
        assertEquals("Include clarifying comments in files.", page.textContent("[data-hint=comments]"))
        assertEquals("Application", page.textContent("label:has(input[name=component-type][value=application]) .choice-label"))
    }

    @TestFactory
    fun showsOnlyTheOptionsOfTheChosenType(): List<DynamicTest> = onPage { page, _ ->
        page.check("input[name=component-type][value=basic]")
        assertTrue(!page.isVisible("#field-language"))
        assertTrue(!page.isVisible("#field-package") && !page.isVisible("#field-java-version"))
        assertTrue(!page.isVisible("#field-test-framework") && !page.isVisible("#field-split-project"))
        assertTrue(page.isVisible("#field-dsl") && page.isVisible("#field-project-name"))

        page.check("input[name=component-type][value=gradle-plugin]")
        assertEquals(listOf("java", "kotlin", "groovy"), values(page, "input[name=language]"))
        assertTrue(page.isChecked("input[name=language][value=java]"))
        assertTrue(page.isVisible("#field-package") && !page.isVisible("#field-split-project"))

        page.check("input[name=component-type][value=application]")
        page.check("input[name=test-framework][value=spock]")
        page.check("#split-project")
        assertEquals(listOf("junit-jupiter"), values(page, "input[name=test-framework]"))
        page.uncheck("#split-project")
        assertTrue(page.isChecked("input[name=test-framework][value=junit-jupiter]"))
    }

    @TestFactory
    fun keepsNativeKeyboardBehaviour(): List<DynamicTest> = onPage { page, _ ->
        page.focus("input[name=dsl][value=kotlin]")
        page.keyboard().press("ArrowRight")
        assertTrue(page.isChecked("input[name=dsl][value=groovy]"))
        assertTrue(commandText(page).contains("--dsl groovy"))
        page.focus("#comments")
        page.keyboard().press("Space")
        assertTrue(!page.isChecked("#comments"))
        assertTrue(commandText(page).contains("--no-comments"))
    }

    @TestFactory
    fun validatesWithTheModelsRules(): List<DynamicTest> = onPage { page, server ->
        page.fill("#package", "1acme")
        assertTrue(page.isVisible("#package-error"))
        assertEquals("true", page.getAttribute("#package", "aria-invalid"))
        page.click("#download")
        assertEquals("package", page.evaluate("() => document.activeElement.id"))
        assertTrue(server.requests.none { it.startsWith("/api/generate") }, "no request is sent with invalid input")
        page.fill("#package", "com.acme")
        assertTrue(!page.isVisible("#package-error"))
        assertEquals("false", page.getAttribute("#package", "aria-invalid"))
        page.fill("#java-version", "6")
        assertTrue(page.textContent("#java-version-error")!!.contains("7"))
    }

    @TestFactory
    fun commandAndUrlFollowTheChoices(): List<DynamicTest> = onPage { page, server ->
        page.fill("#project-name", "demo-app")
        assertEquals(
            "gradle init \\\n  --type java-application \\\n  --project-name demo-app \\\n  --use-defaults",
            page.textContent("#command"),
        )
        assertEquals("${server.url}/api/generate?type=java-application&project-name=demo-app", page.textContent("#api-url")!!.replace(Regex("\\s+"), ""))
        page.check("input[name=dsl][value=groovy]")
        page.uncheck("#comments")
        assertEquals("gradle init --type java-application --dsl groovy --project-name demo-app --no-comments --use-defaults", commandText(page))
        assertEquals("${server.url}/api/generate?type=java-application&dsl=groovy&project-name=demo-app&comments=false", page.textContent("#api-url")!!.replace(Regex("\\s+"), ""))
        page.check("input[name=dsl][value=kotlin]")
        page.check("#comments")
        assertEquals("gradle init --type java-application --project-name demo-app --use-defaults", commandText(page), "back at the defaults, the options disappear")
        page.click("#copy-command")
        page.waitForSelector("#copy-command.is-copied")
        assertEquals("Copied", page.textContent("#copy-command .copy-label"))
        page.click("#copy-url")
        page.waitForSelector("#copy-url.is-copied")
        @Suppress("UNCHECKED_CAST")
        val copied = page.evaluate("() => window.__copied") as List<String>
        assertEquals("gradle init --type java-application --project-name demo-app --use-defaults", copied[0])
        assertEquals(server.url + "/api/generate?type=java-application&project-name=demo-app", copied[1])
    }

    @TestFactory
    fun explainsTheCommandAndTheUrl(): List<DynamicTest> = onPage { page, _ ->
        assertTrue(!page.isVisible("#explain-command"))
        assertEquals("false", page.getAttribute("#explain-command-toggle", "aria-expanded"))
        page.click("#explain-command-toggle")
        assertEquals("true", page.getAttribute("#explain-command-toggle", "aria-expanded"))
        val tokens = page.locator("#command .token").count()
        val items = page.locator("#explain-command .explanation-item")
        assertEquals(tokens, items.count())
        page.waitForFunction("() => document.querySelectorAll('#explain-command svg.connectors path').length > 0")
        assertEquals(tokens, page.locator("#explain-command svg.connectors path").count())
        assertEquals("Set the type of project to generate.", page.textContent("#explain-command [data-option=type] .explanation-description"))
        assertEquals("Application in Java", page.textContent("#explain-command [data-option=type] .explanation-value"))
        page.hover("#command .token[data-token-id=option-type]")
        assertTrue(page.getAttribute("#explain-command .explanation-item[data-token-id=option-type]", "class")!!.contains("is-active"))
        assertTrue(page.getAttribute("#explain-command svg.connectors path[data-token-id=option-type]", "class")!!.contains("is-active"))
        page.focus("#explain-command .explanation-item[data-token-id=option-project-name]")
        assertTrue(page.getAttribute("#command .token[data-token-id=option-project-name]", "class")!!.contains("is-active"))

        page.click("#explain-url-toggle")
        assertTrue(page.textContent("#explain-url [data-kind=endpoint] .explanation-description")!!.contains("9.8.2"))
        page.waitForFunction("() => document.querySelectorAll('#explain-url svg.connectors path').length > 0")
        assertEquals(page.locator("#api-url .token").count(), page.locator("#explain-url svg.connectors path").count())

        page.setViewportSize(375, 800)
        page.waitForFunction("() => document.querySelectorAll('#explain-command svg.connectors path').length === 0")
        assertTrue(page.isVisible("#explain-command [data-option=type] .explanation-token"))
        val scrolls = page.evaluate("() => document.documentElement.scrollWidth > document.documentElement.clientWidth") as Boolean
        assertTrue(!scrolls, "no horizontal scrolling at 375 pixels")

        page.setViewportSize(1280, 800)
        page.click("#explain-command-toggle")
        assertTrue(!page.isVisible("#explain-command"))
    }

    @TestFactory
    fun clickingATokenRevealsItsExplanation(): List<DynamicTest> = onPage { page, _ ->
        page.setViewportSize(1280, 500)
        val nameToken = "#command .token[data-token-id=option-project-name]"
        val nameItem = "#explain-command .explanation-item[data-token-id=option-project-name]"
        val box = page.locator(nameToken).boundingBox()!!
        page.mouse().move(box.x + 2, box.y + box.height / 2)
        page.mouse().down()
        page.mouse().move(box.x + box.width - 2, box.y + box.height / 2)
        page.mouse().up()
        assertTrue(!page.isVisible("#explain-command"), "selecting text must not open the explanation")
        page.evaluate("() => getSelection().removeAllRanges()")

        page.click(nameToken)
        assertEquals("true", page.getAttribute("#explain-command-toggle", "aria-expanded"))
        assertTrue(page.isVisible("#explain-command"))
        assertTrue(page.getAttribute(nameItem, "class")!!.contains("is-active"))
        assertTrue(page.getAttribute(nameToken, "class")!!.contains("is-active"))
        page.waitForFunction("""selector => { const r = document.querySelector(selector).getBoundingClientRect(); return r.top >= 0 && r.bottom <= innerHeight }""", nameItem)

        page.click("#command .token[data-token-id=option-type]")
        assertTrue(page.getAttribute("#explain-command .explanation-item[data-token-id=option-type]", "class")!!.contains("is-active"))
        assertTrue(!page.getAttribute(nameItem, "class")!!.contains("is-active"))

        page.click("#explain-command-toggle")
        assertTrue(!page.isVisible("#explain-command"))
        assertTrue(!page.getAttribute("#command .token[data-token-id=option-type]", "class")!!.contains("is-active"))

        page.click("#api-url .token[data-token-id=param-project-name]")
        assertEquals("true", page.getAttribute("#explain-url-toggle", "aria-expanded"))
        assertTrue(page.getAttribute("#explain-url .explanation-item[data-token-id=param-project-name]", "class")!!.contains("is-active"))
        assertEquals("pointer", page.evaluate("() => getComputedStyle(document.querySelector('#api-url .token')).cursor"))
    }

    @TestFactory
    fun offersTheAgentSkillWithNpxOrCurl(): List<DynamicTest> = onPage { page, server ->
        val npx = "npx skills add ${server.url} -g"
        val curl = "curl -fsSL --create-dirs -o ~/.claude/skills/gradle-new/SKILL.md ${server.url}/.well-known/agent-skills/gradle-new/SKILL.md"
        assertEquals("Or let your coding agent do it", page.textContent("#agent-title")!!.trim())
        assertEquals("Install the gradle-new skill once, then ask your agent for a new Gradle project.", page.textContent("#agent-title + .section-lead")!!.trim())
        assertTrue(page.locator("#snippet-skill").boundingBox()!!.y > page.locator("#snippet-url").boundingBox()!!.y)
        assertEquals("Agent skill", page.textContent("#snippet-skill .code-title")!!.trim())
        assertSkillTab(page, "npx", npx)
        assertEquals("npx skills add ${server.url} -g", page.textContent("#skill-command"), "the npx command fits on one line")
        page.click("#copy-skill")
        page.waitForSelector("#copy-skill.is-copied")

        page.click("#tab-curl")
        assertSkillTab(page, "curl", curl)
        assertEquals(3, page.textContent("#skill-command")!!.lines().size, "the curl command is wrapped")
        page.click("#copy-skill")
        page.waitForFunction("command => window.__copied.at(-1) === command", curl)
        @Suppress("UNCHECKED_CAST")
        assertEquals(listOf(npx, curl), page.evaluate("() => window.__copied") as List<String>)

        page.focus("#tab-curl")
        for ((key, tab) in listOf("ArrowLeft" to "npx", "ArrowLeft" to "curl", "ArrowRight" to "npx", "End" to "curl", "Home" to "npx")) {
            page.keyboard().press(key)
            assertSkillTab(page, tab, if (tab == "npx") npx else curl)
            assertEquals("tab-$tab", page.evaluate("() => document.activeElement.id"), "focus follows $key")
        }

        assertEquals("/.well-known/agent-skills/gradle-new/SKILL.md", page.getAttribute("#read-skill", "href"))
        assertEquals("/llms.txt", page.getAttribute("#read-llms", "href"))
        assertEquals("Read the skill", page.textContent("#read-skill")!!.trim())
        assertEquals("llms.txt", page.textContent("#read-llms")!!.trim())
    }

    @TestFactory
    fun explainsTheSkillInstallCommands(): List<DynamicTest> = onPage { page, _ ->
        assertTrue(!page.isVisible("#explain-skill"))
        page.click("#explain-skill-toggle")
        assertEquals("true", page.getAttribute("#explain-skill-toggle", "aria-expanded"))
        for (tab in listOf("npx", "curl")) {
            page.click("#tab-$tab")
            val tokens = page.locator("#skill-command .token").count()
            assertEquals(tokens, page.locator("#explain-skill .explanation-item").count(), tab)
            page.waitForFunction("count => document.querySelectorAll('#explain-skill svg.connectors path').length === count", tokens)
        }
        assertTrue(page.textContent("#explain-skill .explanation-item[data-token-id=output] .explanation-description")!!.contains("Claude Code"))
        page.hover("#skill-command .token[data-token-id=output]")
        assertTrue(page.getAttribute("#explain-skill .explanation-item[data-token-id=output]", "class")!!.contains("is-active"))
        assertEquals("-o", page.textContent("#skill-command .token[data-token-id=output] .token-option"))

        page.click("#tab-npx")
        assertTrue(page.textContent("#explain-skill .explanation-item[data-token-id=site] .explanation-description")!!.contains("/.well-known/agent-skills/index.json"))
        page.click("#explain-skill-toggle")
        assertTrue(!page.isVisible("#explain-skill"))
        page.click("#skill-command .token[data-token-id=global]")
        assertTrue(page.isVisible("#explain-skill"))
        assertTrue(page.getAttribute("#explain-skill .explanation-item[data-token-id=global]", "class")!!.contains("is-active"))

        page.setViewportSize(375, 800)
        page.waitForFunction("() => document.querySelectorAll('#explain-skill svg.connectors path').length === 0")
        page.click("#tab-curl")
        assertTrue(page.isVisible("#explain-skill .explanation-item[data-token-id=skill-url] .explanation-token"))
        val scrolls = page.evaluate("() => document.documentElement.scrollWidth > document.documentElement.clientWidth") as Boolean
        assertTrue(!scrolls, "no horizontal scrolling at 375 pixels")
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

    @TestFactory
    fun showsTheRateLimitMessageNextToTheDownloadButton(): List<DynamicTest> = onPage { page, server ->
        page.fill("#project-name", "busy")
        page.click("#download")

        page.waitForSelector("#form-error:not([hidden])")
        assertEquals("Too many builds were requested from your address in a short time. Try again in 42 seconds.", page.textContent("#form-error .error-text")!!.trim())
        assertEquals("Download the build", page.textContent("#download .btn-label")!!.trim())
        assertEquals(null, page.getAttribute("#download", "aria-busy"))
        assertTrue(server.requests.any { it.contains("project-name=busy") })
    }

    @TestFactory
    fun downloadsTheBuildOrPlacesTheRejection(): List<DynamicTest> = onPage { page, server ->
        page.fill("#project-name", "demo-app")
        val download = page.waitForDownload { page.click("#download") }
        assertEquals("demo-app.zip", download.suggestedFilename())
        assertTrue(java.nio.file.Files.readAllBytes(download.path()).contentEquals(zip))
        assertTrue(server.requests.any { it.startsWith("/api/generate?type=java-application") })
        assertTrue(page.textContent("#form-status")!!.contains("demo-app.zip"))

        page.fill("#package", "com.ac\$me")
        page.click("#download")
        page.waitForSelector("#package-error:not([hidden])")
        assertEquals("Package name: 'com.ac\$me' is not supported by this service - it must not contain '\$'.", page.textContent("#package-error .error-text"))
        assertEquals("true", page.getAttribute("#package", "aria-invalid"))
        page.fill("#package", "com.acme")
        assertTrue(!page.isVisible("#package-error"))

        page.fill("#project-name", "boom")
        page.click("#download")
        page.waitForSelector("#form-error:not([hidden])")
        assertTrue(page.textContent("#form-error")!!.isNotBlank())
    }

    @TestFactory
    fun offersTheReleaseCandidateChannel(): List<DynamicTest> = onPage(versions = Fixtures.versionsWithRc) { page, server ->
        assertEquals("9.9.0-rc-1", page.textContent("[data-channel=rc] .channel-version")!!.trim())
        assertTrue(page.isEnabled("input[name=version][value=rc]"))
        page.check("input[name=version][value=rc]")
        page.waitForFunction("() => document.querySelector('#api-url').textContent.includes('version=rc')")
        page.waitForFunction("() => [...document.querySelectorAll('#command .token')].length > 0")
        assertTrue(server.requests.contains("/v/9.9.0-rc-1/model.json"))
        page.click("#explain-command-toggle")
        assertTrue(page.textContent("#explain-command [data-kind=gradle] .explanation-description")!!.contains("9.9.0-rc-1"))
    }

    @TestFactory
    fun offersThePreviousMajorChannel(): List<DynamicTest> = onPage { page, server ->
        assertEquals("Previous major", page.textContent("[data-channel=previous] .choice-label")!!.trim())
        assertEquals("8.14.6", page.textContent("[data-channel=previous] .channel-version")!!.trim())
        assertTrue(page.isEnabled("input[name=version][value=previous]"), "the previous major is always offered")
        page.check("input[name=version][value=previous]")
        page.waitForFunction("() => document.querySelector('#api-url').textContent.includes('version=previous')")
        assertTrue(server.requests.contains("/v/8.14.6/model.json"))
        page.click("#explain-command-toggle")
        assertTrue(page.textContent("#explain-command [data-kind=gradle] .explanation-description")!!.contains("8.14.6"))
        page.check("input[name=version][value=stable]")
        page.waitForFunction("() => !document.querySelector('#api-url').textContent.includes('version=')")
    }

    @TestFactory
    fun hasItsTitleDescriptionAndIconAndRespectsReducedMotion(): List<DynamicTest> = onPage { page, _ ->
        assertEquals("gradle.new: create a new Gradle build", page.title())
        assertTrue(page.getAttribute("meta[name=description]", "content")!!.contains("gradle init"))
        assertEquals("assets/logo/gradle-icon-colour.svg", page.getAttribute("link[rel=icon]", "href"))
        assertEquals("Gradle", page.getAttribute(".site-logo", "alt"))
        assertTrue(page.textContent("h1")!!.contains("Gradle®"))
        assertTrue(page.textContent(".site-footer")!!.contains("This website is not affiliated with or endorsed by Gradle, Inc."))
        val normal = page.evaluate("() => parseFloat(getComputedStyle(document.querySelector('.explain-toggle .icon')).transitionDuration)") as Number
        assertTrue(normal.toDouble() in 0.12..0.32, "transitions last 120 to 320 ms, got ${normal}s")
        page.emulateMedia(Page.EmulateMediaOptions().setReducedMotion(com.microsoft.playwright.options.ReducedMotion.REDUCE))
        val reduced = page.evaluate("() => parseFloat(getComputedStyle(document.querySelector('.explain-toggle .icon')).transitionDuration)") as Number
        assertTrue(reduced.toDouble() < 0.01, "reduced motion, got ${reduced}s")
    }

    private fun values(page: Page, selector: String): List<String?> = page.locator(selector).all().map { it.getAttribute("value") }

    private fun commandText(page: Page): String = page.textContent("#command")!!.replace("\\\n", " ").replace(Regex("\\s+"), " ")

    private fun onPage(versions: String = Fixtures.versions, test: (Page, SiteServer) -> Unit): List<DynamicTest> =
        Browsers.engines.map { engine ->
            DynamicTest.dynamicTest(engine) {
                SiteServer(SiteServer.siteDir, overrides(versions)).use { server ->
                    val context = Browsers.browser(engine).newContext(Browser.NewContextOptions().setAcceptDownloads(true).setViewportSize(1280, 800))
                    try {
                        context.addInitScript("window.__copied = []; Object.defineProperty(navigator, 'clipboard', { configurable: true, value: { writeText: (text) => { window.__copied.push(text); return Promise.resolve(); } } });")
                        val page = context.newPage()
                        page.setDefaultTimeout(5_000.0)
                        val problems = mutableListOf<String>()
                        page.onConsoleMessage { if (it.type() == "error") problems.add(it.text()) }
                        page.onPageError { problems.add(it) }
                        page.navigate(server.url + "/", Page.NavigateOptions().setTimeout(FIRST_LOAD_TIMEOUT))
                        page.waitForSelector("#command .token", Page.WaitForSelectorOptions().setTimeout(FIRST_LOAD_TIMEOUT))
                        test(page, server)
                        assertEquals(emptyList(), problems.filterNot { it.contains("status of 400") || it.contains("status of 500") || it.contains("400 (Bad Request)") || it.contains("500 (Internal Server Error)") || it.contains("status of 429") || it.contains("429 (Too Many Requests)") }, "console errors in $engine")
                    } finally {
                        context.close()
                    }
                }
            }
        }

    private fun overrides(versions: String): Map<String, (HttpExchange) -> Unit> = mapOf(
        "/api/versions" to { exchange -> SiteServer.respond(exchange, 200, "application/json", versions.toByteArray()) },
        "/v/*" to { exchange -> serveFixture(exchange) },
        "/api/generate" to { exchange -> generate(exchange) },
    )

    private fun serveFixture(exchange: HttpExchange) {
        val file = File(Fixtures.fixtureDir, exchange.requestURI.path.removePrefix("/"))
        if (file.isFile) SiteServer.respond(exchange, 200, "application/json", file.readBytes()) else exchange.sendResponseHeaders(404, -1)
    }

    private fun generate(exchange: HttpExchange) {
        val query = java.net.URLDecoder.decode(exchange.requestURI.rawQuery ?: "", Charsets.UTF_8)
        when {
            query.contains("package=com.ac\$me") -> SiteServer.respond(exchange, 400, "application/json",
                "{\"error\": \"Package name: 'com.ac\$me' is not supported by this service - it must not contain '\$'.\", \"parameter\": \"package\"}".toByteArray())
            query.contains("project-name=boom") -> SiteServer.respond(exchange, 500, "text/plain", "boom".toByteArray())
            query.contains("project-name=busy") -> SiteServer.respond(exchange, 429, "application/json",
                "{\"error\": \"Too many builds were requested from your address in a short time. Try again in 42 seconds.\", \"parameter\": null}".toByteArray(),
                mapOf("Retry-After" to "42"))
            else -> SiteServer.respond(exchange, 200, "application/zip", zip, mapOf("Content-Disposition" to "attachment; filename=\"demo-app.zip\""))
        }
    }
}
