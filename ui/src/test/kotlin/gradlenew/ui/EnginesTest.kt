package gradlenew.ui

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

class EnginesTest {

    @TestFactory
    fun everyEngineLoadsTheSite(): List<DynamicTest> =
        Browsers.engines.map { engine ->
            DynamicTest.dynamicTest(engine) {
                SiteServer(SiteServer.siteDir).use { server ->
                    Browsers.browser(engine).newPage().use { page ->
                        val response = page.navigate(server.url + "/")
                        assertEquals(200, response!!.status())
                        assertTrue(page.title().contains("gradle.new"), page.title())
                    }
                }
            }
        }

    @TestFactory
    fun allThreeEnginesAreConfigured(): List<DynamicTest> =
        listOf(DynamicTest.dynamicTest("engines") { assertEquals(listOf("webkit", "firefox", "chromium"), Browsers.engines) })
}
