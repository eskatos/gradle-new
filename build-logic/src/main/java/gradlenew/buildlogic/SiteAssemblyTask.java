package gradlenew.buildlogic;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

@CacheableTask
public abstract class SiteAssemblyTask extends DefaultTask {

    private static final Pattern IMG = Pattern.compile("<img\\b([^>]*)>");
    private static final Pattern ATTRIBUTE = Pattern.compile("([\\w:-]+)\\s*=\\s*\"([^\"]*)\"");
    private static final Pattern PROLOG = Pattern.compile("<\\?xml[^>]*\\?>|<!--.*?-->", Pattern.DOTALL);
    private static final Pattern ROOT = Pattern.compile("<svg\\b([^>]*)>(.*)</svg>", Pattern.DOTALL);
    private static final Pattern STYLE = Pattern.compile("<style[^>]*>(.*?)</style>", Pattern.DOTALL);
    private static final Pattern RULE = Pattern.compile("\\.([\\w-]+)\\s*\\{([^}]*)\\}");
    private static final Pattern EMPTY_DEFS = Pattern.compile("<defs>\\s*</defs>");
    private static final Pattern TAG = Pattern.compile("<([a-zA-Z][\\w:-]*)((?:\\s+[\\w:-]+\\s*=\\s*\"[^\"]*\")*)\\s*(/?)>");
    private static final Pattern STYLESHEET = Pattern.compile("[ \\t]*<link\\b[^>]*\\brel=\"stylesheet\"[^>]*>\\R?");
    private static final Pattern REFERENCE = Pattern.compile("\\b(href|src)=\"(/?)(assets/[^\"#?]+)\"");
    private static final Pattern URL = Pattern.compile("url\\(\"([^\"#]+)(#[^\"]*)?\"\\)");
    private static final String STYLES = StylesheetBundleTask.BUNDLE_DIR + "/styles.css";
    private static final String SPRITE = StylesheetBundleTask.BUNDLE_DIR + "/" + StylesheetBundleTask.SPRITE;

    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getSiteSources();

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getSprite();

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getStylesheet();

    @OutputDirectory
    public abstract DirectoryProperty getDestination();

    @Inject
    protected abstract FileSystemOperations getFileSystemOperations();

    @TaskAction
    public void assemble() {
        File site = getSiteSources().get().getAsFile();
        File destination = getDestination().get().getAsFile();
        File index = new File(site, StylesheetBundleTask.PAGE);
        List<String> stylesheets = StylesheetBundleTask.stylesheets(index.isFile() ? IconSpriteTask.read(index) : "").stream()
            .map(href -> StylesheetBundleTask.sitePath(href, "")).toList();
        getFileSystemOperations().sync(spec -> {
            spec.from(site);
            spec.into(destination);
            spec.exclude("assets/icons/**");
            spec.exclude(stylesheets);
            spec.setIncludeEmptyDirs(false);
        });
        if (!stylesheets.isEmpty()) {
            copy(getStylesheet().get().getAsFile(), new File(destination, STYLES));
            copy(getSprite().get().getAsFile(), new File(destination, SPRITE));
        }
        File[] pages = destination.listFiles((dir, name) -> name.endsWith(".html"));
        Map<String, String> hashed = new HashMap<>();
        Set<String> inlined = new LinkedHashSet<>();
        if (!stylesheets.isEmpty()) {
            String css = IconSpriteTask.read(new File(destination, STYLES));
            css = hashUrls(css, destination, hashed);
            IconSpriteTask.write(new File(destination, STYLES), css);
            hashed.put(STYLES, hash(destination, STYLES));
        }
        for (File page : pages == null ? new File[0] : pages) {
            String html = IconSpriteTask.read(page);
            if (!stylesheets.isEmpty()) {
                html = oneStylesheet(html, page.getName(), stylesheets, hashed.get(STYLES));
            }
            html = inlineSvgImages(html, site, inlined);
            html = hashReferences(html, destination, hashed);
            IconSpriteTask.write(page, html);
        }
        for (String path : inlined) {
            if (!hashed.containsKey(path)) {
                new File(destination, path).delete();
            }
        }
    }

