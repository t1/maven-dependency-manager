package com.github.t1.mavendep.domain;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
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
import java.util.stream.Stream;

import static com.github.t1.mavendep.domain.Dependency.DependencyType;
import static com.github.t1.mavendep.domain.Dependency.DependencyType.dependency;
import static com.github.t1.mavendep.domain.Dependency.DependencyType.plugin;
import static com.github.t1.mavendep.report.Logger.log;
import static java.nio.file.Files.readString;
import static java.nio.file.Files.writeString;
import static java.util.regex.Pattern.DOTALL;
import static java.util.regex.Pattern.quote;
import static org.w3c.dom.Node.ELEMENT_NODE;

/// Represents a Maven POM (Project Object Model) file with parsing and updating capabilities.
///
/// ## Features
/// - Parses dependencies with groupId, artifactId, version, scope
/// - Parses plugins with groupId, artifactId, version (defaults groupId to `org.apache.maven.plugins` if missing)
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
/// The content and dirty flag are mutable to support efficient chaining of updates.
/// Dependencies and plugins may be updated when resolving version properties from parent POMs.
/// Properties remain immutable and reflect the initial parsed state.
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

    private static class PomParsingException extends RuntimeException {
        public PomParsingException(String message, Exception cause) {super(message, cause);}
    }

    private static Path resolvePath(Path raw) {
        if (!raw.startsWith("~")) return raw;
        var userHome = Path.of(System.getProperty("user.home"));
        return userHome.resolve(raw.subpath(1, raw.getNameCount()));
    }

    private static Pom parse(Path pomPath, String content) {
        var doc = parseDocument(content);
        var properties = parsePropertiesFromDocument(doc);
        var document = doc.getDocumentElement();
        return new Pom(
                content,
                pomPath,
                new Coordinates(
                        textContent("groupId", document),
                        textContent("artifactId", document),
                        Version.fromString(textContent("version", document))),
                parseParentFromDocument(doc, properties),
                parseElementsFromDocument(doc, dependency, properties),
                parseElementsFromDocument(doc, plugin, properties),
                properties,
                parseModulesFromDocument(doc));
    }

    private static String textContent(String tagName, Element element) {
        var nodes = element.getChildNodes();
        for (var i = 0; i < nodes.getLength(); i++) {
            var node = nodes.item(i);
            if (node.getNodeType() == ELEMENT_NODE && node.getNodeName().equals(tagName)) {
                return node.getTextContent().trim();
            }
        }
        return null;
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
                if (child.getNodeType() == ELEMENT_NODE) {
                    var key = child.getNodeName();
                    var value = child.getTextContent().trim();
                    properties.put(key, value);
                }
            }
        }

        return properties;
    }

    private static List<Dependency> parseElementsFromDocument(Document doc, DependencyType type, Map<String, String> properties) {
        var elements = new ArrayList<Dependency>();
        var nodes = doc.getElementsByTagName(type.name());

        for (var i = 0; i < nodes.getLength(); i++) {
            elements.add(new DependencyParser(properties, type).parse(nodes.item(i)));
        }

        return elements;
    }

    private static Optional<Dependency> parseParentFromDocument(Document doc, Map<String, String> properties) {
        var parentNodes = doc.getElementsByTagName("parent");
        if (parentNodes.getLength() == 0) return Optional.empty();
        var parentNode = parentNodes.item(0);
        return Optional.of(new DependencyParser(properties, DependencyType.parent).parse(parentNode));
    }

    private static List<String> parseModulesFromDocument(Document doc) {
        var modules = new ArrayList<String>();
        var modulesNodes = doc.getElementsByTagName("modules");

        if (modulesNodes.getLength() > 0) {
            var modulesNode = modulesNodes.item(0);
            var children = modulesNode.getChildNodes();

            for (var i = 0; i < children.getLength(); i++) {
                var child = children.item(i);
                if (child.getNodeType() == ELEMENT_NODE && child.getNodeName().equals("module")) {
                    modules.add(child.getTextContent().trim());
                }
            }
        }

        return modules;
    }

    private static Optional<String> extractPropertyName(String ref) {
        if (ref != null && ref.startsWith("${") && ref.endsWith("}")) {
            return Optional.of(ref.substring(2, ref.length() - 1));
        }
        return Optional.empty();
    }

    private static String resolve(String value, Map<String, String> properties) {
        var propertyName = extractPropertyName(value);
        if (propertyName.isPresent()) {
            return properties.get(propertyName.get());
        }
        return value;
    }

    private record DependencyParser(Map<String, String> properties, DependencyType type) {
        private static final String DEFAULT_PLUGIN_GROUP_ID = "org.apache.maven.plugins";

        private Dependency parse(Node depNode) {
            var children = depNode.getChildNodes();

            String groupId = null;
            String artifactId = null;
            Version version = null;
            Scope scope = Scope.DEFAULT;
            String versionProperty = null;

            for (var i = 0; i < children.getLength(); i++) {
                var child = children.item(i);
                switch (child.getNodeName()) {
                    case "groupId" -> groupId = resolve(child.getTextContent().trim());
                    case "artifactId" -> artifactId = resolve(child.getTextContent().trim());
                    case "version" -> {
                        var raw = child.getTextContent().trim();
                        versionProperty = extractPropertyName(raw).orElse(null);
                        version = Version.fromString(resolve(raw));
                    }
                    case "scope" -> scope = Scope.of(child.getTextContent().trim());
                }
            }

            if (groupId == null && type == plugin) {
                log("Warning: missing groupId in plugin " + artifactId + "; assuming " + DEFAULT_PLUGIN_GROUP_ID);
                groupId = DEFAULT_PLUGIN_GROUP_ID;
            }

            var dependency = new Dependency(type, groupId, artifactId, version, scope, versionProperty);
            if (groupId == null) log("Warning: missing groupId in " + dependency);
            if (artifactId == null) log("Warning: missing artifactId in " + dependency);
            return dependency;
        }

        private String resolve(String value) {
            return Pom.resolve(value, properties);
        }
    }


    private boolean dirty;
    private String content;
    private final Path path;
    private final Coordinates coordinates;
    private final Optional<Dependency> parent;
    private final Map<String, String> properties;
    private final List<Dependency> dependencies;
    private final List<Dependency> plugins;
    private final List<String> modules;
    private Pom parentPom;

    private Pom(
            String content,
            Path path,
            Coordinates coordinates,
            Optional<Dependency> parent,
            List<Dependency> dependencies,
            List<Dependency> plugins,
            Map<String, String> properties,
            List<String> modules) {
        this.content = content;
        this.path = path;
        this.coordinates = coordinates;
        this.parent = parent;
        this.properties = properties;
        this.dependencies = dependencies;
        this.plugins = plugins;
        this.modules = modules;
    }

    @Override public String toString() {return "Pom[" + path + "]";}

    public Path path() {return path;}

    public Coordinates coordinates() {return coordinates;}

    public List<Dependency> dependencies() {return dependencies;}

    public List<Dependency> plugins() {return plugins;}

    public Optional<Dependency> parent() {return parent;}

    public Map<String, String> properties() {return properties;}

    public List<String> modules() {return modules;}

    public void resolveUnresolvedVersionsFrom(Pom parentPom) {
        this.parentPom = parentPom;
        resolveVersionsInList(dependencies, parentPom.properties());
        resolveVersionsInList(plugins, parentPom.properties());
    }

    private static void resolveVersionsInList(List<Dependency> list, Map<String, String> parentProperties) {
        list.replaceAll(dependency -> {
            if (dependency.version() != null || dependency.versionProperty() == null) return dependency;
            var resolved = parentProperties.get(dependency.versionProperty());
            return resolved != null ? dependency.with(Version.fromString(resolved)) : dependency;
        });
    }

    public boolean isDirty() {return dirty;}

    public void apply(Stream<DependencyUpdate> updates) {updates.forEach(new Updater()::apply);}

    public void writeToDisk() {
        try {
            writeString(path, content);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private class Updater {
        private static final String OPTIONAL_WHITESPACE = "\\s*";
        private static final String OPTIONAL_COMMENTS = "(?:\\s*<!--.*?-->\\s*)*";

        private void apply(DependencyUpdate update) {
            if (update.versionProperty() != null) updatePropertyValue(update.versionProperty(), update);
            else if (update.type() == DependencyType.parent) updateParentDirectVersion(update);
            else updateDirectVersion(update);
        }

        private void updatePropertyValue(String propertyName, DependencyUpdate update) {
            if (properties.containsKey(propertyName)) {
                replaceVersionInTag("<" + propertyName + ">", "</" + propertyName + ">", update);
            } else if (parentPom != null) {
                parentPom.apply(Stream.of(update));
            }
        }

        private void updateDirectVersion(DependencyUpdate update) {
            replaceVersionInTag("<version>", "</version>", update);
        }

        private void updateParentDirectVersion(DependencyUpdate update) {
            var pattern = Pattern.compile(
                    "<parent>.*?<version>" +
                    OPTIONAL_COMMENTS +
                    OPTIONAL_WHITESPACE +
                    quote(update.currentVersion().toString()) +
                    OPTIONAL_WHITESPACE +
                    OPTIONAL_COMMENTS +
                    "</version>.*?</parent>",
                    DOTALL
            );

            replaceVersion(pattern, update);
        }

        private void replaceVersionInTag(String openingTag, String closingTag, DependencyUpdate update) {
            var pattern = Pattern.compile(
                    quote(openingTag) +
                    OPTIONAL_COMMENTS +
                    OPTIONAL_WHITESPACE +
                    quote(update.currentVersion().toString()) +
                    OPTIONAL_WHITESPACE +
                    OPTIONAL_COMMENTS +
                    quote(closingTag),
                    DOTALL
            );

            replaceVersion(pattern, update);
        }

        private void replaceVersion(Pattern pattern, DependencyUpdate update) {
            var matcher = pattern.matcher(content);
            if (matcher.find()) {
                var matched = matcher.group();
                var replacement = matched.replaceFirst(
                        quote(update.currentVersion().toString()),
                        update.latestVersion().toString()
                );
                content = content.replace(matched, replacement);
                dirty = true;
            }
        }
    }
}
