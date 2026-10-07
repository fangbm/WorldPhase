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

## Next issue

After WPH-001 review, proceed to **#2 / WPH-002: Define Phase domain model**.
No later issue is implemented by this bootstrap.
