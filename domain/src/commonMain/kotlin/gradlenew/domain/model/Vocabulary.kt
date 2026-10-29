package gradlenew.domain.model

data class ComponentType(val id: String, val displayName: String, val defaultProjectNames: List<String>)

data class Language(val id: String, val displayName: String)

data class Dsl(val id: String, val displayName: String, val fileExtension: String)

data class ModularizationOption(val id: String, val displayName: String)

data class TestFramework(val id: String, val displayName: String)

data class Choice(val values: List<String>, val default: String) {
    init {
        require(values.isNotEmpty()) { "A choice needs at least one value" }
        require(default in values) { "Default '$default' is not one of $values" }
    }
}

data class TestFrameworkChoice(val modularization: String, val values: List<String>, val default: String?) {
    init {
        require(default == null || default in values) { "Default '$default' is not one of $values" }
        require(values.isEmpty() == (default == null)) { "A test framework choice has a default exactly when it has values" }
    }
}

data class JavaVersionParameter(val minimum: Int, val default: Int, val maximum: Int) {
    init {
        require(default >= minimum) { "Default Java version $default is below the minimum $minimum" }
        require(maximum >= default) { "Highest known Java version $maximum is below the default $default" }
    }
}

data class TextParameter(val pattern: String?, val default: String?)

data class InitOption(val name: String, val description: String, val negatedDescription: String?) {
    val negatable: Boolean get() = negatedDescription != null
}
