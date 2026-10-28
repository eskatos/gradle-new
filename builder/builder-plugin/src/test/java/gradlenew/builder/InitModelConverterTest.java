package gradlenew.builder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.gradle.buildinit.plugins.internal.BuildGenerator;
import org.gradle.buildinit.plugins.internal.InitSettings;
import org.gradle.buildinit.plugins.internal.modifiers.BuildInitDsl;
import org.gradle.buildinit.plugins.internal.modifiers.BuildInitTestFramework;
import org.gradle.buildinit.plugins.internal.modifiers.ComponentType;
import org.gradle.buildinit.plugins.internal.modifiers.Language;
import org.gradle.buildinit.plugins.internal.modifiers.ModularizationOption;
import org.junit.jupiter.api.Test;

class InitModelConverterTest {

    private final InitModelConverter converter = new InitModelConverter("9.8.2", 7, 21, "org.example");

    @Test
    void convertsVocabulariesFromTheEnums() {
        Model.Init model = converter.convert(List.of());

        assertEquals("9.8.2", model.getGradleVersion());
        assertEquals(List.of("application", "library", "gradle-plugin", "basic"), model.getComponentTypes().stream().map(Model.Component::getId).toList());
        assertEquals("Gradle plugin", model.getComponentTypes().get(2).getDisplayName());
        assertEquals(List.of("app", "list", "utilities"), model.getComponentTypes().get(0).getDefaultProjectNames());
        assertEquals(List.of("java", "kotlin", "groovy", "scala", "cpp", "swift"), model.getLanguages().stream().map(Model.Named::getId).toList());
        assertEquals("C++", model.getLanguages().get(4).getDisplayName());
        assertEquals(List.of("kotlin", "groovy"), model.getDsls().stream().map(Model.ScriptDsl::getId).toList());
        assertEquals(".gradle.kts", model.getDsls().get(0).getFileExtension());
        assertEquals(List.of("single-project", "with-library-projects"), model.getModularizationOptions().stream().map(Model.Named::getId).toList());
        assertFalse(model.getTestFrameworks().stream().map(Model.Named::getId).toList().contains("none"));
        assertTrue(model.getTestFrameworks().stream().map(Model.Named::getId).toList().containsAll(List.of("junit-jupiter", "kotlintest", "cpptest")));
        assertEquals(7, model.getJavaVersion().getMinimum());
        assertEquals(21, model.getJavaVersion().getDefault());
        assertEquals("org.example", model.getPackageName().getDefault());
        assertNull(model.getProjectName().getPattern());
    }

    @Test
    void convertsAJvmApplicationGenerator() {
        Model.Init model = converter.convert(List.of(javaApplication()));

        Model.Type type = model.getBuildTypes().get(0);
        assertEquals("java-application", type.getId());
        assertEquals("application", type.getComponentType());
        assertEquals("java", type.getLanguage());
        assertEquals(List.of("kotlin", "groovy"), type.getDsls().getValues());
        assertEquals("kotlin", type.getDsls().getDefault());
        assertEquals(List.of("single-project", "with-library-projects"), type.getModularization().getValues());
        assertEquals("single-project", type.getModularization().getDefault());
        assertEquals(2, type.getTestFrameworks().size());
        assertEquals(List.of("junit", "testng", "spock", "junit-jupiter"), type.getTestFrameworks().get(0).getValues());
        assertEquals("junit-jupiter", type.getTestFrameworks().get(0).getDefault());
        assertEquals("with-library-projects", type.getTestFrameworks().get(1).getModularization());
        assertEquals(List.of("junit-jupiter"), type.getTestFrameworks().get(1).getValues());
        assertTrue(type.isPackageSupported());
        assertTrue(type.isJavaVersionSupported());
        assertTrue(type.isProjectNameSupported());
    }

    @Test
    void convertsTheBasicGeneratorWithoutLanguageOrTestFramework() {
        Model.Init model = converter.convert(List.of(javaApplication(), basic()));

        assertEquals(List.of("basic", "java-application"), model.getBuildTypes().stream().map(Model.Type::getId).toList());
        Model.Type type = model.getBuildTypes().get(0);
        assertNull(type.getLanguage());
        assertEquals("basic", type.getComponentType());
        assertEquals(List.of(), type.getTestFrameworks().get(0).getValues());
        assertNull(type.getTestFrameworks().get(0).getDefault());
        assertFalse(type.isPackageSupported());
        assertFalse(type.isJavaVersionSupported());
    }

    private static BuildGenerator javaApplication() {
        return new FakeGenerator("java-application", ComponentType.APPLICATION, Language.JAVA,
            EnumSet.of(ModularizationOption.SINGLE_PROJECT, ModularizationOption.WITH_LIBRARY_PROJECTS),
            option -> option == ModularizationOption.SINGLE_PROJECT
                ? EnumSet.of(BuildInitTestFramework.JUNIT, BuildInitTestFramework.JUNIT_JUPITER, BuildInitTestFramework.SPOCK, BuildInitTestFramework.TESTNG)
                : EnumSet.of(BuildInitTestFramework.JUNIT_JUPITER),
            BuildInitTestFramework.JUNIT_JUPITER, true, true);
    }

    private static BuildGenerator basic() {
        return new FakeGenerator("basic", ComponentType.BASIC, null,
            EnumSet.of(ModularizationOption.SINGLE_PROJECT),
            option -> EnumSet.of(BuildInitTestFramework.NONE),
            BuildInitTestFramework.NONE, false, false);
    }

    private record FakeGenerator(String id, ComponentType componentType, Language language,
                                 Set<ModularizationOption> modularizationOptions,
                                 java.util.function.Function<ModularizationOption, Set<BuildInitTestFramework>> frameworks,
                                 BuildInitTestFramework defaultFramework, boolean supportsPackage, boolean supportsJava)
        implements BuildGenerator {

        @Override
        public String getId() {
            return id;
        }

        @Override
        public ComponentType getComponentType() {
            return componentType;
        }

        @Override
        public boolean productionCodeUses(Language candidate) {
            return candidate == language;
        }

        @Override
        public boolean supportsJavaTargets() {
            return supportsJava;
        }

        @Override
        public Set<ModularizationOption> getModularizationOptions() {
            return modularizationOptions;
        }

        @Override
        public Set<BuildInitDsl> getDsls() {
            return EnumSet.of(BuildInitDsl.KOTLIN, BuildInitDsl.GROOVY);
        }

        @Override
        public BuildInitDsl getDefaultDsl() {
            return BuildInitDsl.KOTLIN;
        }

        @Override
        public boolean supportsProjectName() {
            return true;
        }

        @Override
        public boolean supportsPackage() {
            return supportsPackage;
        }

        @Override
        public Set<BuildInitTestFramework> getTestFrameworks(ModularizationOption option) {
            return frameworks.apply(option);
        }

        @Override
        public BuildInitTestFramework getDefaultTestFramework(ModularizationOption option) {
            return defaultFramework;
        }

        @Override
        public List<String> getDefaultProjectNames() {
            return componentType.getDefaultProjectNames();
        }

        @Override
        public Optional<String> getFurtherReading(InitSettings settings) {
            return Optional.empty();
        }

        @Override
        public void generate(InitSettings settings) {
        }
    }
}
