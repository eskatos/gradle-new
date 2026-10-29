package gradlenew.buildlogic;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;

public abstract class PublishedSiteExtension {

    public abstract DirectoryProperty getBuilderSources();

    public abstract RegularFileProperty getVersionCatalog();

    public abstract Property<Integer> getTemplateBudgetMegabytes();
}
