package gradlenew.ui

import com.microsoft.playwright.Page
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

class MaintenancePageTest {

    @TestFactory
    fun saysTheSiteIsNotAvailableInTheSiteStyle(): List<DynamicTest> =
        Browsers.engines.map { engine ->
            DynamicTest.dynamicTest(engine) {
                SiteServer(SiteServer.siteDir).use { server ->
                    Browsers.browser(engine).newPage().use { page ->
                        val problems = mutableListOf<String>()
                        page.onResponse { if (it.status() >= 400) problems.add("${it.status()} ${it.url()}") }
                        page.onConsoleMessage { if (it.type() == "error") problems.add(it.text()) }
                        page.setDefaultTimeout(FIRST_LOAD_TIMEOUT)

                        assertEquals(200, page.navigate(server.url + "/maintenance.html")!!.status())
                        page.waitForFunction("() => document.fonts.status === 'loaded' && [...document.images].every((i) => i.complete && i.naturalWidth > 0)")

                        assertTrue(page.title().contains("gradle-new"), page.title())
                        assertTrue(page.textContent("main")!!.contains("not available at the moment"), page.textContent("main"))
                        assertEquals("https://gradle.org/", page.getAttribute("#gradle-home", "href"))
                        assertEquals("https://develocity.ai/", page.getAttribute("#develocity-home", "href"))
                        assertTrue(page.isVisible(".site-footer"))
                        assertEquals(0, page.locator("script").count(), "the maintenance page needs no script")
                        @Suppress("UNCHECKED_CAST")
                        val references = page.evaluate("() => [...document.querySelectorAll('link[href], img[src]')].map((e) => e.getAttribute('href') || e.getAttribute('src')).filter((u) => !u.startsWith('https://'))") as List<String>
                        assertTrue(references.isNotEmpty() && references.all { it.startsWith("/") }, "absolute references, so the page renders on any path: $references")
                        assertEquals(emptyList(), problems)
                        val maintenanceStyle = headerStyle(page)

                        page.navigate(server.url + "/")
                        assertEquals(headerStyle(page), maintenanceStyle, "the header looks like the site's")
                    }
                }
            }
        }

    private fun headerStyle(page: Page): Any? =
        page.evaluate("() => { const s = getComputedStyle(document.querySelector('.site-header')); const b = getComputedStyle(document.body); return [s.backgroundColor, s.color, b.fontFamily, b.backgroundColor].join(' | ') }")
}
