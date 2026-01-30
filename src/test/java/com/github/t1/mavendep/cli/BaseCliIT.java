package com.github.t1.mavendep.cli;

import com.github.t1.mavendep.domain.Version;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;

abstract class BaseCliIT {
    @TempDir Path tempDir;

    private static final Path jarPath;
    private static final ThreadLocal<FakeRepo> fakeRepo = new ThreadLocal<>();

    static {
        jarPath = Paths.get("target/maven-dep-manager.jar").toAbsolutePath();
        if (!Files.exists(jarPath)) {
            throw new IllegalStateException("JAR file not found: " + jarPath + ". Run 'mvn package' first.");
        }
    }

    /// simulates a Maven Repo to fetch available versions from.
    ///
    /// We open a new {@link HttpServer} for every method, so it can have its own versions
    private static class FakeRepo {
        private final HttpServer httpServer;
        private final Map<String, String> responses = new ConcurrentHashMap<>();

        private FakeRepo() {
            try {
                httpServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
                httpServer.createContext("/", this::handle);
                httpServer.start();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        private void handle(HttpExchange exchange) {
            try (exchange) {
                var response = responses.get(exchange.getRequestURI().getPath());
                if (response == null) {
                    exchange.sendResponseHeaders(404, -1);
                } else {
                    var bytes = response.getBytes(UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/xml");
                    exchange.sendResponseHeaders(200, bytes.length);
                    exchange.getResponseBody().write(bytes);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        void givenMavenRepoVersions(String groupId, String artifactId, List<Version> versions) {
            var metadataXml = generateMetadataXml(groupId, artifactId, versions);
            var urlPath = "/" + groupId.replace('.', '/') + "/" + artifactId + "/maven-metadata.xml";
            responses.put(urlPath, metadataXml);
        }

        private String generateMetadataXml(String groupId, String artifactId, List<Version> versions) {
            var versionsXml = versions.stream().map(v -> "    <version>" + v + "</version>").collect(Collectors.joining("\n"));

            return """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <metadata>
                      <groupId>%s</groupId>
                      <artifactId>%s</artifactId>
                      <versioning>
                        <versions>
                    %s
                        </versions>
                      </versioning>
                    </metadata>
                    """.formatted(groupId, artifactId, versionsXml);
        }

        public int port() {return httpServer.getAddress().getPort();}
    }

    @BeforeEach void setUp() {
        fakeRepo.set(new FakeRepo());
    }

    void givenMavenRepoVersions(String groupId, String artifactId, List<Version> versions) {
        fakeRepo.get().givenMavenRepoVersions(groupId, artifactId, versions);
    }


    String runCli(String... args) throws IOException, InterruptedException {
        return runCli(null, args);
    }

    String runCli(Path workingDirectory, String... args) throws IOException, InterruptedException {
        return runCli(workingDirectory, false, args);
    }

    String runCli(Path workingDirectory, boolean redirectErrorStream, String... args) throws IOException, InterruptedException {
        var command = new ArrayList<String>();
        command.add("java");
        command.add("--enable-preview");
        command.add("-Dmaven.central.url=" + fakeMavenRepo());
        command.add("-Duser.home=" + tempDir);
        command.add("-jar");
        command.add(jarPath.toString());
        command.addAll(List.of(args));

        var processBuilder = new ProcessBuilder(command);
        processBuilder.redirectErrorStream(redirectErrorStream);
        if (workingDirectory != null) {
            processBuilder.directory(workingDirectory.toFile());
        }

        var process = processBuilder.start();
        var output = new StringBuilder();

        try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        var exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new RuntimeException("CLI process failed with exit code " + exitCode + ".\n" +
                                       "Command: " + String.join(" ", command) + ":\n" +
                                       output);
        }

        return output.toString();
    }

    static String fakeMavenRepo() {
        return "http://localhost:" + fakeRepo.get().port();
    }
}
