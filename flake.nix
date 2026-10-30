{
  description = "QuPath MCP Extension development environment";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-24.11";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = { self, nixpkgs, flake-utils, ... }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = nixpkgs.legacyPackages.${system};

        # JDK 21 LTS is required for QuPath (targets JDK 25 via toolchains)
        jdk = pkgs.jdk21;

        # Gradle with JDK 21
        gradle = pkgs.gradle.override { java = jdk; };
      in
      {
        devShells.default = pkgs.mkShell {
          name = "qupath-mcp-dev";

          buildInputs = with pkgs; [
            # JDK for building
            jdk

            # Build tools
            gradle

            # Development tools
            git

            # For MCP testing
            curl
            jq

            # Optional: MCP Inspector for testing (npm included with nodejs)
            nodejs_22
          ];

          # Set JAVA_HOME
          shellHook = ''
            export JAVA_HOME=${jdk}
            export PATH="$JAVA_HOME/bin:$PATH"

            # Configure Gradle to use our JDK
            export GRADLE_OPTS="-Dorg.gradle.java.home=$JAVA_HOME"

            echo "QuPath MCP Development Environment"
            echo "===================================="
            echo "Java: $(java -version 2>&1 | head -1)"
            echo "Gradle: $(gradle --version 2>&1 | grep 'Gradle' | head -1)"
            echo ""
            echo "Build commands:"
            echo "  ./gradlew build    # Build and test; JAR in build/libs/"
            echo ""
            echo "Test MCP server:"
            echo "  curl http://127.0.0.1:9527/health"
            echo "  npx @modelcontextprotocol/inspector      # MCP Inspector"
          '';
        };

        # Package the extension (optional)
        packages.default = pkgs.stdenv.mkDerivation {
          pname = "qupath-extension-mcp";
          version = "0.2.0";

          src = ./.;

          buildInputs = [ jdk gradle ];

          buildPhase = ''
            export JAVA_HOME=${jdk}
            export GRADLE_USER_HOME=$(mktemp -d)
            chmod +x gradlew
            ./gradlew build --no-daemon
          '';

          installPhase = ''
            mkdir -p $out/lib
            cp build/libs/qupath-extension-mcp-*.jar $out/lib/
          '';
        };
      }
    );
}
