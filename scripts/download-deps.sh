#!/bin/bash
# Download HexControl dependencies to libs directory
# Run this script when network is available to cache dependencies locally

set -e

LIBS_DIR="$(dirname "$0")/../libs"
mkdir -p "$LIBS_DIR"

LWJGL_VERSION="3.3.3"
JOML_VERSION="1.10.5"
GSON_VERSION="2.10.1"

# Detect OS for native libraries
case "$(uname -s)" in
    Linux*)     NATIVES="natives-linux";;
    Darwin*)
        if [ "$(uname -m)" = "arm64" ]; then
            NATIVES="natives-macos-arm64"
        else
            NATIVES="natives-macos"
        fi
        ;;
    MINGW*|CYGWIN*|MSYS*)
        if [ "$(uname -m)" = "x86_64" ]; then
            NATIVES="natives-windows"
        else
            NATIVES="natives-windows-x86"
        fi
        ;;
esac

MAVEN_CENTRAL="https://repo1.maven.org/maven2"

echo "Downloading LWJGL ${LWJGL_VERSION} dependencies..."

# LWJGL modules
LWJGL_MODULES="lwjgl lwjgl-glfw lwjgl-opengl lwjgl-stb lwjgl-nanovg"

for module in $LWJGL_MODULES; do
    echo "  Downloading ${module}..."
    curl -sL -o "$LIBS_DIR/${module}-${LWJGL_VERSION}.jar" \
        "${MAVEN_CENTRAL}/org/lwjgl/${module}/${LWJGL_VERSION}/${module}-${LWJGL_VERSION}.jar"

    echo "  Downloading ${module} natives (${NATIVES})..."
    curl -sL -o "$LIBS_DIR/${module}-${LWJGL_VERSION}-${NATIVES}.jar" \
        "${MAVEN_CENTRAL}/org/lwjgl/${module}/${LWJGL_VERSION}/${module}-${LWJGL_VERSION}-${NATIVES}.jar"
done

echo "Downloading JOML ${JOML_VERSION}..."
curl -sL -o "$LIBS_DIR/joml-${JOML_VERSION}.jar" \
    "${MAVEN_CENTRAL}/org/joml/joml/${JOML_VERSION}/joml-${JOML_VERSION}.jar"

echo "Downloading Gson ${GSON_VERSION}..."
curl -sL -o "$LIBS_DIR/gson-${GSON_VERSION}.jar" \
    "${MAVEN_CENTRAL}/com/google/code/gson/gson/${GSON_VERSION}/gson-${GSON_VERSION}.jar"

echo "Downloading JUnit 5.10.1 for testing..."
curl -sL -o "$LIBS_DIR/junit-jupiter-api-5.10.1.jar" \
    "${MAVEN_CENTRAL}/org/junit/jupiter/junit-jupiter-api/5.10.1/junit-jupiter-api-5.10.1.jar"
curl -sL -o "$LIBS_DIR/junit-jupiter-engine-5.10.1.jar" \
    "${MAVEN_CENTRAL}/org/junit/jupiter/junit-jupiter-engine/5.10.1/junit-jupiter-engine-5.10.1.jar"
curl -sL -o "$LIBS_DIR/junit-platform-commons-1.10.1.jar" \
    "${MAVEN_CENTRAL}/org/junit/platform/junit-platform-commons/1.10.1/junit-platform-commons-1.10.1.jar"
curl -sL -o "$LIBS_DIR/opentest4j-1.3.0.jar" \
    "${MAVEN_CENTRAL}/org/opentest4j/opentest4j/1.3.0/opentest4j-1.3.0.jar"

echo ""
echo "Dependencies downloaded to: $LIBS_DIR"
echo "You can now run: gradle build --offline"
