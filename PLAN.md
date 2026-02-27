# Plan: TUI E2E Testing with pty4j + JediTerm

## Approach

Launch the TUI app in a real pseudo-terminal (via pty4j), feed the PTY output into a
headless terminal emulator (JediTerm core), and assert on the emulated screen buffer.
Everything stays in Java/JUnit — tests run as ITs via maven-failsafe after packaging.

## Architecture

```
┌──────────────────┐     stdin (keystrokes)     ┌────────────────────┐
│  JUnit IT         │ ───────────────────────►  │  TUI app process    │
│                   │                            │  running in PTY     │
│  TuiTestDriver    │ ◄───────────────────────  │  (via pty4j)        │
│                   │     stdout (ANSI stream)  │                     │
└────────┬──────────┘                            └────────────────────┘
         │
         ▼
┌──────────────────┐
│  JediTerminal     │  Interprets ANSI/xterm escape sequences,
│  (headless)       │  updates TerminalTextBuffer
│                   │  (virtual screen grid: rows × cols)
└────────┬──────────┘
         │
         ▼
   Assertions on TerminalTextBuffer:
   "row 0 contains 'Maven Dependency Manager'"
   "selected tab is highlighted"
```

## Components to Build

### 1. `TuiTestDriver` (test driver interface)

Location: `src/test/java/com/github/t1/mavendep/tui/e2e/TuiTestDriver.java`

Decouples the test logic from the PTY + emulator wiring:

```java
interface TuiTestDriver extends AutoCloseable {
    void type(String text);
    void pressKey(Key key);
    String getLine(int row);
    boolean hasText(String text);
    void awaitText(String text, Duration timeout);
    boolean waitForExit(Duration timeout);
}
```

```java
enum Key {
    UP("\033[A"), DOWN("\033[B"), LEFT("\033[D"), RIGHT("\033[C"),
    TAB("\t"), SHIFT_TAB("\033[Z"),
    ENTER("\r"), ESCAPE("\033"),
    HOME("\033[H"), END("\033[F");

    private final byte[] sequence;
}
```

### 2. `Pty4jTuiTestDriver` (pty4j + JediTerm implementation)

Location: `src/test/java/com/github/t1/mavendep/tui/e2e/Pty4jTuiTestDriver.java`

Implements `TuiTestDriver`:
- **Launch**: `PtyProcessBuilder` starts the JAR in a PTY
  - Sets `TERM=xterm-256color`, configures 100×30
  - Command: `java --enable-preview -jar target/maven-dep-manager.jar tui <fixture-pom>`
- **Feed**: background virtual thread reads PTY stdout, decodes UTF-8 to `char[]`,
  feeds into `JediEmulator` via `ArrayTerminalDataStream`:
  ```java
  var stream = new ArrayTerminalDataStream(data.toCharArray());
  var emulator = new JediEmulator(stream, terminal);
  while (emulator.hasNext()) emulator.next();
  ```
- **Input**: `type()` / `pressKey()` write bytes to PTY stdin
- **Query**: `getLine()` / `hasText()` read from `TerminalTextBuffer`
- **Wait**: `awaitText()` polls with 50ms sleeps until match or timeout
- **Cleanup**: `close()` sends 'q', waits briefly, then destroys process

### 3. `NoOpTerminalDisplay`

Location: `src/test/java/com/github/t1/mavendep/tui/e2e/NoOpTerminalDisplay.java`

Implements JediTerm's `TerminalDisplay` with no-ops (modeled after JediTerm's
own `BackBufferDisplay` from their test utilities). ~12 stub methods, no Swing.

### 4. Maven dependencies

```xml
<repository>
    <id>jetbrains-intellij-dependencies</id>
    <url>https://packages.jetbrains.team/maven/p/ij/intellij-dependencies</url>
</repository>
```

```xml
<dependency>
    <groupId>org.jetbrains.pty4j</groupId>
    <artifactId>pty4j</artifactId>
    <version>0.13.10</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.jetbrains.jediterm</groupId>
    <artifactId>jediterm-core</artifactId>
    <version>3.63</version>
    <scope>test</scope>
</dependency>
```

Transitive: kotlin-stdlib, slf4j-api, JNA. No Swing/AWT.

### 5. Test fixture

`src/test/resources/tui-e2e-fixture/pom.xml`:
- Small Maven project with 2-3 dependencies that have known updates
- Ensures predictable scan results for assertions

### 6. Test class

`src/test/java/com/github/t1/mavendep/tui/e2e/TuiDashboardIT.java`

```java
class TuiDashboardIT {
    TuiTestDriver tui;

    @BeforeEach void setUp() {
        tui = new Pty4jTuiTestDriver(fixturePom);
    }

    @AfterEach void tearDown() { tui.close(); }

    @Test void showsTitleAndTabs() {
        tui.awaitText("Maven Dependency Manager", seconds(10));
        tui.awaitText("Dependencies", seconds(2));
        tui.awaitText("[q]uit", seconds(2));
    }

    @Test void scanCompletesAndShowsDependencies() {
        tui.awaitText("Scanning...", seconds(10));
        tui.awaitText("selected", seconds(30));
        tui.awaitText("junit", seconds(2));
    }

    @Test void tabSwitching() {
        tui.awaitText("selected", seconds(30));
        tui.type("]");
        tui.awaitText("Plugins", seconds(2));
    }

    @Test void quitExitsCleanly() {
        tui.awaitText("Maven Dependency Manager", seconds(10));
        tui.type("q");
        assertTrue(tui.waitForExit(seconds(5)));
    }
}
```

## Risks & Limitations

- **JetBrains Maven repo**: `jediterm-core` is not on Maven Central; requires
  adding `packages.jetbrains.team` repository.
- **Kotlin transitive**: both libraries pull in kotlin-stdlib.
- **JediTerm API stability**: headless usage is not the primary use case;
  API may change across versions.
- **Test speed**: each test spawns a JVM in a PTY + scans dependencies.
- **Fixture network dependency**: scanning may hit Maven Central.
  Consider pre-warming the local cache.
