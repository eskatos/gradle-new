package gradlenew.buildlogic;

import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;

public abstract class PublishedSiteExtension {

    public abstract Property<String> getStable();

    public abstract Property<String> getRc();

    public abstract DirectoryProperty getBuilderSources();

    public abstract RegularFileProperty getVersionCatalog();
}
