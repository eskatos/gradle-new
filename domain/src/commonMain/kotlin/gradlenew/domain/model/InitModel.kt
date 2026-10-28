package gradlenew.domain.model

data class InitModel(
    val gradleVersion: String,
    val componentTypes: List<ComponentType>,
    val languages: List<Language>,
    val dsls: List<Dsl>,
    val modularizationOptions: List<ModularizationOption>,
    val testFrameworks: List<TestFramework>,
    val buildTypes: List<BuildType>,
    val javaVersion: JavaVersionParameter,
    val packageName: TextParameter,
    val projectName: TextParameter,
) {
    init {
        require(gradleVersion.isNotBlank()) { "Gradle version must not be blank" }
        requireUnique("component types", componentTypes.map { it.id })
        requireUnique("languages", languages.map { it.id })
        requireUnique("dsls", dsls.map { it.id })
        requireUnique("modularization options", modularizationOptions.map { it.id })
        requireUnique("test frameworks", testFrameworks.map { it.id })
        requireUnique("build types", buildTypes.map { it.id })
        val componentTypeIds = componentTypes.map { it.id }.toSet()
        val languageIds = languages.map { it.id }.toSet()
        val dslIds = dsls.map { it.id }.toSet()
        val modularizationIds = modularizationOptions.map { it.id }.toSet()
        val testFrameworkIds = testFrameworks.map { it.id }.toSet()
        for (type in buildTypes) {
            requireDefined(type, "component type", listOf(type.componentType), componentTypeIds)
            requireDefined(type, "language", listOfNotNull(type.language), languageIds)
            requireDefined(type, "dsl", type.dsls.values, dslIds)
            requireDefined(type, "modularization option", type.modularization.values, modularizationIds)
            requireDefined(type, "test framework", type.testFrameworks.flatMap { it.values }, testFrameworkIds)
        }
    }

    fun buildType(id: String): BuildType =
        requireNotNull(buildTypes.firstOrNull { it.id == id }) { "Unknown build type '$id'" }

    private fun requireUnique(what: String, ids: List<String>) {
        val duplicates = ids.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        require(duplicates.isEmpty()) { "Duplicate $what: $duplicates" }
    }

    private fun requireDefined(type: BuildType, what: String, references: List<String>, defined: Set<String>) {
        val unknown = references.filterNot { it in defined }
        require(unknown.isEmpty()) { "Build type '${type.id}' references unknown $what $unknown" }
    }
}