    private static String oneStylesheet(String html, String page, List<String> bundled, String stylesheet) {
        List<String> linked = StylesheetBundleTask.stylesheets(html);
        if (!linked.stream().map(href -> StylesheetBundleTask.sitePath(href, "")).toList().equals(bundled)) {
            throw new GradleException(page + " links " + linked + ", not the stylesheets of " + StylesheetBundleTask.PAGE + " that were bundled");
        }
        String prefix = linked.get(0).startsWith("/") ? "/" : "";
        Matcher link = STYLESHEET.matcher(html);
        StringBuilder result = new StringBuilder();
        boolean first = true;
        while (link.find()) {
            link.appendReplacement(result, first ? Matcher.quoteReplacement("<link rel=\"stylesheet\" href=\"" + prefix + stylesheet + "\">\n") : "");
            first = false;
        }
        link.appendTail(result);
        return result.toString();
    }

    private static String hashUrls(String css, File destination, Map<String, String> hashed) {
        Matcher url = URL.matcher(css);
        StringBuilder result = new StringBuilder();
        while (url.find()) {
            if (url.group(1).startsWith("data:") || url.group(1).matches("[a-z]+://.*")) {
                url.appendReplacement(result, Matcher.quoteReplacement(url.group()));
                continue;
            }
            String path = StylesheetBundleTask.sitePath(url.group(1), StylesheetBundleTask.BUNDLE_DIR);
            String name = hashed.computeIfAbsent(path, original -> hash(destination, original));
            String fragment = url.group(2) == null ? "" : url.group(2);
            url.appendReplacement(result, Matcher.quoteReplacement("url(\"" + StylesheetBundleTask.relativeToBundle(name) + fragment + "\")"));
        }
        url.appendTail(result);
        return result.toString();
    }

