package gradlenew.verification

object DistributionCheck {

    fun of(version: String, expectedSha256: String, actualSha256: String): CheckResult =
        CheckResult.of(
            Check.DISTRIBUTION,
            1,
            if (actualSha256 == expectedSha256) emptyList() else listOf(Failure(zipOf(version), "SHA-256 is $actualSha256, expected $expectedSha256")),
        )

    fun unavailable(version: String, reason: String): CheckResult =
        CheckResult.of(Check.DISTRIBUTION, 1, listOf(Failure(zipOf(version), "the distribution could not be obtained: $reason")))

    private fun zipOf(version: String) = "gradle-$version-bin.zip"
}
