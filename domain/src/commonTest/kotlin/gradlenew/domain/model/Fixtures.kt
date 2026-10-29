package gradlenew.domain.model

val application = ComponentType("application", "Application", listOf("app"))
val basic = ComponentType("basic", "Basic (build structure only)", emptyList())
val java = Language("java", "Java")
val kotlinDsl = Dsl("kotlin", "Kotlin", ".gradle.kts")
val groovyDsl = Dsl("groovy", "Groovy", ".gradle")
val singleProject = ModularizationOption("single-project", "Single application project")
val withLibraries = ModularizationOption("with-library-projects", "Application and library project")
val junitJupiter = TestFramework("junit-jupiter", "JUnit Jupiter")
val spock = TestFramework("spock", "Spock")

fun javaApplication(
    dsls: Choice = Choice(listOf("kotlin", "groovy"), "kotlin"),
    modularization: Choice = Choice(listOf("single-project", "with-library-projects"), "single-project"),
    testFrameworks: List<TestFrameworkChoice> = listOf(
        TestFrameworkChoice("single-project", listOf("junit-jupiter", "spock"), "junit-jupiter"),
        TestFrameworkChoice("with-library-projects", listOf("junit-jupiter"), "junit-jupiter"),
    ),
) = BuildType(
    id = "java-application",
    componentType = "application",
    language = "java",
    dsls = dsls,
    modularization = modularization,
    testFrameworks = testFrameworks,
    supportsPackage = true,
    supportsJavaVersion = true,
    supportsProjectName = true,
    furtherReading = "https://docs.gradle.org/samples",
)

val basicBuild = BuildType(
    id = "basic",
    componentType = "basic",
    language = null,
    dsls = Choice(listOf("kotlin", "groovy"), "kotlin"),
    modularization = Choice(listOf("single-project"), "single-project"),
    testFrameworks = listOf(TestFrameworkChoice("single-project", emptyList(), null)),
    supportsPackage = false,
    supportsJavaVersion = false,
    supportsProjectName = true,
    furtherReading = null,
)

fun model(
    buildTypes: List<BuildType> = listOf(javaApplication(), basicBuild),
    componentTypes: List<ComponentType> = listOf(application, basic),
    languages: List<Language> = listOf(java),
    dsls: List<Dsl> = listOf(kotlinDsl, groovyDsl),
    modularizationOptions: List<ModularizationOption> = listOf(singleProject, withLibraries),
    testFrameworks: List<TestFramework> = listOf(junitJupiter, spock),
    javaVersion: JavaVersionParameter = JavaVersionParameter(minimum = 7, default = 21, maximum = 31),
    options: List<InitOption> = listOf(typeOption, commentsOption),
) = InitModel(
    gradleVersion = "9.8.2",
    componentTypes = componentTypes,
    languages = languages,
    dsls = dsls,
    modularizationOptions = modularizationOptions,
    testFrameworks = testFrameworks,
    buildTypes = buildTypes,
    javaVersion = javaVersion,
    packageName = TextParameter(pattern = "^[a-z]+(\\.[a-z]+)*$", default = "org.example"),
    projectName = TextParameter(pattern = null, default = null),
    options = options,
)

val typeOption = InitOption("type", "Set the type of project to generate.", negatedDescription = null)
val commentsOption = InitOption("comments", "Include clarifying comments in files.", negatedDescription = "Disables option --comments.")
