package gradlenew.buildlogic;

import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;

final class ContainerImageAttributes {

    static final Attribute<String> ARTIFACT_TYPE = Attribute.of("gradlenew.artifact", String.class);
    static final String CONTAINER_IMAGE_REFERENCE = "container-image-reference";
    static final String SITE = "site";
    static final String TEMPLATES = "templates";

    private ContainerImageAttributes() {
    }

    static void imageReference(AttributeContainer attributes) {
        attributes.attribute(ARTIFACT_TYPE, CONTAINER_IMAGE_REFERENCE);
    }

    static void site(AttributeContainer attributes) {
        attributes.attribute(ARTIFACT_TYPE, SITE);
    }

    static void templates(AttributeContainer attributes) {
        attributes.attribute(ARTIFACT_TYPE, TEMPLATES);
    }
}
