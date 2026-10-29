package gradlenew.ui

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress

class SiteServer(private val siteDir: File, private val overrides: Map<String, (HttpExchange) -> Unit> = emptyMap()) : AutoCloseable {

    val requests: MutableList<String> = java.util.Collections.synchronizedList(mutableListOf())

    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/") { exchange ->
            exchange.use {
                val path = exchange.requestURI.path
                requests.add(exchange.requestURI.toString())
                val override = overrides.entries.firstOrNull { path == it.key || path.startsWith(it.key.removeSuffix("*")) && it.key.endsWith("*") }
                if (override != null) {
                    override.value(exchange)
                } else {
                    serveFile(exchange, path)
                }
            }
        }
        start()
    }

    val url: String get() = "http://127.0.0.1:${server.address.port}"

    private fun serveFile(exchange: HttpExchange, path: String) {
        val relative = if (path.endsWith("/")) path + "index.html" else path
        val file = File(siteDir, relative.removePrefix("/")).canonicalFile
        if (!file.path.startsWith(siteDir.canonicalPath) || !file.isFile) {
            exchange.sendResponseHeaders(404, -1)
            return
        }
        exchange.responseHeaders.add("Content-Type", contentType(file.name))
        exchange.responseHeaders.add("Content-Security-Policy", "default-src 'self'")
        val bytes = file.readBytes()
        exchange.sendResponseHeaders(200, bytes.size.toLong())
        exchange.responseBody.write(bytes)
    }

    override fun close() {
        server.stop(0)
    }

    companion object {
        fun contentType(name: String): String = when (name.substringAfterLast('.')) {
            "html" -> "text/html; charset=utf-8"
            "css" -> "text/css; charset=utf-8"
            "js" -> "text/javascript; charset=utf-8"
            "json" -> "application/json"
            "svg" -> "image/svg+xml"
            "woff2" -> "font/woff2"
            "png" -> "image/png"
            else -> "application/octet-stream"
        }

        fun respond(exchange: HttpExchange, status: Int, contentType: String, body: ByteArray, headers: Map<String, String> = emptyMap()) {
            exchange.responseHeaders.add("Content-Type", contentType)
            headers.forEach { (name, value) -> exchange.responseHeaders.add(name, value) }
            exchange.sendResponseHeaders(status, body.size.toLong())
            exchange.responseBody.write(body)
        }

        val siteDir: File get() = File(System.getProperty("ui.siteDir") ?: error("ui.siteDir is not set"))
    }
}
