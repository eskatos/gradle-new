package gradlenew.builder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.options.Option;
import org.gradle.buildinit.tasks.InitBuild;
import org.junit.jupiter.api.Test;

class InitOptionsTest {

    abstract static class FakeTask {
        @Option(option = "type", description = "Set the type of project to generate.")
        public void setType(String type) {
        }

        @Option(option = "split-project", description = "Split functionality across multiple subprojects?")
        public void setSplitProject(boolean split) {
        }

        @Option(option = "use-defaults", description = "Use default values for options not configured explicitly")
        public abstract Property<Boolean> getUseDefaults();

        @Option(option = "into", description = "Set the directory where the project is generated.")
        public abstract Property<String> getInto();

        public void notAnOption(boolean value) {
        }
    }

    @Test
    void readsNamesDescriptionsAndNegationsSortedByName() {
        List<Model.Option> options = InitOptions.read(FakeTask.class);

        assertEquals(List.of("into", "split-project", "type", "use-defaults"), options.stream().map(Model.Option::getName).toList());
        assertEquals("Set the type of project to generate.", options.get(2).getDescription());
        assertNull(options.get(0).getNegatedDescription());
        assertNull(options.get(2).getNegatedDescription());
        assertEquals("Disables option --split-project.", options.get(1).getNegatedDescription());
        assertEquals("Disables option --use-defaults.", options.get(3).getNegatedDescription());
    }

    @Test
    void readsTheOptionsOfGradlesInitTask() {
        List<Model.Option> options = InitOptions.read(InitBuild.class);

        List<String> names = options.stream().map(Model.Option::getName).toList();
        assertTrue(names.containsAll(List.of("type", "dsl", "test-framework", "project-name", "package", "comments")), names.toString());
        assertTrue(options.stream().allMatch(option -> !option.getDescription().isBlank()));
        assertFalse(options.stream().filter(option -> option.getName().equals("type")).findFirst().orElseThrow().getNegatedDescription() != null);
        assertEquals("Disables option --comments.", options.stream().filter(option -> option.getName().equals("comments")).findFirst().orElseThrow().getNegatedDescription());
    }
}
