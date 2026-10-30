# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

QuPath-MCP is a standalone QuPath extension (one JAR) that embeds a Model Context Protocol server inside
QuPath. Users install it by dragging the JAR onto QuPath 0.7.0. The repository holds only the extension
plus its docs, examples, benchmark, paper and docs site — QuPath itself is a dependency, not source here.

## Build Commands

```bash
./gradlew build    # compile, run unit tests, build build/libs/qupath-extension-mcp-<version>.jar
./gradlew test     # unit tests only (no running QuPath needed)
./gradlew test --tests "qupath.lib.extension.mcp.protocol.TestMcpProtocolHandler"
```

The build uses QuPath's Gradle plugins (`io.github.qupath.qupath-extension-settings` in
`settings.gradle.kts`, `qupath-conventions` in `build.gradle.kts`), which supply the `libs` version catalog
and the JDK 25 toolchain. The target QuPath version is set in `settings.gradle.kts`.

There is no `./gradlew run`: to try a change, build the JAR, drag it onto a QuPath 0.7.0 window, fully
restart QuPath, then run `./test-mcp-server.sh` against the live server on port 9527.

## Layout

```
src/main/java/qupath/lib/extension/mcp/
├── McpExtension.java        # QuPathExtension entry point (menu, auto-start)
├── McpServer.java           # wires the tool registry, protocol handler and transports
├── McpConfiguration.java    # env vars / system properties / mcp.properties
├── protocol/                # JSON-RPC + MCP protocol handling, tool/result models
├── tools/                   # the MCP tools, grouped by area (Image, Object, Analysis, Script, ...)
├── transport/               # Streamable HTTP (java.net.ServerSocket) and stdio
└── util/
src/main/resources/META-INF/services/qupath.lib.gui.extensions.QuPathExtension
docs/ examples/ benchmark/ paper/ site/   # documentation, workflows, benchmark, manuscript, Pages site
```

- Extensions are discovered via `ServiceLoader`; keep the service file in sync with `McpExtension`.
- Every tool declares a side-effect hint (read-only / write / destructive); keep `docs/tools.md` in sync
  when adding or changing tools.
- The HTTP server must stay on `java.base` only — QuPath's bundled runtime has no `jdk.httpserver`.

## Releases

Bump the version in `build.gradle.kts`, `.zenodo.json` and `CITATION.cff`, then push a tag `mcp-v<version>`.
`.github/workflows/release.yml` verifies the versions match, builds, and publishes a GitHub release with the
JAR; Zenodo archives it and mints a DOI. `.github/workflows/build.yml` runs on pushes and PRs to `main`.

## Code Style

- Tests are named `Test*.java` and use JUnit 5.
- No starred or unused imports; always use `@Override`.
- Javadoc for all `public` and `protected` members.
- Prefer JSON via `qupath.lib.io.GsonTools`.
