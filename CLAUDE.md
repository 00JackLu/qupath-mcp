# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

QuPath uses Gradle with Kotlin DSL. Use the Gradle wrapper (`./gradlew` on Unix, `gradlew.bat` on Windows).

```bash
# Build the project (compiles, runs tests, creates distribution)
./gradlew build

# Run the application
./gradlew run

# Run tests only
./gradlew test

# Run a specific test class
./gradlew test --tests "qupath.lib.objects.TestPathObjects"

# Create native installers/packages
./gradlew jpackage

# Generate merged Javadocs
./gradlew mergedJavadocs

# Clean build artifacts
./gradlew clean
```

**Java Version:** QuPath targets JDK 25 (configured via Gradle toolchains). The build will automatically download the required JDK if not present.

## Module Architecture

QuPath is a multi-module Gradle project with clear separation of concerns:

```
qupath-app/                    # Main application launcher (qupath.QuPath main class)
├── qupath-gui-fx/            # JavaFX-based GUI
│   ├── qupath-core-processing/ # Image processing algorithms (OpenCV, ImageJ)
│   │   └── qupath-core/      # Core library (no UI dependencies)
│   │   └── qupath.lib.*      # Core packages: analysis, color, images, objects, roi, scripting
│   └── qupath-core/
└── qupath-extension-*/       # Bundled extensions (bioformats, openslide, script-editor, svg, processing)
```

**Key packages:**
- `qupath.lib.objects` - Pathology objects (annotations, detections, cells)
- `qupath.lib.images.servers` - ImageServer abstraction for image backends
- `qupath.lib.gui.extensions` - Extension API (QuPathExtension interface)
- `qupath.lib.scripting` - Groovy scripting support

**Dependency flow:** qupath-app → qupath-gui-fx → qupath-core-processing → qupath-core

## Extension Architecture

QuPath uses Java's ServiceLoader for plugin discovery:

1. **QuPathExtension** - Implement this interface for general extensions
   - `installExtension(QuPathGUI)` - Called when GUI starts
   - `installHeadless()` - Called for headless/script mode
   - Register in `META-INF/services/qupath.lib.gui.extensions.QuPathExtension`

2. **ImageServerBuilder** - Implement to add image format support
   - Register in `META-INF/services/qupath.lib.images.servers.ImageServerBuilder`

Extensions are discovered at runtime and can be added as separate JARs without modifying core code.

## Entry Points

- **GUI Mode:** `qupath.QuPath` → `QuPathApp.launch()` → `QuPathGUI` (singleton)
- **Headless/Script Mode:** `qupath.QuPath script <script.groovy> [options]`
- **TMA Viewer:** `qupath.QuPath --tma`

Command-line options:
```bash
./gradlew run --args="--help"           # Show all options
./gradlew run --args="script --help"    # Show script options
./gradlew run --args="script script.groovy -p project.qpproj"  # Run script on project
```

## Testing

- **Framework:** JUnit 5 (Jupiter)
- **Naming Convention:** Test files MUST be named `Test*.java` (enforced by checkstyle)
- **Location:** `*/src/test/java/` parallel to source directories
- Tests run automatically during `./gradlew build`

## Code Style

Checkstyle enforces these rules (`config/checkstyle/checkstyle.xml`):
- Correct modifier order
- No redundant or unused imports
- No starred imports (use full import lists)

**Conventions:**
- `CamelCase` with capital for classes, lowercase for methods/variables
- Always use `@Override` annotation when overriding methods
- UTF-8 encoding for all text files
- Javadoc required for all `public` and `protected` members
- Prefer JSON serialization via `qupath.lib.io.GsonTools` over Java serialization

## Key Files

| File | Purpose |
|------|---------|
| `VERSION` | Current version string |
| `gradle/libs.versions.toml` | All dependency versions |
| `buildSrc/` | Gradle convention plugins |
| `qupath.lib.gui.QuPathGUI` | Main GUI singleton class |
| `qupath.lib.gui.extensions.QuPathExtension` | Extension interface |
| `qupath.lib.images.servers.ImageServerBuilder` | Image server SPI |

## Maven Publishing

Published to SciJava Maven:
- Releases: `https://maven.scijava.org/content/repositories/releases`
- Snapshots: `https://maven.scijava.org/content/repositories/snapshots`
- Group ID: `io.github.qupath`
