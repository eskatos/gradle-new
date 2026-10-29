package gradlenew.ui

import com.microsoft.playwright.Browser
import com.microsoft.playwright.Page

class Script private constructor(private val server: SiteServer, private val page: Page) : AutoCloseable {

    @Suppress("UNCHECKED_CAST")
    fun call(function: String, vararg argumentsJson: String): Any? =
        page.evaluate(
            "async ([fn, args]) => { const module = await import('/app/model.js'); return module[fn](...args.map(a => JSON.parse(a))); }",
            listOf(function, argumentsJson.toList()),
        )

    @Suppress("UNCHECKED_CAST")
    fun map(function: String, vararg argumentsJson: String): Map<String, Any?> = call(function, *argumentsJson) as Map<String, Any?>

    @Suppress("UNCHECKED_CAST")
    fun list(function: String, vararg argumentsJson: String): List<Map<String, Any?>> = call(function, *argumentsJson) as List<Map<String, Any?>>

    override fun close() {
        page.context().close()
        server.close()
    }

    companion object {
        fun open(engine: String): Script {
            val server = SiteServer(SiteServer.siteDir, mapOf("/harness" to { exchange -> SiteServer.respond(exchange, 200, "text/html; charset=utf-8", Fixtures.harness.readBytes()) }))
            val context = Browsers.browser(engine).newContext(Browser.NewContextOptions().setBypassCSP(true))
            val page = context.newPage()
            page.navigate(server.url + "/harness")
            return Script(server, page)
        }

        fun json(vararg pairs: Pair<String, Any?>): String = pairs.joinToString(",", "{", "}") { (key, value) -> "\"$key\":" + literal(value) }

        private fun literal(value: Any?): String = when (value) {
            null -> "null"
            is String -> "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
            is Boolean, is Number -> value.toString()
            else -> error("unsupported $value")
        }
    }
}
