#!/bin/bash

# This script must be sourced, not executed
# Usage: source use-java23.sh

# Set JAVA_HOME to Java 23
export JAVA_HOME=/opt/homebrew/Cellar/openjdk/23.0.2/libexec/openjdk.jdk/Contents/Home

# Update PATH to use Java 23
export PATH=$JAVA_HOME/bin:$PATH

# Optional: Show confirmation if running interactively with verbose flag
if [ "$1" = "-v" ] || [ "$1" = "--verbose" ]; then
    echo "🔄 Switched to Java 23"
    echo "📍 JAVA_HOME: $JAVA_HOME"
    java -version 2>&1
fi