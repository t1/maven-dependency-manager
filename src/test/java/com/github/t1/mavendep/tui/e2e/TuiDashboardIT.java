package com.github.t1.mavendep.tui.e2e;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.Duration.ofSeconds;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TuiDashboardIT {
    private static final Path FIXTURE_POM = Path.of("src/test/resources/tui-e2e-fixture/pom.xml").toAbsolutePath();

    @TempDir Path tempDir;

    private HttpServer fakeRepo;
    private final Map<String, String> repoResponses = new ConcurrentHashMap<>();
    private TuiTestDriver tui;

    @BeforeEach void setUp() throws IOException {
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
        tui = new Pty4jTuiTestDriver(FIXTURE_POM, repoUrl, tempDir);
    }

    private void givenAllVersions() {
        givenVersions("org.assertj", "assertj-core", "3.25.0", "3.27.7");
        givenVersions("org.junit.jupiter", "junit-jupiter", "5.10.0", "6.0.3");
    }

    @Test void showsTitleAndTabs() {
        givenAllVersions();
        startTui();

        tui.awaitText("Maven Dependency Manager", ofSeconds(10));
        tui.awaitText("Dependencies", ofSeconds(2));
        tui.awaitText("[q]uit", ofSeconds(5));
    }

    @Test void scanCompletesAndShowsDependencies() {
        givenAllVersions();
        startTui();

        tui.awaitText("Scanning...", ofSeconds(10));
        tui.awaitText("selected", ofSeconds(10));
        tui.awaitText("assertj-core", ofSeconds(2));
        tui.awaitText("junit-jupiter", ofSeconds(2));
    }

    @Test void tabSwitching() {
        givenAllVersions();
        startTui();

        tui.awaitText("selected", ofSeconds(10));
        tui.type("]");
        tui.awaitText("Plugins", ofSeconds(2));
    }

    @Test void quitExitsCleanly() {
        givenAllVersions();
        startTui();

        tui.awaitText("Maven Dependency Manager", ofSeconds(10));
        tui.type("q");
        assertTrue(tui.waitForExit(ofSeconds(5)));
    }

    @Test void scanCompletesEvenWhenVersionLookupFails() {
        givenVersions("org.assertj", "assertj-core", "3.25.0", "3.27.7");
        // junit-jupiter metadata is missing — will get 404
        startTui();

        tui.awaitText("selected", ofSeconds(10));
        tui.awaitText("assertj-core", ofSeconds(2));
        tui.awaitText("3.27.7", ofSeconds(2));
        // junit-jupiter shows up but with update type "none"
        tui.awaitText("junit-jupiter", ofSeconds(2));
        tui.awaitText("none", ofSeconds(2));
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
