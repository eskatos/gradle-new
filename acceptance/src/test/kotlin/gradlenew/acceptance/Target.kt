package gradlenew.acceptance

import java.net.Socket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait

class Target private constructor(val baseUrl: String, private val container: GenericContainer<*>?) : AutoCloseable {

    private val client = HttpClient.newHttpClient()

    fun get(path: String): HttpResponse<String> =
        client.send(HttpRequest.newBuilder(URI.create(baseUrl + path)).GET().build(), HttpResponse.BodyHandlers.ofString())

    fun getBytes(path: String): HttpResponse<ByteArray> =
        client.send(HttpRequest.newBuilder(URI.create(baseUrl + path)).GET().build(), HttpResponse.BodyHandlers.ofByteArray())

    fun hasShell(): Boolean? = container?.let {
        runCatching { it.execInContainer("sh", "-c", "true").exitCode == 0 }.getOrDefault(false)
    }

    fun statusWithHost(path: String, host: String): Int? = container?.let {
        Socket(it.host, it.getMappedPort(8080)).use { socket ->
            socket.getOutputStream().write("GET $path HTTP/1.1\r\nHost: $host\r\nConnection: close\r\n\r\n".toByteArray())
            socket.getInputStream().bufferedReader().readLine().split(' ')[1].toInt()
        }
    }

    fun inMaintenance(): Target? = container?.let { open(it.dockerImageName, mapOf("MAINTENANCE" to "on")) }

    override fun close() {
        container?.stop()
    }

    companion object {
        fun open(): Target {
            val target = System.getProperty("acceptance.target") ?: error("acceptance.target is not set")
            if (target != "image") return Target(target.trimEnd('/'), null)
            val imageRef = System.getProperty("acceptance.imageRef") ?: error("acceptance.imageRef is not set: no image was provided")
            return open(imageRef, emptyMap())
        }

        private fun open(imageRef: String, environment: Map<String, String>): Target {
            val container = GenericContainer(imageRef)
                .withExposedPorts(8080)
                .withEnv(environment)
                .waitingFor(Wait.forHttp("/api/versions").forPort(8080).forStatusCodeMatching { it == 200 || it == 503 })
                .withStartupAttempts(3)
            container.start()
            return Target("http://${container.host}:${container.getMappedPort(8080)}", container)
        }
    }
}
