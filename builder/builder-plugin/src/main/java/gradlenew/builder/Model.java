package gradlenew.builder;

import gradlenew.builder.api.BuildType;
import gradlenew.builder.api.Choice;
import gradlenew.builder.api.ComponentType;
import gradlenew.builder.api.Dsl;
import gradlenew.builder.api.InitModel;
import gradlenew.builder.api.JavaVersionParameter;
import gradlenew.builder.api.NamedElement;
import gradlenew.builder.api.TestFrameworkChoice;
import gradlenew.builder.api.TextParameter;
import java.io.Serializable;
import java.util.List;

final class Model {

    private Model() {
    }

    static final class Named implements NamedElement, Serializable {
        private final String id;
        private final String displayName;

        Named(String id, String displayName) {
            this.id = id;
            this.displayName = displayName;
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public String getDisplayName() {
            return displayName;
        }
    }

    static final class Component implements ComponentType, Serializable {
        private final String id;
        private final String displayName;
        private final List<String> defaultProjectNames;

        Component(String id, String displayName, List<String> defaultProjectNames) {
            this.id = id;
            this.displayName = displayName;
            this.defaultProjectNames = List.copyOf(defaultProjectNames);
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public String getDisplayName() {
            return displayName;
        }

        @Override
        public List<String> getDefaultProjectNames() {
            return defaultProjectNames;
        }
    }

    static final class ScriptDsl implements Dsl, Serializable {
        private final String id;
        private final String displayName;
        private final String fileExtension;

        ScriptDsl(String id, String displayName, String fileExtension) {
            this.id = id;
            this.displayName = displayName;
            this.fileExtension = fileExtension;
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public String getDisplayName() {
            return displayName;
        }

        @Override
        public String getFileExtension() {
            return fileExtension;
        }
    }

    static final class Options implements Choice, Serializable {
        private final List<String> values;
        private final String defaultValue;

        Options(List<String> values, String defaultValue) {
            this.values = List.copyOf(values);
            this.defaultValue = defaultValue;
        }

        @Override
        public List<String> getValues() {
            return values;
        }

        @Override
        public String getDefault() {
            return defaultValue;
        }
    }

    static final class TestFrameworks implements TestFrameworkChoice, Serializable {
        private final String modularization;
        private final List<String> values;
        private final String defaultValue;

        TestFrameworks(String modularization, List<String> values, String defaultValue) {
            this.modularization = modularization;
            this.values = List.copyOf(values);
            this.defaultValue = defaultValue;
        }

        @Override
        public String getModularization() {
            return modularization;
        }

        @Override
        public List<String> getValues() {
            return values;
        }

        @Override
        public String getDefault() {
            return defaultValue;
        }
    }

    static final class Type implements BuildType, Serializable {
        private final String id;
        private final String componentType;
        private final String language;
        private final Options dsls;
        private final Options modularization;
        private final List<TestFrameworks> testFrameworks;
        private final boolean packageSupported;
        private final boolean javaVersionSupported;
        private final boolean projectNameSupported;
        private final String furtherReading;

        Type(String id, String componentType, String language, Options dsls, Options modularization,
             List<TestFrameworks> testFrameworks, boolean packageSupported, boolean javaVersionSupported,
             boolean projectNameSupported, String furtherReading) {
            this.id = id;
            this.componentType = componentType;
            this.language = language;
            this.dsls = dsls;
            this.modularization = modularization;
            this.testFrameworks = List.copyOf(testFrameworks);
            this.packageSupported = packageSupported;
            this.javaVersionSupported = javaVersionSupported;
            this.projectNameSupported = projectNameSupported;
            this.furtherReading = furtherReading;
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public String getComponentType() {
            return componentType;
        }

        @Override
        public String getLanguage() {
            return language;
        }

        @Override
        public Choice getDsls() {
            return dsls;
        }

        @Override
        public Choice getModularization() {
            return modularization;
        }

        @Override
        public List<TestFrameworks> getTestFrameworks() {
            return testFrameworks;
        }

        @Override
        public boolean isPackageSupported() {
            return packageSupported;
        }

        @Override
        public boolean isJavaVersionSupported() {
            return javaVersionSupported;
        }

        @Override
        public boolean isProjectNameSupported() {
            return projectNameSupported;
        }

        @Override
        public String getFurtherReading() {
            return furtherReading;
        }
    }

    static final class JavaVersion implements JavaVersionParameter, Serializable {
        private final int minimum;
        private final int defaultVersion;

        JavaVersion(int minimum, int defaultVersion) {
            this.minimum = minimum;
            this.defaultVersion = defaultVersion;
        }

        @Override
        public int getMinimum() {
            return minimum;
        }

        @Override
        public int getDefault() {
            return defaultVersion;
        }
    }

    static final class Text implements TextParameter, Serializable {
        private final String pattern;
        private final String defaultValue;

        Text(String pattern, String defaultValue) {
            this.pattern = pattern;
            this.defaultValue = defaultValue;
        }

        @Override
        public String getPattern() {
            return pattern;
        }

        @Override
        public String getDefault() {
            return defaultValue;
        }
    }

    static final class Init implements InitModel, Serializable {
        private final String gradleVersion;
        private final List<Component> componentTypes;
        private final List<Named> languages;
        private final List<ScriptDsl> dsls;
        private final List<Named> modularizationOptions;
        private final List<Named> testFrameworks;
        private final List<Type> buildTypes;
        private final JavaVersion javaVersion;
        private final Text packageName;
        private final Text projectName;

        Init(String gradleVersion, List<Component> componentTypes, List<Named> languages, List<ScriptDsl> dsls,
             List<Named> modularizationOptions, List<Named> testFrameworks, List<Type> buildTypes,
             JavaVersion javaVersion, Text packageName, Text projectName) {
            this.gradleVersion = gradleVersion;
            this.componentTypes = List.copyOf(componentTypes);
            this.languages = List.copyOf(languages);
            this.dsls = List.copyOf(dsls);
            this.modularizationOptions = List.copyOf(modularizationOptions);
            this.testFrameworks = List.copyOf(testFrameworks);
            this.buildTypes = List.copyOf(buildTypes);
            this.javaVersion = javaVersion;
            this.packageName = packageName;
            this.projectName = projectName;
        }

        @Override
        public String getGradleVersion() {
            return gradleVersion;
        }

        @Override
        public List<Component> getComponentTypes() {
            return componentTypes;
        }

        @Override
        public List<Named> getLanguages() {
            return languages;
        }

        @Override
        public List<ScriptDsl> getDsls() {
            return dsls;
        }

        @Override
        public List<Named> getModularizationOptions() {
            return modularizationOptions;
        }

        @Override
        public List<Named> getTestFrameworks() {
            return testFrameworks;
        }

        @Override
        public List<Type> getBuildTypes() {
            return buildTypes;
        }

        @Override
        public JavaVersion getJavaVersion() {
            return javaVersion;
        }

        @Override
        public Text getPackageName() {
            return packageName;
        }

        @Override
        public Text getProjectName() {
            return projectName;
        }
    }
}
