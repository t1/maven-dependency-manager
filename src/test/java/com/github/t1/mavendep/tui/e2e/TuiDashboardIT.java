package com.github.t1.mavendep.tui.e2e;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.Isolated;

import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.writeString;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Isolated
@Execution(SAME_THREAD)
class TuiDashboardIT {
    private static final String POM_CONTENT = """
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0">
                <modelVersion>4.0.0</modelVersion>
                <groupId>com.example</groupId>
                <artifactId>tui-e2e-fixture</artifactId>
                <version>1.0.0</version>
                <name>TUI E2E Fixture</name>
                <dependencies>
                    <dependency>
                        <groupId>org.assertj</groupId>
                        <artifactId>assertj-core</artifactId>
                        <version>3.25.0</version>
                    </dependency>
                    <dependency>
                        <groupId>org.junit.jupiter</groupId>
                        <artifactId>junit-jupiter</artifactId>
                        <version>5.10.0</version>
                        <scope>test</scope>
                    </dependency>
                </dependencies>
            </project>
            """;

    @TempDir Path tempDir;

    private HttpServer fakeRepo;
    private final Map<String, String> repoResponses = new ConcurrentHashMap<>();
    private TuiTestDriver tui;
    private Path pomFile;

    @BeforeEach void setUp() throws IOException {
        pomFile = tempDir.resolve("pom.xml");
        writeString(pomFile, POM_CONTENT);

        fakeRepo = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        fakeRepo.createContext("/", exchange -> {
            try (exchange) {
                var response = repoResponses.get(exchange.getRequestURI().getPath());
                if (response == null) {
                    exchange.sendResponseHeaders(404, -1);
                } else {
                    var bytes = response.getBytes(UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/xml");
                    exchange.sendResponseHeaders(200, bytes.length);
                    exchange.getResponseBody().write(bytes);
                }
            }
        });
        fakeRepo.start();
    }

    @AfterEach void tearDown() {
        tui.close();
        fakeRepo.stop(0);
    }

    private void startTui() {
        var repoUrl = "http://localhost:" + fakeRepo.getAddress().getPort();
        var localRepo = tempDir.resolve("local-repo");
        tui = new Pty4jTuiTestDriver(pomFile, repoUrl, localRepo);
    }

    private void givenAllVersions() {
        givenVersions("org.assertj", "assertj-core", "3.25.0", "3.27.7");
        givenVersions("org.junit.jupiter", "junit-jupiter", "5.10.0", "6.0.3");
    }

    @Test void showsTitleAndTabs() {
        givenAllVersions();
        startTui();

        tui.awaitText("Maven Dependency Manager — TUI E2E Fixture");
        tui.awaitText("Dependencies");
        tui.awaitText("[s]how all");
        tui.awaitText("[q/Esc] quit");
    }

    @Test void scanCompletesAndShowsDependencies() {
        givenAllVersions();
        startTui();

        tui.awaitAnyText("Scanning...", "selected");
        tui.awaitText("selected");
        tui.awaitText("assertj-core");
        tui.awaitText("junit-jupiter");
    }

    @Test void tabSwitching() {
        givenAllVersions();
        startTui();

        tui.awaitText("selected");
        tui.type("]");
        tui.awaitText("Plugins");
        tui.type("]]");
        tui.awaitText("Git Diff");
    }

    @Test void quitExitsCleanlyOnQ() {
        givenAllVersions();
        startTui();

        tui.awaitText("Maven Dependency Manager — TUI E2E Fixture");
        tui.type("q");
        assertTrue(tui.waitForExit());
    }

    @Test void quitExitsCleanlyOnEscape() {
        givenAllVersions();
        startTui();

        tui.awaitText("selected");
        tui.pressKey(Key.ESCAPE);
        assertTrue(tui.waitForExit());
    }

    @Test void selectionTogglesWithSpaceKey() {
        givenAllVersions();
        startTui();

        tui.awaitText("0 selected");
        tui.type(" ");
        tui.awaitText("1 selected");
    }

    @Test void selectAllAndDeselectAll() {
        givenAllVersions();
        startTui();

        tui.awaitText("0 selected");
        tui.type("a");
        tui.awaitText("2 selected");
        tui.type("a");
        tui.awaitText("0 selected");
    }

    @Test void deselectAllWithN() {
        givenAllVersions();
        startTui();

        tui.awaitText("0 selected");
        tui.type("a");
        tui.awaitText("2 selected");
        tui.type("n");
        tui.awaitText("0 selected");
    }

    @Test void cursorNavigationWithArrowKeys() {
        givenAllVersions();
        startTui();

        tui.awaitText("0 selected");
        // cursor starts on first item; select it
        tui.type(" ");
        tui.awaitText("1 selected");
        // move down and select second item
        tui.pressKey(Key.DOWN);
        tui.type(" ");
        tui.awaitText("2 selected");
        // move back up and deselect first item
        tui.pressKey(Key.UP);
        tui.type(" ");
        tui.awaitText("1 selected");
    }

    @Test void cursorNavigationWithPageKeys() {
        givenAllVersions();
        startTui();

        tui.awaitText("0 selected");
        tui.pressKey(Key.PAGE_DOWN);
        tui.type(" ");
        tui.awaitText("1 selected");
        tui.pressKey(Key.PAGE_UP);
        tui.type(" ");
        tui.awaitText("2 selected");
    }

    @Test void versionPickerOpensAndCloses() {
        givenAllVersions();
        startTui();

        tui.awaitText("[Enter] pick version");
        tui.pressKey(Key.ENTER);
        tui.awaitText("[Esc] cancel");
        tui.awaitNoText("[Enter] pick version");
        tui.awaitText("3.27.7");
        tui.pressKey(Key.ESCAPE);
        tui.awaitText("[Enter] pick version");
    }

    @Test void scanCompletesEvenWhenVersionLookupFails() {
        givenVersions("org.assertj", "assertj-core", "3.25.0", "3.27.7");
        // junit-jupiter metadata is missing — will get 404
        startTui();

        tui.awaitText("selected");
        tui.awaitText("assertj-core");
        tui.awaitText("3.27.7");
        // junit-jupiter has no latest version metadata and is hidden by default
        tui.type("s"); // toggle show-all
        tui.awaitText("junit-jupiter");
        tui.awaitText("5.10.0 → ?");
    }

    private void givenVersions(String groupId, String artifactId, String... versions) {
        var versionsXml = new StringBuilder();
        for (var v : versions) versionsXml.append("    <version>").append(v).append("</version>\n");
        var xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <metadata>
                  <groupId>%s</groupId>
                  <artifactId>%s</artifactId>
                  <versioning>
                    <versions>
                %s\
                    </versions>
                  </versioning>
                </metadata>
                """.formatted(groupId, artifactId, versionsXml);
        var path = "/" + groupId.replace('.', '/') + "/" + artifactId + "/maven-metadata.xml";
        repoResponses.put(path, xml);
    }
}
