plugins {
    // Bundles any non-QuPath dependencies into the extension JAR (there are none today)
    id("com.gradleup.shadow") version "8.3.5"
    id("qupath-conventions")
}

qupathExtension {
    name = "qupath-extension-mcp"
    group = "io.github.00jacklu"
    version = "0.2.1"
    description = "QuPath extension providing an MCP (Model Context Protocol) server for AI agents"
    automaticModule = "qupath.extension.mcp"
}

dependencies {
    // Provided by QuPath at runtime, so not bundled into the JAR
    shadow(libs.bundles.qupath)
    shadow(libs.bundles.logging)
    // Required by qupath_run_script for the Groovy JSR223 ScriptEngine
    shadow(libs.bundles.groovy)

    testImplementation(libs.bundles.qupath)
    testRuntimeOnly(libs.bundles.groovy)
    testImplementation(libs.junit)
    // Gradle 9 no longer adds the launcher implicitly; the version comes from the JUnit BOM
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
