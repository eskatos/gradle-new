package gradlenew.ui

import com.microsoft.playwright.Browser
import com.microsoft.playwright.Page
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

class AccessibilityTest {

    @TestFactory
    fun everyControlIsNamedAndGrouped(): List<DynamicTest> = onPage { page, _ ->
        val tree = page.locator("main").ariaSnapshot()
        for (group in listOf("Gradle version", "Project type", "Language", "Build script DSL", "Test framework")) {
            assertTrue(tree.contains("group \"$group\""), "group $group\n$tree")
        }
        for (entry in listOf("radio \"Stable 9.8.2\" [checked]", "radio \"Application\" [checked]", "textbox \"Project name\"", "textbox \"Package\"", "textbox \"Java version\"",
            "switch \"Split into subprojects\"", "switch \"Use incubating features\"", "switch \"Add clarifying comments\" [checked]",
            "button \"Download the build\"", "button \"Copy the command\"", "button \"Copy the API URL\"", "button \"Explain this command\"", "button \"Explain this URL\"",
            "tablist \"Install with\"", "tab \"npx\" [selected]", "tab \"curl\"", "tabpanel \"npx\"", "button \"Copy the install command\"", "link \"Read the skill\"", "link \"llms.txt\"")) {
            assertTrue(tree.contains(entry), "$entry\n$tree")
        }
        for (id in listOf("field-component-type", "field-dsl", "field-test-framework", "project-name", "package", "java-version", "split-project")) {
            val described = page.getAttribute("#$id", "aria-describedby")!!.split(' ').first()
            assertTrue(page.textContent("#$described")!!.isNotBlank(), "#$id is described by #$described")
        }
        assertEquals("Gradle", page.getAttribute(".site-logo", "alt"))
    }

    @TestFactory
    fun announcesTheExpandedStateOfTheExplanations(): List<DynamicTest> = onPage { page, _ ->
        assertTrue(!page.locator("#snippet-command").ariaSnapshot().contains("[expanded]"))
        page.click("#explain-command-toggle")
        assertTrue(page.locator("#snippet-command").ariaSnapshot().contains("button \"Explain this command\" [expanded]"), page.locator("#snippet-command").ariaSnapshot())
        assertEquals("explain-command", page.getAttribute("#explain-command-toggle", "aria-controls"))
        page.click("#explain-skill-toggle")
        assertTrue(page.locator("#snippet-skill").ariaSnapshot().contains("button \"Explain this command\" [expanded]"), page.locator("#snippet-skill").ariaSnapshot())
        assertEquals("explain-skill", page.getAttribute("#explain-skill-toggle", "aria-controls"))
        assertEquals("skill-panel", page.getAttribute("#tab-curl", "aria-controls"))
    }

    @TestFactory
    fun theKeyboardReachesEveryControlInReadingOrderWithAVisibleFocus(): List<DynamicTest> = onPage { page, engine ->
        val expected = listOf("gradle-home", "develocity-home", "version", "component-type", "language", "dsl", "test-framework", "project-name", "package", "java-version",
            "split-project", "incubating", "comments", "download", "copy-command", "explain-command-toggle", "copy-url", "explain-url-toggle",
            "tab-npx", "copy-skill", "skill-panel", "explain-skill-toggle", "read-skill", "read-llms")
        val reached = mutableListOf<String>()
        page.focus("body")
        repeat(expected.size) {
            page.keyboard().press(Browsers.focusNextKey(engine))
            val focused = page.evaluate("""() => {
                const e = document.activeElement;
                const ring = e.type === 'radio' ? getComputedStyle(e.closest('.choice')).boxShadow : getComputedStyle(e).boxShadow;
                return { key: e.type === 'radio' ? e.name : e.id, ring };
            }""") as Map<*, *>
            reached.add(focused["key"] as String)
            assertTrue((focused["ring"] as String) != "none", "visible focus on ${focused["key"]}")
        }
        assertEquals(expected, reached)
    }

    private fun onPage(test: (Page, String) -> Unit): List<DynamicTest> =
        Browsers.engines.map { engine ->
            DynamicTest.dynamicTest(engine) {
                SiteServer(SiteServer.siteDir, mapOf(
                    "/api/versions" to { e -> SiteServer.respond(e, 200, "application/json", Fixtures.versions.toByteArray()) },
                    "/v/*" to { e -> SiteServer.respond(e, 200, "application/json", File(Fixtures.fixtureDir, e.requestURI.path.removePrefix("/")).readBytes()) },
                )).use { server ->
                    val context = Browsers.browser(engine).newContext(Browser.NewContextOptions().setViewportSize(1280, 900))
                    try {
                        val page = context.newPage()
                        page.setDefaultTimeout(5_000.0)
                        page.navigate(server.url + "/", Page.NavigateOptions().setTimeout(FIRST_LOAD_TIMEOUT))
                        page.waitForSelector("#command .token", Page.WaitForSelectorOptions().setTimeout(FIRST_LOAD_TIMEOUT))
                        test(page, engine)
                    } finally {
                        context.close()
                    }
                }
            }
        }
}
