package gradlenew.verification

enum class Check(val id: String) {
    MODEL("model"),
    DETERMINISM("determinism"),
    JAVA_VERSION("java-version"),
    DIFFERENTIAL("differential"),
    HYGIENE("hygiene"),
    BUDGET("budget");

    companion object {
        fun of(id: String): Check = requireNotNull(entries.firstOrNull { it.id == id }) { "Unknown check '$id'" }
    }
}

enum class Outcome { PASSED, FAILED, SKIPPED }

data class Failure(val subject: String, val message: String)

data class CheckResult(val check: Check, val outcome: Outcome, val checked: Int, val failures: List<Failure>) {
    init {
        require((outcome == Outcome.FAILED) == failures.isNotEmpty()) { "Check ${check.id} is $outcome with ${failures.size} failures" }
    }

    companion object {
        fun of(check: Check, checked: Int, failures: List<Failure>) =
            CheckResult(check, if (failures.isEmpty()) Outcome.PASSED else Outcome.FAILED, checked, failures)

        fun skipped(check: Check) = CheckResult(check, Outcome.SKIPPED, 0, emptyList())
    }
}

data class VersionReport(val version: String, val checks: List<CheckResult>, val javaEnumeratedTypes: List<String>) {
    init {
        require(checks.map { it.check }.toSet().size == checks.size) { "Each check is reported once for Gradle $version" }
    }

    val passed: Boolean get() = checks.isNotEmpty() && checks.all { it.outcome == Outcome.PASSED }

    fun check(check: Check): CheckResult? = checks.firstOrNull { it.check == check }
}

data class VerificationReport(val versions: List<VersionReport>) {

    val passed: Boolean get() = versions.all { it.passed }

    val passedVersions: List<String> get() = versions.filter { it.passed }.map { it.version }

    val failedVersions: List<String> get() = versions.filterNot { it.passed }.map { it.version }

    fun version(version: String): VersionReport = requireNotNull(versions.firstOrNull { it.version == version }) { "No report for Gradle $version" }

    fun summary(): String = buildString {
        for (version in versions) {
            appendLine("Gradle ${version.version}: ${if (version.passed) "passed" else "FAILED"}")
            for (result in version.checks) {
                when (result.outcome) {
                    Outcome.PASSED -> appendLine("  ${result.check.id}: passed (${result.checked} checked)")
                    Outcome.SKIPPED -> appendLine("  ${result.check.id}: skipped")
                    Outcome.FAILED -> {
                        appendLine("  ${result.check.id}: FAILED (${result.checked} checked, ${result.failures.size} failures)")
                        result.failures.take(SHOWN_FAILURES).forEach { appendLine("    ${it.subject}: ${it.message}") }
                        if (result.failures.size > SHOWN_FAILURES) appendLine("    and ${result.failures.size - SHOWN_FAILURES} more")
                    }
                }
            }
            if (version.javaEnumeratedTypes.isNotEmpty()) appendLine("  Java version enumerated for: ${version.javaEnumeratedTypes.joinToString(", ")}")
        }
    }

    private companion object {
        const val SHOWN_FAILURES = 5
    }
}
