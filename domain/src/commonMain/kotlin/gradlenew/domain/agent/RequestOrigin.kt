package gradlenew.domain.agent

object RequestOrigin {

    private val label = Regex("[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?")
    private val ipv6 = Regex("\\[[0-9a-f]*:[0-9a-f:.]*\\]")
    private val port = Regex("[0-9]{1,5}")

    fun of(host: String?): String? {
        val value = host?.lowercase() ?: return null
        val name = if (value.startsWith("[")) value.substringBefore("]") + "]" else value.substringBefore(":")
        if (!value.startsWith(name)) return null
        val rest = value.removePrefix(name)
        if (rest.isNotEmpty() && !(rest.startsWith(":") && port.matches(rest.drop(1)) && rest.drop(1).toInt() in 1..65535)) return null
        if (!isHostName(name) && !ipv6.matches(name)) return null
        return (if (isLoopback(name)) "http://" else "https://") + value
    }

    private fun isHostName(name: String): Boolean = name.length in 1..253 && name.split('.').all { label.matches(it) }

    private fun isLoopback(name: String): Boolean = name == "localhost" || name == "[::1]" || isIpv4Loopback(name)

    private fun isIpv4Loopback(name: String): Boolean {
        val octets = name.split('.')
        return octets.size == 4 && octets[0] == "127" && octets.all { it.toIntOrNull() in 0..255 }
    }
}
