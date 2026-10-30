package gradlenew.versions

val testJava: (String) -> Int = { version -> if (GradleVersion.parse(version).major < 9) 21 else 25 }

fun pin(
    stable: String,
    rc: String? = null,
    previous: String = "8.14.6",
    stableChecksums: Checksums? = null,
    rcChecksums: Checksums? = null,
    previousChecksums: Checksums? = null,
): Pin = Pin(
    PinnedVersion(stable, testJava(stable), stableChecksums),
    PinnedVersion(previous, testJava(previous), previousChecksums),
    rc?.let { PinnedVersion(it, testJava(it), rcChecksums) },
)