    private static String hashReferences(String html, File destination, Map<String, String> hashed) {
        Matcher reference = REFERENCE.matcher(html);
        StringBuilder result = new StringBuilder();
        while (reference.find()) {
            String path = reference.group(3);
            String replacement = reference.group();
            if (!path.startsWith(StylesheetBundleTask.BUNDLE_DIR + "/social/") && (hashed.containsKey(path) || new File(destination, path).isFile())) {
                String name = hashed.computeIfAbsent(path, original -> hash(destination, original));
                replacement = reference.group(1) + "=\"" + reference.group(2) + name + "\"";
            }
            reference.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        reference.appendTail(result);
        return result.toString();
    }

    private static String hash(File destination, String path) {
        if (path.matches(".*\\.[0-9a-f]{12}\\.[^./]+")) {
            return path;
        }
        File file = new File(destination, path);
        if (!file.isFile()) {
            throw new GradleException("The site references " + path + ", which is not in the site");
        }
        try {
            String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file.toPath()))).substring(0, 12);
            int dot = path.lastIndexOf('.');
            String name = path.substring(0, dot) + "." + digest + path.substring(dot);
            Files.move(file.toPath(), new File(destination, name).toPath());
            return name;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void copy(File from, File to) {
        try {
            Files.createDirectories(to.toPath().getParent());
            Files.copy(from.toPath(), to.toPath());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String inlineSvgImages(String html, File site, Set<String> inlined) {
        Matcher img = IMG.matcher(html);
        StringBuilder result = new StringBuilder();
        while (img.find()) {
            Map<String, String> attributes = attributes(img.group(1));
            String src = attributes.getOrDefault("src", "");
            String replacement = img.group();
            if (src.endsWith(".svg") && !src.matches("[a-z]+://.*")) {
                String alt = attributes.get("alt");
                if (alt == null || alt.isBlank()) {
                    throw new GradleException("The image " + src + " has no alt text to become the inline SVG's accessible name");
                }
                String path = StylesheetBundleTask.sitePath(src, "");
                inlined.add(path);
                File svg = new File(site, path);
                replacement = inline(IconSpriteTask.read(svg), svg.getName().replaceFirst("\\.svg$", "") + "-", attributes);
            }
            img.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        img.appendTail(result);
        return result.toString();
    }

    private static String inline(String svg, String prefix, Map<String, String> image) {
        Matcher root = ROOT.matcher(PROLOG.matcher(svg).replaceAll(""));
        if (!root.find()) {
            throw new GradleException("Not an SVG image: " + image.get("src"));
        }
        String body = root.group(2);
        Map<String, Map<String, String>> rules = new LinkedHashMap<>();
        Matcher style = STYLE.matcher(body);
        while (style.find()) {
            String css = style.group(1).replaceAll("/\\*.*?\\*/", "");
            Matcher rule = RULE.matcher(css);
            while (rule.find()) {
                Map<String, String> declarations = rules.computeIfAbsent(rule.group(1), name -> new LinkedHashMap<>());
                for (String declaration : rule.group(2).split(";")) {
                    if (declaration.isBlank()) continue;
                    String[] parts = declaration.split(":", 2);
                    declarations.put(parts[0].trim(), parts[1].trim());
                }
            }
            if (!RULE.matcher(css).replaceAll("").isBlank()) {
                throw new GradleException("The SVG image " + image.get("src") + " has CSS other than class rules, which cannot be inlined under the content security policy");
            }
        }
        body = EMPTY_DEFS.matcher(STYLE.matcher(body).replaceAll("")).replaceAll("");
        Matcher tag = TAG.matcher(body);
        StringBuilder elements = new StringBuilder();
        while (tag.find()) {
            Map<String, String> attributes = attributes(tag.group(2));
            String classes = attributes.remove("class");
            if (classes != null) {
                for (String name : classes.trim().split("\\s+")) {
                    rules.getOrDefault(name, Map.of()).forEach(attributes::putIfAbsent);
                }
            }
            prefixReferences(attributes, prefix);
            tag.appendReplacement(elements, Matcher.quoteReplacement("<" + tag.group(1) + render(attributes) + (tag.group(3).isEmpty() ? ">" : "/>")));
        }
        tag.appendTail(elements);

        Map<String, String> rootAttributes = attributes(root.group(1));
        String id = rootAttributes.remove("id");
        rootAttributes.remove("data-name");
        rootAttributes.remove("version");
        rootAttributes.remove("width");
        rootAttributes.remove("height");
        String[] viewBox = rootAttributes.getOrDefault("viewBox", "").trim().split("[\\s,]+");
        if (viewBox.length != 4) {
            throw new GradleException("The SVG image " + image.get("src") + " has no viewBox to size it by");
        }
        BigDecimal height = new BigDecimal(image.getOrDefault("height", viewBox[3]));
        BigDecimal width = height.multiply(new BigDecimal(viewBox[2])).divide(new BigDecimal(viewBox[3]), 2, RoundingMode.HALF_UP).stripTrailingZeros();
        Map<String, String> svgAttributes = new LinkedHashMap<>();
        if (image.containsKey("class")) svgAttributes.put("class", image.get("class"));
        svgAttributes.put("role", "img");
        svgAttributes.put("aria-label", image.get("alt"));
        svgAttributes.put("width", width.toPlainString());
        svgAttributes.put("height", height.stripTrailingZeros().toPlainString());
        svgAttributes.putAll(rootAttributes);
        if (id != null) svgAttributes.put("id", prefix + id);
        return "<svg" + render(svgAttributes) + ">" + elements.toString().trim().replaceAll(">\\s+<", "><") + "</svg>";
    }

    private static void prefixReferences(Map<String, String> attributes, String prefix) {
        for (Map.Entry<String, String> attribute : attributes.entrySet()) {
            String value = attribute.getValue();
            if (attribute.getKey().equals("id")) {
                attribute.setValue(prefix + value);
            } else if ((attribute.getKey().equals("href") || attribute.getKey().equals("xlink:href")) && value.startsWith("#")) {
                attribute.setValue("#" + prefix + value.substring(1));
            } else if (value.contains("url(#")) {
                attribute.setValue(value.replace("url(#", "url(#" + prefix));
            }
        }
    }

    static Map<String, String> attributes(String text) {
        Map<String, String> attributes = new LinkedHashMap<>();
        Matcher attribute = ATTRIBUTE.matcher(text);
        while (attribute.find()) {
            attributes.put(attribute.group(1), attribute.group(2));
        }
        return attributes;
    }

    private static String render(Map<String, String> attributes) {
        List<String> parts = new ArrayList<>();
        attributes.forEach((name, value) -> parts.add(" " + name + "=\"" + value + "\""));
        return String.join("", parts);
    }
}
