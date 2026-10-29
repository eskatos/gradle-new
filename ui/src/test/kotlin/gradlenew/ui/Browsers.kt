package gradlenew.ui

import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Playwright

const val FIRST_LOAD_TIMEOUT = 30_000.0

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
            val options = BrowserType.LaunchOptions().setHeadless(true)
            if (engine == "firefox") options.setFirefoxUserPrefs(mapOf("accessibility.tabfocus" to 7))
            type.launch(options)
        }

    fun focusNextKey(engine: String): String =
        if (engine == "webkit" && System.getProperty("os.name").startsWith("Mac")) "Alt+Tab" else "Tab"

    init {
        Runtime.getRuntime().addShutdownHook(Thread {
            browsers.values.forEach { runCatching { it.close() } }
            runCatching { playwright.close() }
        })
    }
}
