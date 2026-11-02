package gradlenew.buildlogic;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

@CacheableTask
public abstract class StylesheetBundleTask extends DefaultTask {

    static final String PAGE = "index.html";
    static final String BUNDLE_DIR = "assets";
    static final String SPRITE = "icons.svg";
    private static final Pattern STYLESHEET = Pattern.compile("<link\\b[^>]*\\brel=\"stylesheet\"[^>]*>");
    private static final Pattern HREF = Pattern.compile("\\bhref=\"([^\"]*)\"");
    private static final Pattern URL = Pattern.compile("url\\(\\s*(['\"]?)([^'\")]*)\\1\\s*\\)");
    private static final Pattern ICON = Pattern.compile("assets/icons/([^/]+)\\.svg");

    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getSiteSources();

    @OutputFile
    public abstract RegularFileProperty getStylesheet();

    @TaskAction
    public void assemble() {
        File site = getSiteSources().get().getAsFile();
        List<String> sources = stylesheets(IconSpriteTask.read(new File(site, PAGE)));
        List<String> parts = new ArrayList<>();
        for (String source : sources) {
            String path = sitePath(source, "");
            File file = new File(site, path);
            if (!file.isFile()) {
                throw new GradleException(PAGE + " links the stylesheet " + source + ", which does not exist");
            }
            String css = IconSpriteTask.read(file);
            if (css.contains("@import")) {
                throw new GradleException(path + " uses @import, which the stylesheet bundle does not follow");
            }
            parts.add(rewriteUrls(css, path.contains("/") ? path.substring(0, path.lastIndexOf('/')) : ""));
        }
        IconSpriteTask.write(getStylesheet().get().getAsFile(), String.join("\n", parts));
    }

    static List<String> stylesheets(String page) {
        List<String> hrefs = new ArrayList<>();
        Matcher link = STYLESHEET.matcher(page);
        while (link.find()) {
            Matcher href = HREF.matcher(link.group());
            if (href.find()) {
                hrefs.add(href.group(1));
            }
        }
        return hrefs;
    }

    private String rewriteUrls(String css, String directory) {
        Matcher url = URL.matcher(css);
        StringBuilder result = new StringBuilder();
        while (url.find()) {
            String value = url.group(2).trim();
            String replacement;
            if (value.isEmpty() || value.startsWith("data:") || value.startsWith("#") || value.matches("[a-z]+://.*")) {
                replacement = url.group();
            } else {
                String fragment = value.contains("#") ? value.substring(value.indexOf('#')) : "";
                String path = sitePath(fragment.isEmpty() ? value : value.substring(0, value.indexOf('#')), directory);
                Matcher icon = ICON.matcher(path);
                String target = icon.matches() ? SPRITE + "#" + icon.group(1) : relativeToBundle(path) + fragment;
                replacement = "url(\"" + target + "\")";
            }
            url.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        url.appendTail(result);
        return result.toString();
    }

    static String sitePath(String reference, String directory) {
        String path = reference.startsWith("/") ? reference.substring(1) : (directory.isEmpty() ? reference : directory + "/" + reference);
        List<String> segments = new ArrayList<>();
        for (String segment : path.split("/")) {
            if (segment.equals("..")) {
                if (segments.isEmpty()) {
                    throw new GradleException("The reference " + reference + " leaves the site");
                }
                segments.remove(segments.size() - 1);
            } else if (!segment.isEmpty() && !segment.equals(".")) {
                segments.add(segment);
            }
        }
        return String.join("/", segments);
    }

    static String relativeToBundle(String sitePath) {
        return sitePath.startsWith(BUNDLE_DIR + "/") ? sitePath.substring(BUNDLE_DIR.length() + 1) : "../" + sitePath;
    }
}
