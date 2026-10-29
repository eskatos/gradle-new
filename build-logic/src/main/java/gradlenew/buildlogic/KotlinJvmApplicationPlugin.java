package gradlenew.buildlogic;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.Directory;
import org.gradle.api.plugins.JavaApplication;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;

public abstract class KotlinJvmApplicationPlugin implements Plugin<Project> {

    static final String TEMPLATE_BUDGET_PROPERTY = "gradlenew.templates.budget-mb";
    private static final int DEFAULT_TEMPLATE_BUDGET_MEGABYTES = 64;

    @Override
    public void apply(Project project) {
        JvmConventions.applyKotlinJvm(project, Catalog.of(project));
        project.getPluginManager().apply("application");
        JavaApplication application = project.getExtensions().getByType(JavaApplication.class);
        application.getMainClass().set(mainClassFor(project.getName()));

        OfferedVersions offered = OfferedVersions.of(project.getProviders());
        Directory rootDir = project.getLayout().getSettingsDirectory();
        PublishedSiteExtension site = project.getExtensions().create("publishedSite", PublishedSiteExtension.class);
        site.getStable().convention(offered.stable());
        site.getRc().convention(offered.rc());
        site.getBuilderSources().convention(rootDir.dir("builder"));
        site.getVersionCatalog().convention(rootDir.file("gradle/libs.versions.toml"));
        site.getTemplateBudgetMegabytes().convention(project.getProviders().gradleProperty(TEMPLATE_BUDGET_PROPERTY).map(Integer::valueOf).orElse(DEFAULT_TEMPLATE_BUDGET_MEGABYTES));

        SourceSet main = project.getExtensions().getByType(JavaPluginExtension.class).getSourceSets().getByName("main");
        TaskProvider<PublishSiteTask> publishSite = project.getTasks().register("publishSite", PublishSiteTask.class, task -> {
            task.setGroup("build");
            task.setDescription("Runs the application to produce the site tree for the offered Gradle versions.");
            task.getMainClass().set(application.getMainClass());
            task.setClasspath(main.getRuntimeClasspath());
            task.getStable().set(site.getStable());
            task.getRc().set(site.getRc());
            task.getBuilderSources().set(site.getBuilderSources());
            task.getBuilderSourceFiles().setFrom(site.getBuilderSources().map(dir ->
                dir.getAsFileTree().matching(pattern -> pattern.exclude(BuilderSources.EXCLUDED))));
            task.getVersionCatalog().set(site.getVersionCatalog());
            task.getOutputDirectory().set(project.getLayout().getBuildDirectory().dir("site"));
            task.getTemplatesOutputDirectory().set(project.getLayout().getBuildDirectory().dir("templates"));
            task.getWorkDirectory().set(project.getLayout().getBuildDirectory().dir("publisher-work"));
            task.getReportDirectory().set(project.getLayout().getBuildDirectory().dir("verification"));
            task.getTemplateBudgetMegabytes().set(site.getTemplateBudgetMegabytes());
        });

        project.getConfigurations().consumable("siteElements", configuration -> {
            ContainerImageAttributes.site(configuration.getAttributes());
            configuration.getOutgoing().artifact(publishSite.flatMap(PublishSiteTask::getOutputDirectory));
        });
        project.getConfigurations().consumable("templatesElements", configuration -> {
            ContainerImageAttributes.templates(configuration.getAttributes());
            configuration.getOutgoing().artifact(publishSite.flatMap(PublishSiteTask::getTemplatesOutputDirectory));
        });
    }

    static String mainClassFor(String projectName) {
        return "gradlenew." + projectName.replace("-", "") + ".MainKt";
    }
}
