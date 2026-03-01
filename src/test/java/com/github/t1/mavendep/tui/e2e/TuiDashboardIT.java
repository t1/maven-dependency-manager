package com.github.t1.mavendep.tui.e2e;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TuiDashboardIT {
    private static final Path FIXTURE_POM = Path.of("src/test/resources/tui-e2e-fixture/pom.xml").toAbsolutePath();

    @TempDir Path tempDir;

    private HttpServer fakeRepo;
    private final Map<String, String> repoResponses = new ConcurrentHashMap<>();
    private TuiTestDriver tui;
    private Path pomCopy;

    @BeforeEach void setUp() throws IOException {
        pomCopy = tempDir.resolve("pom.xml");
        Files.copy(FIXTURE_POM, pomCopy);

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
        tui = new Pty4jTuiTestDriver(pomCopy, repoUrl, localRepo);
    }

    private void givenAllVersions() {
        givenVersions("org.assertj", "assertj-core", "3.25.0", "3.27.7");
        givenVersions("org.junit.jupiter", "junit-jupiter", "5.10.0", "6.0.3");
    }

    @Test void showsTitleAndTabs() {
        givenAllVersions();
        startTui();

        tui.awaitText("Maven Dependency Manager");
        tui.awaitText("Dependencies");
        tui.awaitText("[s]how all");
        tui.awaitText("[q]uit");
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

    @Test void quitExitsCleanly() {
        givenAllVersions();
        startTui();

        tui.awaitText("Maven Dependency Manager");
        tui.type("q");
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

    @Test void versionPickerOpensAndCloses() {
        givenAllVersions();
        startTui();

        tui.awaitText("0 selected");
        tui.pressKey(Key.ENTER);
        tui.awaitText("3.27.7");
        tui.awaitText("3.25.0");
        tui.pressKey(Key.ESCAPE);
        tui.awaitText("0 selected");
    }

    @Test void scanCompletesEvenWhenVersionLookupFails() {
        givenVersions("org.assertj", "assertj-core", "3.25.0", "3.27.7");
        // junit-jupiter metadata is missing — will get 404
        startTui();

        tui.awaitText("selected");
        tui.awaitText("assertj-core");
        tui.awaitText("3.27.7");
        // junit-jupiter has update type "none" and is hidden by default
        tui.type("s"); // toggle show-all
        tui.awaitText("junit-jupiter");
        tui.awaitText("none");
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
