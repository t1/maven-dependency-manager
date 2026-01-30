package com.github.t1.mavendep.domain;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import static com.github.t1.mavendep.report.Logger.log;
import static java.nio.file.Files.readString;
import static java.nio.file.Files.writeString;
import static java.util.regex.Pattern.DOTALL;
import static java.util.regex.Pattern.quote;

/// Represents a Maven POM (Project Object Model) file with parsing and updating capabilities.
///
/// ## Features
/// - Parses dependencies with groupId, artifactId, version, scope
/// - Parses plugins with groupId, artifactId, version
/// - Resolves property references (${property.name})
/// - Extracts parent POM information
/// - Extracts module definitions for multi-module projects
/// - Updates dependency, plugin, and parent versions
/// - Preserves formatting: whitespace, comments, indentation
/// - Supports in-memory chaining of updates without re-parsing
///
/// ## Update Strategy
/// - Detects property-based versions (${property.name}) and updates the property
/// - Updates direct versions in `<version>` tags
/// - Uses regex-based text replacement (not DOM manipulation)
/// - Preserves all formatting while updating versions
///
/// ## Example
/// ```java
/// var pom = Pom.parse(pomPath);
/// pom.applyUpdates(dependencyUpdates);
/// pom.applyUpdates(pluginUpdates);
/// pom.updateParentVersion(parentUpdate);
/// pom.writeToDisk(pomPath);
/// ```
///
/// ## Mutability
/// The content field is mutable to support efficient chaining of updates. Dependencies, plugins,
/// and properties remain immutable and reflect the initial parsed state.
public class Pom {
    public static Optional<Pom> parse(Path pomPath) {
        var resolvedPomPath = resolvePath(pomPath);
        try {
            String content = readString(resolvedPomPath);
            return Optional.of(parse(pomPath, content)); // NOT the resolvedPomPath
        } catch (IOException e) {
            log("Warning: can't read POM file " + resolvedPomPath + ": " + e.getClass().getSimpleName(), e);
            return Optional.empty();
        } catch (PomParsingException e) {
            log("Warning: Can't parse POM file: " + pomPath + ": " + e.getMessage(), e);
            return Optional.empty();
        }
    }

    private static Path resolvePath(Path raw) {
        if (!raw.startsWith("~")) return raw;
        var userHome = Path.of(System.getProperty("user.home"));
        return userHome.resolve(raw.subpath(1, raw.getNameCount()));
    }

    private static Pom parse(Path pomPath, String content) {
        var doc = parseDocument(content);
        var properties = parsePropertiesFromDocument(doc);
        return new Pom(
                content,
                pomPath,
                parseParentFromDocument(doc, properties),
                parseDependenciesFromDocument(doc, properties),
                parsePluginsFromDocument(doc, properties),
                properties,
                parseModulesFromDocument(doc));
    }

    private static Document parseDocument(String content) {
        try {
            var factory = DocumentBuilderFactory.newInstance();
            var builder = factory.newDocumentBuilder();
            return builder.parse(new InputSource(new StringReader(content)));
        } catch (ParserConfigurationException | SAXException | IOException e) {
            throw new PomParsingException("can't parse POM", e);
        }
    }

    private static Map<String, String> parsePropertiesFromDocument(Document doc) {
        var properties = new HashMap<String, String>();
        var propertiesNodes = doc.getElementsByTagName("properties");

        if (propertiesNodes.getLength() > 0) {
            var propertiesNode = propertiesNodes.item(0);
            var children = propertiesNode.getChildNodes();

            for (var i = 0; i < children.getLength(); i++) {
                var child = children.item(i);
                if (child.getNodeType() == Node.ELEMENT_NODE) {
                    var key = child.getNodeName();
                    var value = child.getTextContent().trim();
                    properties.put(key, value);
                }
            }
        }

        return properties;
    }

    private static List<Dependency> parseDependenciesFromDocument(Document doc, Map<String, String> properties) {
        var dependencies = new ArrayList<Dependency>();
        var dependencyNodes = doc.getElementsByTagName("dependency");

        for (var i = 0; i < dependencyNodes.getLength(); i++) {
            var depNode = dependencyNodes.item(i);
            dependencies.add(new DependencyParser(properties).parse(depNode));
        }

        return dependencies;
    }

    private static Optional<Dependency> parseParentFromDocument(Document doc, Map<String, String> properties) {
        var parentNodes = doc.getElementsByTagName("parent");
        if (parentNodes.getLength() == 0) return Optional.empty();
        var parentNode = parentNodes.item(0);
        return Optional.of(new DependencyParser(properties).parse(parentNode));
    }

    private static List<Dependency> parsePluginsFromDocument(Document doc, Map<String, String> properties) {
        var plugins = new ArrayList<Dependency>();
        var pluginNodes = doc.getElementsByTagName("plugin");

        for (var i = 0; i < pluginNodes.getLength(); i++) {
            var pluginNode = pluginNodes.item(i);
            plugins.add(new DependencyParser(properties).parse(pluginNode));
        }

        return plugins;
    }

