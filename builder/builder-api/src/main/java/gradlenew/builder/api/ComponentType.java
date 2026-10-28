package gradlenew.builder.api;

import java.util.List;

public interface ComponentType extends NamedElement {

    List<String> getDefaultProjectNames();
}
