plugins {
  id("qupath.extension-conventions")
  id("qupath.javafx-conventions")
  id("qupath.publishing-conventions")
  `java-library`
}

extra["moduleName"] = "qupath.extension.mcp"

base {
  archivesName = "qupath-extension-mcp"
  description = "QuPath extension providing an MCP (Model Context Protocol) server for LLM integration"
}

dependencies {
  // Core QuPath modules are included via qupath.extension-conventions
  // - qupath-core
  // - qupath-core-processing
  // - qupath-gui-fx

  // Required by qupath_run_script for the Groovy JSR223 ScriptEngine.
  runtimeOnly(libs.bundles.groovy)

  // Testing
  testImplementation(libs.junit)
}

tasks.named<Jar>("jar") {
  manifest {
    attributes(
      "Automatic-Module-Name" to "qupath.extension.mcp",
      "Implementation-Title" to "QuPath MCP Extension",
      "Implementation-Version" to project.version
    )
  }
}