    private static List<String> parseModulesFromDocument(Document doc) {
        var modules = new ArrayList<String>();
        var modulesNodes = doc.getElementsByTagName("modules");

        if (modulesNodes.getLength() > 0) {
            var modulesNode = modulesNodes.item(0);
            var children = modulesNode.getChildNodes();

            for (var i = 0; i < children.getLength(); i++) {
                var child = children.item(i);
                if (child.getNodeType() == Node.ELEMENT_NODE && child.getNodeName().equals("module")) {
                    modules.add(child.getTextContent().trim());
                }
            }
        }

        return modules;
    }

    private static String resolve(String value, Map<String, String> properties) {
        if (value != null && value.startsWith("${") && value.endsWith("}")) {
            var propertyName = value.substring(2, value.length() - 1);
            return properties.get(propertyName);
        }
        return value;
    }

    private record DependencyParser(Map<String, String> properties) {
        private Dependency parse(Node depNode) {
            var children = depNode.getChildNodes();

            String groupId = null;
            String artifactId = null;
            Version version = null;
            Scope scope = Scope.DEFAULT;

            for (var i = 0; i < children.getLength(); i++) {
                var child = children.item(i);
                switch (child.getNodeName()) {
                    case "groupId" -> groupId = resolve(child.getTextContent().trim());
                    case "artifactId" -> artifactId = resolve(child.getTextContent().trim());
                    case "version" -> version = Version.fromString(resolve(child.getTextContent().trim()));
                    case "scope" -> scope = Scope.of(child.getTextContent().trim());
                }
            }

            var dependency = new Dependency(groupId, artifactId, version, scope);
            if (groupId == null) log("Warning: missing groupId in dependency " + dependency);
            if (artifactId == null) log("Warning: missing artifactId in dependency " + dependency);
            return dependency;
        }

        private String resolve(String value) {
            return Pom.resolve(value, properties);
        }
    }


    private String content;
    private final Path path;
    private final Optional<Dependency> parent;
    private final Map<String, String> properties;
    private final List<Dependency> dependencies;
    private final List<Dependency> plugins;
    private final List<String> modules;

    private Pom(
            String content,
            Path path,
            Optional<Dependency> parent,
            List<Dependency> dependencies,
            List<Dependency> plugins,
            Map<String, String> properties,
            List<String> modules) {
        this.content = content;
        this.path = path;
        this.parent = parent;
        this.properties = properties;
        this.dependencies = dependencies;
        this.plugins = plugins;
        this.modules = modules;
    }

    @Override public String toString() {return "Pom[" + path + "]";}

    public Path path() {return path;}

    public List<Dependency> dependencies() {return dependencies;}

    public List<Dependency> plugins() {return plugins;}

    public Optional<Dependency> parent() {return parent;}

    public Map<String, String> properties() {return properties;}

    public List<String> modules() {return modules;}

    // Update methods

    private static final String OPTIONAL_WHITESPACE = "\\s*";
    private static final String OPTIONAL_COMMENTS = "(?:\\s*<!--.*?-->\\s*)*";
    private static final String PROPERTY_REFERENCE = "\\$\\{[^}]+}";

    public void applyUpdates(List<DependencyUpdate> updates) {
        var propertyMap = buildPropertyMap();

        for (var update : updates) {
            var key = update.groupId() + ":" + update.artifactId();
            var propertyName = propertyMap.get(key);

            if (propertyName != null) {
                updatePropertyValue(propertyName, update);
            } else {
                updateDirectVersion(update);
            }
        }
    }

    public void updateParentVersion(DependencyUpdate parentUpdate) {
        // Check if parent version uses a property
        var parentPattern = Pattern.compile(
                "<parent>.*?<version>" + OPTIONAL_WHITESPACE + "(" + PROPERTY_REFERENCE + "|[^<]+)" +
                OPTIONAL_WHITESPACE + "</version>.*?</parent>",
                DOTALL
        );
        var matcher = parentPattern.matcher(content);
        if (matcher.find()) {
            var versionRef = matcher.group(1).trim();
            if (versionRef.startsWith("${") && versionRef.endsWith("}")) {
                var propertyName = versionRef.substring(2, versionRef.length() - 1);
                updatePropertyValue(propertyName, parentUpdate);
            } else {
                updateParentDirectVersion(parentUpdate);
            }
        }
    }

