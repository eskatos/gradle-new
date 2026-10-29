package gradlenew.buildlogic;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.JavaApplication;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;

public abstract class KotlinJvmApplicationPlugin implements Plugin<Project> {

    static final String TEMPLATE_BUDGET_PROPERTY = "gradlenew.templates.budget-mb";
    static final String FEEDS_URL_PROPERTY = "gradlenew.feeds-url";
    private static final int DEFAULT_TEMPLATE_BUDGET_MEGABYTES = 64;
    private static final String PUBLISHING_GROUP = "publishing";

    @Override
    public void apply(Project project) {
        JvmConventions.applyKotlinJvm(project, Catalog.of(project));
        project.getPluginManager().apply("application");
        JavaApplication application = project.getExtensions().getByType(JavaApplication.class);
        application.getMainClass().set(mainClassFor(project.getName()));

        OfferedVersions offered = OfferedVersions.of(project);
        Directory rootDir = project.getLayout().getSettingsDirectory();
        PublishedSiteExtension site = project.getExtensions().create("publishedSite", PublishedSiteExtension.class);
        site.getBuilderSources().convention(rootDir.dir("builder"));
        site.getVersionCatalog().convention(rootDir.file("gradle/libs.versions.toml"));
        site.getTemplateBudgetMegabytes().convention(project.getProviders().gradleProperty(TEMPLATE_BUDGET_PROPERTY).map(Integer::valueOf).orElse(DEFAULT_TEMPLATE_BUDGET_MEGABYTES));

        FileCollection runtimeClasspath = project.getExtensions().getByType(JavaPluginExtension.class)
            .getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME).getRuntimeClasspath();
        DirectoryProperty buildDir = project.getLayout().getBuildDirectory();

        File distributionCache = new File(project.getGradle().getGradleUserHomeDir(), "caches/gradlenew/distributions");
        List<TaskProvider<PublishVersionTask>> publishVersions = new ArrayList<>();
        for (String version : offered.versions()) {
            publishVersions.add(project.getTasks().register("publishGradle-" + version, PublishVersionTask.class, task -> {
                task.setGroup(PUBLISHING_GROUP);
                task.setDescription("Verifies Gradle " + version + " and publishes its model and templates.");
                task.getMainClass().set(application.getMainClass());
                task.setClasspath(runtimeClasspath);
                task.getGradleVersion().set(version);
                task.getBuilderSources().set(site.getBuilderSources());
                task.getBuilderSourceFiles().setFrom(site.getBuilderSources().map(dir ->
                    dir.getAsFileTree().matching(pattern -> pattern.exclude(BuilderSources.EXCLUDED))));
                task.getVersionCatalog().set(site.getVersionCatalog());
                task.getTemplateBudgetMegabytes().set(site.getTemplateBudgetMegabytes());
                task.getSiteDirectory().set(buildDir.dir("published/" + version + "/site"));
                task.getTemplatesDirectory().set(buildDir.dir("published/" + version + "/templates"));
                task.getReportDirectory().set(buildDir.dir("published/" + version + "/report"));
                task.getWorkDirectory().set(buildDir.dir("publisher-work/" + version));
                task.getDistributionSha256().set(offered.distributionSha256(version));
                task.getWrapperSha256().set(offered.wrapperSha256(version));
                task.getDistributionCache().set(distributionCache);
            }));
        }
        TaskProvider<PublishVersionsIndexTask> publishIndex = project.getTasks().register("publishVersionsIndex", PublishVersionsIndexTask.class, task -> {
            task.setGroup(PUBLISHING_GROUP);
            task.setDescription("Writes versions.json for the offered Gradle versions.");
            task.getMainClass().set(application.getMainClass());
            task.setClasspath(runtimeClasspath);
            task.getStable().set(offered.stable());
            task.getRc().set(offered.rc());
            task.getIndexDirectory().set(buildDir.dir("published/index"));
        });
        project.getTasks().register("publishSite", task -> {
            task.setGroup(PUBLISHING_GROUP);
            task.setDescription("Publishes every offered Gradle version and the versions index.");
            task.dependsOn(publishIndex);
            task.dependsOn(publishVersions.toArray());
        });

        project.getConfigurations().consumable("siteElements", configuration -> {
            ContainerImageAttributes.site(configuration.getAttributes());
            configuration.getOutgoing().artifact(publishIndex.flatMap(PublishVersionsIndexTask::getIndexDirectory));
            for (TaskProvider<PublishVersionTask> publish : publishVersions) {
                configuration.getOutgoing().artifact(publish.flatMap(PublishVersionTask::getSiteDirectory));
            }
        });
        project.getConfigurations().consumable("templatesElements", configuration -> {
            ContainerImageAttributes.templates(configuration.getAttributes());
            for (TaskProvider<PublishVersionTask> publish : publishVersions) {
                configuration.getOutgoing().artifact(publish.flatMap(PublishVersionTask::getTemplatesDirectory));
            }
        });

        Provider<String> feedsUrl = project.getProviders().gradleProperty(FEEDS_URL_PROPERTY);
        String pinFile = OfferedVersions.pinFile(project).getAsFile().getAbsolutePath();
        project.getTasks().register("proposeVersions", JavaExec.class, task -> {
            task.setGroup(PUBLISHING_GROUP);
            task.setDescription("Proposes changes to " + OfferedVersions.FILE + " from the Gradle versions feeds and writes them.");
            task.getMainClass().set(application.getMainClass());
            task.setClasspath(runtimeClasspath);
            task.doNotTrackState("Reads the Gradle versions feeds over the network and writes the pin file");
            ProposeArgumentProvider arguments = project.getObjects().newInstance(ProposeArgumentProvider.class);
            arguments.getPinFile().set(pinFile);
            arguments.getFeedsUrl().set(feedsUrl);
            task.getArgumentProviders().add(arguments);
        });
    }

    static String mainClassFor(String projectName) {
        return "gradlenew." + projectName.replace("-", "") + ".MainKt";
    }
}
