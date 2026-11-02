package gradlenew.buildlogic;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

@CacheableTask
public abstract class IconSpriteTask extends DefaultTask {

    static final int SIZE = 24;
    private static final Pattern COMMENT = Pattern.compile("<!--(.*?)-->", Pattern.DOTALL);
    private static final Pattern ROOT = Pattern.compile("<svg\\b([^>]*)>(.*)</svg>", Pattern.DOTALL);
    private static final Pattern ATTRIBUTE = Pattern.compile("([\\w:-]+)\\s*=\\s*\"([^\"]*)\"");
    private static final Set<String> ROOT_ONLY = Set.of("class", "xmlns", "width", "height", "viewBox");

    @InputFiles
    @PathSensitive(PathSensitivity.NAME_ONLY)
    public abstract ConfigurableFileCollection getIcons();

    @OutputFile
    public abstract RegularFileProperty getSprite();

    @TaskAction
    public void assemble() {
        List<File> icons = new ArrayList<>(getIcons().getFiles());
        icons.sort(Comparator.comparing(File::getName));
        Set<String> comments = new LinkedHashSet<>();
        StringBuilder body = new StringBuilder();
        for (int i = 0; i < icons.size(); i++) {
            File icon = icons.get(i);
            String name = icon.getName().replaceFirst("\\.svg$", "");
            String text = read(icon);
            Matcher comment = COMMENT.matcher(text);
            while (comment.find()) {
                comments.add(comment.group(1).trim());
            }
            Matcher root = ROOT.matcher(COMMENT.matcher(text).replaceAll(""));
            if (!root.find()) {
                throw new GradleException("Not an SVG icon: " + icon);
            }
            StringBuilder attributes = new StringBuilder();
            Matcher attribute = ATTRIBUTE.matcher(root.group(1));
            while (attribute.find()) {
                if (!ROOT_ONLY.contains(attribute.group(1))) {
                    attributes.append(' ').append(attribute.group(1)).append("=\"").append(attribute.group(2)).append('"');
                }
            }
            int x = i * SIZE;
            body.append("<view id=\"").append(name).append("\" viewBox=\"").append(x).append(" 0 ").append(SIZE).append(' ').append(SIZE).append("\"/>")
                .append("<svg x=\"").append(x).append("\" y=\"0\" width=\"").append(SIZE).append("\" height=\"").append(SIZE)
                .append("\" viewBox=\"0 0 ").append(SIZE).append(' ').append(SIZE).append('"').append(attributes).append('>')
                .append(root.group(2).trim().replaceAll(">\\s+<", "><"))
                .append("</svg>\n");
        }
        StringBuilder sprite = new StringBuilder();
        for (String comment : comments) {
            sprite.append("<!-- ").append(comment).append(" -->\n");
        }
        sprite.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"").append(SIZE).append("\" height=\"").append(SIZE)
            .append("\" viewBox=\"0 0 ").append(icons.size() * SIZE).append(' ').append(SIZE).append("\">\n")
            .append(body)
            .append("</svg>\n");
        write(getSprite().get().getAsFile(), sprite.toString());
    }

    static String read(File file) {
        try {
            return Files.readString(file.toPath());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static void write(File file, String text) {
        try {
            Files.createDirectories(file.toPath().getParent());
            Files.writeString(file.toPath(), text);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
