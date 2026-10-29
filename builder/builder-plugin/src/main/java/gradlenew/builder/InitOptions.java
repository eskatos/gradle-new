package gradlenew.builder;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.options.Option;

final class InitOptions {

    private InitOptions() {
    }

    static List<Model.Option> read(Class<?> taskType) {
        List<Model.Option> options = new ArrayList<>();
        for (Method method : taskType.getDeclaredMethods()) {
            add(options, method, isBoolean(method));
        }
        for (Field field : taskType.getDeclaredFields()) {
            add(options, field, isBoolean(field.getGenericType()));
        }
        options.sort(Comparator.comparing(Model.Option::getName));
        return options;
    }

    static String negated(String name) {
        return "Disables option --" + name + ".";
    }

    private static void add(List<Model.Option> options, AnnotatedElement element, boolean negatable) {
        Option option = element.getAnnotation(Option.class);
        if (option == null || options.stream().anyMatch(existing -> existing.getName().equals(option.option()))) {
            return;
        }
        options.add(new Model.Option(option.option(), option.description(), negatable ? negated(option.option()) : null));
    }

    private static boolean isBoolean(Method method) {
        if (method.getParameterCount() == 1) {
            return isBoolean(method.getGenericParameterTypes()[0]);
        }
        return method.getParameterCount() == 0 && isBoolean(method.getGenericReturnType());
    }

    private static boolean isBoolean(Type type) {
        if (type == boolean.class || type == Boolean.class) {
            return true;
        }
        return type instanceof ParameterizedType parameterized
            && parameterized.getRawType() == Property.class
            && parameterized.getActualTypeArguments()[0] == Boolean.class;
    }
}
