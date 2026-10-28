package gradlenew.domain.model

data class BuildType(
    val id: String,
    val componentType: String,
    val language: String?,
    val dsls: Choice,
    val modularization: Choice,
    val testFrameworks: List<TestFrameworkChoice>,
    val supportsPackage: Boolean,
    val supportsJavaVersion: Boolean,
    val supportsProjectName: Boolean,
    val furtherReading: String?,
) {
    init {
        val covered = testFrameworks.map { it.modularization }
        require(covered.toSet() == modularization.values.toSet() && covered.size == covered.toSet().size) {
            "Build type '$id' needs exactly one test framework choice per modularization option ${modularization.values}, got $covered"
        }
    }

    fun testFrameworksFor(modularizationOption: String): TestFrameworkChoice =
        requireNotNull(testFrameworks.firstOrNull { it.modularization == modularizationOption }) {
            "Build type '$id' has no modularization option '$modularizationOption'"
        }
}