    public void writeToDisk() {
        try {
            writeString(path, content);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Map<String, String> buildPropertyMap() {
        var propertyMap = new HashMap<String, String>();

        for (var dep : dependencies) {
            findPropertyForDependency(dep)
                    .ifPresent(propertyName ->
                            propertyMap.put(dep.groupId() + ":" + dep.artifactId(), propertyName)
                    );
        }

        for (var plugin : plugins) {
            findPropertyForPlugin(plugin)
                    .ifPresent(propertyName ->
                            propertyMap.put(plugin.groupId() + ":" + plugin.artifactId(), propertyName)
                    );
        }

        return propertyMap;
    }

    private Optional<String> findPropertyForDependency(Dependency dep) {
        var dependencyBlock = findDependencyBlock(dep);
        if (dependencyBlock.isEmpty()) {
            return Optional.empty();
        }

        return extractPropertyReference(dependencyBlock.get());
    }

    private Optional<String> findDependencyBlock(Dependency dep) {
        var depPattern = Pattern.compile("<dependency>.*?<version>" + OPTIONAL_WHITESPACE + "(" + PROPERTY_REFERENCE + ")" + OPTIONAL_WHITESPACE + "</version>.*?</dependency>", DOTALL);
        var matcher = depPattern.matcher(content);

        while (matcher.find()) {
            var depBlock = matcher.group();
            var versionRef = matcher.group(1);
            if (matches(depBlock, dep.groupId(), dep.artifactId())) {
                return Optional.of(versionRef);
            }
        }

        return Optional.empty();
    }

    private boolean matches(String depBlock, String groupId, String artifactId) {
        var groupIdPattern = Pattern.compile("<groupId>" + OPTIONAL_WHITESPACE + "(?:" + PROPERTY_REFERENCE + "|" + quote(groupId) + ")" + OPTIONAL_WHITESPACE + "</groupId>");
        var artifactIdPattern = Pattern.compile("<artifactId>" + OPTIONAL_WHITESPACE + "(?:" + PROPERTY_REFERENCE + "|" + quote(artifactId) + ")" + OPTIONAL_WHITESPACE + "</artifactId>");

        return groupIdPattern.matcher(depBlock).find() && artifactIdPattern.matcher(depBlock).find();
    }

    private Optional<String> findPropertyForPlugin(Dependency plugin) {
        var pluginBlock = findPluginBlock(plugin);
        if (pluginBlock.isEmpty()) {
            return Optional.empty();
        }

        return extractPropertyReference(pluginBlock.get());
    }

    private Optional<String> findPluginBlock(Dependency plugin) {
        var pluginPattern = Pattern.compile("<plugin>.*?<version>" + OPTIONAL_WHITESPACE + "(" + PROPERTY_REFERENCE + ")" + OPTIONAL_WHITESPACE + "</version>.*?</plugin>", DOTALL);
        var matcher = pluginPattern.matcher(content);

        while (matcher.find()) {
            var pluginBlock = matcher.group();
            var versionRef = matcher.group(1);
            if (matchesPlugin(pluginBlock, plugin)) {
                return Optional.of(versionRef);
            }
        }

        return Optional.empty();
    }

    private boolean matchesPlugin(String pluginBlock, Dependency plugin) {
        return matches(pluginBlock, plugin.groupId(), plugin.artifactId());
    }

    private Optional<String> extractPropertyReference(String versionRef) {
        if (versionRef.startsWith("${") && versionRef.endsWith("}")) {
            var propertyName = versionRef.substring(2, versionRef.length() - 1);
            return Optional.of(propertyName);
        }

        return Optional.empty();
    }

    private void updatePropertyValue(String propertyName, DependencyUpdate update) {
        content = updateVersionInTag("<" + propertyName + ">", "</" + propertyName + ">", update);
    }

    private void updateDirectVersion(DependencyUpdate update) {
        content = updateVersionInTag("<version>", "</version>", update);
    }

    private void updateParentDirectVersion(DependencyUpdate update) {
        var pattern = Pattern.compile(
                "<parent>.*?<version>" +
                OPTIONAL_COMMENTS +           // optional comments with whitespace
                OPTIONAL_WHITESPACE +         // optional whitespace before version
                quote(update.currentVersion().toString()) +
                OPTIONAL_WHITESPACE +         // optional whitespace after version
                OPTIONAL_COMMENTS +           // optional comments with whitespace
                "</version>.*?</parent>",
                DOTALL
        );

        content = replaceVersion(pattern, update);
    }

    private String updateVersionInTag(String openingTag, String closingTag, DependencyUpdate update) {
        var pattern = Pattern.compile(
                quote(openingTag) +
                OPTIONAL_COMMENTS +           // optional comments with whitespace
                OPTIONAL_WHITESPACE +         // optional whitespace before version
                quote(update.currentVersion().toString()) +
                OPTIONAL_WHITESPACE +         // optional whitespace after version
                OPTIONAL_COMMENTS +           // optional comments with whitespace
                quote(closingTag),
                DOTALL
        );

        return replaceVersion(pattern, update);
    }

    private String replaceVersion(Pattern pattern, DependencyUpdate update) {
        var matcher = pattern.matcher(content);
        if (matcher.find()) {
            var matched = matcher.group();
            // Replace only the version number, preserving all whitespace and comments
            var replacement = matched.replaceFirst(
                    quote(update.currentVersion().toString()),
                    update.latestVersion().toString()
            );
            return content.replace(matched, replacement);
        }

        return content;
    }

    private static class PomParsingException extends RuntimeException {
        public PomParsingException(String message, Exception cause) {super(message, cause);}
    }
}
