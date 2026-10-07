# WorldPhase development

## WPH-001 scope

This bootstrap implements only Issue #1, under the Week 1 tracking Issue #16.
It adds a common, dedicated-server-safe `worldphase` entry point and a startup
log message. Packages under `dev.fangbm.worldphase` follow Technical Design
v0.1 section 4; `package-info.java` files reserve their responsibilities without
implementing PhaseId, registries, terrain, transfer, commands or tutorials.

## Toolchain

| Component | Pinned version |
| --- | --- |
| Minecraft | 26.1 |
| NeoForge | 26.1.0.19-beta |
| Java toolchain | 25 |
| ModDevGradle | 2.0.148 |
| Gradle wrapper | 9.2.1 |
| Foojay toolchain resolver | 1.0.0 |

The baseline follows the [official Minecraft 26.1 ModDevGradle MDK](https://github.com/NeoForgeMDKs/MDK-26.1-ModDevGradle/tree/15b9ce62ebd2ea80871cea2ebb1b6551ef972280).
The wrapper scripts/JAR and template license are copied from that revision.
The distribution SHA-256 comes from Gradle's official distribution endpoint.
[NeoForge Maven metadata](https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml)
confirms the pinned release for Minecraft 26.1 (hotfix 0).

[NeoForge's 26.1 release notes](https://neoforged.net/news/26.1release/)
explain the four-component version format and the Java 25 / Gradle 9.1+
requirements. `26.1.0.19-beta` concretizes the design's `26.1.x`; this is not
a switch to Minecraft 26.1.1 or 26.1.2. No compatibility hacks, Parchment,
mixins or access transformers are needed for this bootstrap.

Metadata follows the current MDK's `neoforge.mods.toml` format. Minecraft is
restricted to `[26.1]`, and NeoForge to `[26.1.0.19-beta,26.1.1)` so a different
Minecraft hotfix cannot silently satisfy the loader dependency. The project
uses `All Rights Reserved` because the repository has not chosen a project
license; `TEMPLATE_LICENSE.txt` applies to the upstream template only.

## Build

Install a JDK 25 and point `JAVA_HOME` at it. The wrapper uses `JAVA_HOME` (or
`java` on PATH) to launch Gradle; the Java 25 compile toolchain is configured
separately. Foojay can provision a missing compile toolchain once Gradle runs.
First builds require network access to Gradle, Maven and Minecraft downloads.

```sh
./gradlew build
```

On Windows PowerShell:

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-25'
.\gradlew.bat build
```

The mod JAR is written to `build/libs/worldphase-0.1.0-dev.jar`.

## Client

```sh
./gradlew runClient
```

Uses `run/client`. On first launch, complete Minecraft's initial accessibility
screen and check the main menu. The Mods screen should list WorldPhase.
Quit through the game menu to end the Gradle task normally.

## Dedicated server

```sh
./gradlew runServer
```

Uses `run/server` and `--nogui`. NeoForge 26.1's development runtime automatically
agrees to the EULA and uses offline mode; this Gradle run configuration is for
local development. A standalone installed server still requires the normal
Minecraft EULA acceptance and appropriate server settings. Wait for
`Done (...)! For help, type "help"`, then enter `stop` for a clean shutdown.
Server console input is forwarded by the Gradle run task. Any generated
`server.properties`, worlds and logs stay under the ignored game directory.

PowerShell equivalents are `.\gradlew.bat runClient` and
`.\gradlew.bat runServer`. IDEs can import the Gradle project; ModDevGradle
generates the launch configurations on sync. IDE configuration files and all
game directories remain local and are ignored by Git.

## WPH-001 validation record

Validated on 2026-10-07 (Asia/Shanghai), Windows x64, Temurin
25.0.3+9 and an NVIDIA RTX 5070 Ti Laptop GPU.

| Check | Command / action | Observed result |
| --- | --- | --- |
| Compile/package | `gradlew.bat build` | `BUILD SUCCESSFUL`; JAR created |
| Unix wrapper | `./gradlew build` in Git Bash | `BUILD SUCCESSFUL` (cached tasks); wrapper works with LF and executable bit |
| Client | `gradlew.bat runClient` | Welcome screen completed; actual main menu visually inspected, showing Minecraft 26.1 / NeoForge 26.1.0.19-beta |
| Mod discovery | Select WorldPhase on the Mods screen | `ModID: worldphase`, `Version: 0.1.0-dev`, `State: loaded`; common bootstrap log also present |
| Client shutdown | Click Quit Game | `Stopping!`, Gradle `BUILD SUCCESSFUL`, exit 0 |
| Dedicated server | `gradlew.bat runServer` | Initial new-world boot: `Done (1.870s)!`; second boot: `Done (0.274s)!`; bootstrap log on both |
| Server shutdown | Enter `stop` in the terminal | All dimensions saved, Gradle `BUILD SUCCESSFUL`, exit 0 on both runs |
| Packaging / hygiene | `jar tf build/libs/worldphase-0.1.0-dev.jar`, `git diff --cached --check`, `git check-ignore` | Only entry class and mod metadata in JAR; diff clean; build, IDE and game output ignored |

The first dependency download failed with a TLS handshake interruption for
`lwjgl-3.4.1-natives-windows-arm64.jar`. The successful initial build and first
client/server launches used this machine's existing HTTP proxy via command-line
JVM properties (`-Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=7890
-Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=7890`). No proxy settings are
committed. The subsequent Git Bash build and second server launch succeeded
without those properties, using the downloaded cache.

On the first server boot Minecraft logged a missing `server.properties` before
creating it and successfully starting. The second boot had no ERROR entries.
The second server smoke run used `server-ip=127.0.0.1` in the ignored local
properties file. Upstream command-ambiguity, beta rendering and Windows
performance-counter warnings did not prevent startup; the development server's
offline-mode warning is expected. No gameplay regression tests are added in
this bootstrap (`test NO-SOURCE`); those belong to WPH-012. This validates
development launch configurations, not a production server installation or any
future Phase gameplay.

No scope or target-stack deviation from the design documents was necessary.
Current official APIs/configuration worked directly. Exact versions and the
development EULA/offline behavior are documented above.

## Phase domain unit tests (WPH-002)

```sh
./gradlew test --tests dev.fangbm.worldphase.phase.PhaseDomainTest
./gradlew build
```

JUnit Jupiter 5.13.4 and Mockito 5.21.0 are test-only dependencies. The minimal
[ModDevGradle JUnit configuration](https://docs.neoforged.net/toolchain/docs/plugins/mdg/#unit-testing-with-junit)
enables Minecraft/NeoForge classes in tests, with WorldPhase as the tested mod.
Mockito uses its subclass mock maker to stub `ServerLevel.dimension()` without
an inline-mocking agent; NeoForge bootstraps its own test environment. The
`test` task is included in `build`; its HTML report is
`build/reports/tests/test/index.html`.

These tests validate the domain model and Level-key resolution. They do not
generate Phase worlds or test worldgen, transfer, commands or tutorials.
See [Phase domain API](PHASE_DOMAIN.md) for the contract and scope.

Validated on 2026-10-07 (Asia/Shanghai), Windows x64 / Temurin 25.0.3+9:

- Focused `PhaseDomainTest` run: **29 tests, 0 failures/errors/skips**.
- `./gradlew build` in Git Bash: **BUILD SUCCESSFUL**, including a fresh test
  execution with the same 29 passing cases and the packaged domain classes.
- JAR inspection confirms no test classes, mocking resources or test libraries
  are packaged. `git diff --check` passes; outputs remain ignored.

The initial test worker failed before test execution with
`ClassNotFoundException: worker.org.gradle.process.internal.worker.GradleWorkerMain`
under this machine's non-ASCII Gradle home. A separate ASCII cache directory
resolved worker startup. An ASCII Java temporary directory also avoided a
NeoForge self-attach error under the user temporary directory. The successful
build used these local environment values, outside the repository:

```powershell
$env:GRADLE_USER_HOME = 'D:\codex-temp\worldphase-gradle-ascii'
$env:JAVA_TOOL_OPTIONS = '-Djava.io.tmpdir=D:\codex-temp\worldphase-java-tmp'
```

Set `JAVA_HOME` to JDK 25 as above. These paths are machine-local validation
settings, not project requirements or committed Gradle configuration. Use your
own ASCII directories if encountering the same Windows worker/temporary-path
issue. The successful full build required no proxy command-line properties.

## Next issue

After WPH-002 review, the next issue is **#3 / WPH-003: Deterministic noise kernels**.
WPH-003 has not been started.
