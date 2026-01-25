#!/bin/bash

# Build script for hitorro-jsonts-mongo

set -e

echo "Building hitorro-jsonts-mongo..."

# Clean and build
mvn clean install

echo "Build complete!"
echo ""
echo "To run tests only: mvn test"
echo "To skip tests: mvn clean install -DskipTests"
