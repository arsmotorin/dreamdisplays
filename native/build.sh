#!/bin/bash
set -e

command -v cmake >/dev/null || { echo "CMake not found"; exit 1; }
command -v make >/dev/null || { echo "Make not found"; exit 1; }

[ -z "$JAVA_HOME" ] && JAVA_HOME=$(/usr/libexec/java_home 2>/dev/null || true)

mkdir -p build
cd build

cmake ..
make
make install

ls -lh ../../common/src/main/resources/natives/ 2>/dev/null || echo "Files not found"
echo "Next: ./gradlew :fabric:build"
