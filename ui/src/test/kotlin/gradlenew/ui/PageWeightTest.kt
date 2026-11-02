package gradlenew.ui

import com.microsoft.playwright.Page
import com.microsoft.playwright.options.LoadState
import com.sun.net.httpserver.HttpExchange
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

class PageWeightTest {

    private val icons = mapOf(
        "download" to "icon-download", "copy" to "icon-copy", "check" to "icon-check", "circle-alert" to "icon-alert",
        "chevron-down" to "icon-chevron", "terminal" to "icon-terminal", "link" to "icon-link",
    )

    @TestFactory
    fun aFirstVisitMakesAtMostTwelveRequests(): List<DynamicTest> = Browsers.engines.map { engine ->
        DynamicTest.dynamicTest(engine) {
            SiteServer(SiteServer.siteDir, api()).use { server ->
                val context = Browsers.browser(engine).newContext()
                try {
                    val page = context.newPage()
                    page.setDefaultTimeout(WAIT_TIMEOUT)
                    val requests = mutableListOf<String>()
                    page.onRequest { requests.add(it.url().removePrefix(server.url)) }
                    page.navigate(server.url + "/")
                    page.waitForSelector("#command .token")
                    page.waitForFunction("() => document.fonts.status === 'loaded'")
                    page.waitForLoadState(LoadState.NETWORKIDLE)
                    assertTrue(requests.size <= 12, "$engine made ${requests.size} requests on a first visit: $requests")
                } finally {
                    context.close()
                }
            }
        }
    }

    @TestFactory
    fun everyIconRendersAsTheSourceIcon(): List<DynamicTest> = Browsers.engines.map { engine ->
        DynamicTest.dynamicTest(engine) {
            val index = File(SiteServer.siteDir, "index.html").readText()
            val stylesheets = Regex("""<link rel="stylesheet" href="/?([^"]+)">""").findAll(index).map { it.groupValues[1] }.toList()
            val page = "<!doctype html>" + stylesheets.joinToString("") { """<link rel="stylesheet" href="/$it">""" } +
                """<link rel="stylesheet" href="/icon-check.css"><div class="big">""" +
                icons.entries.joinToString("") { (name, cssClass) -> """<span class="icon $cssClass" id="site-$name"></span><span class="ref ref-$name" id="ref-$name"></span>""" } +
                "</div>"
            val css = ".big { font-size: 96px; line-height: 1; color: #000; background: #fff; }\n.big .icon, .ref { display: block; margin: 8px; vertical-align: top; }\n" +
                ".ref { display: inline-block; width: 96px; height: 96px; background-color: #000; }\n" +
                icons.keys.joinToString("\n") { ".ref-$it { -webkit-mask: url(/source-icons/$it.svg) center / contain no-repeat; mask: url(/source-icons/$it.svg) center / contain no-repeat; }" }
            SiteServer(SiteServer.siteDir, mapOf(
                "/icon-check.html" to { e -> SiteServer.respond(e, 200, "text/html; charset=utf-8", page.toByteArray()) },
                "/icon-check.css" to { e -> SiteServer.respond(e, 200, "text/css; charset=utf-8", css.toByteArray()) },
                "/source-icons/*" to { e -> SiteServer.respond(e, 200, "image/svg+xml", File("src/site/assets/icons", e.requestURI.path.substringAfterLast('/')).readBytes()) },
            )).use { server ->
                Browsers.browser(engine).newPage().use { browserPage ->
                    browserPage.setDefaultTimeout(WAIT_TIMEOUT)
                    browserPage.navigate(server.url + "/icon-check.html")
                    browserPage.waitForLoadState(LoadState.NETWORKIDLE)
                    for (name in icons.keys) {
                        val fromSite = dark(browserPage, "site-$name")
                        val fromSource = dark(browserPage, "ref-$name")
                        val inked = fromSource.count { it }
                        val differing = fromSite.indices.count { fromSite[it] != fromSource[it] }
                        assertTrue(inked > 200, "$engine: the source $name icon renders ($inked dark pixels)")
                        assertTrue(differing < inked / 20, "$engine: the site's $name icon matches its source ($differing of $inked pixels differ)")
                    }
                }
            }
        }
    }

    private fun dark(page: Page, id: String): BooleanArray {
        val image = ImageIO.read(ByteArrayInputStream(page.locator("#$id").screenshot()))
        return BooleanArray(image.width * image.height) { k -> (image.getRGB(k % image.width, k / image.width) and 0xff) < 128 }
    }

    private fun api(): Map<String, (HttpExchange) -> Unit> = mapOf(
        "/api/versions" to { e -> SiteServer.respond(e, 200, "application/json", Fixtures.versions.toByteArray()) },
        "/build.json" to { e -> e.sendResponseHeaders(204, -1) },
        "/v/*" to { e ->
            val file = File(Fixtures.fixtureDir, e.requestURI.path.removePrefix("/"))
            if (file.isFile) SiteServer.respond(e, 200, "application/json", file.readBytes()) else e.sendResponseHeaders(404, -1)
        },
    )
}
