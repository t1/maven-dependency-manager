package com.github.t1.mavendep.domain;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import static com.github.t1.mavendep.domain.Dependency.DependencyType;
import static com.github.t1.mavendep.domain.Dependency.DependencyType.dependency;
import static com.github.t1.mavendep.domain.Dependency.DependencyType.plugin;
import static com.github.t1.mavendep.domain.Logger.log;
import static java.nio.file.Files.readString;
import static java.nio.file.Files.writeString;
import static javax.xml.transform.OutputKeys.OMIT_XML_DECLARATION;
import static org.w3c.dom.Node.ELEMENT_NODE;

/// Represents a Maven POM (Project Object Model) file with parsing and updating capabilities.
///
/// ## Features
/// - Inherits groupId and version from parent POM when not explicitly specified
/// - Parses dependencies with groupId, artifactId, version, scope
/// - Parses plugins with groupId, artifactId, version (defaults groupId to `org.apache.maven.plugins` if missing)
/// - Resolves property references (${property.name})
/// - Extracts parent POM information
/// - Extracts project name and module definitions for multi-module projects
/// - Updates dependency, plugin, and parent versions
/// - Preserves formatting: whitespace, comments, indentation
/// - Supports in-memory chaining of updates without re-parsing
///
/// ## Update Strategy
/// - Detects property-based versions (${property.name}) and updates the property
/// - Updates direct versions in `<version>` tags
/// - Uses DOM manipulation to update version text nodes, preserving comments and whitespace
/// - Serializes via `Transformer` with the original XML declaration prepended
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
        try {
            String content = readString(pomPath);
            return Optional.of(parse(pomPath, content));
        } catch (IOException e) {
            log().warning("can't read POM file " + pomPath + ": " + e, e);
            return Optional.empty();
        } catch (PomParsingException e) {
            log().warning("Can't parse POM file: " + pomPath + ": " + e, e);
            return Optional.empty();
        }
    }

    private static class PomParsingException extends RuntimeException {
        public PomParsingException(String message, Exception cause) {super(message, cause);}
    }

    static Pom parse(Path pomPath, String content) {
        var doc = parseDocument(content);
        var properties = parsePropertiesFromDocument(doc);
        var document = doc.getDocumentElement();
        var parent = parseParentFromDocument(doc, properties);
        var groupId = textContent("groupId", document);
        var version = textContent("version", document);
        if (groupId == null && parent.isPresent()) groupId = parent.get().groupId();
        if (version == null && parent.isPresent()) version = parent.get().version().toString();
        var dependencies = parseAllElements(doc, dependency, properties);
        var plugins = parseAllElements(doc, plugin, properties);
        return new Pom(
                doc,
                content,
                pomPath,
                new Coordinates(groupId, textContent("artifactId", document), Version.fromString(version)),
                textContent("name", document),
                parent,
                dependencies,
                plugins,
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
        return parsePropertiesFrom(doc.getDocumentElement());
    }

    private static Map<String, String> parsePropertiesFrom(Element parent) {
        var properties = new HashMap<String, String>();
        var children = parent.getChildNodes();
        for (var i = 0; i < children.getLength(); i++) {
            var child = children.item(i);
            if (child.getNodeType() == ELEMENT_NODE && child.getNodeName().equals("properties")) {
                var propChildren = child.getChildNodes();
                for (var j = 0; j < propChildren.getLength(); j++) {
                    var propChild = propChildren.item(j);
                    if (propChild.getNodeType() == ELEMENT_NODE) {
                        properties.put(propChild.getNodeName(), propChild.getTextContent().trim());
                    }
                }
            }
        }
        return properties;
    }

    private static List<Dependency> parseAllElements(Document doc, DependencyType type, Map<String, String> properties) {
        var elements = new ArrayList<>(parseElementsFromDocument(doc, type, properties));
        elements.addAll(parseElementsFromProfiles(doc, type, properties));
        return elements;
    }

    private static List<Dependency> parseElementsFromDocument(Document doc, DependencyType type, Map<String, String> properties) {
        var elements = new ArrayList<Dependency>();
        var nodes = doc.getElementsByTagName(type.name());

        for (var i = 0; i < nodes.getLength(); i++) {
            var node = nodes.item(i);
            if (!isInsideProfile(node)) {
                elements.add(new DependencyParser(properties, type, null).parse(node));
            }
        }

        return elements;
    }

    private static boolean isInside(Node node, String ancestorName) {
        var parent = node.getParentNode();
        while (parent != null) {
            if (parent.getNodeName().equals(ancestorName)) return true;
            parent = parent.getParentNode();
        }
        return false;
    }

    private static boolean isInsideProfile(Node node) {
        return isInside(node, "profile");
    }

    private static List<Dependency> parseElementsFromProfiles(Document doc, DependencyType type, Map<String, String> properties) {
        var elements = new ArrayList<Dependency>();
        var profiles = doc.getElementsByTagName("profile");

        for (var i = 0; i < profiles.getLength(); i++) {
            var profile = (Element) profiles.item(i);
            var profileId = textContent("id", profile);
            var mergedProperties = mergeProfileProperties(properties, profile);
            var nodes = profile.getElementsByTagName(type.name());
            for (var j = 0; j < nodes.getLength(); j++) {
                elements.add(new DependencyParser(mergedProperties, type, profileId).parse(nodes.item(j)));
            }
        }

        return elements;
    }

    private static Map<String, String> mergeProfileProperties(Map<String, String> baseProperties, Element profile) {
        var merged = new HashMap<>(baseProperties);
        merged.putAll(parsePropertiesFrom(profile));
        return merged;
    }

    private static Optional<Dependency> parseParentFromDocument(Document doc, Map<String, String> properties) {
        var parentNodes = doc.getElementsByTagName("parent");
        if (parentNodes.getLength() == 0) return Optional.empty();
        var parentNode = parentNodes.item(0);
        return Optional.of(new DependencyParser(properties, DependencyType.parent, null).parse(parentNode));
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

    private record DependencyParser(Map<String, String> properties, DependencyType type, String profile) {
        private static final String DEFAULT_PLUGIN_GROUP_ID = "org.apache.maven.plugins";

        private Dependency parse(Node depNode) {
            var children = depNode.getChildNodes();

            String groupId = "";
            String artifactId = "";
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

            if (groupId.isEmpty() && type == plugin) {
                log().warning(new ArtifactRef(DEFAULT_PLUGIN_GROUP_ID, artifactId), "missing groupId; assuming " + DEFAULT_PLUGIN_GROUP_ID);
                groupId = DEFAULT_PLUGIN_GROUP_ID;
            }

            var dependency = new Dependency(type, new Coordinates(groupId, artifactId, version), scope, versionProperty, profile,
                    declarationOf(depNode));
            if (groupId.isEmpty()) log().warning(dependency.artifactRef(), "missing groupId");
            if (artifactId.isEmpty()) log().warning(dependency.artifactRef(), "missing artifactId");
            return dependency;
        }

        private Dependency.Declaration declarationOf(Node depNode) {
            return switch (type) {
                case dependency -> isInside(depNode, "dependencyManagement")
                        ? Dependency.Declaration.dependencyManagement
                        : Dependency.Declaration.direct;
                case plugin -> isInside(depNode, "pluginManagement")
                        ? Dependency.Declaration.pluginManagement
                        : Dependency.Declaration.direct;
                case parent -> Dependency.Declaration.direct;
            };
        }

        private String resolve(String value) {
            return Pom.resolve(value, properties);
        }
    }


    private boolean dirty;
    private Document doc;
    private final String originalContent;
    private final Path path;
    private final Coordinates coordinates;
    private final String name;
    private final Optional<Dependency> parent;
    private final Map<String, String> properties;
    private final List<Dependency> dependencies;
    private final List<Dependency> plugins;
    private final List<String> modules;
    private Pom parentPom;

    private Pom(
            Document doc,
            String originalContent,
            Path path,
            Coordinates coordinates,
            String name,
            Optional<Dependency> parent,
            List<Dependency> dependencies,
            List<Dependency> plugins,
            Map<String, String> properties,
            List<String> modules) {
        this.doc = doc;
        this.originalContent = originalContent;
        this.path = path;
        this.coordinates = coordinates;
        this.name = name;
        this.parent = parent;
        this.properties = properties;
        this.dependencies = dependencies;
        this.plugins = plugins;
        this.modules = modules;
    }

    @Override public String toString() {return "Pom[" + path + "]";}

    public Path path() {return path;}

    public Coordinates coordinates() {return coordinates;}

    public String name() {return name;}

    public List<Dependency> dependencies() {return dependencies;}

    public List<Dependency> plugins() {return plugins;}

    public Optional<Dependency> parent() {return parent;}

    public int totalDependencyCount() {
        return dependencies.size() + plugins.size() + (parent.isPresent() ? 1 : 0);
    }

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

    /// Resets the POM content to its original parsed state, discarding any in-memory changes.
    public void reset() {
        doc = parseDocument(originalContent);
        dirty = false;
    }

    public void apply(Stream<Update> updates) {
        updates.filter(update -> update.latestVersion() != null && !Objects.equals(update.declaredVersion(), update.latestVersion()))
                .forEach(new Updater()::apply);
    }

    public void writeToDisk() {
        try {
            writeString(path, dirty ? serialize(doc) : originalContent);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String serialize(Document doc) {
        try {
            var transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OMIT_XML_DECLARATION, "yes");
            var writer = new StringWriter();
            transformer.transform(new DOMSource(doc), new StreamResult(writer));
            var xmlDeclEnd = originalContent.indexOf("?>");
            var xmlDeclaration = (xmlDeclEnd >= 0) ? originalContent.substring(0, xmlDeclEnd + 2) + "\n" : "";
            return xmlDeclaration + writer;
        } catch (TransformerException e) {
            throw new RuntimeException("can't serialize POM", e);
        }
    }

    private class Updater {
        private void apply(Update update) {
            if (update.versionProperty() != null) updatePropertyValue(update.versionProperty(), update);
            else updateVersionElement(update);
        }

        private void updatePropertyValue(String propertyName, Update update) {
            var updated = updateElementByName(propertyName, update.declaredVersion(), update.latestVersion());
            if (!updated && parentPom != null) {
                parentPom.apply(Stream.of(update));
            }
        }

        private void updateVersionElement(Update update) {
            if (update.declaredVersion() != null) {
                var versionElements = doc.getElementsByTagName("version");
                for (var i = 0; i < versionElements.getLength(); i++) {
                    if (replaceTextContent(versionElements.item(i), update.declaredVersion(), update.latestVersion())) return;
                }
                return;
            }
            insertVersionElement(update);
        }

        private boolean updateElementByName(String elementName, Version currentVersion, Version latestVersion) {
            var elements = doc.getElementsByTagName(elementName);
            for (var i = 0; i < elements.getLength(); i++) {
                if (replaceTextContent(elements.item(i), currentVersion, latestVersion)) return true;
            }
            return false;
        }

        private boolean replaceTextContent(Node element, Version currentVersion, Version latestVersion) {
            if (currentVersion == null || latestVersion == null) return false;
            if (!element.getTextContent().trim().equals(currentVersion.toString())) return false;
            var children = element.getChildNodes();
            for (var i = 0; i < children.getLength(); i++) {
                var child = children.item(i);
                if (child.getNodeType() == Node.TEXT_NODE && child.getTextContent().trim().equals(currentVersion.toString())) {
                    child.setTextContent(child.getTextContent().replace(currentVersion.toString(), latestVersion.toString()));
                    dirty = true;
                    return true;
                }
            }
            return false;
        }

        private void insertVersionElement(Update update) {
            if (update.latestVersion() == null) return;
            findElementWithoutVersion(update).ifPresent(element -> {
                var artifactId = directChild(element, "artifactId").orElseThrow();
                var nextElement = nextElementSibling(artifactId);
                var indentation = childIndentation(element);
                if (nextElement.isPresent()) {
                    element.insertBefore(createVersionNode(update), nextElement.get());
                    element.insertBefore(doc.createTextNode(indentation), nextElement.get());
                } else {
                    var trailingWhitespace = trailingWhitespace(element).orElse(null);
                    if (trailingWhitespace != null) {
                        element.insertBefore(doc.createTextNode(indentation), trailingWhitespace);
                        element.insertBefore(createVersionNode(update), trailingWhitespace);
                    } else {
                        element.appendChild(doc.createTextNode(indentation));
                        element.appendChild(createVersionNode(update));
                    }
                }
                dirty = true;
            });
        }

        private Optional<Element> findElementWithoutVersion(Update update) {
            var nodes = doc.getElementsByTagName(update.type().name());
            for (var i = 0; i < nodes.getLength(); i++) {
                var node = nodes.item(i);
                if (!(node instanceof Element element)) continue;
                if (matches(update, element) && directChild(element, "version").isEmpty()) return Optional.of(element);
            }
            return Optional.empty();
        }

        private boolean matches(Update update, Element element) {
            var groupId = directChildText(element, "groupId");
            if (groupId == null && update.type() == plugin) groupId = DependencyParser.DEFAULT_PLUGIN_GROUP_ID;
            return Objects.equals(groupId, update.groupId())
                   && Objects.equals(directChildText(element, "artifactId"), update.artifactId())
                   && Objects.equals(profileId(element), update.profile());
        }

        private Optional<Element> directChild(Element parent, String tagName) {
            var children = parent.getChildNodes();
            for (var i = 0; i < children.getLength(); i++) {
                var child = children.item(i);
                if (child.getNodeType() == ELEMENT_NODE && child.getNodeName().equals(tagName)) {
                    return Optional.of((Element) child);
                }
            }
            return Optional.empty();
        }

        private String directChildText(Element parent, String tagName) {
            return directChild(parent, tagName).map(Element::getTextContent).map(String::trim).orElse(null);
        }

        private Optional<Node> nextElementSibling(Node node) {
            var sibling = node.getNextSibling();
            while (sibling != null) {
                if (sibling.getNodeType() == ELEMENT_NODE) return Optional.of(sibling);
                sibling = sibling.getNextSibling();
            }
            return Optional.empty();
        }

        private String childIndentation(Element element) {
            var children = element.getChildNodes();
            for (var i = 0; i < children.getLength(); i++) {
                var child = children.item(i);
                if (child.getNodeType() == Node.TEXT_NODE && child.getTextContent().contains("\n")) {
                    return child.getTextContent();
                }
            }
            return "\n    ";
        }

        private Optional<Node> trailingWhitespace(Element element) {
            var child = element.getLastChild();
            if (child != null && child.getNodeType() == Node.TEXT_NODE && child.getTextContent().isBlank()) return Optional.of(child);
            return Optional.empty();
        }

        private Element createVersionNode(Update update) {
            var version = doc.createElement("version");
            version.setTextContent(update.latestVersion().toString());
            return version;
        }

        private String profileId(Node node) {
            var current = node.getParentNode();
            while (current != null) {
                if (current instanceof Element element && current.getNodeName().equals("profile")) {
                    return directChildText(element, "id");
                }
                current = current.getParentNode();
            }
            return null;
        }
    }
}
