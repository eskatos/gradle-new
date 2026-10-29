package gradlenew.acceptance

import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Playwright

object Browsers {

    val engines: List<String> = (System.getProperty("playwright.engines") ?: "webkit,firefox,chromium").split(',').map { it.trim() }.filter { it.isNotEmpty() }

    private val playwright: Playwright by lazy { Playwright.create() }
    private val browsers = mutableMapOf<String, Browser>()

    @Synchronized
    fun browser(engine: String): Browser =
        browsers.getOrPut(engine) {
            val type: BrowserType = when (engine) {
                "webkit" -> playwright.webkit()
                "firefox" -> playwright.firefox()
                "chromium" -> playwright.chromium()
                else -> error("Unknown engine '$engine'")
            }
            type.launch(BrowserType.LaunchOptions().setHeadless(true))
        }

    init {
        Runtime.getRuntime().addShutdownHook(Thread {
            browsers.values.forEach { runCatching { it.close() } }
            runCatching { playwright.close() }
        })
    }
}
