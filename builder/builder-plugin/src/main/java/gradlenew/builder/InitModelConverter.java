package gradlenew.builder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.gradle.buildinit.plugins.internal.BuildGenerator;
import org.gradle.buildinit.plugins.internal.modifiers.BuildInitDsl;
import org.gradle.buildinit.plugins.internal.modifiers.BuildInitTestFramework;
import org.gradle.buildinit.plugins.internal.modifiers.ComponentType;
import org.gradle.buildinit.plugins.internal.modifiers.Language;
import org.gradle.buildinit.plugins.internal.modifiers.ModularizationOption;

final class InitModelConverter {

    static final String PACKAGE_PATTERN = "^[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*$";

    private final String gradleVersion;
    private final int minimumJavaVersion;
    private final int defaultJavaVersion;
    private final String defaultPackageName;

    InitModelConverter(String gradleVersion, int minimumJavaVersion, int defaultJavaVersion, String defaultPackageName) {
        this.gradleVersion = gradleVersion;
        this.minimumJavaVersion = minimumJavaVersion;
        this.defaultJavaVersion = defaultJavaVersion;
        this.defaultPackageName = defaultPackageName;
    }

    Model.Init convert(List<? extends BuildGenerator> generators) {
        List<Model.Type> types = new ArrayList<>();
        for (BuildGenerator generator : generators) {
            types.add(convert(generator));
        }
        types.sort(Comparator.comparing(Model.Type::getId));
        return new Model.Init(
            gradleVersion,
            Arrays.stream(ComponentType.values())
                .map(type -> new Model.Component(idOf(type), type.getDisplayName(), type.getDefaultProjectNames()))
                .toList(),
            Arrays.stream(Language.values())
                .map(language -> new Model.Named(language.getName(), language.toString()))
                .toList(),
            Arrays.stream(BuildInitDsl.values())
                .map(dsl -> new Model.ScriptDsl(dsl.getId(), capitalize(dsl.getId()), dsl.fileNameFor("")))
                .toList(),
            Arrays.stream(ModularizationOption.values())
                .map(option -> new Model.Named(idOf(option), option.getDisplayName()))
                .toList(),
            Arrays.stream(BuildInitTestFramework.values())
                .filter(framework -> framework != BuildInitTestFramework.NONE)
                .map(framework -> new Model.Named(framework.getId(), framework.toString()))
                .toList(),
            types,
            new Model.JavaVersion(minimumJavaVersion, defaultJavaVersion),
            new Model.Text(PACKAGE_PATTERN, defaultPackageName),
            new Model.Text(null, null));
    }

    private Model.Type convert(BuildGenerator generator) {
        List<ModularizationOption> modularizationOptions = Arrays.stream(ModularizationOption.values())
            .filter(generator.getModularizationOptions()::contains)
            .toList();
        List<Model.TestFrameworks> testFrameworks = new ArrayList<>();
        for (ModularizationOption option : modularizationOptions) {
            List<String> frameworks = Arrays.stream(BuildInitTestFramework.values())
                .filter(framework -> framework != BuildInitTestFramework.NONE)
                .filter(generator.getTestFrameworks(option)::contains)
                .map(BuildInitTestFramework::getId)
                .toList();
            BuildInitTestFramework defaultFramework = generator.getDefaultTestFramework(option);
            String defaultId = defaultFramework == null || defaultFramework == BuildInitTestFramework.NONE ? null : defaultFramework.getId();
            testFrameworks.add(new Model.TestFrameworks(idOf(option), frameworks, defaultId));
        }
        return new Model.Type(
            generator.getId(),
            idOf(generator.getComponentType()),
            Arrays.stream(Language.values()).filter(generator::productionCodeUses).map(Language::getName).findFirst().orElse(null),
            new Model.Options(
                Arrays.stream(BuildInitDsl.values()).filter(generator.getDsls()::contains).map(BuildInitDsl::getId).toList(),
                generator.getDefaultDsl().getId()),
            new Model.Options(
                modularizationOptions.stream().map(InitModelConverter::idOf).toList(),
                idOf(modularizationOptions.contains(ModularizationOption.SINGLE_PROJECT) ? ModularizationOption.SINGLE_PROJECT : modularizationOptions.get(0))),
            testFrameworks,
            generator.supportsPackage(),
            generator.supportsJavaTargets(),
            generator.supportsProjectName(),
            null);
    }

    static String idOf(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private static String capitalize(String value) {
        return value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1);
    }
}
